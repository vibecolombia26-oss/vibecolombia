package com.flowcolombia.flowcolombia;

import com.flowcolombia.flowcolombia.service.NotificacionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Candado del checkout: si el pedido se guarda, la respuesta SIEMPRE es OK.
 *
 * <p>Este test existe por un incidente real en producción: el pedido se
 * guardaba (y llegaba el push al móvil), pero un fallo en las tareas de
 * post-proceso (correo, Meta CAPI o notificación) se escapaba del método y el
 * navegador recibía la página de error. El cliente volvía a pulsar «Confirmar»
 * y se creaban pedidos duplicados.</p>
 *
 * <p>Aquí se simulan esas dependencias fallando y se comprueba que el
 * controlador sigue devolviendo {@code status=OK} con el código
 * {@code FLOW-…}. Ni Spring, ni base de datos, ni Mockito: Proxy y subclases
 * de mentira, como el resto de la suite.</p>
 */
class PedidoControllerCheckoutTest {

    /** Repositorio de pedidos: asigna ID al guardar. */
    @SuppressWarnings("unchecked")
    private <T> T simular(Class<T> tipo, Map<String, Function<Object[], Object>> respuestas) {
        return (T) Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo},
                (proxy, metodo, args) -> {
                    Function<Object[], Object> respuesta = respuestas.get(metodo.getName());
                    if (respuesta != null) {
                        return respuesta.apply(args == null ? new Object[0] : args);
                    }
                    if (metodo.getReturnType() == List.class) return List.of();
                    if (metodo.getReturnType() == Optional.class) return Optional.empty();
                    if (metodo.getReturnType() == boolean.class) return false;
                    if (metodo.getReturnType() == long.class) return 0L;
                    return null;
                });
    }

    @Test
    @DisplayName("Un fallo del correo, de Meta o del push NO convierte el pedido guardado en error")
    void pedidoGuardadoSiempreDevuelveOk() {
        Map<String, Function<Object[], Object>> pedidos = new HashMap<>();
        pedidos.put("save", args -> {
            Pedido p = (Pedido) args[0];
            p.setId(42L);
            return p;
        });

        EmailService emailQueRevienta = new EmailService() {
            @Override
            public void enviarConfirmacion(String destinatario, String codigoPedido,
                                           String productos, Double total) {
                throw new IllegalStateException("SendGrid caído");
            }
        };

        NotificacionService pushQueRevienta = new NotificacionService("https://ntfy.sh/topic-test", 1000) {
            @Override
            public void notificarPedidoNuevo(Pedido pedido) {
                throw new IllegalStateException("ntfy caído");
            }
        };

        PedidoController controlador = new PedidoController(
                simular(PedidoRepository.class, pedidos),
                simular(MensajeRepository.class, new HashMap<>()),
                emailQueRevienta,
                null,   // Meta CAPI: se pasaría por el camino que lanza NPE
                simular(ProductoRepository.class, new HashMap<>()),
                pushQueRevienta);

        Pedido pedido = new Pedido();
        pedido.setNombreCliente("Cliente Prueba");
        pedido.setTelefono("3001234567");
        pedido.setEmail("cliente@example.com");
        pedido.setProductos("Destornillador x1 - $89.900");
        pedido.setTotal(89900d);

        Map<String, String> respuesta = controlador.guardarPedido(pedido, null);

        assertNotNull(respuesta, "el método debe devolver respuesta");
        assertEquals("OK", respuesta.get("status"),
                "con el pedido guardado la respuesta debe ser OK aunque falle el post-proceso");
        assertTrue(respuesta.get("codigo").startsWith("FLOW-"),
                "el cliente necesita su código de seguimiento para no repetir el pedido");
        assertEquals("Pendiente", pedido.getEstado());
    }
}
