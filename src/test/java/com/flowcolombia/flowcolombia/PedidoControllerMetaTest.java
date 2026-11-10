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
        // Por defecto: catálogo sin productos excluidos.
        catalogo.put("findAllById", args -> List.of());
        catalogo.put("findNombresExcluidosMeta", args -> List.of());

        // Los servicios que este método no usa se pasan como null a propósito:
        // si algún día se tocan, la prueba estalla en vez de pasar en falso.
        controlador = new PedidoController(
                simular(PedidoRepository.class),
                simular(MensajeRepository.class),
                null,
                null,
                simular(ProductoRepository.class));
    }

    private static Producto producto(Long id, String nombre, boolean excluirMeta) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setExcluirMeta(excluirMeta);
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
