package com.flowcolombia.flowcolombia;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
public class EmailService {

    private static final String API_KEY = "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";
    private static final String FROM_EMAIL = "vibecolombia26@gmail.com";
    private static final String FROM_NAME = "VIBE COLOMBIA 26";

    public void enviarConfirmacion(String to, String nombre, String productos, double total) {
        String asunto = "✅ Pedido Confirmado - VIBE COLOMBIA 26";
        String cuerpo = "¡Hola " + nombre + "!\n\n" +
                "Tu pedido ha sido registrado exitosamente en VIBE COLOMBIA 26.\n\n" +
                "📦 Resumen de tu orden:\n" + productos + "\n" +
                "💰 Total: $" + String.format("%,.0f", total) + "\n" +
                "🚚 Envío: GRATIS\n" +
                "💵 Pago: Contra entrega\n\n" +
                "Gracias por confiar en VIBE COLOMBIA 26 💜✨";
        enviarCorreo(to, asunto, cuerpo);
    }

    public void enviarActualizacion(String to, String asunto, String cuerpo) {
        enviarCorreo(to, asunto, cuerpo);
    }

    private void enviarCorreo(String to, String asunto, String cuerpo) {
        new Thread(() -> {
            Email from = new Email(FROM_EMAIL, FROM_NAME);
            Email toEmail = new Email(to);
            Content content = new Content("text/plain", cuerpo);
            Mail mail = new Mail(from, asunto, toEmail, content);
            SendGrid sg = new SendGrid(API_KEY);
            Request request = new Request();
            try {
                request.setMethod(Method.POST);
                request.setEndpoint("mail/send");
                request.setBody(mail.build());
                Response response = sg.api(request);
                System.out.println("✅ Correo enviado a: " + to + " | Status: " + response.getStatusCode());
            } catch (IOException e) {
                System.out.println("❌ Error enviando correo: " + e.getMessage());
            }
        }).start();
    }
}