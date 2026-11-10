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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renderiza de verdad el formulario de producto del panel y comprueba el
 * cableado del checkbox de seguridad de Meta ({@code excluirMeta}).
 *
 * <p>Este checkbox es la única forma de excluir un producto de salud íntima
 * del píxel y de la API de Conversiones: si el formulario deja de enviarlo o
 * deja de pintarlo marcado al editar, el administrador creería que el producto
 * está excluido cuando en realidad se está midiendo. Por eso se ejecuta el
 * motor de Thymeleaf (sin Spring ni base de datos) y se revisa el HTML.</p>
 */
class AdminFormPlantillaTest {

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

    private String renderizar(boolean excluirMeta, boolean productoExistente) {
        Producto producto = new Producto();
        if (productoExistente) {
            producto.setId(7L);
            producto.setNombre("Crema Íntima");
            producto.setSku("CREMA-01");
            producto.setPrecio(59900d);
            producto.setCategoria("Cuidado Personal");
        }
        producto.setExcluirMeta(excluirMeta);

        Map<String, Object> modelo = new HashMap<>();
        modelo.put("producto", producto);
        modelo.put("resenas", new ArrayList<>());
        modelo.put("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token-de-prueba"));

        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setRequestURI("/admin/editar/7");
        MockHttpServletResponse response = new MockHttpServletResponse();

        JakartaServletWebApplication aplicacion =
                JakartaServletWebApplication.buildApplication(servletContext);
        WebContext contexto = new WebContext(aplicacion.buildExchange(request, response),
                Locale.forLanguageTag("es-CO"), modelo);

        return motor.process("admin-form", contexto);
    }

    @Test
    @DisplayName("El formulario envía excluirMeta y lo pinta marcado al editar")
    void checkboxMapeadoAlCampo() {
        String html = renderizar(true, true);

        // Mismo `name` que el campo de la entidad: es lo que enlaza Spring.
        assertTrue(html.contains("name=\"excluirMeta\""),
                "el formulario no envía el campo excluirMeta");
        assertTrue(html.contains("value=\"true\""),
                "el checkbox debe enviar el valor true");
        assertTrue(html.contains("checked=\"checked\""),
                "un producto ya excluido debe aparecer marcado al editar");
        assertTrue(html.contains("Ocultar a Meta (Productos de salud íntima o sensibles)"),
                "falta la etiqueta del checkbox");
    }

    @Test
    @DisplayName("Un producto normal (o nuevo) llega desmarcado")
    void checkboxDesmarcadoPorDefecto() {
        String html = renderizar(false, true);
        assertTrue(html.contains("name=\"excluirMeta\""), "el formulario no envía excluirMeta");
        assertFalse(html.contains("checked=\"checked\""),
                "el checkbox no debe nacer marcado: excluir a Meta es una acción explícita");

        String htmlNuevo = renderizar(false, false);
        assertTrue(htmlNuevo.contains("name=\"excluirMeta\""),
                "el formulario de alta tampoco envía excluirMeta");
        assertFalse(htmlNuevo.contains("checked=\"checked\""),
                "un producto nuevo no debe nacer excluido de Meta");
    }
}
