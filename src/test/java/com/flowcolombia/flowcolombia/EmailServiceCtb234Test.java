package com.flowcolombia.flowcolombia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprueba el correo específico del lanzamiento CTB-234.
 *
 * <p>La garantía de <strong>48 horas</strong> para reportar defectos de
 * fábrica es una promesa comercial de la tienda: si el correo no la menciona,
 * el cliente no la conoce y la reclamación llega tarde. Por eso se verifica el
 * contenido real del HTML que se manda.</p>
 *
 * <p>No se habla con SendGrid: se extiende {@link EmailService} y se intercepta
 * {@link EmailService#enviarCorreo(String, String, String)} en memoria. Ni red,
 * ni credenciales, ni Spring (misma filosofía que el resto de la suite: sin
 * Mockito).</p>
 */
class EmailServiceCtb234Test {

    /**
     * Servicio espía: guarda lo que se habría enviado en lugar de mandarlo.
     * Al redefinir {@code enviarCorreo}, el {@code apiKey} de SendGrid nunca
     * se usa.
     */
    private static final class Captura extends EmailService {
        String destinatario;
        String asunto;
        String contenido;

        @Override
        public void enviarCorreo(String destinatario, String asunto, String contenidoHtml) {
            this.destinatario = destinatario;
            this.asunto = asunto;
            this.contenido = contenidoHtml;
        }
    }

    private static Pedido pedidoCtb234() {
        Pedido pedido = new Pedido();
        pedido.setEmail("cliente@example.com");
        pedido.setCodigo("FLOW-ABC234XYZ5");
        pedido.setTotal(89900d);
        pedido.setProductos("Destornillador de Impacto Manual x 13 Piezas x1 - $89.900");
        return pedido;
    }

    @Test
    @DisplayName("El correo de CTB-234 lleva la garantía de 48 horas desde la entrega")
    void correoCtb234LlevaGarantiaDe48Horas() throws Exception {
        Captura captura = new Captura();
        Pedido pedido = pedidoCtb234();

        captura.enviarConfirmacionCtb234(pedido);

        assertEquals("cliente@example.com", captura.destinatario,
                "el correo debe ir al cliente del pedido");
        assertTrue(captura.asunto.contains("CTB-234"),
                "el asunto tiene que identificar el producto para soporte");
        assertTrue(captura.contenido.contains("FLOW-ABC234XYZ5"),
                "falta el código de seguimiento en el cuerpo");
        assertTrue(captura.contenido.contains("48 horas siguientes a la entrega"),
                "el correo debe prometer la garantía de 48 horas, no la genérica");
        assertTrue(captura.contenido.contains("WhatsApp"),
                "el cliente tiene que saber por dónde reportar el defecto");
        assertTrue(captura.contenido.contains("$89.900 COP"),
                "el total debe mostrarse formateado a la colombiana ($89.900)");
        assertFalse(captura.contenido.contains("89900.0"),
                "un total crudo («89900.0») parecería un error en el correo");
    }

    @Test
    @DisplayName("Un pedido sin total no rompe el correo: se muestra $0")
    void correoCtb234AceptaTotalNulo() throws Exception {
        Captura captura = new Captura();
        Pedido pedido = pedidoCtb234();
        pedido.setTotal(null);

        captura.enviarConfirmacionCtb234(pedido);

        assertTrue(captura.contenido.contains("$0 COP"),
                "sin total el correo debe caer a cero en vez de lanzar un NPE");
    }

    @Test
    @DisplayName("El correo genérico sigue igual (no se le coló la garantía de 48h)")
    void correoGenericoSigueIntacto() throws Exception {
        Captura captura = new Captura();

        captura.enviarConfirmacion("otro@example.com", "FLOW-ZZZ999", "Mochila Táctica x1 - $89.900", 89900d);

        assertTrue(captura.contenido.contains("3-5 días hábiles"),
                "el correo del resto del catálogo no debe cambiar");
        assertFalse(captura.contenido.contains("48 horas"),
                "la garantía de 48h es exclusiva del CTB-234");
    }
}
