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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renderiza <strong>de verdad</strong> la ficha de producto con Thymeleaf y
 * comprueba que las dos experiencias del CRO se pintan como toca.
 *
 * <p>Este archivo ya tuvo un 500 en producción por un fallo de plantilla, así
 * que no basta con que el Java compile: aquí se ejecuta el motor de Thymeleaf
 * y se verifica el HTML resultante. No arranca Spring ni toca la base de datos:
 * el motor se monta a mano con un contexto web simulado.</p>
 */
class ProductoDetallePlantillaTest {

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

    /** Renderiza la ficha y devuelve el HTML final. */
    private String renderizar(boolean esCalzado, int totalResenas) {
        return renderizar(esCalzado, totalResenas, List.of());
    }

    /** Renderiza la ficha con las imágenes indicadas (url + color). */
    private String renderizar(boolean esCalzado, int totalResenas, List<Map<String, Object>> imagenes) {
        Producto producto = new Producto();
        producto.setId(7L);
        producto.setNombre("Mochila Táctica");
        producto.setSku("MOCH");
        producto.setSlug("mochila-tactica");
        producto.setPrecio(89900d);
        producto.setCategoria(esCalzado ? "Calzado" : "Mochilas");
        producto.setDescripcion("Ligera y resistente");
        producto.setDescripcionLarga("Ligera, resistente y con bolsillo antirrobo para el día a día.");
        producto.setPeso("500 g");
        producto.setLargo("30");
        producto.setAncho("20");
        producto.setAlto("45");

        Map<String, Object> modelo = new HashMap<>();
        modelo.put("producto", producto);
        modelo.put("variantes", List.of());
        modelo.put("imagenesProducto", imagenes);
        modelo.put("imagenesLegacy", List.of());
        modelo.put("imagenPrincipal", "https://example.com/foto.jpg");
        modelo.put("esCalzado", esCalzado);
        modelo.put("resenas", List.of());
        modelo.put("promedioCalificacion", 4.7d);
        modelo.put("totalResenas", totalResenas);
        modelo.put("urlProducto", "/producto/mochila-tactica");
        modelo.put("urlCanonica", "https://flow.example.com/producto/mochila-tactica");
        modelo.put("seoDescripcion", "Ligera y resistente");
        modelo.put("seoImagenes", List.of("https://example.com/foto.jpg"));
        modelo.put("seoPrecio", 89900L);
        modelo.put("seoDisponible", true);

        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setRequestURI("/producto/mochila-tactica");
        MockHttpServletResponse response = new MockHttpServletResponse();

        JakartaServletWebApplication aplicacion =
                JakartaServletWebApplication.buildApplication(servletContext);
        WebContext contexto = new WebContext(aplicacion.buildExchange(request, response),
                Locale.forLanguageTag("es-CO"), modelo);

        return motor.process("producto-detalle", contexto);
    }

    @Test
    @DisplayName("Calzado: guía de tallas + confirmación, y sin bloque de beneficios")
    void fichaDeCalzado() {
        String html = renderizar(true, 12);

        assertTrue(html.contains("Encuentra tu talla perfecta"), "falta el acordeón de la guía");
        assertTrue(html.contains("EU (Col)"), "falta la cabecera de la tabla de tallas");
        assertTrue(html.contains("25.5 cm"), "falta una fila de la tabla");
        assertTrue(html.contains("Cómo medir tu pie en casa"), "falta el tip de medición");
        assertTrue(html.contains("id=\"confirmacionTalla\""), "falta la casilla de confirmación");
        assertTrue(html.contains("Cambios de talla"), "falta el aviso de la política de cambios");
        assertTrue(html.contains("id=\"btnAgregarCarrito\""), "desapareció el botón de compra");

        // El JS de esta ficha debe exigir la casilla
        assertTrue(html.contains("REQUIERE_CONFIRMACION_TALLA = true"),
                "el JavaScript no marca la ficha como calzado");

        // Lo exclusivo del otro camino no debe aparecer
        assertFalse(html.contains("Lo que ganas con este producto"));
        assertFalse(html.contains("Garantía Flow"));
    }

    @Test
    @DisplayName("No calzado: beneficios, sello de garantía y nota junto al título")
    void fichaNormal() {
        String html = renderizar(false, 12);

        assertTrue(html.contains("Lo que ganas con este producto"), "falta el bloque de beneficios");
        assertTrue(html.contains("Garantía Flow"), "falta el sello de garantía");
        assertTrue(html.contains("clientes lo recomiendan"), "falta la prueba social junto al título");
        assertTrue(html.contains("Por qué te va a encantar"), "la descripción no se reescribió como beneficio");
        assertTrue(html.contains("id=\"btnAgregarCarrito\""), "desapareció el botón de compra");

        assertTrue(html.contains("REQUIERE_CONFIRMACION_TALLA = false"),
                "esta ficha no debe pedir confirmación de talla");

        // La capa de experiencia (barra de progreso + View Transitions) tiene
        // que viajar en todas las páginas públicas.
        assertTrue(html.contains("/js/flow-ui.js?v=1"), "falta flow-ui.js en la ficha");
        assertTrue(html.contains("/js/flow-assistant.js?v=5"), "falta el asistente (esfera de Tina)");

        // Google Analytics 4: script global (public-meta) + evento de ficha.
        assertTrue(html.contains("googletagmanager.com/gtag/js?id=G-5PT3YEX4G7"),
                "falta el script global de GA4 en la ficha");
        assertTrue(html.contains("trackGA4Event('view_item'"),
                "la ficha de producto no dispara view_item");
        assertTrue(html.contains("/js/flow-cart.js?v=4"),
                "la ficha carga una versión vieja de flow-cart.js (sin GA4)");

        // El píxel de Meta sigue vivo: GA4 se suma, no reemplaza.
        assertTrue(html.contains("fbq('init'"), "desapareció el píxel de Meta");
        assertTrue(html.contains("trackMeta('ViewContent'"), "desapareció el ViewContent de Meta");

        assertFalse(html.contains("Encuentra tu talla perfecta"));
        assertFalse(html.contains("id=\"confirmacionTalla\""));
    }

    @Test
    @DisplayName("Sin reseñas no se inventa prueba social")
    void sinResenasNoHayEstrellas() {
        String html = renderizar(false, 0);

        assertFalse(html.contains("clientes lo recomiendan"), "no debe haber nota sin reseñas");
        // Se busca el atributo, no el nombre: la hoja de estilos también
        // menciona la clase y daría un falso positivo.
        assertFalse(html.contains("class=\"pdp__rating-stars\""), "no debe haber estrellas sin reseñas");

        // Los beneficios y la garantía no dependen de las reseñas
        assertTrue(html.contains("Lo que ganas con este producto"));
        assertTrue(html.contains("Garantía Flow"));
    }

    @Test
    @DisplayName("La galería recibe el color de cada imagen y filtra al elegir color")
    void galeriaPorColor() {
        List<Map<String, Object>> imagenes = new ArrayList<>();
        imagenes.add(imagen("https://cdn/general.jpg", "", 0));
        imagenes.add(imagen("https://cdn/negra.jpg", "Negro", 1));
        imagenes.add(imagen("https://cdn/blanca.jpg", "Blanco/beige", 2));

        String html = renderizar(false, 3, imagenes);

        // El DTO viaja al JS con el color de cada foto. (El serializador puede
        // escapar la barra de «Blanco/beige», así que se comprueba el prefijo.)
        assertTrue(html.contains("\"color\":\"Negro\""), "falta el color en el DTO de imágenes");
        assertTrue(html.contains("\"color\":\"Blanco"), "falta el color compuesto");
        assertTrue(html.contains("/cdn/blanca.jpg"), "falta la URL de la imagen");

        // El filtrado y el fundido están cableados.
        assertTrue(html.contains("function imagenesParaColor"), "falta el filtro por color");
        assertTrue(html.contains("aplicarColorGaleria(color)"), "seleccionarColor no filtra la galería");
        assertTrue(html.contains("is-fading"), "falta la transición suave");
        assertTrue(html.contains("function pintarMiniaturas"), "las miniaturas no se repintan");
    }

    private static Map<String, Object> imagen(String url, String color, int orden) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("url", url);
        dto.put("color", color);
        dto.put("orden", orden);
        return dto;
    }

    @Test
    @DisplayName("Nada de ventanas emergentes: el flujo es todo en la página")
    void sinAlertasDeJavaScript() {
        for (boolean calzado : new boolean[]{true, false}) {
            String html = renderizar(calzado, 5);
            assertFalse(html.contains("alert("), "hay un alert() en la plantilla");
            assertFalse(html.contains("window.confirm("), "hay un confirm() en la plantilla");
        }
    }
}
