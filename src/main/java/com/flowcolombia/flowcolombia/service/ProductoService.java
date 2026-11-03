package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Producto;
import com.flowcolombia.flowcolombia.ProductoImagen;
import com.flowcolombia.flowcolombia.ProductoImagenRepository;
import com.flowcolombia.flowcolombia.ProductoRepository;
import com.flowcolombia.flowcolombia.VarianteProducto;
import com.flowcolombia.flowcolombia.VarianteProductoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final ProductoImagenRepository productoImagenRepository;
    private final VarianteProductoRepository varianteProductoRepository;

    public ProductoService(ProductoRepository productoRepository,
                           ProductoImagenRepository productoImagenRepository,
                           VarianteProductoRepository varianteProductoRepository) {
        this.productoRepository = productoRepository;
        this.productoImagenRepository = productoImagenRepository;
        this.varianteProductoRepository = varianteProductoRepository;
    }

    // ============================================================
    // MÉTODOS EXISTENTES (sin cambios)
    // ============================================================
    public List<Producto> listarTodos() {
        return productoRepository.findAll();
    }

    // Fase 1: ahora usa findByCategoria con @EntityGraph
    public List<Producto> listarPorCategoria(String categoria) {
        return productoRepository.findByCategoria(categoria);
    }

    /**
     * Categorías distintas. Se resuelven en la BD con un SELECT DISTINCT:
     * antes se traía la tabla completa de productos en cada carga de la
     * home solo para quedarse con 4 cadenas.
     */
    public List<String> listarCategorias() {
        return productoRepository.findCategoriasDistintas();
    }

    public Producto obtenerPorId(Long id) {
        return productoRepository.findById(id).orElse(null);
    }

    /**
     * Imagen de reemplazo cuando el producto no tiene ninguna imagen
     * (ni en ProductoImagen ni en los campos legacy imagen1..imagen6).
     */
    private static final String IMAGEN_FALLBACK =
            "https://res.cloudinary.com/bcpwhn6o/image/upload/v1783699365/"
            + "1780606143WhatsApp_Image_2026-06-04_at_3.18.44_PM_hygiom.jpg";

    /**
     * DTO plano (sin entidades JPA) con todo lo que la vista de detalle
     * necesita. Se construye dentro de la transacción, de modo que la
     * plantilla nunca toca una colección Lazy ni serializa entidades con
     * relaciones bidireccionales (Producto -> VarianteProducto -> Producto),
     * que era lo que rompía el JavaScript con Thymeleaf.
     */
    public record DetalleProductoVista(
            Producto producto,
            List<Map<String, Object>> variantes,
            List<Map<String, Object>> imagenes,
            List<String> imagenesLegacy,
            String imagenPrincipal
    ) {
    }

    /**
     * Carga el producto con sus imágenes y sus variantes inicializadas.
     *
     * Las dos colecciones son "bags" (@OneToMany List) y no se pueden traer
     * en el mismo JOIN FETCH sin lanzar MultipleBagFetchException, así que se
     * resuelven en dos consultas dentro de la MISMA transacción: la segunda
     * reutiliza la misma instancia gestionada del Persistence Context y
     * termina de inicializar sus colecciones. Además se fuerza la
     * inicialización de forma defensiva, para que el Controlador/Thymeleaf
     * nunca reciban una colección Lazy sin sesión abierta.
     */
    @Transactional(readOnly = true)
    public Producto obtenerDetalleProducto(Long id) {
        if (id == null) {
            return null;
        }

        // 1. Producto + imágenes (JOIN FETCH p.imagenes)
        Producto producto = productoRepository.findDetalleById(id).orElse(null);
        if (producto == null) {
            return null;
        }

        // 2. Variantes en el mismo Persistence Context (JOIN FETCH p.variantes)
        productoRepository.findWithVariantes(id).ifPresent(this::inicializarColecciones);

        // 3. Red de seguridad: inicializar lo que falte dentro de la transacción
        inicializarColecciones(producto);

        return producto;
    }

    /**
     * Fuerza la carga de las colecciones Lazy mientras la sesión sigue abierta.
     * Acceder a size() es suficiente: Hibernate ejecuta el SELECT pendiente.
     */
    private void inicializarColecciones(Producto producto) {
        if (producto == null) {
            return;
        }
        if (producto.getImagenes() != null) {
            producto.getImagenes().size();
        }
        if (producto.getVariantes() != null) {
            producto.getVariantes().size();
        }
    }

    /**
     * Versión "lista para pintar" del detalle: producto con colecciones
     * inicializadas + listas planas de Mapas para el JavaScript de la vista.
     *
     * Las variantes se envían con exactamente los campos que consume el
     * frontend (sku, color, talla, precio, stock, activo), sin el backreference
     * a Producto que provocaba el bucle infinito de serialización.
     *
     * @param id identificador del producto
     * @return la vista o {@code null} si el producto no existe
     */
    @Transactional(readOnly = true)
    public DetalleProductoVista obtenerDetalleVista(Long id) {
        Producto producto = obtenerDetalleProducto(id);
        if (producto == null) {
            return null;
        }

        // ------------------------------------------------------------
        // VARIANTES -> lista de mapas planos
        // ------------------------------------------------------------
        List<Map<String, Object>> variantesDTO = new ArrayList<>();
        if (producto.getVariantes() != null) {
            for (VarianteProducto variante : producto.getVariantes()) {
                if (variante == null) {
                    continue;
                }
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("sku", variante.getSku() != null ? variante.getSku() : "");
                dto.put("color", variante.getColor() != null ? variante.getColor() : "");
                dto.put("talla", variante.getTalla() != null ? variante.getTalla() : "");
                dto.put("precio", variante.getPrecio());
                dto.put("stock", variante.getStock() != null ? variante.getStock() : 0);
                dto.put("activo", Boolean.TRUE.equals(variante.getActivo()));
                variantesDTO.add(dto);
            }
        }

        // ------------------------------------------------------------
        // IMÁGENES -> ProductoImagen es la fuente principal
        // ------------------------------------------------------------
        List<Map<String, Object>> imagenesDTO = new ArrayList<>();
        if (producto.getImagenes() != null) {
            for (ProductoImagen imagen : producto.getImagenes()) {
                if (imagen == null || imagen.getUrl() == null || imagen.getUrl().isBlank()) {
                    continue;
                }
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("url", imagen.getUrl());
                dto.put("orden", imagen.getOrden() != null ? imagen.getOrden() : 0);
                imagenesDTO.add(dto);
            }
        }

        // ------------------------------------------------------------
        // Retrocompatibilidad: campos legacy imagen1..imagen6
        // (solo se usan si todavía no hay filas en producto_imagen)
        // ------------------------------------------------------------
        List<String> imagenesLegacy = new ArrayList<>();
        if (imagenesDTO.isEmpty()) {
            String[] legacy = {
                    producto.getImagen1(), producto.getImagen2(), producto.getImagen3(),
                    producto.getImagen4(), producto.getImagen5(), producto.getImagen6()
            };
            for (String url : legacy) {
                if (url != null && !url.isBlank()) {
                    imagenesLegacy.add(url);
                }
            }
        }

        String imagenPrincipal;
        if (!imagenesDTO.isEmpty()) {
            imagenPrincipal = String.valueOf(imagenesDTO.get(0).get("url"));
        } else if (!imagenesLegacy.isEmpty()) {
            imagenPrincipal = imagenesLegacy.get(0);
        } else {
            imagenPrincipal = IMAGEN_FALLBACK;
        }

        return new DetalleProductoVista(producto, variantesDTO, imagenesDTO, imagenesLegacy, imagenPrincipal);
    }

    public Producto obtenerPorSku(String sku) {
        return productoRepository.findBySku(sku).orElse(null);
    }

    public Producto guardar(Producto producto) {
        return productoRepository.save(producto);
    }

    public void eliminar(Long id) {
        productoImagenRepository.deleteByProductoId(id);
        varianteProductoRepository.deleteByProductoId(id);
        productoRepository.deleteById(id);
    }

    public long contarTodos() {
        return productoRepository.count();
    }

    public VarianteProducto obtenerVariantePorSku(String sku) {
        if (sku == null || sku.trim().isEmpty()) {
            return null;
        }
        return varianteProductoRepository.findBySku(sku.trim()).orElse(null);
    }

    // ============================================================
    // GUARDAR PRODUCTO CON VARIANTES (TRANSACCIONAL)
    // ============================================================
    @Transactional
    public Producto guardarProductoConVariantes(Producto productoForm,
                                                List<String> varianteSku,
                                                List<String> varianteColor,
                                                List<String> varianteTalla,
                                                List<Double> variantePrecio,
                                                List<Double> varianteCosto,
                                                List<Integer> varianteStock,
                                                List<Double> variantePeso,
                                                List<Boolean> varianteActivo,
                                                List<Long> varianteId) {

        // 1. Obtener la entidad gestionada (producto real)
        Producto productoGuardado;
        if (productoForm.getId() != null) {
            productoGuardado = productoRepository.findById(productoForm.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado con ID: " + productoForm.getId()));

            // Copiar campos editables
            productoGuardado.setNombre(productoForm.getNombre());
            productoGuardado.setSku(productoForm.getSku());
            productoGuardado.setPrecio(productoForm.getPrecio());
            productoGuardado.setCategoria(productoForm.getCategoria());
            productoGuardado.setDescripcion(productoForm.getDescripcion());
            productoGuardado.setDescripcionLarga(productoForm.getDescripcionLarga());
            productoGuardado.setLargo(productoForm.getLargo());
            productoGuardado.setAncho(productoForm.getAncho());
            productoGuardado.setAlto(productoForm.getAlto());
            productoGuardado.setPeso(productoForm.getPeso());
            productoGuardado.setTieneColor(productoForm.getTieneColor());
            productoGuardado.setTieneTalla(productoForm.getTieneTalla());
            productoGuardado.setTieneVariaciones(productoForm.getTieneVariaciones());
            productoGuardado.setVariacionesDisponibles(productoForm.getVariacionesDisponibles());
            productoGuardado.setImagen1(productoForm.getImagen1());
            productoGuardado.setImagen2(productoForm.getImagen2());
            productoGuardado.setImagen3(productoForm.getImagen3());
            productoGuardado.setImagen4(productoForm.getImagen4());
            productoGuardado.setImagen5(productoForm.getImagen5());
            productoGuardado.setImagen6(productoForm.getImagen6());
        } else {
            productoGuardado = productoRepository.save(productoForm);
        }

        // 2. Validar longitud de listas
        int size = (varianteSku != null) ? varianteSku.size() : 0;

        if (varianteId != null && varianteId.size() != size) {
            throw new IllegalArgumentException("Los datos de variantes enviados por el formulario están incompletos o desalineados.");
        }
        if (varianteColor != null && varianteColor.size() != size) {
            throw new IllegalArgumentException("Los datos de variantes enviados por el formulario están incompletos o desalineados.");
        }
        if (varianteTalla != null && varianteTalla.size() != size) {
            throw new IllegalArgumentException("Los datos de variantes enviados por el formulario están incompletos o desalineados.");
        }
        if (variantePrecio != null && variantePrecio.size() != size) {
            throw new IllegalArgumentException("Los datos de variantes enviados por el formulario están incompletos o desalineados.");
        }
        if (varianteCosto != null && varianteCosto.size() != size) {
            throw new IllegalArgumentException("Los datos de variantes enviados por el formulario están incompletos o desalineados.");
        }
        if (varianteStock != null && varianteStock.size() != size) {
            throw new IllegalArgumentException("Los datos de variantes enviados por el formulario están incompletos o desalineados.");
        }
        if (variantePeso != null && variantePeso.size() != size) {
            throw new IllegalArgumentException("Los datos de variantes enviados por el formulario están incompletos o desalineados.");
        }
        if (varianteActivo != null && varianteActivo.size() != size) {
            throw new IllegalArgumentException("Los datos de variantes enviados por el formulario están incompletos o desalineados.");
        }

        // 3. Obtener mapa de variantes actuales
        List<VarianteProducto> variantesActuales = new ArrayList<>(productoGuardado.getVariantes());
        Map<Long, VarianteProducto> mapaPorId = variantesActuales.stream()
                .collect(Collectors.toMap(VarianteProducto::getId, v -> v, (a, b) -> a));

        // 4. Procesar filas (validación de SKU duplicado en formulario)
        Set<String> skusEnFormulario = new HashSet<>();
        Set<Long> idsMantenidos = new HashSet<>();
        List<VarianteProducto> nuevasVariantes = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            String sku = (i < varianteSku.size()) ? varianteSku.get(i) : null;
            if (sku == null || sku.trim().isEmpty()) continue;

            String skuNormalizado = sku.trim().toUpperCase();
            if (skusEnFormulario.contains(skuNormalizado)) {
                throw new IllegalArgumentException("SKU duplicado en el formulario: " + sku);
            }
            skusEnFormulario.add(skuNormalizado);

            Long id = (varianteId != null && i < varianteId.size()) ? varianteId.get(i) : null;
            String color = (varianteColor != null && i < varianteColor.size()) ? varianteColor.get(i) : "";
            String talla = (varianteTalla != null && i < varianteTalla.size()) ? varianteTalla.get(i) : "";

            Double precio = (variantePrecio != null && i < variantePrecio.size() && variantePrecio.get(i) != null)
                    ? variantePrecio.get(i)
                    : productoGuardado.getPrecio();

            Double costo = (varianteCosto != null && i < varianteCosto.size() && varianteCosto.get(i) != null)
                    ? varianteCosto.get(i)
                    : 0.0;

            Integer stock = (varianteStock != null && i < varianteStock.size() && varianteStock.get(i) != null)
                    ? varianteStock.get(i)
                    : 0;

            Double peso = (variantePeso != null && i < variantePeso.size() && variantePeso.get(i) != null)
                    ? variantePeso.get(i)
                    : null;

            Boolean activo = (varianteActivo != null && i < varianteActivo.size() && varianteActivo.get(i) != null)
                    ? varianteActivo.get(i)
                    : true;

            // CASO 1: VARIANTE EXISTENTE (con ID)
            if (id != null) {
                VarianteProducto varianteExistente = mapaPorId.get(id);
                if (varianteExistente == null) {
                    throw new IllegalArgumentException("La variante con ID " + id + " no pertenece a este producto.");
                }

                varianteExistente.setSku(sku.trim());
                varianteExistente.setColor(color.trim());
                varianteExistente.setTalla(talla.trim());
                varianteExistente.setPrecio(precio);
                varianteExistente.setCosto(costo);
                varianteExistente.setStock(stock);
                varianteExistente.setPeso(peso);
                varianteExistente.setActivo(activo);

                validarVariante(varianteExistente);
                idsMantenidos.add(id);
            }
            // CASO 2: VARIANTE NUEVA (sin ID)
            else {
                VarianteProducto nueva = new VarianteProducto();
                nueva.setProducto(productoGuardado);
                nueva.setSku(sku.trim());
                nueva.setColor(color.trim());
                nueva.setTalla(talla.trim());
                nueva.setPrecio(precio);
                nueva.setCosto(costo);
                nueva.setStock(stock);
                nueva.setPeso(peso);
                nueva.setActivo(activo);

                validarVariante(nueva);
                nuevasVariantes.add(nueva);
            }
        }

        // 5. ELIMINAR variantes que ya no están en el formulario
        List<VarianteProducto> variantesAEliminar = variantesActuales.stream()
                .filter(v -> v.getId() != null && !idsMantenidos.contains(v.getId()))
                .collect(Collectors.toList());

        if (!variantesAEliminar.isEmpty()) {
            productoGuardado.getVariantes().removeAll(variantesAEliminar);
            varianteProductoRepository.deleteAll(variantesAEliminar);
            varianteProductoRepository.flush();
        }

        // 6. AGREGAR nuevas variantes
        for (VarianteProducto nueva : nuevasVariantes) {
            productoGuardado.getVariantes().add(nueva);
        }

        // 7. GUARDAR PRODUCTO (CASCADE ALL)
        Producto resultado = productoRepository.save(productoGuardado);
        productoRepository.flush();

        return resultado;
    }

    // ============================================================
    // VALIDACIÓN DE VARIANTE
    // ============================================================
    private void validarVariante(VarianteProducto variante) {
        List<String> errores = new ArrayList<>();

        if (variante.getSku() == null || variante.getSku().trim().isEmpty()) {
            errores.add("El SKU de la variante es obligatorio.");
        } else {
            String sku = variante.getSku().trim();
            variante.setSku(sku);

            if (variante.getId() == null) {
                if (varianteProductoRepository.existsBySku(sku)) {
                    errores.add("El SKU '" + sku + "' ya está en uso por otra variante.");
                }
            } else {
                if (varianteProductoRepository.existsBySkuAndIdNot(sku, variante.getId())) {
                    errores.add("El SKU '" + sku + "' ya está en uso por otra variante.");
                }
            }
        }

        if (variante.getPrecio() == null || variante.getPrecio() <= 0) {
            errores.add("El precio de la variante debe ser mayor a 0.");
        }

        if (variante.getStock() == null || variante.getStock() < 0) {
            errores.add("El stock de la variante no puede ser negativo.");
        }

        if (variante.getCosto() != null && variante.getCosto() < 0) {
            errores.add("El costo de la variante no puede ser negativo.");
        }

        if (variante.getPeso() != null && variante.getPeso() < 0) {
            errores.add("El peso de la variante no puede ser negativo.");
        }

        if (!errores.isEmpty()) {
            throw new IllegalArgumentException("Errores en la variante " + variante.getSku() + ": " + String.join("; ", errores));
        }
    }

    // ============================================================
    // PRODUCTOS RELACIONADOS (OPTIMIZADO)
    // ============================================================

    public List<Producto> obtenerRelacionados(Long productoId, String categoria, int limite) {
        if (productoId == null || categoria == null || categoria.trim().isEmpty() || limite < 1) {
            return new ArrayList<>();
        }
        Pageable pageable = PageRequest.of(0, limite);
        return productoRepository.findRelacionados(productoId, categoria.trim(), pageable);
    }

    public List<Producto> obtenerRelacionados(Long productoId, int limite) {
        Producto producto = obtenerPorId(productoId);
        if (producto == null) {
            return new ArrayList<>();
        }
        return obtenerRelacionados(productoId, producto.getCategoria(), limite);
    }
}