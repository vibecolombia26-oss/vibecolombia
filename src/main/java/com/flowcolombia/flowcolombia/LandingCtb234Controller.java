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
 * <p><strong>Acceso:</strong> al vivir bajo {@code /landing/**}, esta ruta NO
 * está en la lista blanca de {@code SecurityConfig}, así que hereda
 * {@code anyRequest().authenticated()} y redirige al login del panel. Abrirla
 * al público exige una línea explícita en {@code TIENDA_PUBLICA}, que por ahora
 * es una decisión del dueño (no se toca desde esta sesión).</p>
 */
@Controller
public class LandingCtb234Controller {

    @GetMapping("/landing/ctb-234")
    public String landing() {
        return "landing-ctb-234";
    }
}
