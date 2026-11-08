package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Producto;
import com.flowcolombia.flowcolombia.ProductoRepository;
import com.flowcolombia.flowcolombia.VarianteProducto;
import com.flowcolombia.flowcolombia.VarianteProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las sobrescrituras (overrides) de la importación de Dropi.
 *
 * <p>Llama al servicio directamente con dos repositorios simulados, sin Spring,
 * sin base de datos y sin DeepSeek. Lo que se comprueba es la regla de negocio
 * pura: <strong>el override gana al texto</strong> y, cuando no llega, se usa el
 * texto exactamente como antes.</p>
 *
 * <p>Los repositorios se simulan con {@link Proxy} dinámicos en lugar de
 * Mockito: las interfaces de Spring Data son interfaces, así que un proxy
 * responde perfectamente y la prueba no depende del <em>mock maker</em> ni de
 * poder auto-adjuntar agentes a la JVM.</p>
 */
class AdminAsistenteImportacionOverrideTest {

    private static final String TEXTO_DROPI = """
            Color: Negro
            Tallas: 38, 39
            Stock: 12
            Costo: $45.000
            Precio sugerido: $89.900
            """;

    /** Lo que el servicio mandó a la base de datos. */
    private List<VarianteProducto> guardadas;

    private AdminAsistenteService servicio;

    @BeforeEach
    void preparar() {
        guardadas = new ArrayList<>();

        Producto producto = new Producto();
        producto.setId(7L);
        producto.setNombre("Mochila Táctica");
        producto.setSku("MOCH");
        producto.setPrecio(80000d);

        Map<String, Function<Object[], Object>> respuestasProducto = new HashMap<>();
        respuestasProducto.put("findById", args -> Optional.of(producto));

        Map<String, Function<Object[], Object>> respuestasVariante = new HashMap<>();
        respuestasVariante.put("findByProductoId", args -> List.of());
        respuestasVariante.put("findSkusExistentes", args -> List.of());
        respuestasVariante.put("saveAll", args -> {
            for (Object variante : (Iterable<?>) args[0]) {
                guardadas.add((VarianteProducto) variante);
            }
            return new ArrayList<>(guardadas);
        });

        ProductoRepository productoRepository = simular(ProductoRepository.class, respuestasProducto);
        VarianteProductoRepository varianteProductoRepository =
                simular(VarianteProductoRepository.class, respuestasVariante);

        servicio = new AdminAsistenteService(
                productoRepository, varianteProductoRepository,
                "", "https://api.deepseek.com", "deepseek-chat", 25);
    }

    @Test
    @DisplayName("Sin overrides se comporta como siempre: manda el texto")
    void sinOverridesUsaElTexto() {
        servicio.importarVariantesDropi(7L, "Mochila Táctica", TEXTO_DROPI);

        assertEquals(2, guardadas.size());
        for (VarianteProducto variante : guardadas) {
            assertEquals(45000d, variante.getCosto(), "costo leído del texto");
            assertEquals(89900d, variante.getPrecio(), "precio leído del texto");
            assertEquals(12, variante.getStock(), "stock leído del texto");
        }
    }

    @Test
    @DisplayName("El precio y el costo forzados ganan al texto; el stock sigue saliendo del texto")
    void overridesGananAlTexto() {
        servicio.importarVariantesDropi(7L, "Mochila Táctica", TEXTO_DROPI, 60000d, 99999d);

        assertEquals(2, guardadas.size());
        for (VarianteProducto variante : guardadas) {
            assertEquals(60000d, variante.getCosto());
            assertEquals(99999d, variante.getPrecio());
            assertEquals(12, variante.getStock(), "el override nunca toca el stock");
        }
    }

    @Test
    @DisplayName("Sólo un override: el otro campo sigue saliendo del texto")
    void unSoloOverride() {
        servicio.importarVariantesDropi(7L, "Mochila Táctica", TEXTO_DROPI, null, 99999d);

        assertEquals(2, guardadas.size());
        for (VarianteProducto variante : guardadas) {
            assertEquals(45000d, variante.getCosto(), "sin costoOverride manda el texto");
            assertEquals(99999d, variante.getPrecio());
        }
    }

    @Test
    @DisplayName("Un override inválido no escribe nada y explica el motivo")
    void overrideInvalidoNoEscribe() {
        String salida = servicio.importarVariantesDropi(7L, "Mochila Táctica", TEXTO_DROPI, -5d, null);

        assertTrue(salida.contains("error"), "debe devolver error: " + salida);
        assertTrue(guardadas.isEmpty(), "no debe guardar nada");
    }

    @Test
    @DisplayName("El nombre aprobado tiene que cuadrar con el producto")
    void nombreQueNoCuadraSeRechaza() {
        String salida = servicio.importarVariantesDropi(7L, "Bolso Playero", TEXTO_DROPI, null, 99999d);

        assertTrue(salida.contains("error"), "debe devolver error: " + salida);
        assertTrue(guardadas.isEmpty(), "no debe guardar nada");
    }

    @Test
    @DisplayName("Sin texto de Dropi no se inventa nada")
    void sinTextoNoHaceNada() {
        String salida = servicio.importarVariantesDropi(7L, "Mochila Táctica", "", 60000d, 99999d);

        assertTrue(salida.contains("error"), "debe devolver error: " + salida);
        assertTrue(guardadas.isEmpty(), "no debe guardar nada");
    }

    @Test
    @DisplayName("La variante nueva nace con los valores forzados, no con el precio base")
    void varianteNuevaNaceConOverride() {
        servicio.importarVariantesDropi(7L, "Mochila Táctica", TEXTO_DROPI, 60000d, 99999d);

        assertFalse(guardadas.isEmpty());
        VarianteProducto primera = guardadas.get(0);
        assertEquals(99999d, primera.getPrecio(), "no debe caer al precio base del producto");
        assertEquals(60000d, primera.getCosto());
        assertEquals(Boolean.TRUE, primera.getActivo());
        assertTrue(primera.getSku().startsWith("MOCH-"), "SKU generado desde el producto: " + primera.getSku());
        assertNull(primera.getId(), "todavía no tiene id: lo asigna la base");
        assertEquals("Mochila Táctica", primera.getProducto().getNombre());
    }

    // ============================================================
    // Simulación de repositorios con proxies dinámicos
    // ============================================================

    /**
     * Crea un repositorio falso que responde a los métodos indicados y
     * devuelve vacío para el resto (lo que haría Spring Data sin resultados).
     */
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
