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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del upsert inteligente de la importación de Dropi.
 *
 * <p>El problema que resuelven: el catálogo tiene «Blanco» y Dropi manda
 * «Blanco/beige» para la misma talla. Si se compara solo por Color+Talla, no
 * coinciden y se crea una variante nueva con el stock repetido; el producto
 * acaba con «Blanco» (0 unidades) y «Blanco/beige» (4 unidades) como si fueran
 * dos cosas distintas.</p>
 *
 * <p>Aquí se comprueba la regla completa: mismo color+talla → actualizar;
 * misma talla con color compatible → <strong>renombrar</strong>; talla con un
 * único candidato → renombrar; talla con varios candidatos → crear (no se
 * adivina); y el precio base del producto se sincroniza con la moda.</p>
 */
class AdminAsistenteImportacionUpsertTest {

    private static final Long PRODUCTO_ID = 7L;
    private static final String NOMBRE = "Tenis Diesel";

    private Producto producto;
    private List<VarianteProducto> variantes;
    private final List<VarianteProducto> guardadas = new ArrayList<>();
    private final List<Producto> productosGuardados = new ArrayList<>();
    private AdminAsistenteService servicio;

    @BeforeEach
    void preparar() {
        guardadas.clear();
        productosGuardados.clear();

        producto = new Producto();
        producto.setId(PRODUCTO_ID);
        producto.setNombre(NOMBRE);
        producto.setSku("TENIS");
        producto.setSlug("tenis-diesel");
        producto.setPrecio(100000d);

        variantes = new ArrayList<>();
        variantes.add(variante(1L, "TENIS-BLANCO-40", "Blanco", "40", 1, 100000d));
        variantes.add(variante(2L, "TENIS-NEGRO-41", "Negro", "41", 2, 100000d));
        producto.getVariantes().addAll(variantes);

        Map<String, Function<Object[], Object>> respuestasProducto = new HashMap<>();
        respuestasProducto.put("findById", args -> Optional.of(producto));
        respuestasProducto.put("save", args -> {
            productosGuardados.add((Producto) args[0]);
            return args[0];
        });

        Map<String, Function<Object[], Object>> respuestasVariante = new HashMap<>();
        respuestasVariante.put("findByProductoId", args -> new ArrayList<>(variantes));
        respuestasVariante.put("findSkusExistentes", args -> List.of());
        respuestasVariante.put("saveAll", args -> {
            for (Object variante : (Iterable<?>) args[0]) {
                guardadas.add((VarianteProducto) variante);
            }
            return guardadas;
        });

        servicio = new AdminAsistenteService(
                simular(ProductoRepository.class, respuestasProducto),
                simular(VarianteProductoRepository.class, respuestasVariante),
                "", "https://api.deepseek.com", "deepseek-chat", 25);
    }

    private static VarianteProducto variante(Long id, String sku, String color, String talla,
                                             int stock, double precio) {
        VarianteProducto variante = new VarianteProducto();
        variante.setId(id);
        variante.setProducto(null);
        variante.setSku(sku);
        variante.setColor(color);
        variante.setTalla(talla);
        variante.setStock(stock);
        variante.setPrecio(precio);
        variante.setCosto(45000d);
        variante.setActivo(true);
        return variante;
    }

    /** Fila de Dropi tal como la pega el administrador (Talla antes que Color). */
    private static String fila(String id, String talla, String color, int stock,
                               String costo, String precio) {
        return "SKU: " + id + " Talla: " + talla + " Color: " + color
                + " Existencia: " + stock
                + " Precio del proveedor: $ " + costo
                + " Precio sugerido: $ " + precio + "\n";
    }

    @Test
    @DisplayName("«Blanco» en la base + «Blanco/beige» en Dropi → renombra, NO duplica")
    void colorCompuestoRenombraLaVarianteExistente() {
        String salida = servicio.importarVariantesDropi(PRODUCTO_ID, NOMBRE, fila(
                "111", "40", "Blanco/beige", 4, "45.000", "75.000"));

        assertTrue(salida.contains("\"ok\":true"), salida);
        assertTrue(salida.contains("\"creadas\":0"), "no debe crear ninguna variante: " + salida);
        assertTrue(salida.contains("\"coloresRenombrados\":1"), salida);

        // La variante de la base sigue siendo la misma (id 1) con el color nuevo.
        VarianteProducto blanco = buscar("40", "Blanco/beige");
        assertNotNull(blanco, "la variante 40 debía renombrarse a Blanco/beige: " + variantes);
        assertEquals(1L, blanco.getId(), "debe conservar su id: es la misma fila");
        assertEquals(4, blanco.getStock());
        assertEquals(75000d, blanco.getPrecio());
        assertEquals(45000d, blanco.getCosto());

        assertEquals(2, variantes.size(), "el producto sigue con 2 variantes, no con 3");
        assertTrue(salida.contains("Blanco → Blanco/beige"), "el resumen debe mostrar el cambio de color: " + salida);
    }

    @Test
    @DisplayName("El precio base del producto se sincroniza con el precio de venta importado")
    void precioBaseSeActualiza() {
        servicio.importarVariantesDropi(PRODUCTO_ID, NOMBRE, fila(
                "111", "40", "Blanco/beige", 4, "45.000", "75.000"));

        assertEquals(75000d, producto.getPrecio(),
                "producto.precio alimenta la ficha pública y el JSON-LD: debe seguirlo");
        assertEquals(1, productosGuardados.size());
    }

    @Test
    @DisplayName("Con varios precios gana el más repetido (la moda)")
    void precioBaseUsaLaModa() {
        String texto = fila("111", "40", "Blanco/beige", 4, "45.000", "75.000")
                + fila("112", "41", "Blanco/beige", 2, "45.000", "75.000")
                + fila("113", "42", "Negro/rojo", 6, "47.500", "79.900");

        servicio.importarVariantesDropi(PRODUCTO_ID, NOMBRE, texto);

        assertEquals(75000d, producto.getPrecio(), "75000 aparece 2 veces y 79900 una");
    }

    @Test
    @DisplayName("Talla con una sola variante y color totalmente distinto → se adopta y se renombra")
    void tallaUnicaSeAdoptaAunqueElColorCambie() {
        // "Marfil" no se parece a "Blanco/beige", pero es la única variante de la talla 40.
        variantes.get(0).setColor("Marfil");

        String salida = servicio.importarVariantesDropi(PRODUCTO_ID, NOMBRE, fila(
                "111", "40", "Blanco/beige", 7, "45.000", "75.000"));

        assertTrue(salida.contains("\"creadas\":0"), salida);
        VarianteProducto adoptada = buscar("40", "Blanco/beige");
        assertNotNull(adoptada);
        assertEquals(1L, adoptada.getId());
        assertEquals(7, adoptada.getStock());
    }

    @Test
    @DisplayName("Si la talla tiene dos variantes y el color no coincide, se crea (no se adivina)")
    void tallaAmbiguaCreaVarianteNueva() {
        // Dos variantes en la talla 40 con colores distintos.
        variantes.add(variante(3L, "TENIS-AZUL-40", "Azul", "40", 1, 100000d));

        String salida = servicio.importarVariantesDropi(PRODUCTO_ID, NOMBRE, fila(
                "111", "40", "Rojo", 5, "45.000", "75.000"));

        assertTrue(salida.contains("\"creadas\":1"), "no debe adivinar cuál renombrar: " + salida);

        // Las existentes se quedan como estaban…
        assertEquals("Blanco", buscar("40", "Blanco").getColor());
        assertEquals("Azul", buscar("40", "Azul").getColor());

        // …y la nueva entra por saveAll (la base le asignará el id).
        assertEquals(1, guardadas.size(), "debe crear exactamente una variante");
        VarianteProducto nueva = guardadas.get(0);
        assertEquals("Rojo", nueva.getColor());
        assertEquals("40", nueva.getTalla());
        assertEquals(5, nueva.getStock());
    }

    @Test
    @DisplayName("Una variante que no existe (talla nueva) se crea")
    void tallaNuevaSeCrea() {
        String salida = servicio.importarVariantesDropi(PRODUCTO_ID, NOMBRE, fila(
                "111", "45", "Blanco/beige", 3, "45.000", "75.000"));

        assertTrue(salida.contains("\"creadas\":1"), salida);
        assertEquals(1, guardadas.size());
        assertEquals("Blanco/beige", guardadas.get(0).getColor());
        assertEquals("45", guardadas.get(0).getTalla());
        assertEquals(3, guardadas.get(0).getStock());
        assertEquals(75000d, guardadas.get(0).getPrecio());
    }

    @Test
    @DisplayName("La segunda pasada del mismo texto no crea nada nuevo (idempotencia)")
    void reimportarNoDuplica() {
        String texto = fila("111", "40", "Blanco/beige", 4, "45.000", "75.000");

        servicio.importarVariantesDropi(PRODUCTO_ID, NOMBRE, texto);
        int variantesTrasPrimera = variantes.size();

        String segunda = servicio.importarVariantesDropi(PRODUCTO_ID, NOMBRE, texto);

        assertTrue(segunda.contains("\"creadas\":0"), segunda);
        assertTrue(segunda.contains("\"coloresRenombrados\":0"), "el color ya está bien: " + segunda);
        assertEquals(variantesTrasPrimera, variantes.size());
    }

    // ============================================================

    private VarianteProducto buscar(String talla, String color) {
        return variantes.stream()
                .filter(v -> v != null && v.getColor() != null
                        && v.getColor().equalsIgnoreCase(color)
                        && v.getTalla() != null && v.getTalla().equalsIgnoreCase(talla))
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
