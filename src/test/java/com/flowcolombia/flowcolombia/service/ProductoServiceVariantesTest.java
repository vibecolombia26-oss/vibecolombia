package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Producto;
import com.flowcolombia.flowcolombia.ProductoImagen;
import com.flowcolombia.flowcolombia.ProductoImagenRepository;
import com.flowcolombia.flowcolombia.ProductoRepository;
import com.flowcolombia.flowcolombia.VarianteProducto;
import com.flowcolombia.flowcolombia.VarianteProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del guardado de variantes (Bug 2 del panel de administración).
 *
 * <p>Reproduce el caso real: un producto que ya tiene variantes y al que se le
 * añade un color nuevo. El fallo original era un falso positivo de SKU — la
 * variante se comparaba consigo misma — y, cuando el formulario perdía los
 * {@code varianteId}, el guardado además borraba las variantes existentes.</p>
 *
 * <p>Sin Spring ni base de datos: los repositorios se simulan con {@link Proxy}
 * dinámicos y se registran las llamadas para poder comprobar <em>qué</em>
 * validación se ejecutó.</p>
 */
class ProductoServiceVariantesTest {

    private static final Long PRODUCTO_ID = 7L;
    private static final Long VARIANTE_ID = 10L;
    private static final String SKU_EXISTENTE = "MOCH-M";
    private static final String SKU_NUEVO = "MOCH-G";

    /** Métodos del repositorio de variantes que se ejecutaron, en orden. */
    private final List<String> llamadas = new ArrayList<>();

    private ProductoService servicio;
    private Producto producto;

    @BeforeEach
    void preparar() {
        llamadas.clear();

        producto = new Producto();
        producto.setId(PRODUCTO_ID);
        producto.setNombre("Mochila Táctica");
        producto.setSku("MOCH");
        producto.setSlug("mochila-tactica");
        producto.setPrecio(100000d);

        VarianteProducto existente = new VarianteProducto();
        existente.setId(VARIANTE_ID);
        existente.setProducto(producto);
        existente.setSku(SKU_EXISTENTE);
        existente.setColor("Negro");
        existente.setTalla("M");
        existente.setPrecio(89900d);
        existente.setCosto(45000d);
        existente.setStock(5);
        existente.setActivo(true);
        producto.getVariantes().add(existente);

        Map<String, Function<Object[], Object>> respuestasProducto = new HashMap<>();
        respuestasProducto.put("findById", args -> Optional.of(producto));
        respuestasProducto.put("save", args -> args[0]);

        Map<String, Function<Object[], Object>> respuestasVariante = new HashMap<>();
        respuestasVariante.put("findByProductoId", args -> new ArrayList<>(producto.getVariantes()));
        respuestasVariante.put("existsBySku", args -> {
            llamadas.add("existsBySku:" + args[0]);
            // El SKU que ya existe pertenece a ESTE producto, no a otro.
            return "OTRO-PRODUCTO-SKU".equals(args[0]);
        });
        respuestasVariante.put("existsBySkuAndIdNot", args -> {
            llamadas.add("existsBySkuAndIdNot:" + args[0] + ":" + args[1]);
            return false;
        });
        respuestasVariante.put("saveAll", args -> new ArrayList<>());

        servicio = new ProductoService(
                simular(ProductoRepository.class, respuestasProducto),
                simular(ProductoImagenRepository.class, new HashMap<>()),
                simular(VarianteProductoRepository.class, respuestasVariante));
    }

    // ============================================================
    // BUG 2 · Añadir un color nuevo a un producto existente
    // ============================================================

    @Test
    @DisplayName("Añadir un color nuevo con los ids del formulario: guarda y no falsea el SKU")
    void anadirColorConIds() {
        Producto resultado = servicio.guardarProductoConVariantes(
                producto,
                List.of(SKU_EXISTENTE, SKU_NUEVO),
                List.of("Negro", "Gris"),
                List.of("M", "L"),
                List.of(89900d, 95000d),
                List.of(45000d, 48000d),
                List.of(5, 3),
                List.of(0.5d, 0.5d),
                List.of(true, true),
                // Arrays.asList y no List.of: la fila nueva viaja con id null.
                java.util.Arrays.asList(VARIANTE_ID, null));

        assertEquals(2, resultado.getVariantes().size(), "la existente + la nueva");

        VarianteProducto existente = buscar(resultado, SKU_EXISTENTE);
        assertNotNull(existente, "la variante original no puede desaparecer");
        assertEquals(VARIANTE_ID, existente.getId());
        assertEquals(5, existente.getStock());

        VarianteProducto nueva = buscar(resultado, SKU_NUEVO);
        assertNotNull(nueva, "falta la variante nueva");
        assertEquals("Gris", nueva.getColor());
        assertEquals(48000d, nueva.getCosto());
        assertEquals(95000d, nueva.getPrecio());

        // La variante existente se validó con existsBySkuAndIdNot (excluyéndose
        // a sí misma); para la nueva sí se consultó existsBySku.
        assertTrue(llamadas.contains("existsBySkuAndIdNot:" + SKU_EXISTENTE + ":" + VARIANTE_ID),
                "la variante existente debe validarse excluyendo su propio id: " + llamadas);
        assertTrue(llamadas.contains("existsBySku:" + SKU_NUEVO),
                "la variante nueva debe comprobar el SKU: " + llamadas);
    }

    @Test
    @DisplayName("Si el formulario pierde los varianteId, la fila se reconoce por SKU y NO se crea un duplicado")
    void filaSinIdSeReconocePorSku() {
        // Escenario del bug: el navegador no envía (o pierde) varianteId.
        Producto resultado = servicio.guardarProductoConVariantes(
                producto,
                List.of(SKU_EXISTENTE, SKU_NUEVO),
                List.of("Negro", "Gris"),
                List.of("M", "L"),
                List.of(89900d, 95000d),
                List.of(45000d, 48000d),
                List.of(9, 3),
                List.of(0.5d, 0.5d),
                List.of(true, true),
                null);   // <- sin ids

        assertEquals(2, resultado.getVariantes().size(),
                "no debe duplicarse la variante existente ni perderse");

        VarianteProducto existente = buscar(resultado, SKU_EXISTENTE);
        assertNotNull(existente, "la fila sin id debía actualizar la variante que ya existía");
        assertEquals(VARIANTE_ID, existente.getId(), "conserva su id: es la misma fila");
        assertEquals(9, existente.getStock(), "se actualiza con lo que envió el formulario");

        // Lo importante: al reconocerla por SKU se validó con existsBySkuAndIdNot,
        // nunca con existsBySku (que era el que devolvía el falso «ya está en uso»).
        assertTrue(llamadas.contains("existsBySkuAndIdNot:" + SKU_EXISTENTE + ":" + VARIANTE_ID),
                "debía validarse por id: " + llamadas);
        assertTrue(!llamadas.contains("existsBySku:" + SKU_EXISTENTE),
                "nunca debe preguntar existsBySku por su propio SKU: " + llamadas);
    }

    @Test
    @DisplayName("Editar una variante existente sin tocar su SKU no dispara el falso duplicado")
    void editarVarianteExistenteNoFalseaElSku() {
        servicio.guardarProductoConVariantes(
                producto,
                List.of(SKU_EXISTENTE),
                List.of("Negro"),
                List.of("M"),
                List.of(95000d),
                List.of(46000d),
                List.of(4),
                List.of(0.5d),
                List.of(true),
                List.of(VARIANTE_ID));

        assertEquals(1, producto.getVariantes().size());
        VarianteProducto variante = producto.getVariantes().get(0);
        assertEquals(95000d, variante.getPrecio());
        assertEquals(46000d, variante.getCosto());
        assertEquals(4, variante.getStock());
    }

    @Test
    @DisplayName("Un SKU que sí pertenece a otro producto sigue rechazándose")
    void skuDeOtroProductoSeRechaza() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                servicio.guardarProductoConVariantes(
                        producto,
                        List.of("OTRO-PRODUCTO-SKU"),
                        List.of("Gris"),
                        List.of("L"),
                        List.of(95000d),
                        List.of(48000d),
                        List.of(3),
                        List.of(0.5d),
                        List.of(true),
                        java.util.Arrays.asList((Long) null)));

        assertTrue(error.getMessage().contains("ya está en uso"),
                "el mensaje debe explicar el problema real: " + error.getMessage());
    }

    @Test
    @DisplayName("Un id de variante de otro producto se rechaza en vez de tocar la fila equivocada")
    void idDeOtroProductoSeRechaza() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                servicio.guardarProductoConVariantes(
                        producto,
                        List.of("SKU-AJENO"),
                        List.of(""),
                        List.of(""),
                        List.of(1000d),
                        List.of(500d),
                        List.of(1),
                        List.of(),
                        List.of(true),
                        List.of(999L)));

        assertTrue(error.getMessage().contains("no pertenece a este producto"), error.getMessage());
    }

    @Test
    @DisplayName("Una lista corta ya no aborta con «desalineados»: se valida la fila y se dice qué falta")
    void listaCortaDaErrorPuntual() {
        // Llega el SKU pero no el costo (lista más corta).
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                servicio.guardarProductoConVariantes(
                        producto,
                        List.of(SKU_EXISTENTE),
                        List.of("Negro"),
                        List.of("M"),
                        List.of(89900d),
                        List.of(),          // <- sin costos
                        List.of(5),
                        List.of(),
                        List.of(true),
                        List.of(VARIANTE_ID)));

        assertTrue(error.getMessage().contains("costo del proveedor"),
                "el error debe nombrar el campo que falta: " + error.getMessage());
        assertTrue(!error.getMessage().contains("desalineados"),
                "ya no debe usar el mensaje genérico: " + error.getMessage());
    }

    // ============================================================
    // BUG 1 · Los borrados tienen que ser transaccionales
    // ============================================================

    @Test
    @DisplayName("Todos los métodos de borrado declaran @Transactional")
    void borradosSonTransaccionales() throws NoSuchMethodException {
        assertTrue(ProductoService.class.getMethod("eliminar", Long.class)
                        .isAnnotationPresent(Transactional.class),
                "ProductoService.eliminar SIN @Transactional: Hibernate no puede procesar el remove");

        assertTrue(PedidoService.class.getMethod("eliminar", Long.class)
                        .isAnnotationPresent(Transactional.class),
                "PedidoService.eliminar sin @Transactional");

        assertTrue(ResenaService.class.getMethod("eliminar", Long.class)
                        .isAnnotationPresent(Transactional.class),
                "ResenaService.eliminar sin @Transactional");

        assertTrue(ResenaService.class.getMethod("eliminarPorProducto", Long.class)
                        .isAnnotationPresent(Transactional.class),
                "ResenaService.eliminarPorProducto usa una consulta derivada y sin transacción falla");
    }

    // ============================================================
    // GALERÍA POR VARIANTE: imágenes con color
    // ============================================================

    @Test
    @DisplayName("Guarda las imágenes con su color y deja null cuando el color está vacío")
    void guardaImagenesConColor() {
        Producto nuevo = new Producto();
        nuevo.setNombre("Tenis Diesel");
        nuevo.setSku("TENIS");
        nuevo.setPrecio(100000d);
        nuevo.setSlug("tenis-diesel");

        servicio.guardarProductoConVariantes(
                nuevo,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                List.of(
                        new ProductoService.ImagenFormulario("https://cdn/negra.jpg", "Negro"),
                        new ProductoService.ImagenFormulario("https://cdn/general.jpg", "   "),
                        new ProductoService.ImagenFormulario("https://cdn/compuesta.jpg", "Blanco / beige"),
                        new ProductoService.ImagenFormulario("   ", "Rojo")   // fila vacía: se ignora
                ));

        List<ProductoImagen> imagenes = nuevo.getImagenes();
        assertEquals(3, imagenes.size(), "la fila sin URL no debe crear imagen");
        assertEquals("https://cdn/negra.jpg", imagenes.get(0).getUrl());
        assertEquals("Negro", imagenes.get(0).getColor());
        assertEquals(0, imagenes.get(0).getOrden());

        assertEquals("https://cdn/general.jpg", imagenes.get(1).getUrl());
        assertNull(imagenes.get(1).getColor(), "un color vacío se guarda como null (galería general)");
        assertEquals(1, imagenes.get(1).getOrden());

        assertEquals("https://cdn/compuesta.jpg", imagenes.get(2).getUrl());
        assertEquals("Blanco/beige", imagenes.get(2).getColor(), "los espacios alrededor de la barra se limpian");
    }

    @Test
    @DisplayName("Al cambiar el color de una imagen existente se reemplaza, no se duplica")
    void cambiaElColorDeUnaImagen() {
        producto.addImagen("https://cdn/negra.jpg", "Negro", 0);

        servicio.guardarProductoConVariantes(
                producto,
                List.of(SKU_EXISTENTE), List.of("Negro"), List.of("M"),
                List.of(89900d), List.of(45000d), List.of(5),
                List.of(0.5d), List.of(true), List.of(VARIANTE_ID),
                List.of(new ProductoService.ImagenFormulario("https://cdn/negra.jpg", "Negro/azabache")));

        assertEquals(1, producto.getImagenes().size(), "misma URL: una sola fila");
        assertEquals("Negro/azabache", producto.getImagenes().get(0).getColor());
    }

    @Test
    @DisplayName("Sin lista de imágenes se conservan los colores que ya tenían las URLs")
    void sinListaConservaLosColores() {
        producto.addImagen("https://cdn/negra.jpg", "Negro", 0);
        producto.addImagen("https://cdn/general.jpg", null, 1);
        // Los campos legacy son los que usa el camino antiguo.
        producto.setImagen1("https://cdn/negra.jpg");
        producto.setImagen2("https://cdn/general.jpg");

        servicio.guardarProductoConVariantes(
                producto,
                List.of(SKU_EXISTENTE), List.of("Negro"), List.of("M"),
                List.of(89900d), List.of(45000d), List.of(5),
                List.of(0.5d), List.of(true), List.of(VARIANTE_ID));

        assertEquals(2, producto.getImagenes().size());
        assertEquals("Negro", producto.getImagenes().get(0).getColor(), "el color no se debe perder al guardar stock");
        assertNull(producto.getImagenes().get(1).getColor());
    }

    @Test
    @DisplayName("El formulario del panel envía URL y color por fila")
    void elFormularioEnviaUrlYColor() throws Exception {
        String html = leerPlantilla("admin-form.html");

        assertTrue(html.contains("name=\"imagenUrl\""), "falta el input de URL por fila");
        assertTrue(html.contains("name=\"imagenColor\""), "falta el input de color por fila");
        assertTrue(html.contains("id=\"imagenesLista\""), "falta el contenedor del editor de imágenes");
        assertFalse(html.contains("name=\"imagenesUrls\""), "el textarea viejo ya no debe estar en el formulario");
    }

    // ============================================================
    // CONTRATO DEL FORMULARIO (admin-form.html)
    // ============================================================

    @Test
    @DisplayName("El formulario envía el costo del proveedor en filas existentes y nuevas")
    void elFormularioEnviaElCosto() throws Exception {
        String html = leerPlantilla("admin-form.html");

        // Una aparición en las filas que pinta Thymeleaf y otra en la plantilla
        // JS del botón «+»: si alguna desaparece, el costo se guarda como null
        // y la validación rechaza el producto.
        int apariciones = contar(html, "name=\"varianteCosto\"");
        assertTrue(apariciones >= 2,
                "varianteCosto debe enviarse en ambos caminos del formulario (encontrado " + apariciones + ")");

        // El botón «+» no puede comparar el SKU nuevo consigo mismo.
        assertTrue(html.contains("if (input.id === 'nuevoSku') continue;"),
                "agregarVariante() debe saltarse #nuevoSku al buscar duplicados");
    }

    private static String leerPlantilla(String nombre) throws Exception {
        try (var entrada = ProductoServiceVariantesTest.class
                .getResourceAsStream("/templates/" + nombre)) {
            assertNotNull(entrada, "no se encontró templates/" + nombre);
            return new String(entrada.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static int contar(String texto, String buscado) {
        int total = 0;
        int desde = 0;
        while ((desde = texto.indexOf(buscado, desde)) >= 0) {
            total++;
            desde += buscado.length();
        }
        return total;
    }

    // ============================================================

    private static VarianteProducto buscar(Producto producto, String sku) {
        return producto.getVariantes().stream()
                .filter(v -> v != null && sku.equalsIgnoreCase(v.getSku()))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T simular(Class<T> tipo, Map<String, Function<Object[], Object>> respuestas) {
        return (T) Proxy.newProxyInstance(
                tipo.getClassLoader(),
                new Class<?>[]{ tipo },
                (proxy, metodo, argumentos) -> {
                    Function<Object[], Object> respuesta = respuestas.get(metodo.getName());
                    if (respuesta != null) {
                        return respuesta.apply(argumentos == null ? new Object[0] : argumentos);
                    }
                    Class<?> retorno = metodo.getReturnType();
                    if (retorno == Optional.class) {
                        return Optional.empty();
                    }
                    if (retorno == List.class) {
                        return List.of();
                    }
                    if (retorno == long.class) {
                        return 0L;
                    }
                    if (retorno == int.class) {
                        return 0;
                    }
                    if (retorno == boolean.class) {
                        return false;
                    }
                    return null;
                });
    }
}
