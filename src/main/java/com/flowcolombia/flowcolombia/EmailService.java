package com.flowcolombia.flowcolombia;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${sendgrid.api-key}")
    private String apiKey;

    /**
     * Envío real por SendGrid.
     *
     * <p>Corta si no hay destinatario: el checkout ya no pide correo, así que
     * llamar con una dirección vacía solo generaría un error de la API y ruido
     * en los logs. Nunca debe llegar una petición a SendGrid sin dirección.</p>
     */
    public void enviarCorreo(String destinatario, String asunto, String contenidoHtml) throws IOException {
        if (destinatario == null || destinatario.isBlank()) {
            log.debug("Email: sin destinatario; no se llama a SendGrid.");
            return;
        }

        Email from = new Email("contacto@flowcolombia.com");
        Email to = new Email(destinatario.trim());
        Content content = new Content("text/html", contenidoHtml);
        Mail mail = new Mail(from, asunto, to, content);

        SendGrid sg = new SendGrid(apiKey);
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());
        sg.api(request);
    }

    // 🔥 Método para enviar confirmación de pedido
    /**
     * Correo genérico de confirmación.
     *
     * <p>Es {@link Async}: el checkout NO espera a SendGrid. Antes el envío se
     * hacía en el hilo HTTP y, con la red lenta, la respuesta al cliente podía
     * perderse aunque el pedido ya estuviera guardado (y el cliente repetía la
     * compra). En los tests se invoca sobre una instancia normal, así que se
     * ejecuta en el mismo hilo y se puede inspeccionar el contenido.</p>
     */
    @Async
    public void enviarConfirmacion(String destinatario, String codigoPedido, String productos, Double total) throws IOException {
        // Sin correo no hay nada que enviar: el checkout ya no lo pide y el
        // código de seguimiento viaja por la pantalla de confirmación y por
        // WhatsApp. Esto evita un intento fallido a SendGrid por pedido.
        String para = destinatario == null ? "" : destinatario.trim();
        if (para.isEmpty()) {
            log.debug("Email: pedido {} sin correo; no se envía confirmación.", codigoPedido);
            return;
        }

        String asunto = "✅ Confirmación de pedido - FLOW COLOMBIA";
        String contenido = "<h2>¡Gracias por tu compra!</h2>"
                + "<p><strong>Código de seguimiento:</strong> " + codigoPedido + "</p>"
                + "<p><strong>Productos:</strong><br>" + (productos != null ? productos.replace("\n", "<br>") : "N/A") + "</p>"
                + "<p><strong>Total:</strong> $" + (total != null ? total : 0) + "</p>"
                + "<p>Recibirás tu pedido en 3-5 días hábiles.</p>"
                + "<p>🇨🇴 FLOW COLOMBIA</p>";
        enviarCorreo(para, asunto, contenido);
    }

    /**
     * Confirmación específica del lanzamiento CTB-234.
     *
     * <p>Este producto (Destornillador de Impacto Manual x 13 Piezas) lleva
     * una garantía distinta al resto del catálogo: <strong>48 horas desde la
     * entrega</strong> para reportar defectos de fábrica. Viaja en el propio
     * correo de confirmación, en un recuadro destacado, para que el cliente no
     * tenga que buscarla en los términos ni preguntarla por WhatsApp.</p>
     *
     * <p>No modifica {@link #enviarConfirmacion(String, String, String, Double)}:
     * el resto de pedidos sigue recibiendo el correo genérico. Que este pedido
     * sea CTB-234 lo decide {@code PedidoController}.</p>
     *
     * @param pedido pedido recién guardado (usa correo, código y total)
     * @throws IOException si SendGrid rechaza el envío
     */
    @Async
    public void enviarConfirmacionCtb234(Pedido pedido) throws IOException {
        if (pedido == null) {
            return;
        }

        // Sin correo no se envía nada (el checkout ya no lo pide).
        String para = pedido.getEmail() == null ? "" : pedido.getEmail().trim();
        if (para.isEmpty()) {
            log.debug("Email: pedido CTB-234 {} sin correo; no se envía confirmación.", pedido.getCodigo());
            return;
        }

        // El total se formatea a la colombiana ($89.900): concatenar el Double
        // directo mostraría «$89900.0», que en un correo de confirmación se lee
        // como un error. Si no hay total, cae a 0 sin lanzar nada.
        String total = NumberFormat.getNumberInstance(new Locale("es", "CO"))
                .format(pedido.getTotal() != null ? pedido.getTotal() : 0d);
        String asunto = "✅ Confirmación de pedido CTB-234 - FLOW COLOMBIA";
        String contenido = "<h2>¡Gracias por tu compra!</h2>"
                + "<p><strong>Código de seguimiento:</strong> " + pedido.getCodigo() + "</p>"
                + "<p><strong>Producto:</strong> Destornillador de Impacto Manual x 13 Piezas</p>"
                + "<p><strong>Total:</strong> $" + total + " COP</p>"
                + "<p>Despachamos desde Cali. Recibirás tu pedido en 2-4 días hábiles "
                + "según tu ciudad. Pagas en efectivo al mensajero cuando lo recibes.</p>"
                + "<p style=\"background:#fff3cd;padding:12px;border-left:4px solid #ffc107;\">"
                + "<strong>Importante:</strong> revisa el kit cuando lo recibas. "
                + "Si hay un defecto de fábrica, repórtalo por WhatsApp dentro de las "
                + "<strong>48 horas siguientes a la entrega</strong> y lo resolvemos. "
                + "Pasado ese plazo no podemos gestionar el reemplazo."
                + "</p>"
                + "<p>🇨🇴 FLOW COLOMBIA</p>";
        enviarCorreo(para, asunto, contenido);
    }
}