package com.flowcolombia.flowcolombia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprueba la regla de seguridad de la API de Conversiones (CAPI).
 *
 * <p>Meta prohíbe enviar datos de productos de salud íntima o suplementos: si
 * un pedido contiene uno de esos productos, el {@code Purchase} del servidor
 * <strong>no se envía</strong>. Es mejor perder la venta registrada en el píxel
 * que arriesgar la cuenta publicitaria, y ese criterio no puede quedar solo en
 * un comentario.</p>
 *
 * <p>Se prueba el método de decisión con repositorios simulados por
 * {@link Proxy}, como el resto de la suite: ni Spring, ni base de datos, ni
 * Mockito (que no arranca su MockMaker en este entorno).</p>
 */
class PedidoControllerMetaTest {

    /** Respuestas simuladas del repositorio de productos, por nombre de método. */
    private final Map<String, Function<Object[], Object>> catalogo = new HashMap<>();

    private PedidoController controlador;

    @BeforeEach
    void preparar() {
        catalogo.clear();
        // Por defecto: catálogo sin productos excluidos ni con Purchase bloqueado.
        catalogo.put("findAllById", args -> List.of());
        catalogo.put("findNombresExcluidosMeta", args -> List.of());
        catalogo.put("findNombresBloqueanPurchase", args -> List.of());

        // Los servicios que este método no usa se pasan como null a propósito:
        // si algún día se tocan, la prueba estalla en vez de pasar en falso.
        // (La notificación push es la sexta dependencia y tampoco interviene
        // en la decisión de bloquear el Purchase.)
        controlador = new PedidoController(
                simular(PedidoRepository.class),
                simular(MensajeRepository.class),
                null,
                null,
                simular(ProductoRepository.class),
                null);
    }

    private static Producto producto(Long id, String nombre, boolean excluirMeta) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setExcluirMeta(excluirMeta);
        return producto;
    }

    /** Producto con las dos marcas de Meta (exclusión total y bloqueo de Purchase). */
    private static Producto productoMeta(Long id, String nombre,
                                         boolean excluirMeta, boolean bloquearPurchaseMeta) {
        Producto producto = producto(id, nombre, excluirMeta);
        producto.setBloquearPurchaseMeta(bloquearPurchaseMeta);
        return producto;
    }

    private static Pedido pedido(String detalle, Long... ids) {
        Pedido pedido = new Pedido();
        pedido.setProductos(detalle);
        pedido.setProductosIds(List.of(ids));
        return pedido;
    }

    @Test
    @DisplayName("Pedido con un producto excluido (por ID): no se envía el Purchase")
    void bloqueaPurchasePorId() {
        catalogo.put("findAllById", args -> List.of(
                producto(1L, "Mochila Táctica", false),
                producto(2L, "Crema Íntima", true)));

        Pedido orden = pedido("Mochila Táctica x1 - $89.900\nCrema Íntima x1 - $59.900", 1L, 2L);

        assertTrue(controlador.contieneProductosOcultosAMeta(orden),
                "un pedido con un producto marcado «ocultar a Meta» no debe llegar a la CAPI");
    }

    @Test
    @DisplayName("Pedido solo con productos normales (por ID): sí se envía")
    void permitePurchaseSiTodoEsSeguro() {
        catalogo.put("findAllById", args -> List.of(producto(1L, "Mochila Táctica", false)));

        Pedido orden = pedido("Mochila Táctica x1 - $89.900", 1L);

        assertFalse(controlador.contieneProductosOcultosAMeta(orden),
                "un pedido sin productos excluidos debe medirse con normalidad");
    }

    @Test
    @DisplayName("Carrito antiguo sin IDs: se detecta por el nombre del producto")
    void bloqueaPurchasePorNombre() {
        catalogo.put("findNombresExcluidosMeta", args -> List.of("Crema Íntima"));

        // Sin IDs (cliente viejo) y con el nombre escrito a mano en otro
        // formato: mayúsculas, sin tilde y con el precio pegado.
        Pedido orden = pedido("CREMA INTIMA x2 (Color: Rosa) - $119.800");

        assertTrue(controlador.contieneProductosOcultosAMeta(orden),
                "el respaldo por nombre debe bloquear el pedido sin IDs");
    }

    @Test
    @DisplayName("Pedido sin datos de productos: no bloquea")
    void pedidoVacioNoBloquea() {
        Pedido orden = pedido("Productos no especificados");

        assertFalse(controlador.contieneProductosOcultosAMeta(orden),
                "sin productos identificables no hay nada que excluir");
        assertFalse(controlador.contieneProductosOcultosAMeta(null),
                "un pedido nulo no debe romper el checkout");
    }

    @Test
    @DisplayName("Un producto excluido que no está en el pedido no lo bloquea")
    void nombreAjenoNoBloquea() {
        catalogo.put("findNombresExcluidosMeta", args -> List.of("Crema Íntima"));

        Pedido orden = pedido("Mochila Táctica x1 - $89.900", 1L);

        assertFalse(controlador.contieneProductosOcultosAMeta(orden),
                "solo bloquea si el producto excluido está realmente en el pedido");
    }

    @Test
    @DisplayName("Producto con bloquearPurchaseMeta (por ID): no se envía el Purchase")
    void bloqueaPurchaseMetaPorId() {
        catalogo.put("findAllById", args -> List.of(
                productoMeta(9L, "Destornillador de Impacto", false, true)));

        Pedido orden = pedido("Destornillador de Impacto x1 - $89.900", 9L);

        assertTrue(controlador.contieneProductosConPurchaseBloqueado(orden),
                "un producto contraentrega no debe mandar el Purchase a Meta");
        assertFalse(controlador.contieneProductosOcultosAMeta(orden),
                "bloquearPurchaseMeta no es excluirMeta: el producto no está vetado de Meta");
    }

    @Test
    @DisplayName("Pedido solo con productos normales: el Purchase se envía")
    void permitePurchaseMetaSiTodoEsSeguro() {
        catalogo.put("findAllById", args -> List.of(
                productoMeta(1L, "Mochila Táctica", false, false)));

        Pedido orden = pedido("Mochila Táctica x1 - $89.900", 1L);

        assertFalse(controlador.contieneProductosConPurchaseBloqueado(orden),
                "sin marcas de bloqueo el Purchase debe enviarse con normalidad");
    }

    @Test
    @DisplayName("Con las dos marcas, excluirMeta sigue teniendo prioridad")
    void excluirMetaTienePrioridad() {
        catalogo.put("findAllById", args -> List.of(
                productoMeta(3L, "Crema Íntima", true, true)));

        Pedido orden = pedido("Crema Íntima x1 - $59.900", 3L);

        assertTrue(controlador.contieneProductosOcultosAMeta(orden),
                "excluirMeta debe seguir cortando TODOS los eventos de Meta");
        assertTrue(controlador.contieneProductosConPurchaseBloqueado(orden),
                "el segundo flag también está marcado en ese producto");
    }

    @Test
    @DisplayName("Carrito sin IDs: el bloqueo del Purchase también se detecta por nombre")
    void bloqueaPurchaseMetaPorNombre() {
        catalogo.put("findNombresBloqueanPurchase", args -> List.of("Destornillador de Impacto Manual"));

        // Sin IDs (cliente viejo), en mayúsculas y con el precio pegado.
        Pedido orden = pedido("DESTORNILLADOR DE IMPACTO MANUAL x1 - $89.900");

        assertTrue(controlador.contieneProductosConPurchaseBloqueado(orden),
                "el respaldo por nombre debe frenar el Purchase de los pedidos sin IDs");
    }

    /**
     * Repositorio de mentira: responde lo que diga {@link #catalogo} y
     * devuelve listas vacías para todo lo demás.
     */
    @SuppressWarnings("unchecked")
    private <T> T simular(Class<T> tipo) {
        return (T) Proxy.newProxyInstance(
                tipo.getClassLoader(),
                new Class<?>[]{tipo},
                (proxy, metodo, argumentos) -> {
                    Function<Object[], Object> respuesta = catalogo.get(metodo.getName());
                    if (respuesta != null) {
                        return respuesta.apply(argumentos == null ? new Object[0] : argumentos);
                    }
                    return metodo.getReturnType() == List.class ? List.of() : null;
                });
    }
}
