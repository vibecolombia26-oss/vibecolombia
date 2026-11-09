package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Producto;
import com.flowcolombia.flowcolombia.ProductoRepository;
import com.flowcolombia.flowcolombia.VarianteProducto;
import com.flowcolombia.flowcolombia.VarianteProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las herramientas masivas del asistente de administración.
 *
 * <p>Lo que se comprueba aquí es la promesa que se le hizo al administrador:
 * «no me pases los SKUs, yo los busco». Para eso:</p>
 * <ul>
 *   <li>los masivos tocan <strong>todas</strong> las variantes de una vez;</li>
 *   <li>el precio masivo actualiza también el <strong>precio base</strong> del
 *       producto (la ficha pública);</li>
 *   <li>el costo es opcional y, si no llega, no se borra;</li>
 *   <li>un nombre ambiguo devuelve candidatos en lugar de adivinar;</li>
 *   <li>todo va en transacción y las herramientas no piden SKUs.</li>
 * </ul>
 *
 * <p>Sin Spring ni base de datos: los repositorios se simulan con proxies
 * dinámicos y se captura lo que el servicio manda a guardar.</p>
 */
class AdminAsistenteMasivosTest {

    private static final Long PRODUCTO_ID = 7L;
    private static final String NOMBRE = "Mochila Táctica";
    private static final double PRECIO_BASE = 100000d;
    private static final double COSTO_INICIAL = 45000d;

    private Producto producto;
    private List<VarianteProducto> variantes;
    private final List<VarianteProducto> variantesGuardadas = new ArrayList<>();
    private final List<Producto> productosGuardados = new ArrayList<>();

    private AdminAsistenteService servicio;

    @BeforeEach
    void preparar() {
        variantesGuardadas.clear();
        productosGuardados.clear();

        producto = new Producto();
        producto.setId(PRODUCTO_ID);
        producto.setNombre(NOMBRE);
        producto.setSku("MOCH");
        producto.setSlug("mochila-tactica");
        producto.setPrecio(PRECIO_BASE);

        variantes = new ArrayList<>();
        variantes.add(variante(1L, "MOCH-NEGRO-M", "Negro", "M", 3));
        variantes.add(variante(2L, "MOCH-NEGRO-L", "Negro", "L", 3));
        variantes.add(variante(3L, "MOCH-BLANCO-M", "Blanco", "M", 3));
        producto.getVariantes().addAll(variantes);

        Map<String, Function<Object[], Object>> respuestasProducto = new HashMap<>();
        respuestasProducto.put("findById", args -> Optional.of(producto));
        respuestasProducto.put("findByNombreContainingIgnoreCase", args -> List.of(producto));
        respuestasProducto.put("save", args -> {
            productosGuardados.add((Producto) args[0]);
            return args[0];
        });

        Map<String, Function<Object[], Object>> respuestasVariante = new HashMap<>();
        respuestasVariante.put("findByProductoId", args -> new ArrayList<>(variantes));
        respuestasVariante.put("saveAll", args -> {
            for (Object variante : (Iterable<?>) args[0]) {
                variantesGuardadas.add((VarianteProducto) variante);
            }
            return new ArrayList<>(variantesGuardadas);
        });

        productoRepositoryProxy = simular(ProductoRepository.class, respuestasProducto);
        varianteRepositoryProxy = simular(VarianteProductoRepository.class, respuestasVariante);

        servicio = new AdminAsistenteService(
                productoRepositoryProxy, varianteRepositoryProxy,
                "", "https://api.deepseek.com", "deepseek-chat", 25);
    }

    private ProductoRepository productoRepositoryProxy;
    private VarianteProductoRepository varianteRepositoryProxy;

    private static VarianteProducto variante(Long id, String sku, String color, String talla, int stock) {
        VarianteProducto variante = new VarianteProducto();
        variante.setId(id);
        variante.setSku(sku);
        variante.setColor(color);
        variante.setTalla(talla);
        variante.setPrecio(PRECIO_BASE);
        variante.setCosto(COSTO_INICIAL);
        variante.setStock(stock);
        variante.setActivo(true);
        return variante;
    }

    // ============================================================
    // PRECIO MASIVO
    // ============================================================

    @Test
    @DisplayName("El precio masivo toca todas las variantes Y el precio base del producto")
    void precioMasivoActualizaVariantesYPrecioBase() {
        String salida = servicio.actualizarPrecioMasivo(PRODUCTO_ID, NOMBRE, 105999d, 60000d);

        assertTrue(salida.contains("\"ok\":true"), salida);

        assertEquals(3, variantesGuardadas.size(), "deben guardarse TODAS las variantes");
        for (VarianteProducto variante : variantes) {
            assertEquals(105999d, variante.getPrecio(), "precio nuevo en " + variante.getSku());
            assertEquals(60000d, variante.getCosto(), "costo nuevo en " + variante.getSku());
        }

        // Lo crítico: la ficha pública lee producto.precio, no las variantes.
        assertEquals(105999d, producto.getPrecio(), "el precio base del producto debe quedar actualizado");
        assertEquals(1, productosGuardados.size(), "el producto se guarda explícitamente");
        assertTrue(salida.contains("\"precioBaseActualizado\":true"), salida);
    }

    @Test
    @DisplayName("Sin costo, el costo actual de cada variante no se toca")
    void precioMasivoSinCostoConservaElCosto() {
        servicio.actualizarPrecioMasivo(PRODUCTO_ID, NOMBRE, 105999d, null);

        for (VarianteProducto variante : variantes) {
            assertEquals(105999d, variante.getPrecio());
            assertEquals(COSTO_INICIAL, variante.getCosto(), "el costo no se envía: no debe borrarse");
        }
        assertEquals(105999d, producto.getPrecio());
    }

    @Test
    @DisplayName("Un costo igual o mayor al precio se rechaza y no escribe nada")
    void costoInvalidoNoEscribe() {
        String salida = servicio.actualizarPrecioMasivo(PRODUCTO_ID, NOMBRE, 50000d, 50000d);

        assertTrue(salida.contains("error"), salida);
        assertTrue(variantesGuardadas.isEmpty(), "no debe guardar variantes");
        assertTrue(productosGuardados.isEmpty(), "no debe guardar el producto");
        assertEquals(PRECIO_BASE, producto.getPrecio());
        assertEquals(COSTO_INICIAL, variantes.get(0).getCosto());
    }

    @Test
    @DisplayName("Un precio cero o negativo se rechaza")
    void precioInvalidoNoEscribe() {
        String salida = servicio.actualizarPrecioMasivo(PRODUCTO_ID, NOMBRE, 0d, null);

        assertTrue(salida.contains("error"), salida);
        assertTrue(variantesGuardadas.isEmpty());
        assertEquals(PRECIO_BASE, producto.getPrecio());
    }

    @Test
    @DisplayName("Un nombre que coincide exactamente gana aunque haya otros parecidos")
    void nombreExactoResuelveLaAmbiguedad() {
        Producto mini = new Producto();
        mini.setId(8L);
        mini.setNombre(NOMBRE + " Mini");
        mini.setPrecio(80000d);

        // El repositorio devuelve los dos productos parecidos.
        Map<String, Function<Object[], Object>> respuestas = new HashMap<>();
        respuestas.put("findById", args -> Optional.of(producto));
        respuestas.put("findByNombreContainingIgnoreCase", args -> List.of(producto, mini));
        respuestas.put("save", args -> args[0]);

        AdminAsistenteService conAmbiguedad = new AdminAsistenteService(
                simular(ProductoRepository.class, respuestas), varianteRepositoryProxy,
                "", "https://api.deepseek.com", "deepseek-chat", 25);

        // Llamada por nombre (sin id), con el nombre exacto del producto.
        String salida = conAmbiguedad.actualizarPrecioMasivo(NOMBRE, 105999d, null);

        assertTrue(salida.contains("\"ok\":true"), salida);
        assertEquals(105999d, producto.getPrecio(), "debe elegir el producto exacto");
        assertEquals(80000d, mini.getPrecio(), "no debe tocar el producto parecido");
    }

    // ============================================================
    // STOCK MASIVO
    // ============================================================

    @Test
    @DisplayName("El stock masivo pone todas las variantes al mismo valor y no toca precios")
    void stockMasivoActualizaTodas() {
        String salida = servicio.actualizarStockMasivo(PRODUCTO_ID, NOMBRE, 10);

        assertTrue(salida.contains("\"ok\":true"), salida);
        assertEquals(3, variantesGuardadas.size());
        for (VarianteProducto variante : variantes) {
            assertEquals(10, variante.getStock());
            assertEquals(PRECIO_BASE, variante.getPrecio(), "el stock no debe tocar el precio");
        }
        assertEquals(PRECIO_BASE, producto.getPrecio());
    }

    @Test
    @DisplayName("El nombre heredado (actualizarStockDeTodoElProducto) sigue funcionando")
    void nombreHeredadoSigueFuncionando() {
        String salida = servicio.actualizarStockDeTodoElProducto(PRODUCTO_ID, NOMBRE, 4);

        assertTrue(salida.contains("\"ok\":true"), salida);
        for (VarianteProducto variante : variantes) {
            assertEquals(4, variante.getStock());
        }
    }

    @Test
    @DisplayName("Un nombre realmente ambiguo devuelve candidatos en vez de adivinar")
    void nombreAmbiguoDevuelveCandidatos() {
        Producto otra = new Producto();
        otra.setId(8L);
        otra.setNombre("Mochila Mini");

        Map<String, Function<Object[], Object>> respuestas = new HashMap<>();
        respuestas.put("findByNombreContainingIgnoreCase", args -> List.of(producto, otra));

        AdminAsistenteService conAmbiguedad = new AdminAsistenteService(
                simular(ProductoRepository.class, respuestas), varianteRepositoryProxy,
                "", "https://api.deepseek.com", "deepseek-chat", 25);

        String salida = conAmbiguedad.actualizarStockMasivo(null, "Mochila", 10);

        assertTrue(salida.contains("error"), salida);
        assertTrue(salida.contains("candidatos"), salida);
        assertTrue(salida.contains("Mochila Mini"), "debe listar los candidatos: " + salida);
        assertTrue(variantesGuardadas.isEmpty(), "no puede tocar nada si el nombre es ambiguo");
    }

    // ============================================================
    // HERRAMIENTA DE CONTEXTO
    // ============================================================

    @Test
    @DisplayName("obtenerDetalleProducto devuelve cuántas variantes hay y de qué colores")
    void detalleDevuelveVariantesYColores() {
        String salida = servicio.obtenerDetalleProducto(NOMBRE);

        assertTrue(salida.contains("\"totalVariantes\":3"), salida);
        assertTrue(salida.contains("Negro"), salida);
        assertTrue(salida.contains("Blanco"), salida);
        assertTrue(salida.contains("resumen"), salida);
        assertTrue(salida.contains("3 variante(s)"), "el resumen debe decir el número real: " + salida);
        assertTrue(salida.contains("MOCH-NEGRO-M"), "debe listar los SKUs de las variantes");
    }

    @Test
    @DisplayName("obtenerDetalleProducto de un nombre que no existe no inventa nada")
    void detalleDeProductoInexistente() {
        Map<String, Function<Object[], Object>> respuestas = new HashMap<>();
        respuestas.put("findByNombreContainingIgnoreCase", args -> List.of());

        AdminAsistenteService sinProductos = new AdminAsistenteService(
                simular(ProductoRepository.class, respuestas), varianteRepositoryProxy,
                "", "https://api.deepseek.com", "deepseek-chat", 25);

        String salida = sinProductos.obtenerDetalleProducto("No existe");

        assertTrue(salida.contains("error"), salida);
        assertFalse(salida.contains("totalVariantes"), salida);
    }

    // ============================================================
    // CAMBIOS PARCIALES: SOLO COSTO
    // ============================================================

    @Test
    @DisplayName("Cambiar SOLO el costo: el precio de venta y el precio base no se tocan")
    void costoSoloNoTocaElPrecio() {
        // «pon el costo en 63.000 y deja el precio como está»
        String salida = servicio.actualizarPrecioMasivo(PRODUCTO_ID, NOMBRE, null, 63000d);

        assertTrue(salida.contains("\"ok\":true"), salida);
        assertEquals(3, variantesGuardadas.size());
        for (VarianteProducto variante : variantes) {
            assertEquals(63000d, variante.getCosto());
            assertEquals(PRECIO_BASE, variante.getPrecio(), "el precio de la variante no se toca");
        }
        assertEquals(PRECIO_BASE, producto.getPrecio(), "el precio base del producto no se toca");
        assertTrue(productosGuardados.isEmpty(), "no debe guardar el producto si el precio no cambia");
        assertTrue(salida.contains("\"precioBaseActualizado\":false"), salida);
    }

    @Test
    @DisplayName("Costo solo: se valida contra el precio ACTUAL de la base (costo < precio)")
    void costoSoloSeValidaContraElPrecioActual() {
        // PRECIO_BASE es el precio actual: un costo igual no deja margen.
        String salida = servicio.actualizarPrecioMasivo(PRODUCTO_ID, NOMBRE, null, PRECIO_BASE);

        assertTrue(salida.contains("error"), salida);
        assertTrue(salida.contains("precio actual"), "el mensaje debe hablar del precio actual: " + salida);
        assertTrue(variantesGuardadas.isEmpty());
        for (VarianteProducto variante : variantes) {
            assertEquals(COSTO_INICIAL, variante.getCosto());
        }
    }

    @Test
    @DisplayName("Sin precio ni costo no hay nada que hacer")
    void sinPrecioNiCostoEsError() {
        String salida = servicio.actualizarPrecioMasivo(PRODUCTO_ID, NOMBRE, null, null);

        assertTrue(salida.contains("error"), salida);
        assertTrue(variantesGuardadas.isEmpty());
    }

    // ============================================================
    // ACCIÓN PENDIENTE EN MEMORIA (anti-amnesia)
    // ============================================================

    @Test
    @DisplayName("Un «sí» sin acción reenviada ejecuta la última acción propuesta")
    void ejecutaLaAccionRecordada() throws Exception {
        recordar(propuestaStock(9));

        // El navegador no reenvía nada: solo «confirmarAccion: true».
        AdminAsistenteService.Resultado resultado = servicio.ejecutar(null);

        assertTrue(resultado.ok(), resultado.mensaje());
        for (VarianteProducto variante : variantes) {
            assertEquals(9, variante.getStock());
        }

        // Consumida: un segundo «sí» ya no tiene acción pendiente.
        AdminAsistenteService.Resultado segunda = servicio.ejecutar(null);
        assertFalse(segunda.ok(), "la acción pendiente debe consumirse al ejecutarse");
    }

    @Test
    @DisplayName("Una acción pendiente caducada no se ejecuta")
    void accionCaducadaNoSeEjecuta() throws Exception {
        recordar(propuestaStock(9));

        // Se envejece la marca: 20 minutos (la vigencia es de 15).
        Field cuando = AdminAsistenteService.class.getDeclaredField("ultimaAccionEn");
        cuando.setAccessible(true);
        cuando.set(servicio, java.time.Instant.now().minusSeconds(20 * 60));

        AdminAsistenteService.Resultado resultado = servicio.ejecutar(null);

        assertFalse(resultado.ok(), "una propuesta de hace 20 minutos no puede ejecutarse sola");
        assertTrue(variantesGuardadas.isEmpty());
    }

    private static AdminAsistenteService.AccionPendiente propuestaStock(int stock) {
        return new AdminAsistenteService.AccionPendiente(
                AdminAsistenteService.TOOL_STOCK_MASIVO,
                "poner el stock de las 3 variante(s) de «Mochila Táctica» en " + stock + " unidades cada una",
                new HashMap<>(Map.of(
                        "productoId", PRODUCTO_ID,
                        "nombreProducto", NOMBRE,
                        "nuevoStock", stock)),
                true);
    }

    /** Simula que el asistente acaba de proponer una acción. */
    private void recordar(AdminAsistenteService.AccionPendiente accion) throws Exception {
        Field campo = AdminAsistenteService.class.getDeclaredField("ultimaAccionPropuesta");
        campo.setAccessible(true);
        campo.set(servicio, accion);

        Field cuando = AdminAsistenteService.class.getDeclaredField("ultimaAccionEn");
        cuando.setAccessible(true);
        cuando.set(servicio, java.time.Instant.now());
    }

    // ============================================================
    // CONTRATO: TRANSACCIONES Y "NUNCA PIDAS SKUs"
    // ============================================================

    @Test
    @DisplayName("Las herramientas masivas son transaccionales")
    void masivosSonTransaccionales() throws NoSuchMethodException {
        assertTrue(AdminAsistenteService.class
                        .getMethod("actualizarPrecioMasivo", Long.class, String.class, Double.class, Double.class)
                        .isAnnotationPresent(Transactional.class),
                "actualizarPrecioMasivo debe ser @Transactional (si falla una variante, se revierte todo)");

        assertTrue(AdminAsistenteService.class
                        .getMethod("actualizarStockMasivo", Long.class, String.class, Integer.class)
                        .isAnnotationPresent(Transactional.class),
                "actualizarStockMasivo debe ser @Transactional");

        assertTrue(AdminAsistenteService.class
                        .getMethod("actualizarStockDeTodoElProducto", Long.class, String.class, Integer.class)
                        .isAnnotationPresent(Transactional.class),
                "el nombre heredado también debe seguir siendo transaccional");
    }

    @Test
    @DisplayName("El esquema de herramientas anuncia los masivos por nombre y sin pedir SKUs")
    void esquemaAnunciaLosMasivos() throws Exception {
        String esquema = leerConstante("TOOLS_JSON");

        // Si el JSON se rompe, TODAS las conversaciones del asistente fallan
        // (DeepSeek rechaza el payload entero). Vale la pena comprobarlo.
        com.fasterxml.jackson.databind.JsonNode arbol =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(esquema);
        assertTrue(arbol.isArray(), "TOOLS_JSON debe ser un arreglo de herramientas");
        assertTrue(arbol.size() >= 8, "se esperaban al menos 8 herramientas, hay " + arbol.size());

        assertTrue(esquema.contains("\"name\": \"actualizarPrecioMasivo\""), "falta actualizarPrecioMasivo");
        assertTrue(esquema.contains("\"name\": \"actualizarStockMasivo\""), "falta actualizarStockMasivo");
        assertTrue(esquema.contains("\"name\": \"obtenerDetalleProducto\""), "falta obtenerDetalleProducto");

        // El nombre heredado ya no se anuncia (se sigue aceptando en el despacho).
        assertFalse(esquema.contains("\"actualizarStockDeTodoElProducto\""),
                "el nombre heredado no debe anunciarse: compite con el nuevo");
    }

    @Test
    @DisplayName("El prompt prohíbe expresamente pedir la lista de SKUs")
    void promptProhibePedirSkus() throws Exception {
        String prompt = leerConstante("SYSTEM_PROMPT");

        assertTrue(prompt.contains("NUNCA le pidas la lista de SKUs"),
                "el prompt debe prohibir pedir SKUs");
        assertTrue(prompt.contains("actualizarPrecioMasivo"), "el prompt debe nombrar la herramienta de precio masivo");
        assertTrue(prompt.contains("105999"), "el prompt debe traer el ejemplo del caso real");
    }

    // ============================================================

    private static String leerConstante(String nombre) throws Exception {
        Field campo = AdminAsistenteService.class.getDeclaredField(nombre);
        campo.setAccessible(true);
        return String.valueOf(campo.get(null));
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
