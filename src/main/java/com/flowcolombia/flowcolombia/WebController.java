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

    // Categoría por defecto: Calzado
    private static final String CATEGORIA_DEFAULT = "Calzado";

    public WebController(ProductoRepository productoRepository, ResenaRepository resenaRepository) {
        this.productoRepository = productoRepository;
        this.resenaRepository = resenaRepository;
    }

    // ============================================================
    // PÁGINA PRINCIPAL - SOLO CATEGORÍAS (sin "Todos")
    // ============================================================
    @GetMapping("/")
    public String home(@RequestParam(required = false) String categoria, Model model) {
        // Obtener todas las categorías disponibles desde la BD
        List<String> categoriasDisponibles = productoRepository.findAll()
                .stream()
                .map(Producto::getCategoria)
                .filter(cat -> cat != null && !cat.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // Determinar la categoría activa (por defecto "Calzado")
        String categoriaActiva = CATEGORIA_DEFAULT;
        if (categoria != null && categoriasDisponibles.contains(categoria)) {
            categoriaActiva = categoria;
        }

        // Obtener productos filtrados (solo por categoría, nunca "Todos")
        List<Producto> productosFiltrados;
        if (categoria != null && categoriasDisponibles.contains(categoria)) {
            productosFiltrados = productoRepository.findByCategoria(categoria);
        } else {
            // Por defecto, mostrar la categoría configurada (Calzado)
            productosFiltrados = productoRepository.findByCategoria(CATEGORIA_DEFAULT);
        }

        // Optimizar imágenes de Cloudinary
        for (Producto p : productosFiltrados) {
            optimizarImagenes(p);
        }

        // Producto destacado (opcional)
        Producto productoDestacado = productoRepository.findBySku("2169621").orElse(null);
        if (productoDestacado != null) {
            optimizarImagenes(productoDestacado);
        }

        // Pasar datos a la vista
        model.addAttribute("productos", productosFiltrados);
        model.addAttribute("categoriaActiva", categoriaActiva);
        model.addAttribute("categorias", categoriasDisponibles);
        model.addAttribute("categoriaDefault", CATEGORIA_DEFAULT);
        model.addAttribute("productoDestacado", productoDestacado);
        model.addAttribute("totalProductos", productoRepository.count());

        return "index";
    }

    // ============================================================
    // MÉTODO PARA OPTIMIZAR IMÁGENES DE CLOUDINARY
    // ============================================================
    private void optimizarImagenes(Producto p) {
        if (p == null) return;

        if (p.getImagen1() != null && p.getImagen1().contains("cloudinary.com")) {
            p.setImagen1(p.getImagen1().replace("/upload/", "/upload/f_auto,q_auto/"));
        }
        if (p.getImagen2() != null && p.getImagen2().contains("cloudinary.com")) {
            p.setImagen2(p.getImagen2().replace("/upload/", "/upload/f_auto,q_auto/"));
        }
        if (p.getImagen3() != null && p.getImagen3().contains("cloudinary.com")) {
            p.setImagen3(p.getImagen3().replace("/upload/", "/upload/f_auto,q_auto/"));
        }
        if (p.getImagen4() != null && p.getImagen4().contains("cloudinary.com")) {
            p.setImagen4(p.getImagen4().replace("/upload/", "/upload/f_auto,q_auto/"));
        }
        if (p.getImagen5() != null && p.getImagen5().contains("cloudinary.com")) {
            p.setImagen5(p.getImagen5().replace("/upload/", "/upload/f_auto,q_auto/"));
        }
        if (p.getImagen6() != null && p.getImagen6().contains("cloudinary.com")) {
            p.setImagen6(p.getImagen6().replace("/upload/", "/upload/f_auto,q_auto/"));
        }
    }

    // ============================================================
    // DETALLE DE PRODUCTO - CON RESEÑAS
    // ============================================================
    @GetMapping("/producto/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        System.out.println("🔍 Buscando producto con ID: " + id);

        Producto producto = productoRepository.findById(id).orElse(null);

        if (producto == null) {
            System.out.println("❌ Producto no encontrado con ID: " + id);
            return "redirect:/?error=Producto no encontrado";
        }

        System.out.println("✅ Producto encontrado: " + producto.getNombre());

        optimizarImagenes(producto);

        // Cargar reseñas del producto
        List<Resena> resenas = resenaRepository.findByProductoIdAndAprobadoTrueOrderByFechaDesc(id);
        model.addAttribute("resenas", resenas);
        model.addAttribute("promedioCalificacion", producto.getPromedioCalificacion());
        model.addAttribute("totalResenas", producto.getCantidadResenas());
        model.addAttribute("producto", producto);

        return "producto-detalle";
    }

    // ============================================================
    // OTRAS RUTAS
    // ============================================================
    @GetMapping("/carrito")
    public String carrito() { return "carrito"; }

    @GetMapping("/envios")
    public String envios() { return "envios"; }

    @GetMapping("/seguimiento")
    public String seguimiento() { return "seguimiento"; }

    @GetMapping("/privacidad")
    public String privacidad() { return "privacidad"; }

    @GetMapping("/terminos")
    public String terminos() { return "terminos"; }

    @GetMapping("/contacto")
    public String contacto() { return "contacto"; }

    // ============================================================
    // RUTAS DE ADMINISTRACIÓN
    // ============================================================
    @GetMapping("/admin-login")
    public String adminLogin() { return "admin-login"; }

    @GetMapping("/admin-panel")
    public String adminPanel() { return "admin-panel"; }

    @GetMapping("/admin-form")
    public String adminForm() { return "admin-form"; }

    @GetMapping("/admin-pedidos")
    public String adminPedidos() { return "admin-pedidos"; }

    @GetMapping("/admin-chats")
    public String adminChats() { return "admin-chats"; }
}