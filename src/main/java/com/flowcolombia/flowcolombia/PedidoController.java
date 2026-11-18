package com.flowcolombia.flowcolombia;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.flowcolombia.flowcolombia.service.MetaConversionsService;
import com.flowcolombia.flowcolombia.service.NotificacionService;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;

@RestController
public class PedidoController {

    /**
     * Alfabeto del código de pedido: sin 0/O/1/I para que nadie se confunda
     * al dictarlo por teléfono. 32 símbolos elevados a 10 posiciones dan más
     * de un billón de combinaciones.
     */
    private static final char[] ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static final SecureRandom AZAR = new SecureRandom();

    private final PedidoRepository pedidoRepository;
    private final MensajeRepository mensajeRepository;
    private final EmailService emailService;
    private final MetaConversionsService metaConversionsService;
    private final ProductoRepository productoRepository;
    private final NotificacionService notificacionService;

    public PedidoController(PedidoRepository pedidoRepository,
                            MensajeRepository mensajeRepository,
                            EmailService emailService,
                            MetaConversionsService metaConversionsService,
                            ProductoRepository productoRepository,
                            NotificacionService notificacionService) {
        this.pedidoRepository = pedidoRepository;
        this.mensajeRepository = mensajeRepository;
        this.emailService = emailService;
        this.metaConversionsService = metaConversionsService;
        this.productoRepository = productoRepository;
        this.notificacionService = notificacionService;
    }

    /**
     * Código de seguimiento impredecible.
     *
     * <p>Antes era {@code "FLOW-" + System.currentTimeMillis()}: cualquiera
     * podía restar milisegundos y consultar pedidos ajenos (nombre, dirección
     * y teléfono) desde {@code /api/pedido/{codigo}}. Los pedidos antiguos
     * siguen funcionando porque la búsqueda sigue siendo por código exacto.</p>
     */
    private static String generarCodigo() {
        StringBuilder codigo = new StringBuilder("FLOW-");
        for (int i = 0; i < 10; i++) {
            codigo.append(ALFABETO_CODIGO[AZAR.nextInt(ALFABETO_CODIGO.length)]);
        }
        return codigo.toString();
    }

    /**
     * Datos públicos de seguimiento. Es a propósito un DTO mínimo:
     * <strong>nunca</strong> sale de aquí nombre, dirección, correo, notas ni
     * importes. Lo que no está en este record no se puede filtrar.
     */
    public record PedidoTracking(String codigo,
                                 String estado,
                                 LocalDateTime fechaActualizacion,
                                 String transportadora,
                                 String numeroGuia) {
    }

    /**
     * Seguimiento público con doble candado: código <em>y</em> teléfono.
     *
     * <p>Si la pareja no coincide en el mismo pedido se responde un 404
     * genérico: nunca se confirma si el código existe, para que no se pueda
     * usar como oráculo de enumeración.</p>
     *
     * <p>La ruta literal tiene prioridad sobre {@code /api/pedido/{codigo}},
     * así que no colisiona con los endpoints anteriores. Hereda además el
     * límite de 30 peticiones por minuto de {@code RateLimitFilter}.</p>
     */
    @GetMapping("/api/pedido/track")
    public ResponseEntity<Object> rastrearPedido(@RequestParam(required = false) String codigo,
                                                 @RequestParam(required = false) String telefono) {

        String codigoLimpio = codigo == null ? "" : codigo.trim();
        String telefonoLimpio = soloDigitos(telefono);

        // Mínimo de datos: corta los barridos con valores vacíos o basura.
        if (codigoLimpio.length() < 4 || telefonoLimpio.length() < 7) {
            return pedidoNoEncontrado();
        }

        Pedido pedido = pedidoRepository
                .findFirstByCodigoIgnoreCaseAndTelefono(codigoLimpio, telefonoLimpio)
                .orElse(null);

        if (pedido == null) {
            return pedidoNoEncontrado();
        }

        // No existe columna "actualizadoEn" (y no se toca el esquema): la
        // fecha registrada del pedido es el último movimiento conocido.
        return ResponseEntity.ok(new PedidoTracking(
                pedido.getCodigo(),
                pedido.getEstado(),
                pedido.getFecha(),
                pedido.getTransportadora(),
                pedido.getNumeroGuia()));
    }

    /** Respuesta única e idéntica para cualquier fallo de coincidencia. */
    private ResponseEntity<Object> pedidoNoEncontrado() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "No encontramos un pedido con esos datos."));
    }

    /** El teléfono se guarda sin espacios ni símbolos; se compara igual. */
    private static String soloDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    @PostMapping("/api/pedido")
    @ResponseBody
    public Map<String, String> guardarPedido(@RequestBody Pedido pedido, HttpServletRequest request) {
        System.out.println("=========================================");
        System.out.println("📦 NUEVO PEDIDO RECIBIDO:");
        System.out.println("  Cliente: " + pedido.getNombreCliente());
        System.out.println("  Teléfono: " + pedido.getTelefono());
        System.out.println("  Total recibido: " + pedido.getTotal());
        System.out.println("  Productos: " + pedido.getProductos());
        System.out.println("=========================================");

        pedido.setEstado("Pendiente");
        pedido.setCodigo(generarCodigo());
        pedido.setFecha(LocalDateTime.now());

        if (pedido.getTotal() == null) {
            System.out.println("⚠️ Total es NULL, asignando 0.0");
            pedido.setTotal(0.0);
        }

        if (pedido.getProductos() == null || pedido.getProductos().isEmpty()) {
            System.out.println("⚠️ Productos está vacío");
            pedido.setProductos("Productos no especificados");
        }

        Pedido pedidoGuardado = pedidoRepository.save(pedido);
        System.out.println("✅ Pedido guardado con ID: " + pedidoGuardado.getId());
        System.out.println("   Total guardado: " + pedidoGuardado.getTotal());
        System.out.println("   Productos guardados: " + pedidoGuardado.getProductos());
        System.out.println("=========================================");

        // Aviso push al celular del dueño (ntfy.sh). Es @Async: no retrasa la
        // respuesta al cliente y, si ntfy no responde, la tienda no se entera.
        // Sin NTFY_TOPIC_URL configurada no se envía nada (robot dormido).
        notificacionService.notificarPedidoNuevo(pedidoGuardado);

        if (pedido.getEmail() != null && !pedido.getEmail().isEmpty()) {
            try {
                if (esPedidoCtb234(pedido)) {
                    emailService.enviarConfirmacionCtb234(pedido);
                } else {
                    emailService.enviarConfirmacion(
                            pedido.getEmail(),
                            pedido.getCodigo(),
                            pedido.getProductos(),
                            pedido.getTotal()
                    );
                }
            } catch (Exception e) {
                System.err.println("❌ Error al enviar correo: " + e.getMessage());
            }
        }

        Map<String, String> response = new HashMap<>();
        response.put("codigo", pedido.getCodigo());
        response.put("status", "OK");
        // ============================================================
        // META CAPI: evento Purchase desde el servidor.
        // El event_id es el código del pedido, el MISMO que enviará el
        // navegador desde la pantalla de confirmación: Meta los reconoce
        // como un único evento y no cuenta la venta dos veces.
        // El envío es asíncrono: no retrasa ni puede romper el checkout.
        //
        // REGLA DE SEGURIDAD: si el pedido incluye algún producto marcado
        // como «ocultar a Meta» (salud íntima / sensible), NO se envía
        // NADA. Es mejor perder una venta registrada en el píxel que
        // arriesgar la cuenta publicitaria por una política incumplida.
        // ============================================================
        if (contieneProductosOcultosAMeta(pedidoGuardado)) {
            System.out.println("🚫 Meta CAPI: pedido " + pedidoGuardado.getCodigo()
                    + " contiene productos excluidos de Meta. No se envía el Purchase.");
        } else if (contieneProductosConPurchaseBloqueado(pedidoGuardado)) {
            System.out.println("⏸️ Meta CAPI: pedido " + pedidoGuardado.getCodigo()
                    + " contiene productos con Purchase bloqueado. No se envía el Purchase.");
        } else {
            metaConversionsService.enviarPurchase(pedidoGuardado, contextoMeta(request));
        }

        return response;
    }

    /**
     * ¿El pedido lleva algún producto prohibido para Meta?
     *
     * <p>Meta no permite enviar datos de salud íntima ni de suplementos a su
     * píxel ni a la API de Conversiones, así que el {@code Purchase} se corta
     * <strong>entero</strong> en cuanto aparece un producto marcado. No se
     * manda una versión «parcial» del evento: para Meta el pedido y su valor
     * son indivisibles, y una venta sin registrar no cuesta la cuenta.</p>
     *
     * <p>Se comprueba en dos pasos, de más fiable a menos:</p>
     * <ol>
     *   <li><strong>Por ID</strong> ({@code pedido.productosIds}): lo envía el
     *       checkout y es la fuente autoritativa. Una sola consulta por
     *       pedido y sin depender del texto.</li>
     *   <li><strong>Por nombre</strong>: los carritos antiguos (y cualquier
     *       cliente que no mande los IDs) solo traen el detalle en texto, así
     *       que se busca el nombre de cada producto excluido dentro del
     *       pedido. Es la red de seguridad para líneas sin {@code productoId};
     *       cuando los IDs están, el paso 1 es el que decide.</li>
     * </ol>
     *
     * @param pedido pedido recién guardado
     * @return {@code true} si NO debe enviarse ningún evento a Meta
     */
    boolean contieneProductosOcultosAMeta(Pedido pedido) {
        if (pedido == null) {
            return false;
        }

        // 1. IDs enviados por el checkout (autoritativo).
        List<Long> ids = pedido.getProductosIds();
        if (ids != null && !ids.isEmpty()) {
            for (Producto producto : productoRepository.findAllById(ids)) {
                if (Boolean.TRUE.equals(producto.getExcluirMeta())) {
                    return true;
                }
            }
        }

        // 2. Respaldo por nombre, para pedidos sin IDs.
        List<String> nombresOcultos = productoRepository.findNombresExcluidosMeta();
        if (nombresOcultos.isEmpty()) {
            return false;
        }

        String detalle = normalizarTexto(pedido.getProductos());
        if (detalle.isEmpty()) {
            return false;
        }

        for (String nombre : nombresOcultos) {
            String buscado = normalizarTexto(nombre);
            // Nombres de una letra no pueden comprobarse sin falsos positivos.
            if (buscado.length() >= 3 && detalle.contains(buscado)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ¿El pedido lleva algún producto que bloquea <strong>solo</strong> el
     * {@code Purchase} de Meta?
     *
     * <p>Es la regla de los productos contraentrega (COD): el pedido se puede
     * medir en el embudo ({@code ViewContent}, {@code AddToCart},
     * {@code InitiateCheckout}) y Google Analytics 4 lo mide entero, pero el
     * {@code Purchase} no se envía a Meta al crear el pedido: para COD, un
     * formulario enviado todavía no es una venta, y medirlo como tal le enseña
     * al algoritmo a buscar «form-fillers» en vez de «pagadores».</p>
     *
     * <p>Se comprueba en dos pasos, de más fiable a menos, igual que
     * {@link #contieneProductosOcultosAMeta(Pedido)}:</p>
     * <ol>
     *   <li><strong>Por ID</strong> ({@code pedido.productosIds}): lo envía el
     *       checkout y es la fuente autoritativa. Una sola consulta por pedido
     *       y sin depender del texto.</li>
     *   <li><strong>Por nombre</strong> ({@code findNombresBloqueanPurchase}):
     *       respaldo para carritos antiguos o clientes que no mandan los
     *       IDs; busca el nombre de cada producto bloqueado dentro del texto
     *       del pedido.</li>
     * </ol>
     *
     * <p>La decisión de no enviar es binaria por pedido: para Meta el pedido y
     * su valor son indivisibles, así que si una sola línea bloquea el
     * {@code Purchase}, no se envía la versión «parcial». Esto no afecta a
     * {@code excluirMeta}, que se evalúa antes y corta todos los eventos.</p>
     *
     * @param pedido pedido recién guardado
     * @return {@code true} si el {@code Purchase} de este pedido no debe
     *         enviarse a Meta
     */
    boolean contieneProductosConPurchaseBloqueado(Pedido pedido) {
        if (pedido == null) {
            return false;
        }

        // 1. IDs enviados por el checkout (autoritativo).
        List<Long> ids = pedido.getProductosIds();
        if (ids != null && !ids.isEmpty()) {
            for (Producto producto : productoRepository.findAllById(ids)) {
                if (Boolean.TRUE.equals(producto.getBloquearPurchaseMeta())) {
                    return true;
                }
            }
        }

        // 2. Respaldo por nombre, para pedidos sin IDs.
        List<String> nombresBloqueados = productoRepository.findNombresBloqueanPurchase();
        if (nombresBloqueados.isEmpty()) {
            return false;
        }

        String detalle = normalizarTexto(pedido.getProductos());
        if (detalle.isEmpty()) {
            return false;
        }

        for (String nombre : nombresBloqueados) {
            String buscado = normalizarTexto(nombre);
            // Nombres de una letra no pueden comprobarse sin falsos positivos.
            if (buscado.length() >= 3 && detalle.contains(buscado)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Texto comparable: minúsculas, sin tildes y con los espacios colapsados.
     * Es el mismo criterio para el detalle del pedido y para el nombre del
     * catálogo, así «Crema Íntima» y «crema intima x1» se reconocen.
     */
    private static String normalizarTexto(String valor) {
        if (valor == null) {
            return "";
        }
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * ¿El pedido contiene el producto CTB-234?
     *
     * <p>Se detecta por el código en el texto del pedido
     * ({@code Pedido.productos}) <strong>o</strong> por el SKU cuando llegan
     * los IDs. Basta con que UNA de las dos vías lo reconozca.</p>
     *
     * <p>Es el interruptor del correo específico del lanzamiento: ese pedido
     * lleva la garantía de 48 horas para reportar defectos de fábrica en el
     * propio correo de confirmación. El resto de pedidos mantiene el correo
     * genérico.</p>
     *
     * <p>El fallback por texto usa {@link #normalizarTexto(String)}, así que
     * reconoce «CTB-234» aunque llegue con mayúsculas o con espacios de más.
     * También acepta el nombre del producto porque los carritos antiguos no
     * enviaban IDs ni SKU en el detalle.</p>
     *
     * @param pedido pedido recién creado
     * @return {@code true} si el pedido incluye CTB-234
     */
    private boolean esPedidoCtb234(Pedido pedido) {
        if (pedido == null) return false;
        // Por IDs (autoritativo si viene)
        if (pedido.getProductosIds() != null && !pedido.getProductosIds().isEmpty()) {
            for (Producto p : productoRepository.findAllById(pedido.getProductosIds())) {
                if ("CTB-234".equalsIgnoreCase(p.getSku())) return true;
            }
        }
        // Por texto (fallback para carritos antiguos o sin IDs)
        String detalle = normalizarTexto(pedido.getProductos());
        return detalle.contains("ctb-234") || detalle.contains("destornillador");
    }

    /**
     * Recoge del navegador lo que Meta usa para mejorar el matching:
     * IP, user-agent, cookies del píxel y la URL de origen.
     */
    private static MetaConversionsService.Contexto contextoMeta(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return new MetaConversionsService.Contexto(
                ipDe(request),
                request.getHeader("User-Agent"),
                cookieDe(request, "_fbp"),
                cookieDe(request, "_fbc"),
                request.getHeader("Referer"));
    }

    private static String cookieDe(HttpServletRequest request, String nombre) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (nombre.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * IP del cliente. Con {@code server.forward-headers-strategy=framework}
     * el proxy ya la deja en {@code getRemoteAddr()}.
     */
    private static String ipDe(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return (ip == null || ip.isBlank()) ? null : ip;
    }

    @GetMapping("/api/pedido/{codigo}")
    @ResponseBody
    public Map<String, Object> buscarPedido(@PathVariable String codigo) {
        // Consulta directa por código (antes: findAll() + filtro en memoria,
        // que traía TODOS los pedidos en cada consulta de seguimiento).
        Pedido pedido = pedidoRepository.findFirstByCodigoOrderByIdDesc(codigo).orElse(null);
        Map<String, Object> response = new HashMap<>();
        if (pedido == null) {
            response.put("error", "Pedido no encontrado");
        } else {
            response.put("codigo", pedido.getCodigo());
            response.put("estado", pedido.getEstado());
            response.put("productos", pedido.getProductos());
            response.put("total", pedido.getTotal());
            response.put("transportadora", pedido.getTransportadora());
            response.put("numeroGuia", pedido.getNumeroGuia());
            response.put("telefono", pedido.getTelefono());
        }
        return response;
    }

    @GetMapping("/api/pedido/telefono/{telefono}")
    @ResponseBody
    public Map<String, Object> buscarPorTelefono(@PathVariable String telefono) {
        Pedido pedido = pedidoRepository.findFirstByTelefonoOrderByIdDesc(telefono).orElse(null);
        Map<String, Object> response = new HashMap<>();
        if (pedido == null) {
            response.put("error", "Pedido no encontrado");
        } else {
            response.put("codigo", pedido.getCodigo());
            response.put("estado", pedido.getEstado());
            response.put("productos", pedido.getProductos());
            response.put("total", pedido.getTotal());
            response.put("transportadora", pedido.getTransportadora());
            response.put("numeroGuia", pedido.getNumeroGuia());
            response.put("telefono", pedido.getTelefono());
        }
        return response;
    }

    @PostMapping("/api/chat/enviar")
    @ResponseBody
    public Map<String, String> enviarMensaje(@RequestBody Map<String, String> body) {
        Mensaje msg = new Mensaje(body.get("codigo"), body.get("telefono"), body.get("mensaje"), true);
        mensajeRepository.save(msg);
        Map<String, String> response = new HashMap<>();
        response.put("status", "OK");
        return response;
    }

    @GetMapping("/api/chat/mensajes")
    @ResponseBody
    public List<Mensaje> obtenerMensajes(@RequestParam(required = false) String codigo) {
        if (codigo != null && !codigo.isEmpty()) {
            return mensajeRepository.findByCodigoPedidoOrderByFechaAsc(codigo);
        }
        return List.of();
    }

    @GetMapping("/api/chat/todos")
    @ResponseBody
    public List<Mensaje> obtenerTodosMensajes() {
        // Solo los últimos 200: este endpoint se consulta en bucle desde el
        // panel de administración y antes devolvía la tabla completa.
        return mensajeRepository.findTop200ByOrderByFechaDesc();
    }
}