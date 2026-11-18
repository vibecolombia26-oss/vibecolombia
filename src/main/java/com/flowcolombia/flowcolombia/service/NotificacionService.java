package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Aviso al celular del dueño cuando entra un pedido nuevo, usando
 * <strong>ntfy.sh</strong> (gratis, sin registro y sin SDK).
 *
 * <p><strong>Nunca frena la tienda:</strong> el envío es {@link Async} y todos
 * los fallos se registran y se descartan. Si ntfy no responde, el cliente ya
 * tiene su código de pedido y el panel sigue funcionando igual.</p>
 *
 * <p><strong>Robot dormido:</strong> si {@code ntfy.topic.url} está vacío (o
 * falta la variable {@code NTFY_TOPIC_URL}), no se hace ninguna petición HTTP
 * ni se genera tráfico a un servicio externo.</p>
 *
 * <p><strong>Privacidad:</strong> el tema de ntfy.sh es público para quien
 * conozca su nombre, y aquí viajan el nombre del cliente y el total. Usa un
 * tema largo e imposible de adivinar (por ejemplo
 * {@code flowcolombia-7f3k9x2a}) o un tema con acceso protegido. Si algún día
 * se prefiere no sacar el nombre del cliente, basta con cambiar
 * {@link #construirMensaje(Pedido)}: es el único punto donde se arma el
 * texto.</p>
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    /** Formato de moneda colombiano: 89900 → «89.900». */
    private static final Locale LOCALE_CO = new Locale("es", "CO");

    private final String url;
    private final RestTemplate restTemplate;

    public NotificacionService(@Value("${ntfy.topic.url:}") String url,
                               @Value("${ntfy.timeout-ms:5000}") int timeoutMs) {
        this.url = url;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /** ¿Hay tema configurado? Sin URL no se envía nada. */
    public boolean estaConfigurado() {
        return url != null && !url.isBlank();
    }

    /**
     * Avisa del pedido nuevo en segundo plano.
     *
     * <p>Se ejecuta en otro hilo: el cliente recibe su confirmación sin esperar
     * a ntfy y un fallo de la notificación no puede romper el checkout.</p>
     *
     * @param pedido pedido recién guardado (usa nombre, total y código)
     */
    @Async
    public void notificarPedidoNuevo(Pedido pedido) {
        if (pedido == null) {
            return;
        }
        if (!estaConfigurado()) {
            log.debug("Notificación push: sin ntfy.topic.url configurada; no se envía nada.");
            return;
        }

        try {
            enviar(construirMensaje(pedido));
        } catch (Exception e) {
            // La tienda sigue exactamente igual: la notificación es un extra.
            log.warn("Notificación push: no se pudo enviar ({})", e.getMessage());
        }
    }

    /**
     * Petición real a ntfy. Aislada para poder sustituirla en los tests sin
     * red (el proyecto no usa Mockito).
     *
     * @param mensaje texto plano que ntfy muestra como notificación
     */
    protected void enviar(String mensaje) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));
        restTemplate.postForEntity(url, new HttpEntity<>(mensaje, headers), String.class);
    }

    /**
     * Texto de la notificación.
     *
     * <p>Formato aprobado: {@code 🛍️ Nuevo pedido: [Nombre] - Total: $[Total]
     * - Código: [FLOW-XXXX]}. El total se formatea a la colombiana para que se
     * lea {@code $89.900} y no {@code $89900.0}.</p>
     *
     * @param pedido pedido guardado
     * @return mensaje de una sola línea, listo para el cuerpo del POST
     */
    static String construirMensaje(Pedido pedido) {
        String nombre = textoSeguro(pedido.getNombreCliente(), "Cliente");
        String codigo = textoSeguro(pedido.getCodigo(), "sin código");
        double total = pedido.getTotal() == null ? 0d : pedido.getTotal();
        String totalFormateado = NumberFormat.getNumberInstance(LOCALE_CO).format(total);
        return "🛍️ Nuevo pedido: " + nombre + " - Total: $" + totalFormateado + " - Código: " + codigo;
    }

    private static String textoSeguro(String valor, String porDefecto) {
        return (valor == null || valor.isBlank()) ? porDefecto : valor.trim();
    }
}
