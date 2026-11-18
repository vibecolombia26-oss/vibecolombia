package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Prueba la notificación push sin red, sin Spring y sin Mockito.
 *
 * <p>Se extiende {@link NotificacionService} y se intercepta
 * {@link NotificacionService#enviar(String)}: así se verifica el
 * <strong>texto exacto</strong> que viajaría a ntfy y que el robot queda
 * dormido cuando no hay tema configurado.</p>
 */
class NotificacionServiceTest {

    /** Servicio espía: guarda el mensaje en lugar de hacer el POST. */
    private static final class Captura extends NotificacionService {
        private final boolean falla;
        String enviado;
        int envios;

        Captura(String url, boolean falla) {
            super(url, 1000);
            this.falla = falla;
        }

        @Override
        protected void enviar(String mensaje) {
            envios++;
            if (falla) {
                throw new IllegalStateException("ntfy no responde");
            }
            enviado = mensaje;
        }
    }

    private static Pedido pedido() {
        Pedido pedido = new Pedido();
        pedido.setNombreCliente("María Rodríguez");
        pedido.setTotal(89900d);
        pedido.setCodigo("FLOW-ABC2345678");
        return pedido;
    }

    @Test
    @DisplayName("La notificación lleva el formato aprobado, con el total formateado")
    void formatoDelMensaje() {
        Captura servicio = new Captura("https://ntfy.sh/flowcolombia-test", false);

        servicio.notificarPedidoNuevo(pedido());

        assertEquals(1, servicio.envios, "debe enviar una notificación");
        assertEquals("🛍️ Nuevo pedido: María Rodríguez - Total: $89.900 - Código: FLOW-ABC2345678",
                servicio.enviado,
                "el texto debe seguir el formato del brief con el total en formato colombiano");
    }

    @Test
    @DisplayName("Sin tema configurado no se envía nada (robot dormido)")
    void sinTemaNoEnvia() {
        Captura servicio = new Captura("", false);

        servicio.notificarPedidoNuevo(pedido());

        assertEquals(0, servicio.envios, "sin NTFY_TOPIC_URL no debe haber petición");
        assertNull(servicio.enviado);
    }

    @Test
    @DisplayName("Un pedido sin nombre ni total no rompe el mensaje")
    void pedidoIncompletoNoRompe() {
        Captura servicio = new Captura("https://ntfy.sh/flowcolombia-test", false);
        Pedido pedido = new Pedido();

        servicio.notificarPedidoNuevo(pedido);

        assertEquals("🛍️ Nuevo pedido: Cliente - Total: $0 - Código: sin código",
                servicio.enviado,
                "los valores ausentes caen a un texto seguro, no a «null»");
    }

    @Test
    @DisplayName("Si ntfy falla, la excepción no sale del servicio")
    void falloDeNtfyNoPropaga() {
        Captura servicio = new Captura("https://ntfy.sh/flowcolombia-test", true);

        assertDoesNotThrow(() -> servicio.notificarPedidoNuevo(pedido()),
                "un fallo de ntfy nunca debe llegar al checkout");
        assertEquals(1, servicio.envios, "el intento se hace, aunque falle");
    }

    @Test
    @DisplayName("Un pedido nulo no llama a ntfy")
    void pedidoNuloNoEnvia() {
        Captura servicio = new Captura("https://ntfy.sh/flowcolombia-test", false);

        servicio.notificarPedidoNuevo(null);

        assertEquals(0, servicio.envios);
    }
}
