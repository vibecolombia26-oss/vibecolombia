package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Producto;
import com.flowcolombia.flowcolombia.ProductoRepository;
import com.flowcolombia.flowcolombia.config.ConfiguracionIA;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contexto de página del asistente (misión 3): la ruta que manda el navegador
 * se resuelve contra el catálogo en memoria y se convierte en una línea oculta
 * del prompt con el nombre del producto.
 *
 * <p>Sin Spring, sin base de datos y sin red: el repositorio se simula con un
 * {@link Proxy} y el catálogo se carga una vez con {@code cargarCatalogo()}.</p>
 */
class AsistenteContextoPaginaTest {

    private AsistenteService asistente;

    @BeforeEach
    void preparar() {
        Producto producto = new Producto();
        producto.setId(7L);
        producto.setNombre("Mochila Táctica");
        producto.setSku("MOCH");
        producto.setSlug("mochila-tactica");
        producto.setPrecio(89900d);
        producto.setCategoria("Mochilas");

        ProductoRepository repositorio = (ProductoRepository) Proxy.newProxyInstance(
                ProductoRepository.class.getClassLoader(),
                new Class<?>[]{ProductoRepository.class},
                (proxy, metodo, args) -> metodo.getReturnType() == List.class ? List.of(producto) : null);

        asistente = new AsistenteService(
                repositorio,
                new ConfiguracionIA("cercano, premium, colombiano, pícaro"),
                "",                     // sin clave de IA: aquí no se llama al modelo
                "https://api.deepseek.com",
                "deepseek-chat",
                400,
                25);

        asistente.cargarCatalogo();
    }

    @Test
    @DisplayName("La ficha por slug resuelve el producto del catálogo")
    void encuentraProductoPorSlug() {
        AsistenteService.ProductoCatalogo encontrado =
                asistente.productoDeLaRuta("/producto/mochila-tactica");

        assertEquals("Mochila Táctica", encontrado.nombre());
        assertEquals("mochila-tactica", encontrado.slug());
    }

    @Test
    @DisplayName("El contexto oculto incluye el nombre y la instrucción corta")
    void contextoIncluyeNombreDelProducto() {
        String contexto = asistente.contextoPagina("/producto/mochila-tactica");

        assertTrue(contexto.contains("El cliente está viendo el producto: Mochila Táctica"),
                "falta el nombre del producto en el contexto de página");
        assertTrue(contexto.contains("comentario corto y persuasivo"),
                "falta la instrucción de comentar el producto");
    }

    @Test
    @DisplayName("Acepta también la ruta antigua por id y los parámetros de la URL")
    void aceptaIdAntiguoYQueryString() {
        assertEquals("Mochila Táctica", asistente.productoDeLaRuta("/producto/7").nombre());
        assertEquals("Mochila Táctica",
                asistente.productoDeLaRuta("/producto/mochila-tactica?ref=instagram").nombre());
    }

    @Test
    @DisplayName("Rutas que no son una ficha no aportan contexto")
    void otrasRutasNoAportanContexto() {
        assertNull(asistente.productoDeLaRuta("/carrito"));
        assertNull(asistente.productoDeLaRuta("/producto/"));
        assertNull(asistente.productoDeLaRuta("/producto/desconocido"));
        assertEquals("", asistente.contextoPagina(null));
        assertEquals("", asistente.contextoPagina("  "));
    }
}
