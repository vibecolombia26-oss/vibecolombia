package com.flowcolombia.flowcolombia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cliente HTTP hacia Dropi.
 *
 * <p><strong>Única pieza del sync que depende del contrato de Dropi.</strong>
 * Dropi no publica documentación oficial de su API (su propio MCP reconoce que
 * los endpoints se reconstruyeron del cliente web), así que aquí se concentran
 * la URL, la cabecera de autenticación y los nombres de campo aceptados. Si
 * Dropi cambia algo, se ajusta solo esta clase.</p>
 *
 * <p>Tres decisiones de seguridad:</p>
 * <ol>
 *   <li>El token se lee de la variable de entorno {@code DROPI_API_TOKEN} y
 *       nunca se registra en logs ni se envía al navegador.</li>
 *   <li>La respuesta se reduce en {@link #parsear(String)} a
 *       {@link DropiEstado}: solo referencia, estado y guía. El nombre, la
 *       dirección, el teléfono y cualquier otro dato personal se descartan al
 *       leer el JSON, no se guardan en ningún objeto intermedio.</li>
 *   <li>Cualquier fallo (timeout, 401, 500, JSON raro) se registra y se
 *       traduce a «sin datos»: la tienda nunca depende de Dropi.</li>
 * </ol>
 *
 * <p>Coste: una sola petición HTTP por sincronización, con timeout corto. No
 * toca Neon.</p>
 */
@Service
public class DropiHttpClient implements DropiClient {

    private static final Logger log = LoggerFactory.getLogger(DropiHttpClient.class);

    /** Nombres de campo aceptados, en orden de preferencia. */
    private static final String[] CAMPOS_REFERENCIA = {
            "reference", "external_id", "order_reference", "codigo"
    };
    private static final String[] CAMPOS_ID = {
            "id", "order_id", "dropi_id"
    };
    private static final String[] CAMPOS_ESTADO = {
            "status", "estado", "order_status", "state"
    };
    private static final String[] CAMPOS_GUIA = {
            "guide_number", "numero_guia", "tracking_number", "guide", "tracking"
    };

    /** Posibles envoltorios del array de pedidos en la respuesta. */
    private static final String[] ENVOLTORIOS = {"data", "orders", "results", "items"};

    private final String url;
    private final String token;
    private final String cabeceraAuth;
    private final String prefijoAuth;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DropiHttpClient(@Value("${DROPI_API_URL:}") String url,
                           @Value("${DROPI_API_TOKEN:}") String token,
                           @Value("${DROPI_API_AUTH_HEADER:Authorization}") String cabeceraAuth,
                           @Value("${DROPI_API_AUTH_PREFIX:Bearer }") String prefijoAuth,
                           @Value("${DROPI_API_TIMEOUT_MS:8000}") int timeoutMs) {
        this.url = url;
        this.token = token;
        this.cabeceraAuth = cabeceraAuth;
        this.prefijoAuth = prefijoAuth;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /** ¿Está configurada la integración? Sin URL y token no se llama nada. */
    @Override
    public boolean estaConfigurado() {
        return url != null && !url.isBlank() && token != null && !token.isBlank();
    }

    @Override
    public Map<String, DropiEstado> consultarEstados(Collection<String> referencias) {
        if (!estaConfigurado()) {
            log.debug("Dropi: sin DROPI_API_URL y DROPI_API_TOKEN configurados; no se consulta nada.");
            return Map.of();
        }
        if (referencias == null || referencias.isEmpty()) {
            return Map.of();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            // El token viaja en cabecera, nunca en la URL: así no queda en logs
            // de proxy ni en el historial de peticiones.
            headers.set(cabeceraAuth, prefijoAuth + token);

            ResponseEntity<String> respuesta = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (!respuesta.getStatusCode().is2xxSuccessful() || respuesta.getBody() == null) {
                log.warn("Dropi: respuesta {} al consultar pedidos.", respuesta.getStatusCode());
                return Map.of();
            }

            Map<String, DropiEstado> porReferencia = indexar(parsear(respuesta.getBody()));
            Map<String, DropiEstado> pedidos = new HashMap<>();
            for (String referencia : referencias) {
                DropiEstado estado = porReferencia.get(normalizar(referencia));
                if (estado != null) {
                    pedidos.put(referencia, estado);
                }
            }
            return pedidos;

        } catch (Exception e) {
            // Nunca se propaga: un fallo de Dropi no puede romper el sync ni la tienda.
            log.warn("Dropi: no se pudieron consultar los pedidos: {}", e.getMessage());
            return Map.of();
        }
    }

    /** Índice doble: por referencia y por ID de Dropi, ambos normalizados. */
    private static Map<String, DropiEstado> indexar(List<DropiEstado> estados) {
        Map<String, DropiEstado> indice = new HashMap<>();
        for (DropiEstado estado : estados) {
            String referencia = normalizar(estado.referencia());
            if (!referencia.isEmpty()) {
                indice.putIfAbsent(referencia, estado);
            }
            String idDropi = normalizar(estado.idDropi());
            if (!idDropi.isEmpty()) {
                indice.putIfAbsent(idDropi, estado);
            }
        }
        return indice;
    }

    /**
     * Convierte el JSON de Dropi en la lista <strong>sanitizada</strong>.
     *
     * <p>Se aceptan varios nombres de campo porque el contrato no está
     * publicado; el orden de preferencia está en los arrays de constantes. Si
     * un pedido no trae referencia o el JSON no tiene la forma esperada, se
     * ignora: el sync simplemente no actualiza nada y lo deja en el log.</p>
     *
     * @param json cuerpo de la respuesta de Dropi
     * @return lista de estados con solo referencia, estado y guía
     */
    static List<DropiEstado> parsear(String json) {
        List<DropiEstado> estados = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return estados;
        }

        try {
            JsonNode raiz = new ObjectMapper().readTree(json);
            JsonNode pedidos = localizarArray(raiz);
            if (pedidos == null || !pedidos.isArray()) {
                return estados;
            }

            for (JsonNode pedido : pedidos) {
                if (pedido == null || !pedido.isObject()) {
                    continue;
                }
                // Solo estos cuatro campos salen del JSON. Nada de nombre,
                // dirección, teléfono, correo ni documentos.
                String referencia = primerTexto(pedido, CAMPOS_REFERENCIA);
                String idDropi = primerTexto(pedido, CAMPOS_ID);
                String estado = primerTexto(pedido, CAMPOS_ESTADO);
                String guia = primerTexto(pedido, CAMPOS_GUIA);

                if (referencia == null && idDropi == null && estado == null && guia == null) {
                    continue;
                }
                estados.add(new DropiEstado(referencia, idDropi, estado, guia));
            }
        } catch (Exception e) {
            log.warn("Dropi: respuesta ilegible ({}); no se aplica ningún cambio.", e.getMessage());
            return new ArrayList<>();
        }

        return estados;
    }

    /** Busca el array de pedidos en la raíz o en los envoltorios conocidos. */
    private static JsonNode localizarArray(JsonNode raiz) {
        if (raiz == null) {
            return null;
        }
        if (raiz.isArray()) {
            return raiz;
        }
        for (String envoltorio : ENVOLTORIOS) {
            JsonNode hijo = raiz.get(envoltorio);
            if (hijo != null && hijo.isArray()) {
                return hijo;
            }
        }
        return null;
    }

    /** Primer valor textual no vacío de una lista de posibles nombres. */
    private static String primerTexto(JsonNode nodo, String[] campos) {
        for (String campo : campos) {
            JsonNode valor = nodo.get(campo);
            if (valor == null || valor.isNull() || valor.isContainerNode()) {
                continue;
            }
            String texto = valor.asText("").trim();
            if (!texto.isEmpty()) {
                return texto;
            }
        }
        return null;
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }
}
