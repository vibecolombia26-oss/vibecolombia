package com.flowcolombia.flowcolombia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowcolombia.flowcolombia.DropiParserUtil;
import com.flowcolombia.flowcolombia.Producto;
import com.flowcolombia.flowcolombia.ProductoRepository;
import com.flowcolombia.flowcolombia.VarianteImportada;
import com.flowcolombia.flowcolombia.VarianteProducto;
import com.flowcolombia.flowcolombia.VarianteProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Asistente de administración con <strong>Tool Calling</strong> (llamada a
 * funciones) sobre la API de DeepSeek.
 *
 * <p><strong>Cómo sabe la IA qué puede hacer:</strong> además del texto del
 * system prompt, se le envían las definiciones formales en el arreglo
 * {@code tools} (formato OpenAI/DeepSeek: {@code type: "function"} + JSON
 * Schema de parámetros). El modelo no ejecuta nada: devuelve un
 * {@code tool_calls} con el nombre de la función y los argumentos, y es este
 * servicio quien decide ejecutar o pedir confirmación.</p>
 *
 * <p><strong>Flujo de confirmación:</strong></p>
 * <ol>
 *   <li>El admin escribe. Si el modelo pide una herramienta de <em>escritura</em>,
 *       no se ejecuta: se devuelve {@link AccionPendiente} con una descripción
 *       legible para que la persona pulse "Aprobar y Ejecutar".</li>
 *   <li>El frontend reenvía la acción con {@code confirmarAccion = true} y el
 *       backend la valida otra vez antes de aplicarla (nunca se confía en lo
 *       que llega del navegador).</li>
 *   <li>Las herramientas de <em>solo lectura</em> (buscar) se ejecutan al
 *       instante y su resultado se devuelve al modelo para que conteste en
 *       lenguaje natural.</li>
 * </ol>
 *
 * <p>Si falta {@code DEEPSEEK_API_KEY}, el asistente no se cae: avisa de que
 * no está configurado.</p>
 */
@Service
public class AdminAsistenteService {

    private static final Logger log = LoggerFactory.getLogger(AdminAsistenteService.class);

    // ============================================================
    // Nombres de las herramientas (una sola fuente de verdad)
    // ============================================================
    public static final String TOOL_BUSCAR = "buscarProductosPorNombre";
    public static final String TOOL_STOCK = "actualizarStockVariante";
    public static final String TOOL_STOCK_PRODUCTO = "actualizarStockDeTodoElProducto";
    public static final String TOOL_PRECIO = "actualizarPrecioVariante";
    public static final String TOOL_DESCRIPCION = "actualizarDescripcionProducto";
    public static final String TOOL_DROPI = "importarVariantesDropi";

    private static final int MAX_HISTORIAL = 10;
    /**
     * Tope del mensaje del administrador. Se subió para que quepa un inventario
     * de Dropi pegado en el chat (antes eran 600 caracteres).
     */
    private static final int MAX_MENSAJE = 12_000;
    private static final int MAX_DESCRIPCION = 2000;
    private static final int MAX_PRODUCTOS_BUSQUEDA = 8;
    private static final int MAX_ITERACIONES_IA = 3;
    private static final int MAX_VARIANTES_PREVIEW = 10;
    private static final int MAX_LARGO_SKU = 80;
    private static final double PRECIO_MAXIMO = 100_000_000d;
    private static final int STOCK_MAXIMO = 1_000_000;

    /** Cuánto tiempo se recuerda el último texto de Dropi pegado (ver {@link #textoDropiReciente()}). */
    private static final Duration VIGENCIA_TEXTO_DROPI = Duration.ofMinutes(20);

    private static final String SYSTEM_PROMPT =
            "Eres el asistente del administrador de Flow Colombia. Tienes permiso para actualizar stock y precios, "
            + "pero SIEMPRE debes pedir confirmación antes de ejecutar una acción destructiva o modificar datos. Sé conciso.\n\n"
            + "Herramientas disponibles (no las ejecutas tú: las propone el sistema y el administrador aprueba):\n"
            + "- " + TOOL_BUSCAR + "(nombre): busca productos y devuelve id, SKU de cada variante, precio y stock.\n"
            + "- " + TOOL_STOCK + "(sku, nuevoStock): cambia el stock de una variante (entero mayor o igual a 0).\n"
            + "- " + TOOL_STOCK_PRODUCTO + "(nombreProducto, nuevoStock): actualiza el stock de TODAS las variantes "
            + "de un producto a la vez (acción masiva).\n"
            + "- " + TOOL_PRECIO + "(sku, nuevoPrecio): cambia el precio de una variante (mayor que 0).\n"
            + "- " + TOOL_DESCRIPCION + "(productoId, nuevaDescripcion): cambia la descripción de un producto.\n"
            + "- " + TOOL_DROPI + "(productoId, textoDropi): importa un inventario de Dropi a un producto que ya existe. "
            + "El texto lo lee un parser determinista en Java: crea las variantes que falten y actualiza stock, "
            + "costo y precio de las que ya están. Es una acción masiva.\n\n"
            + "Reglas: usa " + TOOL_BUSCAR + " antes de modificar algo si no conoces el SKU o el id. "
            + "Si el usuario pide actualizar el stock de un producto entero (por ejemplo 'todas las tallas de las mochilas'), "
            + "puedes usar la herramienta " + TOOL_STOCK_PRODUCTO + ". En 'nombreProducto' pasa SOLO el nombre del producto, "
            + "sin frases como 'todas las tallas de'. Si la herramienta responde que hay varios productos con ese nombre, "
            + "pide al usuario que sea más específico en lugar de elegir uno tú. "
            + "No inventes productos, SKU ni precios: si la búsqueda no devuelve nada, dilo. "
            + "Nunca propongas precios negativos o cero. Propón una sola acción por mensaje. Responde en español.\n\n"
            + "Importación de Dropi: si el administrador dice 'importa este inventario', 'actualiza con este texto de Dropi' "
            + "o pega un texto con Color, Tallas, Stock, Costo y Precio, primero identifica el producto con " + TOOL_BUSCAR
            + " y pasa su id a " + TOOL_DROPI + ". Si hay varios productos parecidos pide el nombre completo; si no hay "
            + "ninguno dilo y no inventes. El texto NO lo interpretas tú: no lo transcribas, no lo resumas, no calcules "
            + "variantes ni precios y no lo repitas en tu respuesta, porque el sistema ya lo leyó. Si el mensaje del "
            + "administrador acaba de traer ese texto pegado, llama a la herramienta con textoDropi vacío. "
            + "La herramienta solo añade variantes a productos existentes: nunca crees productos nuevos.";

    /**
     * Definiciones que se envían en {@code tools}. Es JSON Schema: así el
     * modelo sabe el nombre exacto, el tipo y el rango de cada parámetro.
     */
    private static final String TOOLS_JSON = """
            [
              {
                "type": "function",
                "function": {
                  "name": "buscarProductosPorNombre",
                  "description": "Busca productos del catálogo por coincidencia parcial del nombre. Devuelve el id del producto, el SKU de cada variante, su precio y su stock. Úsala siempre que no conozcas el SKU o el id.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "nombre": { "type": "string", "description": "Texto a buscar dentro del nombre del producto" }
                    },
                    "required": ["nombre"]
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "actualizarStockVariante",
                  "description": "Cambia el stock de una variante concreta identificada por su SKU.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "sku": { "type": "string", "description": "SKU exacto de la variante" },
                      "nuevoStock": { "type": "integer", "minimum": 0, "maximum": 1000000, "description": "Unidades disponibles" }
                    },
                    "required": ["sku", "nuevoStock"]
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "actualizarStockDeTodoElProducto",
                  "description": "Actualiza el stock de TODAS las variantes de un producto específico usando el nombre del producto. Úsala cuando el administrador pida cambiar el stock de un producto completo (por ejemplo 'todas las tallas de las mochilas'). Es una acción masiva: siempre requiere confirmación.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "nombreProducto": { "type": "string", "description": "Nombre del producto (solo el nombre, sin frases como 'todas las tallas de')" },
                      "nuevoStock": { "type": "integer", "minimum": 0, "maximum": 1000000, "description": "Unidades que tendrá cada variante" }
                    },
                    "required": ["nombreProducto", "nuevoStock"]
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "actualizarPrecioVariante",
                  "description": "Cambia el precio de venta de una variante concreta identificada por su SKU.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "sku": { "type": "string", "description": "SKU exacto de la variante" },
                      "nuevoPrecio": { "type": "number", "exclusiveMinimum": 0, "description": "Precio en pesos colombianos, mayor que 0" }
                    },
                    "required": ["sku", "nuevoPrecio"]
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "actualizarDescripcionProducto",
                  "description": "Reemplaza la descripción de un producto.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "productoId": { "type": "integer", "description": "Id numérico del producto" },
                      "nuevaDescripcion": { "type": "string", "description": "Texto nuevo de la descripción (máximo 2000 caracteres)" }
                    },
                    "required": ["productoId", "nuevaDescripcion"]
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "importarVariantesDropi",
                  "description": "Importa un inventario de Dropi (varias variantes con color, talla, stock, costo y precio sugerido) a un producto que YA existe. El texto lo procesa un parser determinista en Java, no tú. Crea las variantes que falten y actualiza stock, costo y precio de las que ya existen. Es una acción masiva: siempre requiere confirmación.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "productoId": { "type": "integer", "description": "Id del producto existente, obtenido con buscarProductosPorNombre" },
                      "textoDropi": { "type": "string", "description": "Texto crudo de Dropi. Déjalo vacío si el administrador acaba de pegarlo en su último mensaje: el sistema recupera ese texto." }
                    },
                    "required": ["productoId"]
                  }
                }
              }
            ]
            """;

    private final ProductoRepository productoRepository;
    private final VarianteProductoRepository varianteProductoRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;

    /**
     * Último inventario de Dropi que el parser entendió.
     *
     * <p>Sirve para el flujo en dos turnos: el administrador pega el texto y
     * dice «impórtalo en la mochila»; o pega el texto y, cuando el asistente le
     * pregunta a qué producto, contesta solo con el nombre. En ese segundo
     * mensaje ya no viene el texto, y sin esta memoria habría que pedirlo otra
     * vez. Vive en memoria del proceso (no se guarda en Neon) y caduca: el
     * panel de administración es de un único usuario, así que no hay riesgo de
     * mezclar textos entre sesiones.</p>
     */
    private volatile String ultimoTextoDropi;
    private volatile Instant ultimoTextoDropiEn;

    public AdminAsistenteService(ProductoRepository productoRepository,
                                 VarianteProductoRepository varianteProductoRepository,
                                 @Value("${deepseek.api-key:}") String apiKey,
                                 @Value("${deepseek.base-url:https://api.deepseek.com}") String baseUrl,
                                 @Value("${deepseek.model:deepseek-chat}") String model,
                                 @Value("${deepseek.timeout-seconds:25}") int timeoutSeconds) {
        this.productoRepository = productoRepository;
        this.varianteProductoRepository = varianteProductoRepository;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ============================================================
    // DTOs
    // ============================================================

    /** Acción propuesta por la IA, pendiente de aprobación humana. */
    /**
     * Acción propuesta por la IA, pendiente de aprobación humana.
     *
     * @param masiva true cuando la acción afecta a varias filas a la vez
     *               (por ejemplo todo el stock de un producto); el chat la
     *               muestra con un aviso destacado.
     */
    public record AccionPendiente(String tool, String descripcion, Map<String, Object> argumentos, boolean masiva) {
    }

    /**
     * @param respuesta            texto para el chat
     * @param requiereConfirmacion true si hay una acción esperando aprobación
     * @param accion               la acción propuesta (null si no hay)
     */
    public record Respuesta(String respuesta, boolean requiereConfirmacion, AccionPendiente accion) {

        static Respuesta texto(String texto) {
            return new Respuesta(texto, false, null);
        }
    }

    /** Resultado de aplicar una acción ya aprobada. */
    public record Resultado(boolean ok, String mensaje) {
    }

    public record MensajeHistorial(String role, String content) {
    }

    // ============================================================
    // Conversación
    // ============================================================

    /**
     * Responde al mensaje del administrador.
     *
     * <p>Si el modelo pide una herramienta de escritura, NO se ejecuta: se
     * devuelve la propuesta para que la persona la apruebe.</p>
     *
     * <p><strong>Inventario pegado:</strong> si el mensaje trae un texto de
     * Dropi, se lee aquí con {@link DropiParserUtil} y al modelo se le envía
     * solo un resumen. Así el texto largo no gasta tokens de entrada ni hay
     * forma de que el modelo transcriba mal un precio: el original queda en
     * Java, que es quien lo va a usar.</p>
     */
    public Respuesta responder(String mensaje, List<MensajeHistorial> historial) {
        String texto = mensaje == null ? "" : mensaje.trim();
        if (texto.isEmpty()) {
            return Respuesta.texto("Escríbeme qué quieres cambiar, por ejemplo: «sube el stock de MOCH-M a 20».");
        }
        texto = recortar(texto, MAX_MENSAJE);

        if (apiKey == null || apiKey.isBlank()) {
            return Respuesta.texto(
                    "El asistente no está configurado: falta la variable de entorno DEEPSEEK_API_KEY. "
                    + "Mientras tanto puedo seguir ayudándote desde el panel.");
        }

        // El inventario se detecta y se guarda ANTES de hablar con el modelo:
        // el texto crudo nunca sale de Java.
        List<VarianteImportada> inventarioPegado = DropiParserUtil.parsear(texto);
        boolean esInventario = DropiParserUtil.esInventario(inventarioPegado);
        String textoParaElModelo = texto;
        if (esInventario) {
            recordarTextoDropi(texto);
            textoParaElModelo = resumenInventarioPegado(inventarioPegado);
        }

        List<Map<String, Object>> mensajes = new ArrayList<>();
        mensajes.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

        if (historial != null) {
            historial.stream()
                    .filter(m -> m != null && m.content() != null && !m.content().isBlank())
                    .filter(m -> "user".equals(m.role()) || "assistant".equals(m.role()))
                    .limit(MAX_HISTORIAL)
                    .forEach(m -> mensajes.add(Map.of("role", m.role(),
                            "content", historialParaElModelo(m.content()))));
        }

        mensajes.add(Map.of("role", "user", "content", textoParaElModelo));

        try {
            for (int iteracion = 0; iteracion < MAX_ITERACIONES_IA; iteracion++) {
                JsonNode mensajeIA = llamarDeepSeek(mensajes);
                if (mensajeIA == null) {
                    return Respuesta.texto("No pude contactar con el asistente. Revisa la conexión e inténtalo de nuevo.");
                }

                JsonNode toolCalls = mensajeIA.path("tool_calls");
                String contenido = mensajeIA.path("content").asText("");

                // Sin herramientas: respuesta normal.
                if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                    return Respuesta.texto(contenido.isBlank()
                            ? "No tengo una respuesta para eso."
                            : contenido.trim());
                }

                // El mensaje del modelo con sus tool_calls vuelve al historial
                // para poder devolverle el resultado de cada herramienta.
                mensajes.add(objectMapper.convertValue(mensajeIA, Map.class));

                for (JsonNode llamada : toolCalls) {
                    String tool = llamada.path("function").path("name").asText("");
                    String id = llamada.path("id").asText("");
                    JsonNode argumentos = leerArgumentos(llamada);

                    if (esEscritura(tool)) {
                        // El texto pegado por el administrador viaja como
                        // respaldo: si el modelo dejó textoDropi vacío (o lo
                        // copió a medias), manda el original, no su transcripción.
                        Preparacion preparacion = prepararAccion(tool, argumentos, texto);

                        // No se pudo preparar (producto inexistente o ambiguo):
                        // el motivo vuelve al modelo para que pida más detalle
                        // en lugar de ejecutar sobre el producto equivocado.
                        if (preparacion.error() != null) {
                            mensajes.add(Map.of("role", "tool", "tool_call_id", id,
                                    "content", preparacion.error()));
                            continue;
                        }

                        AccionPendiente accion = preparacion.accion();
                        String aviso = contenido == null || contenido.isBlank()
                                ? "Voy a " + accion.descripcion() + " ¿Lo apruebas?"
                                : contenido.trim() + "\n\nAcción propuesta: " + accion.descripcion();

                        return new Respuesta(aviso, true, accion);
                    }

                    String salida = ejecutarLectura(tool, argumentos);
                    mensajes.add(Map.of("role", "tool", "tool_call_id", id,
                            "content", salida == null ? "{}" : salida));
                }
            }

            return Respuesta.texto("No logré cerrar la respuesta. ¿Puedes reformular la petición?");
        } catch (Exception e) {
            log.warn("Asistente admin: fallo al llamar a DeepSeek: {}", e.getMessage());
            return Respuesta.texto("No pude contactar con el asistente. Revisa la conexión e inténtalo de nuevo.");
        }
    }

    // ============================================================
    // Ejecución (solo cuando el administrador aprueba)
    // ============================================================

    /**
     * Aplica una acción aprobada. Vuelve a validar TODO: nunca se confía en
     * los argumentos que llegan del navegador.
     */
    @Transactional
    public Resultado ejecutar(AccionPendiente accion) {
        if (accion == null || accion.tool() == null || accion.tool().isBlank()) {
            return new Resultado(false, "No hay ninguna acción pendiente que ejecutar.");
        }

        JsonNode argumentos = objectMapper.valueToTree(
                accion.argumentos() == null ? Map.of() : accion.argumentos());

        try {
            String salida = ejecutarEscritura(accion.tool(), argumentos);
            JsonNode resultado = objectMapper.readTree(salida);

            if (resultado.has("error")) {
                return new Resultado(false, "No se pudo ejecutar: " + resultado.path("error").asText());
            }
            return new Resultado(true, resultado.path("mensaje").asText("Listo, cambio aplicado."));
        } catch (Exception e) {
            log.warn("Asistente admin: no se pudo ejecutar {}: {}", accion.tool(), e.getMessage());
            return new Resultado(false, "No se pudo ejecutar la acción: " + e.getMessage());
        }
    }

    // ============================================================
    // Herramientas de LECTURA (se ejecutan sin confirmación)
    // ============================================================

    @Transactional(readOnly = true)
    public String buscarProductosPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return json(Map.of("error", "Indica un nombre para buscar."));
        }

        List<Producto> encontrados = productoRepository.findByNombreContainingIgnoreCase(nombre.trim());
        if (encontrados.isEmpty()) {
            return json(Map.of("resultados", List.of(),
                    "mensaje", "Ningún producto coincide con «" + nombre.trim() + "»."));
        }

        List<Producto> limitados = encontrados.stream().limit(MAX_PRODUCTOS_BUSQUEDA).toList();
        List<Long> ids = limitados.stream().map(Producto::getId).toList();

        // Una sola consulta para todas las variantes: sin N+1.
        Map<Long, List<VarianteProducto>> porProducto = new LinkedHashMap<>();
        for (VarianteProducto variante : varianteProductoRepository.findByProductoIdIn(ids)) {
            if (variante != null && variante.getProducto() != null) {
                porProducto.computeIfAbsent(variante.getProducto().getId(), k -> new ArrayList<>()).add(variante);
            }
        }

        List<Map<String, Object>> resultados = new ArrayList<>();
        for (Producto producto : limitados) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", producto.getId());
            item.put("nombre", producto.getNombre());
            item.put("sku", producto.getSku());
            item.put("precio", producto.getPrecio());
            item.put("categoria", producto.getCategoria());

            List<Map<String, Object>> variantes = new ArrayList<>();
            for (VarianteProducto variante : porProducto.getOrDefault(producto.getId(), List.of())) {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("sku", variante.getSku());
                v.put("color", variante.getColor());
                v.put("talla", variante.getTalla());
                v.put("precio", variante.getPrecio());
                v.put("stock", variante.getStock());
                v.put("activo", variante.getActivo());
                variantes.add(v);
            }
            item.put("variantes", variantes);
            resultados.add(item);
        }

        return json(Map.of("resultados", resultados, "total", encontrados.size()));
    }

    // ============================================================
    // Herramientas de ESCRITURA (requieren confirmación)
    // ============================================================

    @Transactional
    public String actualizarStockVariante(String sku, Integer nuevoStock) {
        if (sku == null || sku.isBlank()) {
            return json(Map.of("error", "Falta el SKU de la variante."));
        }
        if (nuevoStock == null || nuevoStock < 0) {
            return json(Map.of("error", "El stock no puede ser negativo ni estar vacío."));
        }
        if (nuevoStock > STOCK_MAXIMO) {
            return json(Map.of("error", "El stock no puede superar " + STOCK_MAXIMO + " unidades."));
        }

        VarianteProducto variante = varianteProductoRepository.findBySku(sku.trim()).orElse(null);
        if (variante == null) {
            return json(Map.of("error", "No existe ninguna variante con el SKU «" + sku.trim() + "»."));
        }

        variante.setStock(nuevoStock);
        varianteProductoRepository.save(variante);

        return json(Map.of("ok", true,
                "mensaje", "El stock de " + variante.getSku() + " quedó en " + nuevoStock + " unidades."));
    }

    @Transactional
    public String actualizarPrecioVariante(String sku, Double nuevoPrecio) {
        if (sku == null || sku.isBlank()) {
            return json(Map.of("error", "Falta el SKU de la variante."));
        }
        if (nuevoPrecio == null || !Double.isFinite(nuevoPrecio) || nuevoPrecio <= 0) {
            return json(Map.of("error", "El precio debe ser un número mayor que 0."));
        }
        if (nuevoPrecio > PRECIO_MAXIMO) {
            return json(Map.of("error", "Ese precio es demasiado alto para ser real."));
        }

        VarianteProducto variante = varianteProductoRepository.findBySku(sku.trim()).orElse(null);
        if (variante == null) {
            return json(Map.of("error", "No existe ninguna variante con el SKU «" + sku.trim() + "»."));
        }

        variante.setPrecio(nuevoPrecio);
        varianteProductoRepository.save(variante);

        return json(Map.of("ok", true,
                "mensaje", "El precio de " + variante.getSku() + " quedó en $" + pesos(nuevoPrecio) + "."));
    }

    @Transactional
    public String actualizarDescripcionProducto(Long productoId, String nuevaDescripcion) {
        if (productoId == null) {
            return json(Map.of("error", "Falta el id del producto."));
        }
        if (nuevaDescripcion == null || nuevaDescripcion.isBlank()) {
            return json(Map.of("error", "La descripción no puede estar vacía."));
        }

        String texto = nuevaDescripcion.trim();
        if (texto.length() > MAX_DESCRIPCION) {
            return json(Map.of("error", "La descripción supera los " + MAX_DESCRIPCION + " caracteres."));
        }

        Producto producto = productoRepository.findById(productoId).orElse(null);
        if (producto == null) {
            return json(Map.of("error", "No existe ningún producto con id " + productoId + "."));
        }

        producto.setDescripcion(texto);
        productoRepository.save(producto);

        return json(Map.of("ok", true,
                "mensaje", "Actualicé la descripción de «" + producto.getNombre() + "»."));
    }

    /**
     * Actualiza el stock de TODAS las variantes de un producto.
     *
     * <p>Es la única herramienta que toca varias filas a la vez, así que el
     * producto se resuelve <strong>antes</strong> de pedir la aprobación (en
     * {@link #prepararAccion}) y el id resuelto viaja en la acción aprobada.
     * Al ejecutar se usa ese id, no el nombre: así el cambio no puede caer en
     * otro producto aunque el nombre se repita o el catálogo cambie entre la
     * propuesta y la aprobación.</p>
     *
     * @param productoId     id ya resuelto en la propuesta (preferente)
     * @param nombreProducto nombre aprobado, para verificar que id y nombre cuadran
     * @param nuevoStock     unidades que tendrá cada variante
     */
    @Transactional
    public String actualizarStockDeTodoElProducto(Long productoId, String nombreProducto, Integer nuevoStock) {
        if (nuevoStock == null || nuevoStock < 0) {
            return json(Map.of("error", "El stock debe ser un entero mayor o igual a 0."));
        }
        if (nuevoStock > STOCK_MAXIMO) {
            return json(Map.of("error", "El stock no puede superar " + STOCK_MAXIMO + " unidades."));
        }

        Producto producto;

        if (productoId != null) {
            producto = productoRepository.findById(productoId).orElse(null);
            if (producto == null) {
                return json(Map.of("error", "El producto con id " + productoId + " ya no existe."));
            }
            // Defensa: el navegador podría reenviar otro id; el id y el nombre
            // aprobados tienen que seguir siendo el mismo producto.
            if (nombreProducto != null && !nombreProducto.isBlank()
                    && !seParecen(producto.getNombre(), nombreProducto)) {
                return json(Map.of("error", "El producto indicado no coincide con el nombre que se aprobó."));
            }
        } else {
            List<Producto> encontrados = resolverPorNombre(nombreProducto);
            if (encontrados.isEmpty()) {
                return json(Map.of("error", "No existe ningún producto que coincida con «"
                        + (nombreProducto == null ? "" : nombreProducto) + "»."));
            }
            if (encontrados.size() > 1) {
                return json(Map.of("error", "Hay " + encontrados.size()
                        + " productos que coinciden con ese nombre: indica el nombre completo."));
            }
            producto = encontrados.get(0);
        }

        List<VarianteProducto> variantes = varianteProductoRepository.findByProductoId(producto.getId());
        if (variantes.isEmpty()) {
            return json(Map.of("error", "El producto «" + producto.getNombre()
                    + "» no tiene variantes que actualizar."));
        }

        for (VarianteProducto variante : variantes) {
            variante.setStock(nuevoStock);
        }
        varianteProductoRepository.saveAll(variantes);

        return json(Map.of("ok", true,
                "mensaje", "El stock de las " + variantes.size() + " variante(s) de «"
                        + producto.getNombre() + "» quedó en " + nuevoStock + " unidades."));
    }

    // ============================================================
    // IMPORTACIÓN MASIVA DESDE DROPI
    // ============================================================

    /**
     * Aplica un inventario de Dropi sobre un producto (upsert por Color+Talla).
     *
     * @param productoId  producto destino
     * @param textoDropi  texto crudo pegado por el administrador
     */
    @Transactional
    public String importarVariantesDropi(Long productoId, String textoDropi) {
        return importarVariantesDropi(productoId, null, textoDropi);
    }

    /**
     * Aplica un inventario de Dropi sobre un producto (upsert por Color+Talla).
     *
     * <p>Todo ocurre dentro de <strong>una sola transacción</strong>: se lee la
     * lista actual de variantes con una consulta, se decide en memoria qué se
     * actualiza y qué se crea, se reservan los SKU con una segunda consulta y
     * se guarda de una vez. Nada de preguntar por cada variante: cada viaje de
     * ida y vuelta a Neon Free Tier cuenta.</p>
     *
     * <p>Lógica de escritura:</p>
     * <ul>
     *   <li>Existe Color+Talla → se actualizan stock, costo y precio. Un dato
     *       que el texto no trajo ({@code null}) <strong>no borra</strong> el
     *       valor actual: la ausencia de dato no es un dato.</li>
     *   <li>No existe → se crea la variante, activa y con SKU único.</li>
     *   <li>Si vuelve stock, una variante desactivada se reactiva.</li>
     * </ul>
     *
     * @param productoId     id ya resuelto en la propuesta
     * @param nombreProducto nombre aprobado, para verificar que id y nombre
     *                       cuadran (defensa ante un reenvío manipulado)
     * @param textoDropi     texto del inventario; si llega vacío se usa el
     *                       último que el parser aceptó
     */
    @Transactional
    public String importarVariantesDropi(Long productoId, String nombreProducto, String textoDropi) {
        if (productoId == null) {
            return json(Map.of("error", "Falta el id del producto donde importar."));
        }

        String texto = (textoDropi == null || textoDropi.isBlank()) ? textoDropiReciente() : textoDropi;
        if (texto == null || texto.isBlank()) {
            return json(Map.of("error", "No recibí el texto de Dropi. Pega el inventario en el chat."));
        }

        List<VarianteImportada> detectadas = DropiParserUtil.parsear(texto);
        if (detectadas.isEmpty()) {
            return json(Map.of(
                    "error", "No reconocí ninguna variante (Color + Talla) en ese texto.",
                    "sugerencia", "Debe incluir líneas como «Color: Negro», «Tallas: 38, 39» y «Stock: 10»."));
        }

        Producto producto = productoRepository.findById(productoId).orElse(null);
        if (producto == null) {
            return json(Map.of("error", "El producto con id " + productoId + " ya no existe."));
        }
        if (nombreProducto != null && !nombreProducto.isBlank()
                && !seParecen(producto.getNombre(), nombreProducto)) {
            return json(Map.of("error", "El producto indicado no coincide con el nombre que se aprobó."));
        }

        // Una consulta trae todas las variantes actuales del producto.
        List<VarianteProducto> actuales = varianteProductoRepository.findByProductoId(producto.getId());
        Map<String, VarianteProducto> porClave = new LinkedHashMap<>();
        Set<String> skusOcupados = new LinkedHashSet<>();
        for (VarianteProducto variante : actuales) {
            porClave.putIfAbsent(DropiParserUtil.clave(variante.getColor(), variante.getTalla()), variante);
            if (variante.getSku() != null && !variante.getSku().isBlank()) {
                skusOcupados.add(variante.getSku());
            }
        }

        int actualizadas = 0;
        int sinCambios = 0;
        List<String> avisos = new ArrayList<>();
        List<VarianteImportada> porCrear = new ArrayList<>();
        List<String> candidatosSku = new ArrayList<>();

        for (VarianteImportada detectada : detectadas) {
            VarianteProducto existente = porClave.get(detectada.clave());
            if (existente == null) {
                porCrear.add(detectada);
                candidatosSku.add(skuBase(producto, detectada));
                continue;
            }
            // Las variantes ya cargadas están gestionadas por la transacción:
            // el UPDATE sale solo al confirmar (dirty checking), sin save().
            if (aplicar(existente, detectada, avisos)) {
                actualizadas++;
            } else {
                sinCambios++;
            }
        }

        if (!candidatosSku.isEmpty()) {
            // Un solo viaje a Neon para saber qué SKU candidatos ya existen.
            skusOcupados.addAll(varianteProductoRepository.findSkusExistentes(candidatosSku));
        }

        List<VarianteProducto> nuevas = new ArrayList<>();
        for (int i = 0; i < porCrear.size(); i++) {
            VarianteImportada detectada = porCrear.get(i);
            VarianteProducto variante = new VarianteProducto();
            variante.setProducto(producto);
            variante.setSku(skuLibre(candidatosSku.get(i), skusOcupados));
            variante.setColor(detectada.color());
            variante.setTalla(detectada.talla());
            variante.setStock(detectada.stock() == null ? 0 : Math.min(detectada.stock(), STOCK_MAXIMO));
            variante.setPrecio(precioDeAlta(producto, detectada, avisos));
            if (detectada.costo() != null && detectada.costo() > 0 && detectada.costo() <= PRECIO_MAXIMO) {
                variante.setCosto(detectada.costo());
            }
            variante.setActivo(true);
            nuevas.add(variante);
        }

        if (!nuevas.isEmpty()) {
            varianteProductoRepository.saveAll(nuevas);
        }

        StringBuilder mensaje = new StringBuilder()
                .append("Importación aplicada en «").append(producto.getNombre()).append("»: ")
                .append(nuevas.size()).append(" variante(s) nueva(s) y ")
                .append(actualizadas).append(" actualizada(s)");
        if (sinCambios > 0) {
            mensaje.append(" (").append(sinCambios).append(" sin cambios)");
        }
        mensaje.append(". Las variantes detectadas: ").append(detectadas.size()).append('.');

        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("ok", true);
        salida.put("mensaje", mensaje.toString());
        salida.put("producto", producto.getNombre());
        salida.put("variantesDetectadas", detectadas.size());
        salida.put("creadas", nuevas.size());
        salida.put("actualizadas", actualizadas);
        salida.put("sinCambios", sinCambios);
        if (!avisos.isEmpty()) {
            salida.put("avisos", avisos.subList(0, Math.min(avisos.size(), 8)));
        }
        return json(salida);
    }

    /**
     * Vuelca una variante leída sobre la fila existente.
     *
     * @return {@code true} si cambió algo (hay UPDATE pendiente)
     */
    private boolean aplicar(VarianteProducto existente, VarianteImportada detectada, List<String> avisos) {
        boolean cambio = false;

        if (detectada.stock() != null) {
            int stock = Math.max(0, Math.min(detectada.stock(), STOCK_MAXIMO));
            if (!Integer.valueOf(stock).equals(existente.getStock())) {
                existente.setStock(stock);
                cambio = true;
            }
        }

        if (detectada.precioVenta() != null) {
            Double precio = detectada.precioVenta();
            if (precio > 0 && precio <= PRECIO_MAXIMO) {
                if (!precio.equals(existente.getPrecio())) {
                    existente.setPrecio(precio);
                    cambio = true;
                }
            } else {
                avisos.add("precio fuera de rango en " + detectada.etiqueta() + " ($" + pesos(precio) + ")");
            }
        }

        if (detectada.costo() != null && detectada.costo() > 0 && detectada.costo() <= PRECIO_MAXIMO) {
            if (!detectada.costo().equals(existente.getCosto())) {
                existente.setCosto(detectada.costo());
                cambio = true;
            }
        }

        // Una variante que vuelve a tener stock vuelve a la tienda.
        if (Boolean.FALSE.equals(existente.getActivo())
                && existente.getStock() != null && existente.getStock() > 0) {
            existente.setActivo(true);
            cambio = true;
        }

        return cambio;
    }

    /** Precio de una variante nueva: el del texto o, si no vino, el del producto. */
    private static Double precioDeAlta(Producto producto, VarianteImportada detectada, List<String> avisos) {
        Double precio = detectada.precioVenta();
        if (precio != null && precio > 0 && precio <= PRECIO_MAXIMO) {
            return precio;
        }
        if (precio != null) {
            avisos.add("precio fuera de rango en " + detectada.etiqueta()
                    + "; se usó el precio base del producto");
        }
        return producto.getPrecio() == null ? 0d : producto.getPrecio();
    }

    /**
     * SKU propuesto para una variante nueva: {@code SKU_PRODUCTO-COLOR-TALLA}.
     *
     * <p>Incluye el SKU del producto (o su id) para que dos productos distintos
     * no puedan generar el mismo candidato.</p>
     */
    private static String skuBase(Producto producto, VarianteImportada variante) {
        String raiz = (producto.getSku() != null && !producto.getSku().isBlank())
                ? producto.getSku()
                : "P" + producto.getId();

        StringBuilder sb = new StringBuilder(limpiarSku(raiz));
        String color = limpiarSku(variante.color());
        String talla = limpiarSku(variante.talla());
        if (!color.isEmpty()) {
            sb.append('-').append(color);
        }
        if (!talla.isEmpty()) {
            sb.append('-').append(talla);
        }

        String sku = sb.toString().replaceAll("^-+", "");
        if (sku.isEmpty()) {
            sku = "P" + producto.getId() + "-VARIANTE";
        }
        return sku.length() > MAX_LARGO_SKU ? sku.substring(0, MAX_LARGO_SKU) : sku;
    }

    /** Deja un texto en mayúsculas alfanuméricas separadas por guiones. */
    private static String limpiarSku(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }

    /** Primer SKU libre de la forma {@code base}, {@code base-2}, {@code base-3}… */
    private static String skuLibre(String base, Set<String> ocupados) {
        String candidato = base;
        int intento = 1;
        while (ocupados.contains(candidato)) {
            intento++;
            if (intento > 200) {
                candidato = base + "-" + Math.abs(System.nanoTime() % 100_000L);
                break;
            }
            candidato = base + "-" + intento;
        }
        ocupados.add(candidato);
        return candidato;
    }

    /**
     * Prepara la importación: lee el texto, resuelve el producto y describe el
     * cambio para la tarjeta de confirmación.
     *
     * <p><strong>De dónde sale el texto</strong> (en este orden):</p>
     * <ol>
     *   <li>lo que el administrador acaba de pegar en este mensaje — la fuente
     *       fiable, ya que el modelo no lo transcribe;</li>
     *   <li>lo que el modelo envió en {@code textoDropi};</li>
     *   <li>el último inventario aceptado, por si el texto se pegó en un
     *       mensaje anterior y ahora solo se responde con el nombre.</li>
     * </ol>
     */
    private Preparacion prepararImportacionDropi(JsonNode argumentos, String textoMensaje) {
        Long productoId = argumentos.hasNonNull("productoId") ? argumentos.path("productoId").asLong() : null;
        if (productoId == null) {
            return new Preparacion(null, json(Map.of(
                    "error", "Falta productoId.",
                    "sugerencia", "Usa " + TOOL_BUSCAR + " para encontrar el producto y luego vuelve a llamar a "
                            + TOOL_DROPI + " con su id.")));
        }

        String texto = null;
        List<VarianteImportada> detectadas = List.of();

        if (DropiParserUtil.pareceInventario(textoMensaje)) {
            texto = textoMensaje;
            detectadas = DropiParserUtil.parsear(texto);
        } else {
            String delModelo = argumentos.path("textoDropi").asText("");
            if (DropiParserUtil.pareceInventario(delModelo)) {
                texto = delModelo;
                detectadas = DropiParserUtil.parsear(texto);
            } else {
                String reciente = textoDropiReciente();
                if (reciente != null && DropiParserUtil.pareceInventario(reciente)) {
                    texto = reciente;
                    detectadas = DropiParserUtil.parsear(texto);
                }
            }
        }

        if (detectadas.isEmpty()) {
            return new Preparacion(null, json(Map.of(
                    "error", "No tengo ningún inventario de Dropi que importar.",
                    "sugerencia", "Pide al administrador que pegue el texto (Color, Tallas, Stock, Costo y Precio).")));
        }

        Producto producto = productoRepository.findById(productoId).orElse(null);
        if (producto == null) {
            return new Preparacion(null, json(Map.of(
                    "error", "No existe ningún producto con id " + productoId + ".",
                    "sugerencia", "Busca el producto con " + TOOL_BUSCAR + ".")));
        }

        // Comparación para la vista previa: cuántas se crean y cuántas se tocan.
        Set<String> yaExisten = new HashSet<>();
        for (VarianteProducto variante : varianteProductoRepository.findByProductoId(producto.getId())) {
            yaExisten.add(DropiParserUtil.clave(variante.getColor(), variante.getTalla()));
        }

        int nuevas = 0;
        int cambios = 0;
        int mostradas = 0;
        StringBuilder vista = new StringBuilder();

        for (VarianteImportada detectada : detectadas) {
            boolean existe = yaExisten.contains(detectada.clave());
            if (existe) {
                cambios++;
            } else {
                nuevas++;
            }
            if (mostradas < MAX_VARIANTES_PREVIEW) {
                mostradas++;
                vista.append("\n• ").append(detectada.etiqueta());
                if (detectada.stock() != null) {
                    vista.append(" · stock ").append(detectada.stock());
                }
                if (detectada.precioVenta() != null) {
                    vista.append(" · $").append(pesos(detectada.precioVenta()));
                }
                vista.append(existe ? "  (actualizar)" : "  (nueva)");
            }
        }
        if (detectadas.size() > mostradas) {
            vista.append("\n… y ").append(detectadas.size() - mostradas).append(" más");
        }

        Map<String, Object> aprobados = new LinkedHashMap<>();
        aprobados.put("productoId", producto.getId());
        aprobados.put("nombreProducto", producto.getNombre());
        aprobados.put("textoDropi", texto == null ? "" : texto);
        aprobados.put("variantesDetectadas", detectadas.size());

        String descripcion = "importar " + detectadas.size() + " variante(s) de Dropi en «"
                + producto.getNombre() + "» (" + nuevas + " nueva(s), " + cambios + " a actualizar)" + vista;

        // masiva = true → el chat pinta la tarjeta en ámbar y exige un clic.
        return new Preparacion(new AccionPendiente(TOOL_DROPI, descripcion, aprobados, true), null);
    }

    /**
     * Versión de un mensaje del historial que se envía al modelo.
     *
     * <p>Si es un inventario de Dropi pegado (cientos o miles de caracteres) se
     * sustituye por una marca: el texto ya está leído por Java y al modelo no
     * le aporta nada, así que no tiene sentido pagarlo en tokens en cada
     * turno.</p>
     */
    private static String historialParaElModelo(String contenido) {
        String limpio = contenido == null ? "" : contenido.trim();
        if (limpio.length() > 200 && DropiParserUtil.pareceInventario(limpio)) {
            return "[inventario de Dropi pegado en un mensaje anterior: ya está cargado en el servidor]";
        }
        return recortar(limpio, 500);
    }

    /**
     * Resumen del inventario pegado que se envía al modelo en lugar del texto.
     *
     * <p>Mantiene al asistente informado (qué variantes se detectaron) sin
     * pagar el texto completo en tokens y sin darle nada que pueda transcribir
     * mal: los números que aparecen aquí son los ya leídos por Java.</p>
     */
    private static String resumenInventarioPegado(List<VarianteImportada> variantes) {
        StringBuilder sb = new StringBuilder();
        sb.append("[El administrador pegó un inventario de Dropi. El texto crudo ya está cargado en el servidor: "
                + "no lo repitas, no lo transcribas y no calcules nada. Variantes detectadas: ")
                .append(variantes.size()).append('.');

        int mostradas = 0;
        for (VarianteImportada variante : variantes) {
            if (mostradas >= 12) {
                sb.append(" …");
                break;
            }
            mostradas++;
            sb.append(' ').append(variante.etiqueta());
            if (variante.stock() != null) {
                sb.append(" stock ").append(variante.stock());
            }
            if (variante.precioVenta() != null) {
                sb.append(" precio $").append(pesos(variante.precioVenta()));
            }
            sb.append(';');
        }

        sb.append(" Identifica el producto con ").append(TOOL_BUSCAR)
                .append(" y llama a ").append(TOOL_DROPI).append(" con su id y textoDropi vacío.]");
        return sb.toString();
    }

    /** Guarda el inventario recién aceptado (para el flujo en dos turnos). */
    private void recordarTextoDropi(String texto) {
        if (texto == null || texto.isBlank()) {
            return;
        }
        this.ultimoTextoDropi = texto;
        this.ultimoTextoDropiEn = Instant.now();
    }

    /** @return el último inventario aceptado, o {@code null} si ya caducó */
    private String textoDropiReciente() {
        String texto = ultimoTextoDropi;
        Instant cuando = ultimoTextoDropiEn;
        if (texto == null || cuando == null) {
            return null;
        }
        if (cuando.plus(VIGENCIA_TEXTO_DROPI).isBefore(Instant.now())) {
            return null;
        }
        return texto;
    }

    /**
     * Convierte una llamada de escritura en una acción propuesta.
     *
     * <p>Para las acciones masivas el producto se resuelve aquí: si no existe o
     * hay varios candidatos, se devuelve un error que vuelve al modelo para que
     * pida más detalle, y <strong>no se propone ninguna acción</strong>.</p>
     *
     * @param textoMensaje lo que escribió el administrador en este turno (ya
     *                     recortado); es la fuente fiable del inventario pegado
     */
    private Preparacion prepararAccion(String tool, JsonNode argumentos, String textoMensaje) {
        if (TOOL_DROPI.equals(tool)) {
            return prepararImportacionDropi(argumentos, textoMensaje);
        }

        if (!TOOL_STOCK_PRODUCTO.equals(tool)) {
            return new Preparacion(
                    new AccionPendiente(tool, describirAccion(tool, argumentos), aMapa(argumentos), false),
                    null);
        }

        String nombre = argumentos.path("nombreProducto").asText("").trim();
        Integer stock = argumentos.hasNonNull("nuevoStock") ? argumentos.path("nuevoStock").asInt() : null;

        if (nombre.isBlank()) {
            return new Preparacion(null, json(Map.of("error", "Falta el nombre del producto.")));
        }
        if (stock == null || stock < 0) {
            return new Preparacion(null, json(Map.of("error", "El stock debe ser un entero mayor o igual a 0.")));
        }
        if (stock > STOCK_MAXIMO) {
            return new Preparacion(null, json(Map.of("error", "El stock no puede superar " + STOCK_MAXIMO + ".")));
        }

        List<Producto> encontrados = resolverPorNombre(nombre);

        if (encontrados.isEmpty()) {
            return new Preparacion(null, json(Map.of(
                    "error", "No existe ningún producto que coincida con «" + nombre + "».",
                    "sugerencia", "Usa " + TOOL_BUSCAR + " para encontrar el nombre exacto.")));
        }

        if (encontrados.size() > 1) {
            List<String> candidatos = new ArrayList<>();
            for (Producto p : encontrados) {
                if (candidatos.size() >= 5) {
                    break;
                }
                candidatos.add(p.getNombre());
            }
            return new Preparacion(null, json(Map.of(
                    "error", "Hay " + encontrados.size() + " productos que coinciden con «" + nombre + "».",
                    "candidatos", candidatos,
                    "sugerencia", "Pide al administrador que sea más específico (nombre completo) "
                            + "en lugar de elegir uno tú.")));
        }

        Producto producto = encontrados.get(0);
        int numeroVariantes = varianteProductoRepository.findByProductoId(producto.getId()).size();

        // El id resuelto queda clavado en la acción aprobada.
        Map<String, Object> aprobados = new LinkedHashMap<>();
        aprobados.put("productoId", producto.getId());
        aprobados.put("nombreProducto", producto.getNombre());
        aprobados.put("nuevoStock", stock);

        String descripcion = "actualizar el stock de las " + numeroVariantes + " variante(s) de «"
                + producto.getNombre() + "» a " + stock + " unidades cada una";

        return new Preparacion(new AccionPendiente(tool, descripcion, aprobados, true), null);
    }

    /**
     * Busca productos por nombre y, si no hay coincidencias, reintenta sin la
     * 's' final ("mochilas" → "mochila"). No adivina: sin coincidencias
     * devuelve la lista vacía.
     */
    private List<Producto> resolverPorNombre(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isBlank()) {
            return List.of();
        }

        List<Producto> encontrados = productoRepository.findByNombreContainingIgnoreCase(limpio);
        if (!encontrados.isEmpty()) {
            return encontrados;
        }

        if (limpio.length() > 3 && limpio.toLowerCase(Locale.ROOT).endsWith("s")) {
            encontrados = productoRepository.findByNombreContainingIgnoreCase(
                    limpio.substring(0, limpio.length() - 1));
        }
        return encontrados;
    }

    /** ¿El nombre aprobado y el del producto hablan del mismo artículo? */
    private static boolean seParecen(String nombreProducto, String nombreAprobado) {
        String a = normalizar(nombreProducto);
        String b = normalizar(nombreAprobado);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.contains(b) || b.contains(a);
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

    /** Resultado de preparar una acción: o la acción, o el motivo del fallo. */
    private record Preparacion(AccionPendiente accion, String error) {
    }

    // ============================================================
    // Despacho de herramientas
    // ============================================================

    private static boolean esEscritura(String tool) {
        return TOOL_STOCK.equals(tool)
                || TOOL_STOCK_PRODUCTO.equals(tool)
                || TOOL_PRECIO.equals(tool)
                || TOOL_DESCRIPCION.equals(tool)
                || TOOL_DROPI.equals(tool);
    }

    private String ejecutarLectura(String tool, JsonNode argumentos) {
        if (TOOL_BUSCAR.equals(tool)) {
            return buscarProductosPorNombre(argumentos.path("nombre").asText(""));
        }
        return json(Map.of("error", "Herramienta de lectura desconocida: " + tool));
    }

    private String ejecutarEscritura(String tool, JsonNode argumentos) {
        return switch (tool) {
            case TOOL_STOCK -> actualizarStockVariante(
                    argumentos.path("sku").asText(""),
                    argumentos.hasNonNull("nuevoStock") ? argumentos.path("nuevoStock").asInt() : null);
            case TOOL_STOCK_PRODUCTO -> actualizarStockDeTodoElProducto(
                    argumentos.hasNonNull("productoId") ? argumentos.path("productoId").asLong() : null,
                    argumentos.path("nombreProducto").asText(""),
                    argumentos.hasNonNull("nuevoStock") ? argumentos.path("nuevoStock").asInt() : null);
            case TOOL_PRECIO -> actualizarPrecioVariante(
                    argumentos.path("sku").asText(""),
                    argumentos.hasNonNull("nuevoPrecio") ? argumentos.path("nuevoPrecio").asDouble() : null);
            case TOOL_DESCRIPCION -> actualizarDescripcionProducto(
                    argumentos.hasNonNull("productoId") ? argumentos.path("productoId").asLong() : null,
                    argumentos.path("nuevaDescripcion").asText(""));
            case TOOL_DROPI -> importarVariantesDropi(
                    argumentos.hasNonNull("productoId") ? argumentos.path("productoId").asLong() : null,
                    argumentos.path("nombreProducto").asText(""),
                    argumentos.path("textoDropi").asText(""));
            default -> json(Map.of("error", "Solo se pueden ejecutar acciones de escritura conocidas."));
        };
    }

    // ============================================================
    // DeepSeek (formato OpenAI: tools + tool_calls)
    // ============================================================

    private JsonNode llamarDeepSeek(List<Map<String, Object>> mensajes) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", model);
            payload.put("temperature", 0.2);
            payload.put("max_tokens", 600);
            payload.put("stream", false);
            payload.set("messages", objectMapper.valueToTree(mensajes));
            payload.set("tools", objectMapper.readTree(TOOLS_JSON));
            payload.put("tool_choice", "auto");

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
                log.warn("Asistente admin: DeepSeek respondió {}: {}",
                        response.statusCode(), recortar(response.body(), 300));
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode mensaje = root.path("choices").path(0).path("message");
            return mensaje.isMissingNode() || mensaje.isNull() ? null : mensaje;
        } catch (Exception e) {
            log.warn("Asistente admin: error de red con DeepSeek: {}", e.getMessage());
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

    // ============================================================
    // Utilidades
    // ============================================================

    private JsonNode leerArgumentos(JsonNode llamada) {
        String crudo = llamada.path("function").path("arguments").asText("");
        if (crudo.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(crudo);
        } catch (Exception e) {
            log.warn("Asistente admin: argumentos no válidos en la llamada: {}", recortar(crudo, 200));
            return objectMapper.createObjectNode();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> aMapa(JsonNode nodo) {
        if (nodo == null || !nodo.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(nodo, Map.class);
    }

    /** Frase legible de la acción, para que el admin sepa qué aprueba. */
    private static String describirAccion(String tool, JsonNode argumentos) {
        return switch (tool) {
            case TOOL_STOCK -> "cambiar el stock de la variante " + argumentos.path("sku").asText("(sin SKU)")
                    + " a " + argumentos.path("nuevoStock").asInt(0) + " unidades";
            case TOOL_STOCK_PRODUCTO -> "actualizar el stock de todas las variantes de «"
                    + argumentos.path("nombreProducto").asText("(sin nombre)")
                    + "» a " + argumentos.path("nuevoStock").asInt(0) + " unidades";
            case TOOL_PRECIO -> "cambiar el precio de la variante " + argumentos.path("sku").asText("(sin SKU)")
                    + " a $" + pesos(argumentos.path("nuevoPrecio").asDouble(0));
            case TOOL_DESCRIPCION -> "cambiar la descripción del producto con id "
                    + argumentos.path("productoId").asText("(sin id)");
            case TOOL_DROPI -> "importar un inventario de Dropi en el producto con id "
                    + argumentos.path("productoId").asText("(sin id)");
            default -> tool;
        };
    }

    private String json(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (Exception e) {
            return "{\"error\":\"No se pudo serializar la respuesta\"}";
        }
    }

    private static String pesos(double valor) {
        return String.format(Locale.forLanguageTag("es-CO"), "%,.0f", valor);
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
