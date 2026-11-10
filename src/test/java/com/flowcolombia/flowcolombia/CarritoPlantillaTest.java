package com.flowcolombia.flowcolombia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
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
 * Renderiza el carrito con Thymeleaf y comprueba el cableado de los eventos
 * de e-commerce de GA4.
 *
 * <p>El checkout es la parte más cara de la tienda: un error de JavaScript en
 * esta plantilla deja al cliente sin poder comprar. Por eso se ejecuta el motor
 * de Thymeleaf de verdad (sin Spring ni base de datos) en lugar de confiar en
 * que el archivo «se ve bien».</p>
 */
class CarritoPlantillaTest {

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

    private String renderizar() {
        Map<String, Object> modelo = new HashMap<>();
        // La plantilla lee ${_csrf.token} para el checkout: sin este objeto
        // Thymeleaf fallaría al resolver la propiedad.
        modelo.put("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token-de-prueba"));

        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setRequestURI("/carrito");
        MockHttpServletResponse response = new MockHttpServletResponse();

        JakartaServletWebApplication aplicacion =
                JakartaServletWebApplication.buildApplication(servletContext);
        WebContext contexto = new WebContext(aplicacion.buildExchange(request, response),
                Locale.forLanguageTag("es-CO"), modelo);

        return motor.process("carrito", contexto);
    }

    @Test
    @DisplayName("El checkout dispara begin_checkout y purchase, con transaction_id")
    void eventosDeCheckout() {
        String html = renderizar();

        assertTrue(html.contains("trackGA4Event('begin_checkout'"),
                "falta begin_checkout en el carrito");
        assertTrue(html.contains("trackGA4Event('purchase'"),
                "falta purchase al confirmar el pedido");

        // El transaction_id es el código del pedido que devuelve el backend.
        assertTrue(html.contains("transaction_id: data.codigo"),
                "purchase debe viajar con el código del pedido como transaction_id");
        assertTrue(html.contains("transaction_id: codigo"),
                "mostrarConfirmacion debe poder disparar purchase con el código recibido");

        // Los items se capturan ANTES de vaciar el carrito.
        assertTrue(html.contains("items: itemsComprados.map(FlowCart.ga4Item)"),
                "purchase debe llevar los items[] de GA4");
    }

    @Test
    @DisplayName("El píxel de Meta sigue intacto junto a GA4")
    void metaYGa4Coexisten() {
        String html = renderizar();

        assertTrue(html.contains("trackMeta('InitiateCheckout'"), "desapareció InitiateCheckout de Meta");
        assertTrue(html.contains("trackMeta('Purchase'"), "desapareció Purchase de Meta");
        assertTrue(html.contains("fbq('init'"), "desapareció el píxel de Meta");
        assertTrue(html.contains("googletagmanager.com/gtag/js?id=G-5PT3YEX4G7"),
                "falta el script global de GA4 en el carrito");
    }

    @Test
    @DisplayName("El carrito carga la versión de flow-cart.js que filtra Meta")
    void cargaLaVersionNuevaDelCarrito() {
        String html = renderizar();
        assertTrue(html.contains("/js/flow-cart.js?v=5"),
                "la plantilla carga una versión vieja de flow-cart.js (sin el filtro de Meta)");
        assertFalse(html.contains("/js/flow-cart.js?v=4"),
                "quedaron referencias a la versión anterior");
    }

    @Test
    @DisplayName("Los eventos de Meta del checkout filtran los productos sensibles")
    void metaFiltraProductosSensibles() {
        String html = renderizar();

        // InitiateCheckout y Purchase se arman SOLO con las líneas que no
        // están excluidas; GA4 sigue usando el carrito completo.
        assertTrue(html.contains("FlowCart.itemsParaMeta"),
                "el checkout no filtra los productos excluidos de Meta");
        assertTrue(html.contains("if (datosCompra)"),
                "Purchase debe poder no dispararse cuando todo el pedido es sensible");
        assertTrue(html.contains("value: FlowCart.subtotal(itemsMeta)"),
                "el valor de Meta debe calcularse solo con los productos permitidos");

        // Los IDs viajan al backend para aplicar la regla de la CAPI.
        assertTrue(html.contains("productosIds:"),
                "el pedido debe mandar los IDs de producto para la regla de la CAPI");
    }
}
