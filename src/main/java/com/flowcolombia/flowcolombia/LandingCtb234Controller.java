package com.flowcolombia.flowcolombia;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Landing standalone del smoke test CTB-234
 * (Destornillador de Impacto Manual x 13 Piezas).
 *
 * <p>Ruta fija, hardcoded: no es un CRUD. Sirve el HTML estático con el copy
 * aprobado por el brief de marketing. Un solo producto, un solo flujo, cero
 * variantes.</p>
 *
 * <p><strong>No hay lógica de negocio aquí</strong>: el carrito y el checkout
 * siguen siendo los de la tienda ({@code /carrito?checkout=1}); esta vista solo
 * presenta el producto y dispara los eventos de medición desde
 * {@code flow-landing-ctb.js}.</p>
 *
 * <p><strong>Acceso:</strong> la ruta es pública. {@code /landing/**} está en
 * la lista blanca {@code TIENDA_PUBLICA} de {@code SecurityConfig} (solo
 * {@code GET}, sin estado y sin privilegios), así que el tráfico orgánico del
 * marketplace entra sin sesión. El panel de seguimiento
 * ({@code /admin/landing/ctb-234}) sigue exigiendo rol ADMIN.</p>
 */
@Controller
public class LandingCtb234Controller {

    @GetMapping("/landing/ctb-234")
    public String landing() {
        return "landing-ctb-234";
    }
}
