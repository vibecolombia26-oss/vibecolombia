package com.flowcolombia.flowcolombia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renderiza <strong>de verdad</strong> la landing del lanzamiento CTB-234
 * con el motor de Thymeleaf y comprueba que el HTML sale completo.
 *
 * <p>Una landing de campaña no puede dar un 500 ni perder un CTA: el tráfico
 * llega pagado y no hay segunda oportunidad. Por eso se ejecuta el motor de
 * Thymeleaf (sin Spring ni base de datos) igual que en el resto de tests de
 * plantilla del proyecto.</p>
 *
 * <p>Se verifica también el contrato de la landing: header mínimo (sin menú,
 * sin "volver a la tienda"), sin Tina, sin footer de la tienda y con el copy
 * del brief intacto.</p>
 */
class LandingCtb234PlantillaTest {

    private SpringTemplateEngine motor;
    private MockServletContext servletContext;

    @BeforeEach
    void prepararMotor() {
        servletContext = new MockServletContext();

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        motor = new SpringTemplateEngine();
        motor.setTemplateResolver(resolver);
    }

    /** La landing no recibe modelo: todo el copy vive en la plantilla. */
    private String renderizar() {
        Map<String, Object> modelo = new HashMap<>();

        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setRequestURI("/landing/ctb-234");
        MockHttpServletResponse response = new MockHttpServletResponse();

        JakartaServletWebApplication aplicacion =
                JakartaServletWebApplication.buildApplication(servletContext);
        WebContext contexto = new WebContext(aplicacion.buildExchange(request, response),
                Locale.forLanguageTag("es-CO"), modelo);

        return motor.process("landing-ctb-234", contexto);
    }

    @Test
    @DisplayName("La landing renderiza el hero con precio y CTA al checkout")
    void heroCompleto() {
        String html = renderizar();

        assertTrue(html.contains("Destornillador de Impacto Manual x 13 Piezas"),
                "falta el nombre del producto en el H1");
        assertTrue(html.contains("Es manual: se acciona con el golpe de la mano, no necesita batería ni corriente."),
                "falta la respuesta a la objeción #1");
        assertTrue(html.contains("$89.900 COP — Envío gratis — Pago contra entrega"),
                "falta la línea de precio");
        assertTrue(html.contains("id=\"ctaHero\""),
                "el CTA del hero necesita id para el evento de intención");
        assertTrue(html.contains("href=\"/carrito?checkout=1\""),
                "el CTA debe llevar al checkout de la tienda");
    }

    @Test
    @DisplayName("Están los 8 bloques del brief, con su copy exacto")
    void bloquesDelBrief() {
        String html = renderizar();

        // Bloque 0 · header mínimo
        assertTrue(html.contains("🔒 Compra segura"), "falta el badge de compra segura");

        // Bloque 2 · franja de confianza
        assertTrue(html.contains("Reporta defectos de fábrica en las primeras 48 horas"),
                "falta la franja de confianza");

        // Bloque 3 · video + beneficios
        assertTrue(html.contains("Míralo en uso: así trabaja el golpe manual"), "falta el H2 del video");
        assertTrue(html.contains("Acero CR-V: las puntas no se deforman con tornillos apretados."),
                "falta un beneficio del brief");

        // Bloque 4 · qué incluye
        assertTrue(html.contains("Qué recibes exactamente"), "falta el bloque de contenido del kit");
        assertTrue(html.contains("13 puntas intercambiables de acero CR-V."), "falta una línea de Qué incluye");

        // Bloque 5 · 3 pasos
        assertTrue(html.contains("Comprar es así de simple"), "falta el bloque de los 3 pasos");
        assertTrue(html.contains("No pagas ahora. Pagas cuando recibes."), "falta el micro-copy de pago");

        // Bloque 6 · FAQ
        assertTrue(html.contains("Preguntas frecuentes"), "falta el FAQ");
        assertTrue(html.contains("¿Es eléctrico o manual?"), "falta la primera pregunta");
        assertTrue(html.contains("Pasado ese plazo no podemos gestionar el reemplazo."),
                "falta la respuesta de garantía de 48 horas");

        // Bloque 7 · CTA final
        assertTrue(html.contains("Tu pedido está listo para despacho"), "falta el H2 del cierre");
        assertTrue(html.contains("CONFIRMAR MI PEDIDO AHORA"), "falta el CTA final");

        // Bloque 8 · WhatsApp flotante
        assertTrue(html.contains("aria-label=\"Escribir por WhatsApp\""), "falta el fallback de WhatsApp");
        assertTrue(html.contains("wa.me/573150152056"), "falta el enlace de WhatsApp del negocio");
    }

    @Test
    @DisplayName("La landing no arrastra la tienda: sin menú, sin Tina, sin footer")
    void landingAislada() {
        String html = renderizar();

        assertFalse(html.contains("site-header"), "la landing no debe llevar el header completo de la tienda");
        assertFalse(html.contains("Volver a la tienda"), "no debe haber enlaces de salida al escaparate");
        assertFalse(html.contains("flow-assistant.js"), "la landing no lleva a Tina (solo WhatsApp)");
        assertFalse(html.contains("public-footer"), "la landing no debe llevar el footer de la tienda");
    }

    @Test
    @DisplayName("Carga el script de medición propio y la base del carrito")
    void trackingCableado() {
        String html = renderizar();

        assertTrue(html.contains("/js/flow-landing-ctb.js"), "falta el script del lanzamiento");
        assertTrue(html.contains("/js/flow-cart.js"), "falta flow-cart.js (carrito y puente de medición)");
        assertTrue(html.contains("fbq('init'"), "falta el píxel de Meta");
        assertTrue(html.contains("googletagmanager.com/gtag/js?id=G-5PT3YEX4G7"),
                "falta el script global de GA4");
    }
}
