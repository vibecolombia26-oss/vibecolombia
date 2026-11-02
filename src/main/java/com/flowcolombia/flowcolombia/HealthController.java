package com.flowcolombia.flowcolombia;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint de salud SIN acceso a base de datos.
 *
 * Úsalo como health check del hosting (Render, etc.) o como destino de un
 * monitor de disponibilidad. NUNCA apuntes el monitor a "/" : la home
 * consulta Neon y cada ping impediría que la compute se suspendiera,
 * consumiendo las CU-hours del free tier durante las 24 horas.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> respuesta = new LinkedHashMap<>();
        respuesta.put("status", "ok");
        respuesta.put("app", "flowcolombia");
        return respuesta;
    }
}
