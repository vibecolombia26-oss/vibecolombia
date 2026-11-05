package com.flowcolombia.flowcolombia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowcolombia.flowcolombia.Producto;
import com.flowcolombia.flowcolombia.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Asistente de compras con IA.
 *
 * <p>El catálogo (nombre, categoría, precio, stock e imagen) se carga
 * <strong>una sola vez</strong> en memoria al arrancar la aplicación. Cada
 * mensaje del chat usa esta copia: no se consulta la base de datos por
 * mensaje, por lo que no se despierta Neon ni se gasta el free tier.</p>
 *
 * <p>La clave de DeepSeek vive solo en el servidor
 * ({@code DEEPSEEK_API_KEY}); el frontend nunca la recibe. Si no hay clave
 * o la API falla, el asistente responde con una guía local basada en el
 * catálogo real.</p>
 */
@Service
public class AsistenteService {

    private static final Logger log = LoggerFactory.getLogger(AsistenteService.class);

    private static final int MAX_PRODUCTOS_CONTEXTO = 150;
    private static final int MAX_RECOMENDADOS = 4;
    private static final int MAX_HISTORIAL = 6;
    private static final int MAX_MENSAJE = 600;

    // ============================================================
    // RASTREO DE PEDIDOS (puente con la página Flow Track)
    // ============================================================
    /** Respuesta canónica cuando el usuario quiere rastrear su pedido. */
    private static final String RESPUESTA_RASTREO =
            "Por tu seguridad, puedes ver el estado exacto de tu pedido en nuestra página de seguimiento.";

    /** Acción que interpreta el frontend (flow-assistant.js). */
    private static final String ACCION_IR_A_SEGUIMIENTO = "ir_a_seguimiento";

    /** Token que la IA añade al final de su respuesta para señalar la acción. */
    private static final String MARCA_SEGUIMIENTO = "[[IR_A_SEGUIMIENTO]]";

    /**
     * Frases que indican intención de rastreo. Se resuelven en local: no se
     * gastan tokens de DeepSeek y funciona igual sin clave configurada.
     */
    private static final String[] INTENCION_RASTREO = {
            "rastre", "seguimiento", "tracking",
            "mi pedido", "mi envio",
            "estado de mi pedido", "estado del pedido",
            "donde esta mi pedido", "donde va mi pedido",
            "numero de guia", "guia de envio"
    };

    private static final String SYSTEM_PROMPT =
            "Eres el asistente de compras de FLOW COLOMBIA, una tienda colombiana. " +
            "Solo puedes recomendar productos del catálogo proporcionado. " +
            "Si no sabes un precio o stock, invita al usuario a verificar en la tienda. " +
            "No diagnostiques problemas médicos ni prometas resultados. " +
            "Sé breve, amable y enfocado en ayudar a elegir. " +
            "Reglas adicionales: responde en español, en un máximo de 90 palabras, sin markdown; " +
            "nunca inventes productos, precios ni stock; cuando recomiendes algo, usa el nombre " +
            "exacto del producto tal como aparece en el catálogo y menciona a lo sumo 3 productos. " +
            "REGLA DE SEGUIMIENTO: si el usuario pregunta por el estado, la ubicación o el rastreo " +
            "de un pedido, o pide consultar su pedido, NO inventes estados, fechas ni guías y NO pidas " +
            "datos personales: responde exactamente \"" + RESPUESTA_RASTREO + "\" y añade al final, " +
            "en una línea aparte, el token " + MARCA_SEGUIMIENTO + ". " +
            "En cualquier otro caso no menciones el seguimiento ni escribas ese token.";

    private final ProductoRepository productoRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxTokens;
    private final int timeoutSeconds;

    /** Catálogo en memoria (se reemplaza completo, de ahí volatile). */
    private volatile List<ProductoCatalogo> catalogo = List.of();
    private volatile String catalogoTexto = "";

    public AsistenteService(ProductoRepository productoRepository,
                            @Value("${deepseek.api-key:}") String apiKey,
                            @Value("${deepseek.base-url:https://api.deepseek.com}") String baseUrl,
                            @Value("${deepseek.model:deepseek-chat}") String model,
                            @Value("${deepseek.max-tokens:400}") int maxTokens,
                            @Value("${deepseek.timeout-seconds:25}") int timeoutSeconds) {
        this.productoRepository = productoRepository;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ============================================================
    // CARGA ÚNICA DEL CATÁLOGO (sin consultas por mensaje)
    // ============================================================
    @EventListener(ApplicationReadyEvent.class)
    public void cargarCatalogo() {
        try {
            // Dos consultas en total al arrancar: variantes (stock) e imágenes.
            List<Producto> conVariantes = productoRepository.findAllCatalogoConVariantes();

            Map<Long, String> imagenPorProducto = new HashMap<>();
            for (Producto p : productoRepository.findAllCatalogoConImagenes()) {
                if (p != null && p.getImagenes() != null && !p.getImagenes().isEmpty()) {
                    imagenPorProducto.put(p.getId(), p.getImagenes().get(0).getUrl());
                }
            }

            List<ProductoCatalogo> lista = new ArrayList<>();
            for (Producto p : conVariantes) {
                if (p == null || p.getNombre() == null || p.getNombre().isBlank()) {
                    continue;
                }

                int stock = 0;
                if (p.getVariantes() != null) {
                    for (var variante : p.getVariantes()) {
                        if (variante != null && Boolean.TRUE.equals(variante.getActivo())
                                && variante.getStock() != null && variante.getStock() > 0) {
                            stock += variante.getStock();
                        }
                    }
                }

                String imagen = imagenPorProducto.get(p.getId());
                if (imagen == null || imagen.isBlank()) {
                    imagen = p.getImagen1();
                }

                lista.add(new ProductoCatalogo(
                        p.getId(),
                        p.getNombre().trim(),
                        (p.getCategoria() == null || p.getCategoria().isBlank()) ? "General" : p.getCategoria().trim(),
                        p.getPrecio(),
                        stock,
                        imagen == null ? "" : imagen,
                        p.getSku() == null ? "" : p.getSku()
                ));
            }

            this.catalogo = List.copyOf(lista);
            this.catalogoTexto = construirCatalogoTexto(this.catalogo);
            log.info("Asistente: catálogo en memoria listo ({} productos).", this.catalogo.size());
        } catch (Exception e) {
            log.error("Asistente: no se pudo precargar el catálogo: {}", e.getMessage());
        }
    }

    /**
     * Reintenta la carga si el arranque falló (por ejemplo, Neon dormido).
     * Se ejecuta como máximo una vez por mensaje mientras el catálogo siga vacío.
     */
    private synchronized void asegurarCatalogo() {
        if (catalogo.isEmpty()) {
            cargarCatalogo();
        }
    }

    // ============================================================
    // RESPUESTA
    // ============================================================
    public RespuestaAsistente responder(String mensaje, List<MensajeHistorial> historial) {
        String texto = mensaje == null ? "" : mensaje.trim();
        if (texto.isEmpty()) {
            return new RespuestaAsistente("Cuéntame qué estás buscando y te ayudo a elegir.", List.of());
        }
        texto = recortar(texto, MAX_MENSAJE);

        // Camino rápido: intención de rastreo resuelta en local.
        // Ni base de datos, ni tokens de DeepSeek, ni riesgo de que la IA
        // invente un estado de pedido.
        if (esConsultaDeRastreo(texto)) {
            return new RespuestaAsistente(RESPUESTA_RASTREO, List.of(), ACCION_IR_A_SEGUIMIENTO);
        }

        asegurarCatalogo();
        if (catalogo.isEmpty()) {
            return new RespuestaAsistente(
                    "Estoy cargando el catálogo de la tienda. Intenta de nuevo en unos segundos.", List.of());
        }

        String respuesta = null;
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                respuesta = llamarDeepSeek(texto, historial);
            } catch (Exception e) {
                log.warn("Asistente: fallo al llamar a DeepSeek: {}", e.getMessage());
            }
        }

        if (respuesta == null || respuesta.isBlank()) {
            List<ProductoCatalogo> sugeridos = productosPorPalabras(texto);
            respuesta = respuestaLocal(sugeridos);
        }

        // La IA señaló la acción con el token: se descarta su redacción y se
        // responde con el texto canónico, para no inventar estados de pedido.
        if (respuesta != null && respuesta.contains(MARCA_SEGUIMIENTO)) {
            return new RespuestaAsistente(RESPUESTA_RASTREO, List.of(), ACCION_IR_A_SEGUIMIENTO);
        }

        List<ProductoRecomendado> productos = recomendar(respuesta, texto);
        return new RespuestaAsistente(respuesta.trim(), productos);
    }

    /** ¿El mensaje pide rastrear o consultar un pedido? */
    private static boolean esConsultaDeRastreo(String mensaje) {
        String normalizado = normalizar(mensaje);
        for (String clave : INTENCION_RASTREO) {
            if (normalizado.contains(clave)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fallback sin IA (clave ausente, error o timeout). Usa solo catálogo real
     * para no inventar información.
     */
    private String respuestaLocal(List<ProductoCatalogo> sugeridos) {
        if (sugeridos.isEmpty()) {
            return "Ahora mismo no puedo generar una recomendación personalizada. " +
                    "Puedes explorar el catálogo completo en la tienda o escribirnos por WhatsApp.";
        }
        return "Ahora mismo no puedo generar una recomendación personalizada, pero estas opciones " +
                "del catálogo de FLOW COLOMBIA pueden servirte. Toca una para ver el detalle y el stock actualizado.";
    }

    // ============================================================
    // DEEPSEEK
    // ============================================================
    private String llamarDeepSeek(String mensaje, List<MensajeHistorial> historial) throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("temperature", 0.5);
        payload.put("max_tokens", maxTokens);
        payload.put("stream", false);

        ArrayNode messages = payload.putArray("messages");
        messages.addObject().put("role", "system").put("content", promptSistema());

        if (historial != null) {
            historial.stream()
                    .filter(Objects::nonNull)
                    .filter(m -> "user".equals(m.role()) || "assistant".equals(m.role()))
                    .filter(m -> m.content() != null && !m.content().isBlank())
                    .limit(MAX_HISTORIAL)
                    .forEach(m -> messages.addObject()
                            .put("role", m.role())
                            .put("content", recortar(m.content().trim(), 500)));
        }
        messages.addObject().put("role", "user").put("content", mensaje);

        String body = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint()))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            log.warn("Asistente: DeepSeek respondió {}: {}", response.statusCode(),
                    recortar(response.body(), 300));
            return null;
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
            return null;
        }
        return content.asText();
    }

    private String endpoint() {
        String base = (baseUrl == null || baseUrl.isBlank())
                ? "https://api.deepseek.com"
                : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
    }

    private String promptSistema() {
        return SYSTEM_PROMPT + "\n\nCATÁLOGO DISPONIBLE (solo estos productos):\n" + catalogoTexto;
    }

    private String construirCatalogoTexto(List<ProductoCatalogo> items) {
        StringBuilder sb = new StringBuilder();
        int limite = Math.min(items.size(), MAX_PRODUCTOS_CONTEXTO);
        for (int i = 0; i < limite; i++) {
            ProductoCatalogo p = items.get(i);
            sb.append("- ").append(p.nombre())
              .append(" | ").append(p.categoria())
              .append(" | $").append(formatearPrecio(p.precio()))
              .append(" | ").append(p.stock() > 0 ? "stock " + p.stock() : "agotado")
              .append(" | ref ").append(p.sku())
              .append('\n');
        }
        return sb.toString();
    }

    // ============================================================
    // RECOMENDACIÓN DE PRODUCTOS (siempre del catálogo real)
    // ============================================================
    private List<ProductoRecomendado> recomendar(String respuesta, String mensaje) {
        LinkedHashMap<Long, ProductoCatalogo> elegidos = new LinkedHashMap<>();

        // 1. Productos que la IA mencionó por su nombre exacto.
        String normalizada = normalizar(respuesta);
        List<ProductoCatalogo> porNombre = new ArrayList<>(catalogo);
        porNombre.sort(Comparator.comparingInt((ProductoCatalogo p) -> p.nombre().length()).reversed());

        for (ProductoCatalogo p : porNombre) {
            String nombre = normalizar(p.nombre());
            if (nombre.length() >= 4 && normalizada.contains(nombre)) {
                elegidos.putIfAbsent(p.id(), p);
                if (elegidos.size() >= MAX_RECOMENDADOS) {
                    break;
                }
            }
        }

        // 2. Si la IA no nombró productos, se usan palabras clave del usuario.
        if (elegidos.isEmpty()) {
            for (ProductoCatalogo p : productosPorPalabras(mensaje)) {
                elegidos.putIfAbsent(p.id(), p);
                if (elegidos.size() >= MAX_RECOMENDADOS) {
                    break;
                }
            }
        }

        // 3. Último recurso: primeras referencias con stock (o del catálogo).
        if (elegidos.isEmpty()) {
            List<ProductoCatalogo> fuente = catalogo;
            List<ProductoCatalogo> conStock = catalogo.stream().filter(p -> p.stock() > 0).toList();
            if (!conStock.isEmpty()) {
                fuente = conStock;
            }
            for (ProductoCatalogo p : fuente) {
                elegidos.putIfAbsent(p.id(), p);
                if (elegidos.size() >= MAX_RECOMENDADOS) {
                    break;
                }
            }
        }

        return elegidos.values().stream()
                .map(p -> new ProductoRecomendado(p.id(), p.nombre(), p.precio(), p.imagen()))
                .toList();
    }

    private List<ProductoCatalogo> productosPorPalabras(String mensaje) {
        String n = normalizar(mensaje);
        Set<String> categorias = new HashSet<>();

        if (contiene(n, "casa", "hogar", "sala", "cocina", "habitacion", "decor", "lampara", "mueble")) {
            categorias.add("hogar");
        }
        if (contiene(n, "rutina", "cuidado", "piel", "personal", "cabello", "crema", "perfume")) {
            categorias.add("cuidado personal");
        }
        if (contiene(n, "zapato", "calzado", "tenis", "sneaker", "talla", "comod", "caminar")) {
            categorias.add("calzado");
        }
        if (contiene(n, "tecno", "gadget", "audifon", "cargador", "electron", "reloj", "digital")) {
            categorias.add("tecnologia");
        }
        if (contiene(n, "cultivo", "planta", "jardin", "maceta", "semilla")) {
            categorias.add("cultivo");
        }
        if (contiene(n, "regalo", "regalar", "obsequio", "cumple", "detalle", "sorpresa")) {
            categorias.add("regalo");
        }

        List<ProductoCatalogo> conStock = catalogo.stream().filter(p -> p.stock() > 0).toList();
        List<ProductoCatalogo> fuente = conStock.isEmpty() ? catalogo : conStock;

        if (categorias.isEmpty()) {
            return fuente.stream().limit(MAX_RECOMENDADOS).toList();
        }

        List<ProductoCatalogo> filtrados = fuente.stream()
                .filter(p -> categorias.contains(normalizar(p.categoria())))
                .limit(MAX_RECOMENDADOS)
                .toList();

        // "Regalo" no es una categoría: se ofrecen opciones variadas.
        if (filtrados.isEmpty()) {
            filtrados = fuente.stream().limit(MAX_RECOMENDADOS).toList();
        }
        return filtrados;
    }

    // ============================================================
    // UTILIDADES
    // ============================================================
    private static boolean contiene(String texto, String... palabras) {
        for (String palabra : palabras) {
            if (texto.contains(palabra)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    private static String formatearPrecio(Double precio) {
        return String.format(Locale.forLanguageTag("es-CO"), "%,.0f", precio == null ? 0.0 : precio);
    }

    // ============================================================
    // DTOs
    // ============================================================
    public record ProductoCatalogo(Long id, String nombre, String categoria, Double precio,
                                   int stock, String imagen, String sku) {
    }

    public record ProductoRecomendado(Long id, String nombre, Double precio, String imagen) {
    }

    /**
     * Respuesta del asistente.
     *
     * @param respuesta texto para el usuario
     * @param productos recomendaciones del catálogo real
     * @param accion    acción de interfaz a ejecutar (por ejemplo
     *                  {@code ir_a_seguimiento}); {@code null} si no hay
     */
    public record RespuestaAsistente(String respuesta, List<ProductoRecomendado> productos, String accion) {

        /** Respuesta normal, sin acción especial para el frontend. */
        public RespuestaAsistente(String respuesta, List<ProductoRecomendado> productos) {
            this(respuesta, productos, null);
        }
    }

    public record MensajeHistorial(String role, String content) {
    }
}
