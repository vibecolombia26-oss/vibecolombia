package com.flowcolombia.flowcolombia;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

/**
 * Archivos de infraestructura SEO: sitemap, {@code robots.txt} y la
 * verificación de propiedad de Google Search Console.
 *
 * <p><strong>Coste en Neon:</strong> el sitemap se genera con
 * <em>una única consulta</em> que devuelve solo la columna {@code slug}
 * ({@link ProductoRepository#findSlugsParaSitemap()}). No se cargan entidades
 * ni colecciones perezosas: el XML se arma en memoria y no hay N+1. Los otros
 * dos endpoints no tocan la base de datos.</p>
 *
 * <p>Las URLs se construyen a partir de la petición actual, así que funcionan
 * igual en local y en producción (con {@code forward-headers-strategy=framework}
 * heredan el {@code https} del proxy).</p>
 */
@RestController
public class SitemapController {

    /** Páginas fijas de la tienda: ruta, frecuencia de cambio y prioridad. */
    private static final String[][] PAGINAS = {
            {"/", "daily", "1.0"},
            {"/envios", "monthly", "0.6"},
            {"/seguimiento", "monthly", "0.5"},
            {"/contacto", "monthly", "0.5"},
            {"/terminos", "yearly", "0.3"},
            {"/privacidad", "yearly", "0.3"}
    };

    private final ProductoRepository productoRepository;

    public SitemapController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    /**
     * {@code GET /sitemap.xml}
     *
     * <p>Formato estándar de sitemaps.org. El {@code lastmod} es la fecha del
     * día: el catálogo es dinámico y el esquema no guarda una fecha de
     * actualización por producto (no se toca la base de datos).</p>
     */
    @GetMapping(value = "/sitemap.xml", produces = "application/xml; charset=UTF-8")
    public String sitemap() {
        String base = basePublica();
        String hoy = LocalDate.now().toString();

        StringBuilder xml = new StringBuilder(8192);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
           .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        for (String[] pagina : PAGINAS) {
            agregarUrl(xml, base + pagina[0], hoy, pagina[1], pagina[2]);
        }

        // Productos: solo el slug, sin entidades ni colecciones perezosas.
        List<String> slugs = productoRepository.findSlugsParaSitemap();
        if (slugs != null) {
            for (String slug : slugs) {
                if (slug == null || slug.isBlank()) {
                    continue;
                }
                agregarUrl(xml, base + "/producto/" + slug, hoy, "weekly", "0.8");
            }
        }

        xml.append("</urlset>\n");
        return xml.toString();
    }

    /** {@code GET /robots.txt}: índice abierto, panel y API fuera. */
    @GetMapping(value = "/robots.txt", produces = "text/plain; charset=UTF-8")
    public String robots() {
        return "User-agent: *\n"
                + "Allow: /\n"
                + "Disallow: /admin\n"
                + "Disallow: /api/\n"
                + "\n"
                + "Sitemap: " + basePublica() + "/sitemap.xml\n";
    }

    // ============================================================
    // Utilidades
    // ============================================================

    /**
     * Esquema + host + contexto de la petición actual
     * ({@code https://flowcolombia.com}).
     */
    private String basePublica() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
    }

    private static void agregarUrl(StringBuilder xml, String loc, String lastmod,
                                   String changefreq, String prioridad) {
        xml.append("  <url>\n")
           .append("    <loc>").append(escaparXml(loc)).append("</loc>\n")
           .append("    <lastmod>").append(lastmod).append("</lastmod>\n")
           .append("    <changefreq>").append(changefreq).append("</changefreq>\n")
           .append("    <priority>").append(prioridad).append("</priority>\n")
           .append("  </url>\n");
    }

    /** Los slugs son ASCII seguro, pero se escapa por si llega un nombre raro. */
    private static String escaparXml(String valor) {
        return valor
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
