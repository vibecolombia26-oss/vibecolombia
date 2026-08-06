package com.flowcolombia.flowcolombia;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class WebController {

    private final ProductoRepository productoRepository;
    private final ResenaRepository resenaRepository;

    private static final String CATEGORIA_DEFAULT = "Calzado";

    public WebController(ProductoRepository productoRepository, ResenaRepository resenaRepository) {
        this.productoRepository = productoRepository;
        this.resenaRepository = resenaRepository;
    }

    @GetMapping("/")
    public String home(@RequestParam(required = false) String categoria, Model model) {
        List<String> categoriasDisponibles = productoRepository.findAll()
                .stream()
                .map(Producto::getCategoria)
                .filter(cat -> cat != null && !cat.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        String categoriaActiva = CATEGORIA_DEFAULT;
        if (categoria != null && categoriasDisponibles.contains(categoria)) {
            categoriaActiva = categoria;
        }

        List<Producto> productosFiltrados;
        if (categoria != null && categoriasDisponibles.contains(categoria)) {
            productosFiltrados = productoRepository.findByCategoria(categoria);
        } else {
            productosFiltrados = productoRepository.findByCategoria(CATEGORIA_DEFAULT);
        }

        for (Producto p : productosFiltrados) {
            optimizarImagenes(p);
        }

        Producto productoDestacado = productoRepository.findBySku("2169621").orElse(null);
        if (productoDestacado != null) {
            optimizarImagenes(productoDestacado);
        }

        model.addAttribute("productos", productosFiltrados);
        model.addAttribute("categoriaActiva", categoriaActiva);
        model.addAttribute("categorias", categoriasDisponibles);
        model.addAttribute("categoriaDefault", CATEGORIA_DEFAULT);
        model.addAttribute("productoDestacado", productoDestacado);
        model.addAttribute("totalProductos", productoRepository.count());

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
            System.out.println("🔍 Buscando producto con ID: " + id);

            Producto producto = productoRepository.findById(id).orElse(null);

            if (producto == null) {
                System.out.println("❌ Producto NO encontrado con ID: " + id);
                return "redirect:/?error=Producto no encontrado";
            }

            System.out.println("✅ Producto encontrado: " + producto.getNombre());
            System.out.println("   Imagen1: " + producto.getImagen1());
            System.out.println("   Precio: " + producto.getPrecio());

            // === VALIDACIÓN DE CAMPOS NULOS ===
            if (producto.getTieneColor() == null) producto.setTieneColor(false);
            if (producto.getTieneTalla() == null) producto.setTieneTalla(false);
            if (producto.getTieneVariaciones() == null) producto.setTieneVariaciones(false);

            if (producto.getImagen1() == null) {
                System.out.println("⚠️ Imagen1 es NULL, asignando imagen de prueba");
                producto.setImagen1("https://res.cloudinary.com/bcpwhn6o/image/upload/v1783699365/1780606143WhatsApp_Image_2026-06-04_at_3.18.44_PM_hygiom.jpg");
            }

            optimizarImagenes(producto);

            List<Resena> resenas = resenaRepository.findByProductoIdAndAprobadoTrueOrderByFechaDesc(id);
            model.addAttribute("resenas", resenas);
            model.addAttribute("promedioCalificacion", producto.getPromedioCalificacion());
            model.addAttribute("totalResenas", producto.getCantidadResenas());
            model.addAttribute("producto", producto);

            System.out.println("📤 Producto enviado a la vista: " + producto.getNombre());
            return "producto-detalle";

        } catch (Exception e) {
            System.err.println("🔥 ERROR en detalle de producto ID " + id + ": " + e.getMessage());
            e.printStackTrace();
            return "redirect:/?error=Error al cargar el producto";
        }
    }

    // Otras rutas...
    @GetMapping("/carrito") public String carrito() { return "carrito"; }
    @GetMapping("/envios") public String envios() { return "envios"; }
    @GetMapping("/seguimiento") public String seguimiento() { return "seguimiento"; }
    @GetMapping("/privacidad") public String privacidad() { return "privacidad"; }
    @GetMapping("/terminos") public String terminos() { return "terminos"; }
    @GetMapping("/contacto") public String contacto() { return "contacto"; }
    @GetMapping("/admin-login") public String adminLogin() { return "admin-login"; }
    @GetMapping("/admin-panel") public String adminPanel() { return "admin-panel"; }
    @GetMapping("/admin-form") public String adminForm() { return "admin-form"; }
    @GetMapping("/admin-pedidos") public String adminPedidos() { return "admin-pedidos"; }
    @GetMapping("/admin-chats") public String adminChats() { return "admin-chats"; }
}