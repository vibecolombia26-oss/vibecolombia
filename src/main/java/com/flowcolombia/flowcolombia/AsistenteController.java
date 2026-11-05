package com.flowcolombia.flowcolombia;

import com.flowcolombia.flowcolombia.service.AsistenteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Endpoint público del Asistente de Compras.
 *
 * <p>La clave de DeepSeek se usa solo dentro de {@link AsistenteService};
 * este endpoint nunca la expone. El catálogo ya está en memoria, por lo que
 * cada mensaje se resuelve sin consultar la base de datos.</p>
 */
@RestController
@RequestMapping("/api/asistente")
public class AsistenteController {

    /** Límite blando por IP para proteger el costo de la API de IA. */
    private static final int MAX_PETICIONES_POR_MINUTO = 30;

    private final AsistenteService asistenteService;
    private final Map<String, Deque<Long>> ventanas = new ConcurrentHashMap<>();

    public AsistenteController(AsistenteService asistenteService) {
        this.asistenteService = asistenteService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> chatear(@RequestBody(required = false) Peticion peticion,
                                                       HttpServletRequest request) {
        if (!permitirPeticion(ipDe(request))) {
            return ResponseEntity.status(429)
                    .body(Map.of("error", "Demasiadas consultas seguidas. Espera un momento e intenta de nuevo."));
        }

        if (peticion == null || peticion.mensaje() == null || peticion.mensaje().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Escribe un mensaje para empezar."));
        }

        try {
            AsistenteService.RespuestaAsistente respuesta =
                    asistenteService.responder(peticion.mensaje(), peticion.historial());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("respuesta", respuesta.respuesta());
            body.put("productos", respuesta.productos());

            // Acción de interfaz (ej. "ir_a_seguimiento"): el frontend la usa
            // para pintar el botón que lleva a Flow Track.
            if (respuesta.accion() != null && !respuesta.accion().isBlank()) {
                body.put("accion", respuesta.accion());
            }

            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "No pudimos procesar tu mensaje. Intenta de nuevo."));
        }
    }

    /**
     * Ventana deslizante de un minuto por IP. Es un límite en memoria del
     * propio proceso: suficiente para una tienda pequeña y no toca la BD.
     */
    private boolean permitirPeticion(String ip) {
        long ahora = System.currentTimeMillis();
        if (ventanas.size() > 5000) {
            ventanas.clear();
        }

        Deque<Long> marcas = ventanas.computeIfAbsent(ip, clave -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && ahora - marcas.peekFirst() > 60_000L) {
                marcas.pollFirst();
            }
            if (marcas.size() >= MAX_PETICIONES_POR_MINUTO) {
                return false;
            }
            marcas.addLast(ahora);
            return true;
        }
    }

    private static String ipDe(HttpServletRequest request) {
        if (request == null) {
            return "anon";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String ip = request.getRemoteAddr();
        return (ip == null || ip.isBlank()) ? "anon" : ip;
    }

    /**
     * Cuerpo de la petición: mensaje actual + historial corto (opcional)
     * para dar contexto sin disparar el costo en tokens.
     */
    public record Peticion(String mensaje, List<AsistenteService.MensajeHistorial> historial) {
    }
}
