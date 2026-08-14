package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Producto;
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

    public List<String> listarCategorias() {
        return productoRepository.findAll()
                .stream()
                .map(Producto::getCategoria)
                .filter(cat -> cat != null && !cat.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public Producto obtenerPorId(Long id) {
        return productoRepository.findById(id).orElse(null);
    }

    // Fase 2: nuevo método para el detalle con imágenes y variantes
    public Producto obtenerDetalleProducto(Long id) {
        return productoRepository.findByIdWithImagenesAndVariantes(id).orElse(null);
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

    /**
     * Obtiene productos relacionados de la misma categoría, excluyendo el producto actual.
     * Utiliza la categoría proporcionada para evitar una consulta adicional al producto.
     *
     * @param productoId ID del producto a excluir
     * @param categoria  Categoría para filtrar productos relacionados
     * @param limite     Número máximo de productos a devolver (recomendado: 5)
     * @return Lista de productos relacionados (vacía si no hay o si los parámetros son inválidos)
     */
    public List<Producto> obtenerRelacionados(Long productoId, String categoria, int limite) {
        if (productoId == null || categoria == null || categoria.trim().isEmpty() || limite < 1) {
            return new ArrayList<>();
        }
        Pageable pageable = PageRequest.of(0, limite);
        return productoRepository.findRelacionados(productoId, categoria.trim(), pageable);
    }

    /**
     * Método existente que obtiene productos relacionados a partir del ID del producto.
     * Ahora usa el nuevo método para evitar duplicar la lógica y no cargar la categoría completa.
     *
     * @param productoId ID del producto
     * @param limite     Número máximo de productos a devolver
     * @return Lista de productos relacionados
     */
    public List<Producto> obtenerRelacionados(Long productoId, int limite) {
        Producto producto = obtenerPorId(productoId);
        if (producto == null) {
            return new ArrayList<>();
        }
        return obtenerRelacionados(productoId, producto.getCategoria(), limite);
    }
}