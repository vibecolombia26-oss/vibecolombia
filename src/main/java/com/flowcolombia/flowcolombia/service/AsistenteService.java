package com.flowcolombia.flowcolombia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Tina, la asesora de ventas de Flow Colombia (asistente con IA).
 *
 * <p><strong>Personalidad y método:</strong> el prompt la convierte en una
 * vendedora senior (metodología SPIN adaptada: entender la necesidad → ofrecer
 * máximo 2 productos → beneficio principal → ganchos de la tienda → manejo de
 * objeciones), con límites estrictos para no inventar nada.</p>
 *
 * <p><strong>Búsqueda dinámica (tool calling):</strong> además del catálogo que
 * viaja en el prompt, la IA puede llamar a la función
 * {@code buscarProductos(termino)}. El modelo no ejecuta nada: devuelve un
 * {@code tool_calls}, el backend filtra y le devuelve el resultado como mensaje
 * {@code role: "tool"}, y con eso compone la respuesta final.</p>
 *
 * <p><strong>Coste en Neon:</strong> el catálogo se carga
 * <strong>una sola vez</strong> al arrancar y la búsqueda filtra sobre esa
 * copia en memoria: ni una consulta por mensaje. Si la carga inicial falló
 * (Neon dormido), se reintenta una vez por mensaje mientras el catálogo siga
 * vacío.</p>
 *
 * <p>La clave de DeepSeek vive solo en el servidor
 * ({@code DEEPSEEK_API_KEY}); el navegador nunca la recibe. Si no hay clave o
 * la API falla, el asistente responde con el catálogo real en modo local.</p>
 */
@Service
public class AsistenteService {

    private static final Logger log = LoggerFactory.getLogger(AsistenteService.class);

    private static final int MAX_PRODUCTOS_CONTEXTO = 150;
    /** El prompt le pide máximo 2 productos: las tarjetas respetan lo mismo. */
    private static final int MAX_RECOMENDADOS = 2;
    private static final int MAX_HISTORIAL = 6;
    private static final int MAX_MENSAJE = 600;
    /** Resultados que devuelve la búsqueda al modelo. */
    private static final int MAX_RESULTADOS_BUSQUEDA = 6;
    /** Llamadas a la IA por mensaje: una con herramientas y una final sin ellas. */
    private static final int MAX_ITERACIONES_IA = 2;

    // ============================================================
    // HERRAMIENTA DE BÚSQUEDA
    // ============================================================
    public static final String TOOL_BUSCAR = "buscarProductos";

    private static final String TOOLS_JSON = """
            [
              {
                "type": "function",
                "function": {
                  "name": "buscarProductos",
                  "description": "Busca productos del catálogo de Flow Colombia por nombre, categoría o uso (por ejemplo 'sol', 'hogar', 'regalo', 'mochila'). Devuelve hasta 6 productos con nombre, precio, stock e imagen. Úsala antes de recomendar cuando el cliente pida algo concreto.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "termino": { "type": "string", "description": "Palabra o frase corta a buscar: producto, categoría o uso" }
                    },
                    "required": ["termino"]
                  }
                }
              }
            ]
            """;

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

    // ============================================================
    // EL CEREBRO: personalidad + metodología + límites
    // ============================================================
    private static final String SYSTEM_PROMPT =
            "Eres Tina, la asesora experta en estilo y bienestar de Flow Colombia. "
            + "Eres cálida, profesional y usas un tono colombiano amable (puedes usar 'parce' o 'con gusto' con moderación). "
            + "Tu trabajo es ayudar al cliente a encontrar lo que necesita y acompañarlo hasta la compra, sin ser invasiva.\n\n"

            + "METODOLOGÍA (venta consultiva, estilo SPIN):\n"
            + "1. Primero entiende la necesidad. Si el cliente no ha dicho para qué es, haz UNA sola pregunta corta y concreta "
            + "(por ejemplo: '¿es para uso diario o para un viaje?'). Nunca más de una pregunta por mensaje.\n"
            + "2. Cuando ya sepas la necesidad, recomienda como máximo 2 productos del catálogo que de verdad encajen, "
            + "con el nombre exacto tal como aparece.\n"
            + "3. Destaca siempre el beneficio principal de cada producto ('es impermeable y muy liviana'), no solo sus características.\n"
            + "4. Menciona los ganchos de la tienda en el momento oportuno, sin repetirlos en todos los mensajes: "
            + "'Tenemos envío gratis y pago contra entrega'.\n\n"

            + "MANEJO DE OBJECIONES:\n"
            + "- Si duda por el precio, no inventes descuentos ni rebajes: destaca la calidad y la tranquilidad de pagar al recibir "
            + "('lo pagas cuando ya lo tienes en tus manos').\n"
            + "- Si duda por talla, color o stock, invítala a ver la ficha del producto, donde están las opciones reales.\n"
            + "- Si pide algo que no tenemos, dilo con honestidad y ofrece la alternativa más cercana del catálogo.\n\n"

            + "LÍMITES:\n"
            + "- No inventes productos, precios, descuentos ni stock. Si no sabes algo, invita a ver la ficha del producto.\n"
            + "- No prometas resultados médicos ni curas: eres asesora de estilo y bienestar, no profesional de la salud.\n"
            + "- No pidas datos personales (correo, teléfono, dirección) en el chat.\n"
            + "- Responde en español, sin markdown, en un máximo de 80 palabras y con tono conversacional: "
            + "nada de listas con viñetas.\n\n"

            + "HERRAMIENTA: si el cliente pide algo concreto por nombre, categoría o uso ('algo para el sol', 'para la casa', "
            + "'un regalo'), llama a " + TOOL_BUSCAR + " con un término corto ANTES de responder y usa solo lo que devuelva esa "
            + "búsqueda. Si no devuelve nada, dilo y ofrece una categoría que sí exista.\n\n"

            + "REGLA DE SEGUIMIENTO: si el usuario pregunta por el estado, la ubicación o el rastreo de un pedido, o pide "
            + "consultar su pedido, NO inventes estados, fechas ni guías y NO pidas datos personales: responde exactamente \""
            + RESPUESTA_RASTREO + "\" y añade al final, en una línea aparte, el token " + MARCA_SEGUIMIENTO + ". "
            + "En cualquier otro caso no menciones el seguimiento ni escribas ese token.";

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
                        p.getSku() == null ? "" : p.getSku(),
                        p.getSlug() == null ? "" : p.getSlug()
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

    /** Compatibilidad: conversación sin contexto de carrito. */
    public RespuestaAsistente responder(String mensaje, List<MensajeHistorial> historial) {
        return responder(mensaje, historial, null);
    }

    /**
     * Genera la respuesta de Tina.
     *
     * @param mensaje   lo que escribe el cliente
     * @param historial conversación reciente
     * @param carrito   lo que el cliente ya tiene en el carrito (puede ser null)
     */
    public RespuestaAsistente responder(String mensaje,
                                        List<MensajeHistorial> historial,
                                        ContextoCarrito carrito) {

        String texto = mensaje == null ? "" : mensaje.trim();
        if (texto.isEmpty()) {
            return new RespuestaAsistente(
                    "Cuéntame qué estás buscando y con gusto te ayudo a elegir 🌸", List.of());
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

        ResultadoIA resultado = null;
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                resultado = conversar(texto, historial, carrito);
            } catch (Exception e) {
                log.warn("Asistente: fallo al llamar a DeepSeek: {}", e.getMessage());
            }
        }

        String respuesta = (resultado == null) ? null : resultado.texto();
        List<ProductoCatalogo> buscados = (resultado == null) ? List.of() : resultado.buscados();

        if (respuesta == null || respuesta.isBlank()) {
            List<ProductoCatalogo> sugeridos = buscados.isEmpty() ? productosPorPalabras(texto) : buscados;
            respuesta = respuestaLocal(sugeridos);
        }

        // La IA señaló la acción con el token: se descarta su redacción y se
        // responde con el texto canónico, para no inventar estados de pedido.
        if (respuesta.contains(MARCA_SEGUIMIENTO)) {
            return new RespuestaAsistente(RESPUESTA_RASTREO, List.of(), ACCION_IR_A_SEGUIMIENTO);
        }

        List<ProductoRecomendado> productos = recomendar(respuesta, texto, buscados);
        return new RespuestaAsistente(respuesta.trim(), productos);
    }

    /**
     * Conversación con el modelo, incluyendo el bucle de herramientas.
     *
     * <p>En la última iteración se envía la petición <em>sin</em> herramientas,
     * así el modelo está obligado a cerrar con texto en lugar de encadenar
     * búsquedas sin fin.</p>
     */
    private ResultadoIA conversar(String mensaje, List<MensajeHistorial> historial, ContextoCarrito carrito) {

        List<Map<String, Object>> mensajes = new ArrayList<>();
        Map<String, Object> sistema = new LinkedHashMap<>();
        sistema.put("role", "system");
        sistema.put("content", promptSistema(carrito));
        mensajes.add(sistema);

        if (historial != null) {
            historial.stream()
                    .filter(Objects::nonNull)
                    .filter(m -> "user".equals(m.role()) || "assistant".equals(m.role()))
                    .filter(m -> m.content() != null && !m.content().isBlank())
                    .limit(MAX_HISTORIAL)
                    .forEach(m -> mensajes.add(mensajeRol(m.role(), recortar(m.content().trim(), 500))));
        }

        mensajes.add(mensajeRol("user", mensaje));

        List<ProductoCatalogo> buscados = new ArrayList<>();

        for (int iteracion = 0; iteracion < MAX_ITERACIONES_IA; iteracion++) {
            boolean ultima = iteracion == MAX_ITERACIONES_IA - 1;

            JsonNode mensajeIA = llamarDeepSeek(mensajes, !ultima);
            if (mensajeIA == null) {
                return null;
            }

            JsonNode toolCalls = mensajeIA.path("tool_calls");
            String contenido = mensajeIA.path("content").asText("");

            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                mensajes.add(objectMapper.convertValue(mensajeIA, Map.class));

                for (JsonNode llamada : toolCalls) {
                    String nombre = llamada.path("function").path("name").asText("");
                    String id = llamada.path("id").asText("");
                    JsonNode argumentos = leerArgumentos(llamada);

                    String salida;
                    if (TOOL_BUSCAR.equals(nombre)) {
                        List<ProductoCatalogo> encontrados =
                                buscarProductos(argumentos.path("termino").asText(""));
                        // Se conserva la primera búsqueda: es la que guió la respuesta.
                        if (buscados.isEmpty()) {
                            buscados.addAll(encontrados);
                        }
                        salida = jsonBusqueda(encontrados);
                    } else {
                        salida = "{\"error\":\"Herramienta desconocida\"}";
                    }

                    Map<String, Object> mensajeTool = new LinkedHashMap<>();
                    mensajeTool.put("role", "tool");
                    mensajeTool.put("tool_call_id", id);
                    mensajeTool.put("content", salida);
                    mensajes.add(mensajeTool);
                }
                continue;
            }

            return new ResultadoIA(contenido, List.copyOf(buscados));
        }

        return new ResultadoIA("", List.copyOf(buscados));
    }

    private static Map<String, Object> mensajeRol(String rol, String contenido) {
        Map<String, Object> mensaje = new LinkedHashMap<>();
        mensaje.put("role", rol);
        mensaje.put("content", contenido);
        return mensaje;
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
            return "Ahora mismo no puedo darte una recomendación personalizada, pero puedes explorar el catálogo "
                    + "completo en la tienda o escribirnos por WhatsApp. Tenemos envío gratis y pago contra entrega.";
        }
        return "Ahora mismo no puedo darte una recomendación personalizada, pero estas opciones del catálogo de "
                + "Flow Colombia pueden servirte. Toca una para ver el detalle y el stock real. "
                + "Tenemos envío gratis y pago contra entrega.";
    }

    // ============================================================
    // BÚSQUEDA DINÁMICA (filtra sobre la copia en memoria)
    // ============================================================

    /**
     * Filtra el catálogo en memoria por nombre, categoría o uso.
     *
     * <p>No toca la base de datos: el catálogo ya está cargado y esta búsqueda
     * se resuelve con operaciones de cadenas en el proceso.</p>
     *
     * @param termino palabra o frase corta ("sol", "hogar", "mochila", "regalo")
     * @return hasta {@value #MAX_RESULTADOS_BUSQUEDA} productos, con stock primero
     */
    public List<ProductoCatalogo> buscarProductos(String termino) {
        if (termino == null || termino.isBlank()) {
            return List.of();
        }

        String completo = normalizar(termino);
        List<String> tokens = new ArrayList<>();
        for (String parte : completo.split("\\s+")) {
            if (parte.length() >= 2) {
                tokens.add(parte);
            }
        }
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<ProductoCatalogo> puntuados = new ArrayList<>();
        Map<Long, Integer> puntaje = new HashMap<>();

        for (ProductoCatalogo producto : catalogo) {
            int score = puntuar(producto, completo, tokens);
            if (score > 0) {
                puntuados.add(producto);
                puntaje.put(producto.id(), score);
            }
        }

        puntuados.sort(Comparator
                .comparingInt((ProductoCatalogo p) -> puntaje.getOrDefault(p.id(), 0)).reversed()
                .thenComparing(p -> p.stock() > 0 ? 0 : 1)
                .thenComparing(ProductoCatalogo::nombre));

        return puntuados.size() <= MAX_RESULTADOS_BUSQUEDA
                ? List.copyOf(puntuados)
                : List.copyOf(puntuados.subList(0, MAX_RESULTADOS_BUSQUEDA));
    }

    private static int puntuar(ProductoCatalogo producto, String completo, List<String> tokens) {
        String nombre = normalizar(producto.nombre());
        String categoria = normalizar(producto.categoria());

        int score = 0;
        // Coincidencia de la frase completa: pesa más que palabra por palabra.
        if (nombre.contains(completo)) {
            score += 6;
        }
        if (categoria.contains(completo)) {
            score += 5;
        }
        for (String token : tokens) {
            if (nombre.contains(token)) {
                score += 3;
            }
            if (categoria.contains(token)) {
                score += 2;
            }
        }
        // Lo agotado nunca debe ganarle a lo disponible.
        if (score > 0 && producto.stock() <= 0) {
            score -= 2;
        }
        return score;
    }

    /** Resultado de la búsqueda que se le devuelve al modelo. */
    private String jsonBusqueda(List<ProductoCatalogo> encontrados) {
        List<Map<String, Object>> resultados = new ArrayList<>();
        for (ProductoCatalogo p : encontrados) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nombre", p.nombre());
            item.put("categoria", p.categoria());
            item.put("precio", p.precio());
            item.put("disponible", p.stock() > 0);
            item.put("referencia", p.sku());
            resultados.add(item);
        }

        Set<String> categorias = new java.util.TreeSet<>();
        for (ProductoCatalogo p : catalogo) {
            categorias.add(p.categoria());
        }

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("resultados", resultados);
        cuerpo.put("total", resultados.size());
        cuerpo.put("categorias_disponibles", new ArrayList<>(categorias));
        if (resultados.isEmpty()) {
            cuerpo.put("nota", "Sin coincidencias para ese término. Ofrece una de las categorías disponibles.");
        }

        try {
            return objectMapper.writeValueAsString(cuerpo);
        } catch (Exception e) {
            return "{\"resultados\":[],\"total\":0}";
        }
    }

    // ============================================================
    // DEEPSEEK
    // ============================================================
    private JsonNode llamarDeepSeek(List<Map<String, Object>> mensajes, boolean incluirHerramientas) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", model);
            payload.put("temperature", 0.6);
            payload.put("max_tokens", maxTokens);
            payload.put("stream", false);
            payload.set("messages", objectMapper.valueToTree(mensajes));

            if (incluirHerramientas) {
                payload.set("tools", objectMapper.readTree(TOOLS_JSON));
                payload.put("tool_choice", "auto");
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint()))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.warn("Asistente: DeepSeek respondió {}: {}", response.statusCode(),
                        recortar(response.body(), 300));
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode mensaje = root.path("choices").path(0).path("message");
            return mensaje.isMissingNode() || mensaje.isNull() ? null : mensaje;
        } catch (Exception e) {
            log.warn("Asistente: error de red con DeepSeek: {}", e.getMessage());
            return null;
        }
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

    private JsonNode leerArgumentos(JsonNode llamada) {
        String crudo = llamada.path("function").path("arguments").asText("");
        if (crudo.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(crudo);
        } catch (Exception e) {
            log.warn("Asistente: argumentos no válidos: {}", recortar(crudo, 200));
            return objectMapper.createObjectNode();
        }
    }

    /** Prompt final: personalidad + contexto del carrito + catálogo. */
    private String promptSistema(ContextoCarrito carrito) {
        return SYSTEM_PROMPT
                + contextoCarrito(carrito)
                + "\nCATÁLOGO DISPONIBLE (solo estos productos):\n" + catalogoTexto;
    }

    /**
     * Personalización: qué tiene ya el cliente en el carrito.
     * Sirve para ofrecer un complemento lógico sin sonar a robot.
     */
    private String contextoCarrito(ContextoCarrito carrito) {
        if (carrito == null || carrito.unidades() <= 0) {
            return "\n\nCONTEXTO DEL CLIENTE:\n- Todavía no tiene productos en el carrito.\n";
        }

        StringBuilder lista = new StringBuilder();
        if (carrito.productos() != null) {
            int mostrados = 0;
            for (String nombre : carrito.productos()) {
                if (nombre == null || nombre.isBlank() || mostrados >= 5) {
                    continue;
                }
                lista.append(mostrados == 0 ? "" : ", ").append('"').append(recortar(nombre.trim(), 60)).append('"');
                mostrados++;
            }
        }

        return "\n\nCONTEXTO DEL CLIENTE:\n- Tiene " + carrito.unidades() + " producto(s) en el carrito"
                + (lista.isEmpty() ? "" : ": " + lista)
                + ". Si encaja de forma lógica, ofrécele UN complemento en una sola frase, sin presionar.\n";
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
    private List<ProductoRecomendado> recomendar(String respuesta,
                                                 String mensaje,
                                                 List<ProductoCatalogo> buscados) {
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

        // 2. Si no las nombró, las que devolvió su propia búsqueda.
        if (elegidos.isEmpty() && buscados != null) {
            for (ProductoCatalogo p : buscados) {
                elegidos.putIfAbsent(p.id(), p);
                if (elegidos.size() >= MAX_RECOMENDADOS) {
                    break;
                }
            }
        }

        // 3. Palabras clave del cliente.
        if (elegidos.isEmpty()) {
            for (ProductoCatalogo p : productosPorPalabras(mensaje)) {
                elegidos.putIfAbsent(p.id(), p);
                if (elegidos.size() >= MAX_RECOMENDADOS) {
                    break;
                }
            }
        }

        // 4. Último recurso: primeras referencias con stock (o del catálogo).
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
                .map(p -> new ProductoRecomendado(p.id(), p.nombre(), p.precio(), p.imagen(), p.slug()))
                .toList();
    }

    private List<ProductoCatalogo> productosPorPalabras(String mensaje) {
        String n = normalizar(mensaje);
        Set<String> categorias = new HashSet<>();

        if (contiene(n, "casa", "hogar", "sala", "cocina", "habitacion", "decor", "lampara", "mueble")) {
            categorias.add("hogar");
        }
        if (contiene(n, "rutina", "cuidado", "piel", "personal", "cabello", "crema", "perfume", "bienestar")) {
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
                .replaceAll("\\s+", " ")
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

    /** Lo que el cliente ya tiene en el carrito (lo envía el navegador). */
    public record ContextoCarrito(int unidades, List<String> productos) {
    }

    /**
     * Entrada del catálogo en memoria.
     *
     * @param slug URL canónica del producto; se usa para que las tarjetas del
     *             chat enlacen directo a {@code /producto/{slug}} sin pasar por
     *             la redirección 301 de la ruta antigua por id
     */
    public record ProductoCatalogo(Long id, String nombre, String categoria, Double precio,
                                   int stock, String imagen, String sku, String slug) {
    }

    /**
     * Tarjeta de producto que devuelve el chat.
     *
     * @param slug URL canónica; {@code flow-assistant.js} la prefiere y solo
     *             cae al {@code id} si faltara
     */
    public record ProductoRecomendado(Long id, String nombre, Double precio, String imagen, String slug) {
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

    /** Resultado interno del bucle con el modelo. */
    private record ResultadoIA(String texto, List<ProductoCatalogo> buscados) {
    }
}
