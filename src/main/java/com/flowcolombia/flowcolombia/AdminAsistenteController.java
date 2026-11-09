package com.flowcolombia.flowcolombia;

import com.flowcolombia.flowcolombia.service.AdminAsistenteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoint del asistente de administración.
 *
 * <p><strong>Acceso:</strong> la ruta {@code /api/admin/**} está restringida a
 * {@code hasRole('ADMIN')} en {@code SecurityConfig}, y al ser un POST del
 * panel exige además el token CSRF que añade {@code window.adminFetch}.</p>
 *
 * <p><strong>Dos modos en el mismo endpoint:</strong></p>
 * <ul>
 *   <li>Mensaje normal: se consulta a la IA. Si propone un cambio, la respuesta
 *       trae {@code requiereConfirmacion: true} y la acción <em>no</em> se ha
 *       ejecutado.</li>
 *   <li>{@code confirmarAccion: true} + {@code accionPendiente}: se aplica el
 *       cambio y se devuelve {@code ejecutada: true|false} con el mensaje.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/asistente")
public class AdminAsistenteController {

    private final AdminAsistenteService adminAsistenteService;

    public AdminAsistenteController(AdminAsistenteService adminAsistenteService) {
        this.adminAsistenteService = adminAsistenteService;
    }

    /**
     * Petición del chat.
     *
     * @param mensaje         lo que escribe el administrador
     * @param historial       conversación reciente (la mantiene el navegador)
     * @param confirmarAccion true para ejecutar la acción propuesta
     * @param accionPendiente la acción que se aprobó
     */
    public record Peticion(String mensaje,
                           List<AdminAsistenteService.MensajeHistorial> historial,
                           boolean confirmarAccion,
                           AdminAsistenteService.AccionPendiente accionPendiente) {
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> chatear(@RequestBody(required = false) Peticion peticion) {
        if (peticion == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Falta el cuerpo de la petición."));
        }

        // ------------------------------------------------------------
        // 1. El administrador aprobó una acción propuesta
        // ------------------------------------------------------------
        if (peticion.confirmarAccion()) {
            // Si el navegador no reenvía la acción (un «sí» escrito a mano, una
            // pestaña recargada), el servicio usa la última que propuso él
            // mismo. En este camino NUNCA se llama al modelo: se ejecuta la
            // acción cacheada tal cual se aprobó.
            AdminAsistenteService.Resultado resultado =
                    adminAsistenteService.ejecutar(peticion.accionPendiente());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("respuesta", resultado.mensaje());
            body.put("ejecutada", resultado.ok());
            body.put("requiereConfirmacion", false);
            return ResponseEntity.ok(body);
        }

        // ------------------------------------------------------------
        // 2. Mensaje normal
        // ------------------------------------------------------------
        if (peticion.mensaje() == null || peticion.mensaje().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Escribe un mensaje para el asistente."));
        }

        AdminAsistenteService.Respuesta respuesta =
                adminAsistenteService.responder(peticion.mensaje(), peticion.historial());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("respuesta", respuesta.respuesta());
        body.put("requiereConfirmacion", respuesta.requiereConfirmacion());
        body.put("ejecutada", false);

        // La acción propuesta: el frontend la guarda y la reenvía al aprobar.
        if (respuesta.accion() != null) {
            body.put("accion", respuesta.accion());
        }

        return ResponseEntity.ok(body);
    }
}
