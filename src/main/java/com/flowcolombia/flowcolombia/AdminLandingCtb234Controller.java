package com.flowcolombia.flowcolombia;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Vista de <strong>solo lectura</strong> de la landing CTB-234 para el panel.
 *
 * <p>En la fase 1 el copy de la landing vive en la plantilla
 * ({@code landing-ctb-234.html}) y no hay nada que editar desde el panel. Este
 * controlador solo sirve la ficha informativa: estado de la landing, datos
 * comerciales del producto y qué eventos de medición están implementados.</p>
 *
 * <p>La ruta cae bajo {@code /admin/**}, así que {@code SecurityConfig} ya la
 * protege con rol ADMIN: no requiere ningún cambio en las listas de seguridad.</p>
 */
@Controller
@RequestMapping("/admin/landing")
public class AdminLandingCtb234Controller {

    @GetMapping("/ctb-234")
    public String panel() {
        return "admin-landing-ctb234";
    }
}
