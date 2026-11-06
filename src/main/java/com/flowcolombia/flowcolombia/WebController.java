package com.flowcolombia.flowcolombia;

import com.flowcolombia.flowcolombia.service.ProductoService;
import com.flowcolombia.flowcolombia.service.ResenaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.flowcolombia.flowcolombia.Producto;
import com.flowcolombia.flowcolombia.Resena;

@Controller
public class WebController {

    private final ProductoService productoService;
    private final ResenaService resenaService;

    private static final String CATEGORIA_DEFAULT = "Calzado";

    /** Los ids son Long: 18 dígitos es el máximo seguro para parsear. */
    private static final String PATRON_ID = "\\d{1,18}";

    /** Tope de la meta descripción / JSON-LD, para no inflar el HTML. */
    private static final int MAX_DESCRIPCION_SEO = 300;

    public WebController(ProductoService productoService, ResenaService resenaService) {
        this.productoService = productoService;
        this.resenaService = resenaService;
    }

    @GetMapping("/")
    public String home(@RequestParam(required = false) String categoria, Model model) {
        List<String> categoriasDisponibles = productoService.listarCategorias();

        String categoriaActiva = CATEGORIA_DEFAULT;
        if (categoria != null && categoriasDisponibles.contains(categoria)) {
            categoriaActiva = categoria;
        }

        List<Producto> productosFiltrados;
        if (categoria != null && categoriasDisponibles.contains(categoria)) {
            productosFiltrados = productoService.listarPorCategoria(categoria);
        } else {
            productosFiltrados = productoService.listarPorCategoria(CATEGORIA_DEFAULT);
        }

        for (Producto p : productosFiltrados) {
            optimizarImagenes(p);
        }

        // NOTA: se eliminaron dos consultas por carga de home que no se usaban
        // en la plantilla (findBySku del producto destacado y COUNT(*) total).
        // Cada consulta extra abría una conexión más contra Neon.
        model.addAttribute("productos", productosFiltrados);
        model.addAttribute("categoriaActiva", categoriaActiva);
        model.addAttribute("categorias", categoriasDisponibles);
        model.addAttribute("categoriaDefault", CATEGORIA_DEFAULT);

        return "index";
    }

    private void optimizarImagenes(Producto p) {
        if (p == null) return;
        String[] imagenes = {p.getImagen1(), p.getImagen2(), p.getImagen3(), p.getImagen4(), p.getImagen5(), p.getImagen6()};
        String[] setters = {"setImagen1", "setImagen2", "setImagen3", "setImagen4", "setImagen5", "setImagen6"};
        for (int i = 0; i < imagenes.length; i++) {
            if (imagenes[i] != null && imagenes[i].contains("cloudinary.com")) {
                imagenes[i] = imagenes[i].replace("/upload/", "/upload/f_auto,q_auto/");
                try {
                    p.getClass().getMethod(setters[i], String.class).invoke(p, imagenes[i]);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Detalle público del producto.
     *
     * <p>Una sola ruta cubre los dos formatos:</p>
     * <ul>
     *   <li>{@code /producto/{slug}} → URL canónica, se renderiza.</li>
     *   <li>{@code /producto/{id}} → URL antigua, se responde <strong>301</strong>
     *       hacia la canónica para no perder el posicionamiento.</li>
     * </ul>
     *
     * <p>No se declaran dos {@code @GetMapping} porque competirían por el mismo
     * segmento —ambas coincidirían con {@code /producto/123}— y Spring no
     * garantiza cuál gana. Aquí la decisión es explícita: si es solo dígitos,
     * es un id.</p>
     */
    @GetMapping("/producto/{identificador}")
    public Object detallePublico(@PathVariable String identificador,
                                 Model model,
                                 HttpServletRequest request) {

        if (identificador != null && identificador.matches(PATRON_ID)) {
            return redirigirAlSlug(Long.valueOf(identificador));
        }

        try {
            return renderizar(productoService.obtenerDetalleVistaPorSlug(identificador), model, request);
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/?error=Error al cargar el producto";
        }
    }

    /** 301 permanente desde la ruta antigua por id hacia la URL con slug. */
    private View redirigirAlSlug(Long id) {
        try {
            Producto producto = productoService.obtenerPorId(id);
            String slug = (producto == null) ? null : producto.getSlug();

            if (slug == null || slug.isBlank()) {
                return new RedirectView("/?error=Producto no encontrado", true);
            }

            RedirectView vista = new RedirectView("/producto/" + slug, true);
            vista.setStatusCode(HttpStatus.MOVED_PERMANENTLY);
            vista.setExposeModelAttributes(false);
            return vista;
        } catch (Exception e) {
            return new RedirectView("/?error=Error al cargar el producto", true);
        }
    }

    /**
     * Pinta la ficha con la vista ya resuelta (por id o por slug) y añade todo
     * lo que necesitan las etiquetas meta y el JSON-LD.
     */
    private String renderizar(ProductoService.DetalleProductoVista vista,
                              Model model,
                              HttpServletRequest request) {
        try {
            if (vista == null || vista.producto() == null) {
                return "redirect:/?error=Producto no encontrado";
            }

            Producto producto = vista.producto();
            if (producto.getTieneColor() == null) producto.setTieneColor(false);
            if (producto.getTieneTalla() == null) producto.setTieneTalla(false);
            if (producto.getTieneVariaciones() == null) producto.setTieneVariaciones(false);

            optimizarImagenes(producto);

            Long productoId = producto.getId();

            List<Resena> resenas = resenaService.listarAprobadasPorProducto(productoId);
            model.addAttribute("resenas", resenas);

            // Total y promedio en una sola consulta agregada: antes se
            // inicializaba la colección completa de reseñas del producto
            // (consulta + conexión extra por cada visita).
            ResenaService.EstadisticasResenas estadisticas = resenaService.estadisticasAprobadas(productoId);
            model.addAttribute("promedioCalificacion", estadisticas.promedio());
            model.addAttribute("totalResenas", estadisticas.total());
            model.addAttribute("producto", producto);

            // ============================================================
            // DTOs para el JavaScript de la vista.
            // NUNCA se envía la entidad (Producto -> VarianteProducto ->
            // Producto) porque Thymeleaf/Jackson entra en bucle infinito y
            // rompe todo el <script> de la página.
            // ============================================================
            model.addAttribute("variantes", vista.variantes());
            model.addAttribute("imagenesProducto", vista.imagenes());
            model.addAttribute("imagenesLegacy", vista.imagenesLegacy());
            model.addAttribute("imagenPrincipal", vista.imagenPrincipal());

            // ============================================================
            // SEO: URL canónica, meta descripción y datos estructurados.
            // Todo se calcula con lo que ya está en memoria (los DTO de la
            // vista), sin consultas extra a la base de datos.
            // ============================================================
            model.addAttribute("urlProducto", urlDeProducto(producto));
            model.addAttribute("urlCanonica", request.getRequestURL().toString());
            model.addAttribute("seoDescripcion", descripcionSeo(producto));
            model.addAttribute("seoImagenes", imagenesSeo(vista));
            model.addAttribute("seoPrecio", precioSeo(producto));
            model.addAttribute("seoDisponible", hayStock(vista));

            return "producto-detalle";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/?error=Error al cargar el producto";
        }
    }

    // ============================================================
    // HELPERS DE SEO
    // ============================================================

    /** URL relativa canónica del producto. */
    static String urlDeProducto(Producto producto) {
        String slug = producto.getSlug();
        return (slug == null || slug.isBlank())
                ? "/producto/" + producto.getId()
                : "/producto/" + slug;
    }

    /**
     * Texto real del producto para la meta descripción y el JSON-LD.
     * Se usa {@code descripcionLarga} (o la corta si falta) tal cual está en
     * la base de datos: el contenido legal lo revisa el administrador.
     */
    static String descripcionSeo(Producto producto) {
        String texto = producto.getDescripcionLarga();
        if (texto == null || texto.isBlank()) {
            texto = producto.getDescripcion();
        }
        if (texto == null) {
            return "";
        }
        texto = texto.replaceAll("\\s+", " ").trim();
        return texto.length() <= MAX_DESCRIPCION_SEO
                ? texto
                : texto.substring(0, MAX_DESCRIPCION_SEO - 3).trim() + "...";
    }

    /** Imágenes del producto para schema.org (nueva tabla primero, legacy después). */
    static List<String> imagenesSeo(ProductoService.DetalleProductoVista vista) {
        List<String> urls = new ArrayList<>();

        if (vista.imagenes() != null) {
            for (Map<String, Object> imagen : vista.imagenes()) {
                if (imagen == null) continue;
                Object url = imagen.get("url");
                if (url != null && !String.valueOf(url).isBlank()) {
                    urls.add(String.valueOf(url));
                }
            }
        }

        if (urls.isEmpty() && vista.imagenesLegacy() != null) {
            for (String url : vista.imagenesLegacy()) {
                if (url != null && !url.isBlank()) {
                    urls.add(url);
                }
            }
        }

        return urls;
    }

    /**
     * Precio entero para {@code Offer.price}.
     * schema.org quiere el número sin separadores de miles (-&gt; 89900, no "89.900").
     */
    static long precioSeo(Producto producto) {
        Double precio = producto.getPrecio();
        return precio == null ? 0L : Math.round(precio);
    }

    /** ¿Hay alguna variante activa con stock? Se resuelve con los DTO en memoria. */
    static boolean hayStock(ProductoService.DetalleProductoVista vista) {
        if (vista.variantes() == null || vista.variantes().isEmpty()) {
            return false;
        }

        for (Map<String, Object> variante : vista.variantes()) {
            if (variante == null) continue;

            Object stock = variante.get("stock");
            int unidades = (stock instanceof Number numero) ? numero.intValue() : 0;

            Object activo = variante.get("activo");
            boolean activa = !(activo instanceof Boolean flag) || flag;

            if (unidades > 0 && activa) {
                return true;
            }
        }
        return false;
    }

    @GetMapping("/envios")
    public String envios() {
        return "envios";
    }

    @GetMapping("/seguimiento")
    public String seguimiento() {
        return "seguimiento";
    }

    @GetMapping("/privacidad")
    public String privacidad() {
        return "privacidad";
    }

    @GetMapping("/terminos")
    public String terminos() {
        return "terminos";
    }

    @GetMapping("/contacto")
    public String contacto() {
        return "contacto";
    }

    @GetMapping("/admin-login")
    public String adminLogin() {
        return "admin-login";
    }
}
