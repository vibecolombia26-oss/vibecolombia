package com.vibecolombia.vibecolombia;

import org.springframework.stereotype.Service;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Base64;

@Service
public class EmailService {

    private static final String API_KEY = "6b7f66f3518fbd76097858ae376a2882-d638fab7-32f0de7b";
    private static final String DOMAIN = "sandbox23843a77e68d4d3a9d11578c53f6c181.mailgun.org";
    private static final String FROM = "VIBE COLOMBIA 26 <postmaster@sandbox23843a77e68d4d3a9d11578c53f6c181.mailgun.org>";

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
            try {
                String url = "https://api.mailgun.net/v3/" + DOMAIN + "/messages";
                String auth = Base64.getEncoder().encodeToString(("api:" + API_KEY).getBytes());

                String params = "from=" + URLEncoder.encode(FROM, "UTF-8") +
                        "&to=" + URLEncoder.encode(to, "UTF-8") +
                        "&subject=" + URLEncoder.encode(asunto, "UTF-8") +
                        "&text=" + URLEncoder.encode(cuerpo, "UTF-8");

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Basic " + auth);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(params.getBytes());
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                System.out.println("✅ Correo enviado a: " + to + " | Status: " + code);
            } catch (Exception e) {
                System.out.println("❌ Error enviando correo: " + e.getMessage());
            }
        }).start();
    }
}