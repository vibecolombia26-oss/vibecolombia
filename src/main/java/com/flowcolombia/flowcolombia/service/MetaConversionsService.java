package com.flowcolombia.flowcolombia.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcolombia.flowcolombia.MetaHashUtil;
import com.flowcolombia.flowcolombia.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Envío de eventos a la <strong>API de Conversiones</strong> de Meta (CAPI).
 *
 * <p>Se usa para mandar el {@code Purchase} desde el servidor, con datos
 * personales hasheados (SHA-256) y con el <strong>código del pedido como
 * {@code event_id}</strong>. El navegador envía su propio {@code Purchase} con
 * ese mismo {@code event_id} y el mismo píxel: Meta los reconoce como el mismo
 * evento y cuenta la venta una sola vez.</p>
 *
 * <p><strong>Nunca frena el checkout:</strong> el envío es asíncrono
 * ({@link Async}) y cualquier fallo de Meta se registra y se descarta. Si falta
 * el token, el servicio simplemente no hace nada.</p>
 */
@Service
public class MetaConversionsService {

    private static final Logger log = LoggerFactory.getLogger(MetaConversionsService.class);

    private static final String VERSION_API = "v20.0";
    private static final String MONEDA = "COP";
    private static final String ACTION_SOURCE = "website";
    private static final String TIPO_CONTENIDO = "product";

    /** "Nombre del producto x2" -> cantidad 2 */
    private static final Pattern CANTIDAD = Pattern.compile("\\s+x\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);

    private final String pixelId;
    private final String token;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MetaConversionsService(@Value("${meta.pixel.id:}") String pixelId,
                                  @Value("${meta.capi.token:}") String token) {
        this.pixelId = pixelId;
        this.token = token;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** ¿Hay píxel y token configurados? Sin token no se envía nada. */
    public boolean estaConfigurado() {
        return pixelId != null && !pixelId.isBlank()
                && token != null && !token.isBlank();
    }

    /**
     * Contexto del navegador que mejora el matching de Meta.
     *
     * @param clientIp  IP del cliente
     * @param userAgent user-agent del navegador
     * @param fbp       cookie {@code _fbp} (identificador del píxel)
     * @param fbc       cookie {@code _fbc} (identificador del clic en el anuncio)
     * @param sourceUrl URL desde la que se hizo el pedido
     */
    public record Contexto(String clientIp, String userAgent, String fbp, String fbc, String sourceUrl) {
    }

    /**
     * Envía el evento {@code Purchase} del pedido.
     *
     * <p>Se ejecuta en un hilo aparte: el cliente recibe su confirmación sin
     * esperar a Meta y un error del píxel no puede romper la compra.</p>
     */
    @Async
    public void enviarPurchase(Pedido pedido, Contexto contexto) {
        if (pedido == null) {
            return;
        }
        if (!estaConfigurado()) {
            log.debug("Meta CAPI: sin META_CAPI_TOKEN configurado, se omite el evento del pedido {}.",
                    pedido.getCodigo());
            return;
        }

        try {
            enviar(construirPurchase(pedido, contexto));
        } catch (Exception e) {
            log.warn("Meta CAPI: no se pudo enviar el Purchase del pedido {}: {}",
                    pedido.getCodigo(), e.getMessage());
        }
    }

    // ============================================================
    // Construcción del evento
    // ============================================================
    private MetaEvento construirPurchase(Pedido pedido, Contexto contexto) {
        String[] nombre = partirNombre(pedido.getNombreCliente());
        LineasPedido lineas = leerProductos(pedido.getProductos());

        MetaUserData userData = new MetaUserData(
                MetaHashUtil.email(pedido.getEmail()),
                MetaHashUtil.telefono(pedido.getTelefono()),
                MetaHashUtil.nombre(nombre[0]),
                MetaHashUtil.nombre(nombre[1]),
                identificadorExterno(pedido),
                texto(contexto == null ? null : contexto.fbc()),
                texto(contexto == null ? null : contexto.fbp()),
                texto(contexto == null ? null : contexto.clientIp()),
                texto(contexto == null ? null : contexto.userAgent()));

        MetaCustomData customData = new MetaCustomData(
                pedido.getTotal() == null ? 0.0 : pedido.getTotal(),
                MONEDA,
                lineas.ids(),
                TIPO_CONTENIDO,
                lineas.unidades());

        return new MetaEvento(
                "Purchase",
                // Unix en segundos, como exige Meta.
                Instant.now().getEpochSecond(),
                ACTION_SOURCE,
                // MISMO identificador que enviará el navegador -> deduplicación.
                pedido.getCodigo(),
                texto(contexto == null ? null : contexto.sourceUrl()),
                userData,
                customData);
    }

    private static List<String> identificadorExterno(Pedido pedido) {
        String id = MetaHashUtil.email(pedido.getEmail());
        if (id == null) {
            id = MetaHashUtil.telefono(pedido.getTelefono());
        }
        return id == null ? null : List.of(id);
    }

    /** Separa "Nombre Apellido Segundo" en nombre y resto. */
    private static String[] partirNombre(String completo) {
        if (completo == null || completo.isBlank()) {
            return new String[]{null, null};
        }
        String limpio = completo.replaceAll("\\s+", " ").trim();
        int espacio = limpio.indexOf(' ');
        if (espacio < 0) {
            return new String[]{limpio, null};
        }
        return new String[]{limpio.substring(0, espacio), limpio.substring(espacio + 1)};
    }

    /**
     * Extrae los productos del detalle guardado en el pedido.
     *
     * <p>El pedido guarda los artículos como texto
     * ({@code "Mochila Compact x2 (Talla: M) - $89.900"}). Se usa el nombre del
     * producto como {@code content_id}: es lo único que hay en ese campo, no
     * existe una tabla de líneas de pedido.</p>
     */
    private static LineasPedido leerProductos(String detalle) {
        if (detalle == null || detalle.isBlank()) {
            return new LineasPedido(null, null);
        }

        List<String> ids = new ArrayList<>();
        int unidades = 0;

        for (String linea : detalle.split("\\r?\\n")) {
            String texto = linea.trim();
            if (texto.isEmpty()) {
                continue;
            }

            Matcher cantidad = CANTIDAD.matcher(texto);
            if (cantidad.find()) {
                String nombre = texto.substring(0, cantidad.start()).trim();
                int n = parsearEntero(cantidad.group(1));
                if (!nombre.isEmpty()) {
                    ids.add(nombre);
                }
                unidades += n;
            } else {
                int corte = texto.indexOf(" - ");
                String nombre = corte > 0 ? texto.substring(0, corte).trim() : texto;
                if (!nombre.isEmpty()) {
                    ids.add(nombre);
                }
                unidades += 1;
            }
        }

        return new LineasPedido(
                ids.isEmpty() ? null : ids,
                unidades > 0 ? unidades : null);
    }

    private record LineasPedido(List<String> ids, Integer unidades) {
    }

    // ============================================================
    // Envío HTTP
    // ============================================================
    private void enviar(MetaEvento evento) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("data", List.of(evento));
        // El token viaja en el cuerpo (no en la URL) para que no acabe en logs
        // de proxy ni en el historial de peticiones.
        cuerpo.put("access_token", token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String url = "https://graph.facebook.com/" + VERSION_API + "/" + pixelId + "/events";
        String json = objectMapper.writeValueAsString(cuerpo);

        ResponseEntity<String> respuesta = restTemplate.postForEntity(
                url, new HttpEntity<>(json, headers), String.class);

        if (respuesta.getStatusCode().is2xxSuccessful()) {
            log.info("Meta CAPI: Purchase enviado (event_id={}, pedido={}).",
                    evento.event_id(), evento.event_id());
        } else {
            log.warn("Meta CAPI: respuesta {} del píxel: {}",
                    respuesta.getStatusCode(), recortar(respuesta.getBody()));
        }
    }

    private static int parsearEntero(String valor) {
        try {
            return Math.max(1, Integer.parseInt(valor.trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String texto(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private static String recortar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.length() <= 300 ? valor : valor.substring(0, 300) + "...";
    }

    // ============================================================
    // DTOs (los nombres de campo son los que exige Meta)
    // ============================================================
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MetaEvento(String event_name,
                             long event_time,
                             String action_source,
                             String event_id,
                             String event_source_url,
                             MetaUserData user_data,
                             MetaCustomData custom_data) {
    }

    /**
     * Datos de usuario. {@code em}, {@code ph}, {@code fn}, {@code ln} y
     * {@code external_id} van hasheados; {@code fbc}, {@code fbp},
     * {@code client_ip_address} y {@code client_user_agent} no.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MetaUserData(String em,
                               String ph,
                               String fn,
                               String ln,
                               List<String> external_id,
                               String fbc,
                               String fbp,
                               String client_ip_address,
                               String client_user_agent) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MetaCustomData(Double value,
                                 String currency,
                                 List<String> content_ids,
                                 String content_type,
                                 Integer num_items) {
    }
}
