package com.flowcolombia.flowcolombia;

import com.flowcolombia.flowcolombia.service.ProductoService;
import com.flowcolombia.flowcolombia.service.ResenaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class WebController {

    private final ProductoService productoService;
    private final ResenaService resenaService;

    private static final String CATEGORIA_DEFAULT = "Calzado";

    public WebController(ProductoService productoService, ResenaService resenaService) {
        this.productoService = productoService;
        this.resenaService = resenaService;
    }

    // ============================================================
    // PÁGINA PRINCIPAL
    // ============================================================
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

        Producto productoDestacado = productoService.obtenerPorSku("2169621");
        if (productoDestacado != null) {
            optimizarImagenes(productoDestacado);
        }

        model.addAttribute("productos", productosFiltrados);
        model.addAttribute("categoriaActiva", categoriaActiva);
        model.addAttribute("categorias", categoriasDisponibles);
        model.addAttribute("categoriaDefault", CATEGORIA_DEFAULT);
        model.addAttribute("productoDestacado", productoDestacado);
        model.addAttribute("totalProductos", productoService.contarTodos());

        return "index";
    }

    // ============================================================
    // OPTIMIZACIÓN DE IMÁGENES
    // ============================================================
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

    // ============================================================
    // DETALLE DE PRODUCTO
    // ============================================================
    @GetMapping("/producto/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        try {
            Producto producto = productoService.obtenerPorId(id);
            if (producto == null) {
                return "redirect:/?error=Producto no encontrado";
            }

            if (producto.getTieneColor() == null) producto.setTieneColor(false);
            if (producto.getTieneTalla() == null) producto.setTieneTalla(false);
            if (producto.getTieneVariaciones() == null) producto.setTieneVariaciones(false);

            if (producto.getImagen1() == null) {
                producto.setImagen1("https://res.cloudinary.com/bcpwhn6o/image/upload/v1783699365/1780606143WhatsApp_Image_2026-06-04_at_3.18.44_PM_hygiom.jpg");
            }

            optimizarImagenes(producto);

            List<Resena> resenas = resenaService.listarAprobadasPorProducto(id);
            model.addAttribute("resenas", resenas);
            model.addAttribute("promedioCalificacion", producto.getPromedioCalificacion());
            model.addAttribute("totalResenas", producto.getCantidadResenas());
            model.addAttribute("producto", producto);

            return "producto-detalle";
        } catch (Exception e) {
            return "redirect:/?error=Error al cargar el producto";
        }
    }

    // ============================================================
    // OTRAS PÁGINAS
    // ============================================================
    @GetMapping("/carrito")
    public String carrito() {
        return "carrito";
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

    // ============================================================
    // 🔥 ADMIN LOGIN (ESTE ES EL QUE FALTABA)
    // ============================================================
    @GetMapping("/admin-login")
    public String adminLogin() {
        return "admin-login";
    }
}