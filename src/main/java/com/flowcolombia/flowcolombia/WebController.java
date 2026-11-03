package com.flowcolombia.flowcolombia;

import com.flowcolombia.flowcolombia.service.ProductoService;
import com.flowcolombia.flowcolombia.service.ResenaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import com.flowcolombia.flowcolombia.Producto;
import com.flowcolombia.flowcolombia.Resena;

@Controller
public class WebController {

    private final ProductoService productoService;
    private final ResenaService resenaService;

    private static final String CATEGORIA_DEFAULT = "Calzado";

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

    @GetMapping("/producto/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        try {
            // El servicio carga imágenes + variantes (eager, dentro de la
            // transacción) y devuelve además DTOs planos para la vista.
            ProductoService.DetalleProductoVista vista = productoService.obtenerDetalleVista(id);
            if (vista == null) {
                return "redirect:/?error=Producto no encontrado";
            }

            Producto producto = vista.producto();
            if (producto.getTieneColor() == null) producto.setTieneColor(false);
            if (producto.getTieneTalla() == null) producto.setTieneTalla(false);
            if (producto.getTieneVariaciones() == null) producto.setTieneVariaciones(false);

            optimizarImagenes(producto);

            List<Resena> resenas = resenaService.listarAprobadasPorProducto(id);
            model.addAttribute("resenas", resenas);

            // Total y promedio en una sola consulta agregada: antes se
            // inicializaba la colección completa de reseñas del producto
            // (consulta + conexión extra por cada visita).
            ResenaService.EstadisticasResenas estadisticas = resenaService.estadisticasAprobadas(id);
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

            return "producto-detalle";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/?error=Error al cargar el producto";
        }
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