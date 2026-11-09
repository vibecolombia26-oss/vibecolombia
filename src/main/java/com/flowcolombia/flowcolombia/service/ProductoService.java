package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Producto;
import com.flowcolombia.flowcolombia.ProductoImagen;
import com.flowcolombia.flowcolombia.ProductoImagenRepository;
import com.flowcolombia.flowcolombia.ProductoRepository;
import com.flowcolombia.flowcolombia.SlugUtil;
import com.flowcolombia.flowcolombia.VarianteProducto;
import com.flowcolombia.flowcolombia.VarianteProductoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
     *
     * @param esCalzado decide qué experiencia de ficha se pinta: guía de
     *                  tallas + confirmación (calzado) o beneficios + sellos
     *                  de garantía (el resto). Ver {@link #esCalzado}.
     */
    public record DetalleProductoVista(
            Producto producto,
            List<Map<String, Object>> variantes,
            List<Map<String, Object>> imagenes,
            List<String> imagenesLegacy,
            String imagenPrincipal,
            boolean esCalzado
    ) {
    }

    // ============================================================
    // CRO: ¿es calzado?
    // ============================================================

    /**
     * Palabras de categoría que identifican calzado. Se comparan sin tildes y
     * en minúsculas, así «Calzado», «CALZADO DEPORTIVO» y «Calzadó» caen en el
     * mismo sitio.
     */
    private static final List<String> PALABRAS_CALZADO = List.of(
            "calzado", "zapato", "zapatilla", "tenis", "sneaker",
            "bota", "sandalia", "mocasin", "chancla", "alpargata");

    /** Primera talla de dos cifras del texto ({@code "40"}, {@code "38.5"} → 38). */
    private static final Pattern TALLA_NUMERICA = Pattern.compile("(\\d{2})");

    /** Rango de numeración de calzado: infantil (15-33) y adulto (34-48). */
    private static final int TALLA_MIN = 15;
    private static final int TALLA_MAX = 48;

    /**
     * ¿La ficha debe usar la experiencia de calzado?
     *
     * <p>Se decide sin tocar la base de datos ni añadir campos: con lo que ya
     * viene en la vista.</p>
     *
     * <ol>
     *   <li><strong>Debe tener tallas.</strong> Sin tallas no hay guía que
     *       consultar ni confirmación que firmar; si no, el botón de compra
     *       quedaría bloqueado sin motivo.</li>
     *   <li><strong>Categoría de calzado</strong> («Calzado», «Zapatos»,
     *       «Tenis», «Botas»…).</li>
     *   <li>O, como respaldo, <strong>tallas numéricas de calzado</strong>
     *       (15-48): un producto con tallas «38, 39, 40» es calzado aunque su
     *       categoría esté mal escrita. La ropa con S/M/L o con tallas 2, 4, 6
     *       no entra aquí.</li>
     * </ol>
     *
     * @param categoria categoría del producto (puede ser null)
     * @param variantes DTO planos de variantes de la vista
     * @return {@code true} si la ficha debe mostrar la guía de tallas
     */
    public static boolean esCalzado(String categoria, List<Map<String, Object>> variantes) {
        boolean tieneTallas = false;
        boolean tallaDeCalzado = false;

        if (variantes != null) {
            for (Map<String, Object> variante : variantes) {
                if (variante == null) {
                    continue;
                }
                Object talla = variante.get("talla");
                if (talla == null || String.valueOf(talla).isBlank()) {
                    continue;
                }
                tieneTallas = true;
                if (numeroDeCalzado(String.valueOf(talla)) != null) {
                    tallaDeCalzado = true;
                }
            }
        }

        if (!tieneTallas) {
            return false;
        }
        return esCategoriaDeCalzado(categoria) || tallaDeCalzado;
    }

    /** ¿La categoría habla de calzado? (sin tildes, minúsculas) */
    static boolean esCategoriaDeCalzado(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            return false;
        }
        String limpia = Normalizer.normalize(categoria, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        for (String palabra : PALABRAS_CALZADO) {
            if (limpia.contains(palabra)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Número de talla de calzado contenido en el texto, o {@code null}.
     * «40», «40.5» y «Talla 40» devuelven 40; «M» o «2» devuelven null.
     */
    static Integer numeroDeCalzado(String talla) {
        if (talla == null) {
            return null;
        }
        Matcher matcher = TALLA_NUMERICA.matcher(talla);
        if (!matcher.find()) {
            return null;
        }
        int numero = Integer.parseInt(matcher.group(1));
        return (numero >= TALLA_MIN && numero <= TALLA_MAX) ? numero : null;
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
        return producto == null ? null : construirVista(producto);
    }

    /**
     * Detalle por slug: es la ruta canónica del SEO ({@code /producto/{slug}}).
     *
     * <p>Mismas consultas que la variante por id: producto + imágenes
     * ({@code EntityGraph}) y variantes en una segunda consulta, porque dos
     * bolsas a la vez romperían Hibernate con {@code MultipleBagFetchException}.</p>
     *
     * @param slug slug del producto (se normaliza a minúsculas)
     * @return la vista o {@code null} si no existe
     */
    @Transactional(readOnly = true)
    public DetalleProductoVista obtenerDetalleVistaPorSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }

        Producto producto = productoRepository.findBySlug(slug.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (producto == null) {
            return null;
        }

        productoRepository.findWithVariantes(producto.getId()).orElse(null);
        inicializarColecciones(producto);

        return construirVista(producto);
    }

    /**
     * Arma la vista plana (mapas) a partir de un producto cuyas colecciones ya
     * están inicializadas dentro de la transacción.
     */
    private DetalleProductoVista construirVista(Producto producto) {
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
                // Color de la imagen para la galería por variante.
                // "" = general (la foto se muestra cuando el color elegido no
                // tiene imágenes propias). El JS solo distingue vacío/no vacío.
                dto.put("color", imagen.getColor() == null ? "" : imagen.getColor());
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

        // ------------------------------------------------------------
        // CRO: ¿esta ficha se pinta como calzado?
        // Se calcula aquí, con los DTO ya armados, para que la plantilla
        // reciba un booleano y no tenga que razonar sobre categorías.
        // ------------------------------------------------------------
        boolean calzado = esCalzado(producto.getCategoria(), variantesDTO);

        return new DetalleProductoVista(producto, variantesDTO, imagenesDTO, imagenesLegacy,
                imagenPrincipal, calzado);
    }

    public Producto obtenerPorSku(String sku) {
        return productoRepository.findBySku(sku).orElse(null);
    }

    /**
     * Producto por slug (URL amigable). Devuelve {@code null} si no existe.
     */
    public Producto obtenerPorSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        return productoRepository.findBySlug(slug.trim().toLowerCase(Locale.ROOT)).orElse(null);
    }

    public Producto guardar(Producto producto) {
        return productoRepository.save(producto);
    }

    /**
     * Elimina un producto junto con sus filas hijas (imágenes, variantes y
     * reseñas).
     *
     * <p><strong>El {@code @Transactional} es obligatorio, no decorativo:</strong>
     * los {@code deleteByProductoId} derivados de Spring Data no abren
     * transacción propia. Sin una activa, Hibernate lanza
     * {@code "No EntityManager with actual transaction available for current
     * thread - cannot reliably process 'remove' call"} justo al intentar
     * borrar las imágenes. Con la anotación, los tres borrados (y el
     * {@code CascadeType.ALL} que arrastra las reseñas) ocurren en la misma
     * transacción: o se va todo, o no se va nada.</p>
     *
     * @param id producto a eliminar; si es {@code null} no se hace nada
     */
    @Transactional
    public void eliminar(Long id) {
        if (id == null) {
            return;
        }

        // Hijos explícitos primero. Las colecciones de Producto ya son
        // cascade = CascadeType.ALL, así que esto no es estrictamente
        // necesario, pero deja la intención escrita y evita depender de que
        // Hibernate inicialice las colecciones en mitad del remove.
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

    /**
     * Una fila del editor de imágenes del panel: URL + color opcional.
     *
     * @param url   URL de Cloudinary (obligatoria)
     * @param color color al que pertenece la foto, o {@code null}/vacío para la
     *              galería general
     */
    public record ImagenFormulario(String url, String color) {
    }

    /** Tope de imágenes por producto (protege el formulario y la ficha). */
    private static final int MAX_IMAGENES = 12;

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
        return guardarProductoConVariantes(productoForm, varianteSku, varianteColor, varianteTalla,
                variantePrecio, varianteCosto, varianteStock, variantePeso, varianteActivo,
                varianteId, null);
    }

    /**
     * Guarda el producto, sus imágenes (con color) y sus variantes en una sola
     * transacción.
     *
     * @param imagenes filas del editor de imágenes; {@code null} significa
     *                 «no se tocó el editor» y se conservan los colores que ya
     *                 tuvieran las imágenes existentes
     */
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
                                                List<Long> varianteId,
                                                List<ImagenFormulario> imagenes) {

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

        // SEO: slug único y estable. Se genera a partir del nombre la primera
        // vez y NO se recalcula al renombrar, para no romper URLs ya indexadas.
        productoGuardado.setSlug(asegurarSlug(productoGuardado, productoForm.getSlug()));

        // 2. Sincronizar las imágenes (URL + color) en esta misma transacción:
        //    el borrado de las filas antiguas y el alta de las nuevas son
        //    atómicos.
        sincronizarImagenes(productoGuardado, imagenes);

        // 3. Variantes: se leen por índice, sin exigir que todas las listas
        //    midan exactamente lo mismo. Antes cualquier descuadre (una fila
        //    sin costo, por ejemplo) abortaba con «datos desalineados», un
        //    mensaje que no le dice nada al administrador. Ahora cada fila se
        //    valida por separado y el error nombra la variante y el campo.
        int size = (varianteSku != null) ? varianteSku.size() : 0;

        // 4. Variantes actuales del producto, indexadas por id y por SKU.
        //    El índice por SKU es la red de seguridad del Bug 2: si una fila
        //    llega sin id (el formulario no lo envió o se perdió de camino),
        //    se reconoce la variante que YA existe en lugar de tratarla como
        //    nueva y chocar contra su propio SKU («el SKU ya está en uso»).
        List<VarianteProducto> variantesActuales = new ArrayList<>(productoGuardado.getVariantes());
        Map<Long, VarianteProducto> mapaPorId = new HashMap<>();
        Map<String, VarianteProducto> mapaPorSku = new HashMap<>();
        for (VarianteProducto variante : variantesActuales) {
            if (variante.getId() != null) {
                mapaPorId.putIfAbsent(variante.getId(), variante);
            }
            if (variante.getSku() != null && !variante.getSku().isBlank()) {
                mapaPorSku.putIfAbsent(normalizarSku(variante.getSku()), variante);
            }
        }

        // 5. Procesar filas (validación de SKU duplicado en formulario)
        Set<String> skusEnFormulario = new HashSet<>();
        Set<Long> idsMantenidos = new HashSet<>();
        List<VarianteProducto> nuevasVariantes = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            String sku = (i < varianteSku.size()) ? varianteSku.get(i) : null;
            if (sku == null || sku.trim().isEmpty()) continue;

            String skuNormalizado = normalizarSku(sku);
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

            // Costo del proveedor obligatorio (módulo de rentabilidad):
            // si el formulario no lo envía queda null y la validación lo
            // rechaza con un mensaje explícito.
            Double costo = (varianteCosto != null && i < varianteCosto.size() && varianteCosto.get(i) != null)
                    ? varianteCosto.get(i)
                    : null;

            Integer stock = (varianteStock != null && i < varianteStock.size() && varianteStock.get(i) != null)
                    ? varianteStock.get(i)
                    : 0;

            Double peso = (variantePeso != null && i < variantePeso.size() && variantePeso.get(i) != null)
                    ? variantePeso.get(i)
                    : null;

            Boolean activo = (varianteActivo != null && i < varianteActivo.size() && varianteActivo.get(i) != null)
                    ? varianteActivo.get(i)
                    : true;

            // ------------------------------------------------------------------
            // ¿Esta fila actualiza una variante que ya existe o crea una nueva?
            //   1. por id, si el formulario lo envió y pertenece a este producto;
            //   2. si no, por SKU dentro de ESTE producto (recupera las filas
            //      que llegaron sin id y evita el falso «SKU ya en uso»).
            // ------------------------------------------------------------------
            VarianteProducto varianteExistente = null;

            if (id != null) {
                varianteExistente = mapaPorId.get(id);
                if (varianteExistente == null) {
                    throw new IllegalArgumentException(
                            "La variante con ID " + id + " no pertenece a este producto.");
                }
            } else {
                varianteExistente = mapaPorSku.get(skuNormalizado);
            }

            if (varianteExistente != null) {
                varianteExistente.setSku(sku.trim());
                varianteExistente.setColor(color.trim());
                varianteExistente.setTalla(talla.trim());
                varianteExistente.setPrecio(precio);
                varianteExistente.setCosto(costo);
                varianteExistente.setStock(stock);
                varianteExistente.setPeso(peso);
                varianteExistente.setActivo(activo);

                validarVariante(varianteExistente);

                // Se marca como mantenida aunque haya llegado sin id: así el
                // paso 6 no la borra por «no estar en el formulario».
                if (varianteExistente.getId() != null) {
                    idsMantenidos.add(varianteExistente.getId());
                }
            } else {
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

        // 6. ELIMINAR variantes que ya no están en el formulario
        List<VarianteProducto> variantesAEliminar = variantesActuales.stream()
                .filter(v -> v.getId() != null && !idsMantenidos.contains(v.getId()))
                .collect(Collectors.toList());

        if (!variantesAEliminar.isEmpty()) {
            productoGuardado.getVariantes().removeAll(variantesAEliminar);
            varianteProductoRepository.deleteAll(variantesAEliminar);
            varianteProductoRepository.flush();
        }

        // 7. AGREGAR nuevas variantes
        for (VarianteProducto nueva : nuevasVariantes) {
            productoGuardado.getVariantes().add(nueva);
        }

        // 8. GUARDAR PRODUCTO (CASCADE ALL)
        Producto resultado = productoRepository.save(productoGuardado);
        productoRepository.flush();

        return resultado;
    }

    // ============================================================
    // SINCRONIZACIÓN DE IMÁGENES (LEGACY -> ProductoImagen)
    // ============================================================

    /**
     * Alinea la colección normalizada {@code producto.imagenes} (entidad
     * ProductoImagen, que es la fuente principal de la ficha de producto)
     * con los campos legacy {@code imagen1..imagen6}, que son los que edita
     * el administrador en el textarea de URLs.
     *
     * <p>Se ejecuta dentro de la transacción de
     * {@link #guardarProductoConVariantes}, por lo que el borrado de las
     * filas antiguas (orphanRemoval) y el alta de las nuevas se confirman
     * juntos: si algo falla, no queda la colección a medias.</p>
     *
     * <p>Si la colección ya coincide con los campos legacy no se toca, para
     * no borrar y reinsertar filas en cada guardado (por ejemplo, cuando el
     * administrador solo cambia el stock).</p>
     *
     * @param producto producto gestionado con los campos legacy ya copiados
     */
    /**
     * Alinea la colección {@code producto.imagenes} con lo que envió el
     * administrador: URL + color opcional de cada foto.
     *
     * <p><strong>Dos caminos:</strong></p>
     * <ol>
     *   <li>Si llega la lista del editor ({@code imagenes}), manda ella: cada
     *       fila se convierte en una {@link ProductoImagen} con su color
     *       ({@code null} cuando el campo viene vacío = galería general).</li>
     *   <li>Si llega {@code null} (formularios antiguos, migración de arranque),
     *       se usan los campos legacy {@code imagen1..6} y se
     *       <strong>conservan los colores</strong> que ya tuvieran esas URLs,
     *       para no borrarlos al guardar solo el stock.</li>
     * </ol>
     *
     * <p>El orden de las filas fija {@code orden} (0 = imagen principal).</p>
     *
     * @param producto producto gestionado
     * @param imagenes filas del editor, o {@code null} para usar los campos legacy
     */
    private void sincronizarImagenes(Producto producto, List<ImagenFormulario> imagenes) {
        if (producto == null) {
            return;
        }

        // 1. Colores actuales por URL: sirven para conservarlos cuando el
        //    llamador no manda la lista del editor.
        Map<String, String> coloresPorUrl = new LinkedHashMap<>();
        List<String> actuales = new ArrayList<>();
        if (producto.getImagenes() != null) {
            for (ProductoImagen imagen : producto.getImagenes()) {
                if (imagen == null || imagen.getUrl() == null || imagen.getUrl().isBlank()) {
                    continue;
                }
                String url = imagen.getUrl().trim();
                actuales.add(url);
                coloresPorUrl.putIfAbsent(url, imagen.getColor());
            }
        }

        // 2. Pares deseados (url, color), en orden.
        List<String> urlsDeseadas = new ArrayList<>();
        List<String> coloresDeseados = new ArrayList<>();

        if (imagenes != null) {
            for (ImagenFormulario fila : imagenes) {
                if (fila == null || fila.url() == null || fila.url().isBlank()) {
                    continue;   // fila vacía: se ignora (no es un error)
                }
                if (urlsDeseadas.size() >= MAX_IMAGENES) {
                    break;
                }
                urlsDeseadas.add(fila.url().trim());
                coloresDeseados.add(ProductoImagen.normalizarColor(fila.color()));
            }
        } else {
            String[] legacy = {
                    producto.getImagen1(), producto.getImagen2(), producto.getImagen3(),
                    producto.getImagen4(), producto.getImagen5(), producto.getImagen6()
            };
            for (String url : legacy) {
                if (url == null || url.isBlank()) {
                    continue;
                }
                String limpia = url.trim();
                urlsDeseadas.add(limpia);
                coloresDeseados.add(coloresPorUrl.get(limpia));
            }
        }

        // 3. ¿Ya está exactamente así? Entonces no se toca nada (no se borran y
        //    reinsertan filas en cada guardado).
        boolean iguales = urlsDeseadas.equals(actuales);
        if (iguales && imagenes != null) {
            for (int i = 0; i < coloresDeseados.size(); i++) {
                String actual = i < actuales.size() ? coloresPorUrl.get(actuales.get(i)) : null;
                if (!Objects.equals(ProductoImagen.normalizarColor(actual), coloresDeseados.get(i))) {
                    iguales = false;
                    break;
                }
            }
        }
        if (iguales) {
            return;
        }

        // 4. Limpiar las anteriores. Con orphanRemoval = true, Hibernate borra
        //    las filas de producto_imagen que ya no están en la lista.
        producto.getImagenes().clear();

        // 5. Recrear una ProductoImagen por fila, con su color y su orden.
        for (int i = 0; i < urlsDeseadas.size(); i++) {
            producto.addImagen(urlsDeseadas.get(i), coloresDeseados.get(i), i);
        }
    }

    // ============================================================
    // VALIDACIÓN DE VARIANTE
    // ============================================================

    /** SKU comparable: mayúsculas y sin espacios sobrantes. */
    private static String normalizarSku(String sku) {
        return sku == null ? "" : sku.trim().toUpperCase(Locale.ROOT);
    }

    /** Identificación legible de la variante, para que el error apunte a la fila. */
    private static String identificarVariante(VarianteProducto variante) {
        StringBuilder texto = new StringBuilder(variante.getSku() == null ? "(sin SKU)" : variante.getSku());
        if (variante.getColor() != null && !variante.getColor().isBlank()) {
            texto.append(" · ").append(variante.getColor());
        }
        if (variante.getTalla() != null && !variante.getTalla().isBlank()) {
            texto.append(" · talla ").append(variante.getTalla());
        }
        return texto.toString();
    }

    /**
     * Valida una variante antes de guardarla.
     *
     * <p><strong>Unicidad del SKU:</strong> si la variante ya existe (tiene id)
     * se compara con {@code existsBySkuAndIdNot}, que excluye la propia fila.
     * Ese detalle es el que evitaba el falso positivo al editar: con
     * {@code existsBySku} la variante se encontraba a sí misma y el guardado
     * se caía con «el SKU ya está en uso» sin que nadie lo hubiera duplicado.</p>
     *
     * <p>Para una variante nueva, {@code existsBySku} sí es lo correcto: el
     * llamador ya la descartó como existente por id <em>y</em> por SKU dentro
     * del producto, así que si el SKU aparece es porque pertenece a otro
     * producto.</p>
     */
    private void validarVariante(VarianteProducto variante) {
        List<String> errores = new ArrayList<>();

        if (variante.getSku() == null || variante.getSku().trim().isEmpty()) {
            errores.add("El SKU de la variante es obligatorio.");
        } else {
            String sku = variante.getSku().trim();
            variante.setSku(sku);

            boolean skuOcupado = (variante.getId() == null)
                    ? varianteProductoRepository.existsBySku(sku)
                    : varianteProductoRepository.existsBySkuAndIdNot(sku, variante.getId());

            if (skuOcupado) {
                errores.add("El SKU '" + sku + "' ya está en uso por otra variante.");
            }
        }

        if (variante.getPrecio() == null || variante.getPrecio() <= 0) {
            errores.add("El precio de la variante debe ser mayor a 0.");
        }

        if (variante.getStock() == null || variante.getStock() < 0) {
            errores.add("El stock de la variante no puede ser negativo.");
        }

        // Sin costo de proveedor no hay margen ni ganancia real: es obligatorio.
        if (variante.getCosto() == null || variante.getCosto() <= 0) {
            errores.add("Falta el costo del proveedor (debe ser mayor a 0).");
        } else if (variante.getPrecio() != null && variante.getCosto() >= variante.getPrecio()) {
            errores.add("El costo del proveedor (" + Math.round(variante.getCosto())
                    + ") no puede ser igual o mayor al precio de venta ("
                    + Math.round(variante.getPrecio()) + "): el margen sería nulo o negativo.");
        }

        if (variante.getPeso() != null && variante.getPeso() < 0) {
            errores.add("El peso de la variante no puede ser negativo.");
        }

        if (!errores.isEmpty()) {
            throw new IllegalArgumentException("Errores en la variante " + identificarVariante(variante)
                    + ": " + String.join("; ", errores));
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

    // ============================================================
    // SEO: GENERACIÓN DEL SLUG
    // ============================================================

    /**
     * Devuelve el slug definitivo de un producto.
     *
     * <p>Si ya tenía uno se respeta (URL estable: renombrar el producto no
     * cambia su dirección). Si no, se genera a partir del nombre —o del slug
     * propuesto— y se le añade un sufijo numérico si otro producto ya lo usa.</p>
     *
     * @param producto      producto a guardar (id null si es nuevo)
     * @param slugPropuesto slug enviado desde el formulario, si existe
     */
    private String asegurarSlug(Producto producto, String slugPropuesto) {
        String actual = producto.getSlug();
        if (actual != null && !actual.isBlank()) {
            return actual;
        }

        String texto = (slugPropuesto != null && !slugPropuesto.isBlank())
                ? slugPropuesto
                : producto.getNombre();

        String base = SlugUtil.generar(texto);
        if (base.isEmpty()) {
            base = "producto";
        }

        String candidato = base;
        int intento = 2;
        while (slugEnUso(candidato, producto.getId())) {
            candidato = SlugUtil.conSufijo(base, intento++);
            if (intento > 500) {
                // Salida de emergencia: no debería ocurrir con un catálogo real.
                candidato = SlugUtil.conSufijo(base, (int) (System.currentTimeMillis() % 100_000));
                break;
            }
        }
        return candidato;
    }

    private boolean slugEnUso(String slug, Long idProducto) {
        return idProducto == null
                ? productoRepository.existsBySlug(slug)
                : productoRepository.existsBySlugAndIdNot(slug, idProducto);
    }
}