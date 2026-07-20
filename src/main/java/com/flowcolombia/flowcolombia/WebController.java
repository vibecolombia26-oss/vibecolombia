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
    private final ProductoService productoService; // Asume que tienes un servicio

    // ============================================================
    // CONSTRUCTOR
    // ============================================================
    public WebController(ProductoRepository productoRepository, ProductoService productoService) {
        this.productoRepository = productoRepository;
        this.productoService = productoService;
    }

    // ============================================================
    // PÁGINA PRINCIPAL - CON CATEGORÍAS DINÁMICAS
    // ============================================================
    @GetMapping("/")
    public String home(
            @RequestParam(required = false) String categoria,
            Model model) {

        // 1. Obtener TODAS las categorías disponibles (desde la BD)
        List<String> categoriasDisponibles = productoRepository.findAll()
                .stream()
                .map(Producto::getCategoria)
                .filter(cat -> cat != null && !cat.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // 2. Determinar la categoría por defecto (la del producto destacado)
        String categoriaDefault = "Cultivo"; // Fallback
        if (!categoriasDisponibles.isEmpty()) {
            // Buscar el producto destacado (el que tiene el SKU de la oferta)
            // Asumimos que el producto con SKU "2169621" es el destacado
            Producto productoDestacado = productoRepository.findBySku("2169621").orElse(null);
            if (productoDestacado != null && productoDestacado.getCategoria() != null) {
                categoriaDefault = productoDestacado.getCategoria();
            } else {
                // Si no hay SKU específico, usa la primera categoría
                categoriaDefault = categoriasDisponibles.get(0);
            }
        }

        // 3. Determinar la categoría activa
        String categoriaActiva = categoriaDefault;
        if (categoria != null && !categoria.equals("Todos") && categoriasDisponibles.contains(categoria)) {
            categoriaActiva = categoria;
        } else if (categoria != null && categoria.equals("Todos")) {
            categoriaActiva = "Todos";
        }

        // 4. Obtener los productos filtrados
        List<Producto> productosFiltrados;
        if (categoriaActiva.equals("Todos")) {
            productosFiltrados = productoRepository.findAll();
        } else {
            productosFiltrados = productoRepository.findByCategoria(categoriaActiva);
        }

        // 5. OPTIMIZAR IMÁGENES DE CLOUDINARY
        for (Producto p : productosFiltrados) {
            optimizarImagenes(p);
        }

        // 6. Obtener el producto destacado (para el banner)
        Producto productoDestacado = productoRepository.findBySku("2169621").orElse(null);
        if (productoDestacado != null) {
            optimizarImagenes(productoDestacado);
        }

        // 7. Pasar los datos a la vista
        model.addAttribute("productos", productosFiltrados);
        model.addAttribute("categoriaActiva", categoriaActiva);
        model.addAttribute("categorias", categoriasDisponibles);
        model.addAttribute("categoriaDefault", categoriaDefault);
        model.addAttribute("productoDestacado", productoDestacado);
        model.addAttribute("totalProductos", productoRepository.count());

        return "index";
    }

    // ============================================================
    // MÉTODO PARA OPTIMIZAR IMÁGENES
    // ============================================================
    private void optimizarImagenes(Producto p) {
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
    // DETALLE DE PRODUCTO
    // ============================================================
    @GetMapping("/producto/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        Producto producto = productoRepository.findById(id).orElse(null);
        if (producto != null) {
            optimizarImagenes(producto);
        }
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

    // Rutas de administración
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