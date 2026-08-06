package com.flowcolombia.flowcolombia;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class EmailService {

    @Value("${sendgrid.api-key}")
    private String apiKey;

    public void enviarCorreo(String destinatario, String asunto, String contenidoHtml) throws IOException {
        Email from = new Email("contacto@flowcolombia.com");
        Email to = new Email(destinatario);
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
    public void enviarConfirmacion(String destinatario, String codigoPedido, String productos, Double total) throws IOException {
        String asunto = "✅ Confirmación de pedido - FLOW COLOMBIA";
        String contenido = "<h2>¡Gracias por tu compra!</h2>"
                + "<p><strong>Código de seguimiento:</strong> " + codigoPedido + "</p>"
                + "<p><strong>Productos:</strong><br>" + (productos != null ? productos.replace("\n", "<br>") : "N/A") + "</p>"
                + "<p><strong>Total:</strong> $" + (total != null ? total : 0) + "</p>"
                + "<p>Recibirás tu pedido en 3-5 días hábiles.</p>"
                + "<p>🇨🇴 FLOW COLOMBIA</p>";
        enviarCorreo(destinatario, asunto, contenido);
    }
}