package com.flowcolombia.flowcolombia;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class WebController {

    private final ProductoRepository productoRepository;

    // ============================================================
    // CONFIGURACIÓN DE CATEGORÍAS
    // ============================================================
    // ⚠️ IMPORTANTE: Cambia "Cultivo" por la categoría de tu OFERTA DEL MES
    private static final String CATEGORIA_DEFAULT = "Cultivo";

    // Lista de categorías válidas (deben coincidir con las que usas en admin)
    private static final List<String> CATEGORIAS_VALIDAS = List.of(
            "Cultivo",
            "Hogar",
            "Tecnología",
            "Cuidado Personal"
    );

    // ============================================================
    // CONSTRUCTOR
    // ============================================================
    public WebController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    // ============================================================
    // PÁGINA PRINCIPAL - CON FILTRO POR CATEGORÍA
    // ============================================================
    @GetMapping("/")
    public String home(
            @RequestParam(required = false) String categoria,
            Model model) {

        // 1. Determinar la categoría a mostrar
        String categoriaActiva = CATEGORIA_DEFAULT;
        if (categoria != null && !categoria.equals("Todos") && CATEGORIAS_VALIDAS.contains(categoria)) {
            categoriaActiva = categoria;
        } else if (categoria != null && categoria.equals("Todos")) {
            categoriaActiva = "Todos";
        }

        // 2. Obtener los productos filtrados
        List<Producto> productosFiltrados;
        if (categoriaActiva.equals("Todos")) {
            productosFiltrados = productoRepository.findAll();
        } else {
            // 🔥 AHORA FUNCIONA porque agregamos el método en ProductoRepository
            productosFiltrados = productoRepository.findByCategoria(categoriaActiva);
        }

        // 3. OPTIMIZAR IMÁGENES DE CLOUDINARY AUTOMÁTICAMENTE
        for (Producto p : productosFiltrados) {
            // Optimizar imagen 1
            if (p.getImagen1() != null && p.getImagen1().contains("cloudinary.com")) {
                p.setImagen1(p.getImagen1().replace("/upload/", "/upload/f_auto,q_auto/"));
            }
            // Optimizar imagen 2
            if (p.getImagen2() != null && p.getImagen2().contains("cloudinary.com")) {
                p.setImagen2(p.getImagen2().replace("/upload/", "/upload/f_auto,q_auto/"));
            }
            // Optimizar imagen 3
            if (p.getImagen3() != null && p.getImagen3().contains("cloudinary.com")) {
                p.setImagen3(p.getImagen3().replace("/upload/", "/upload/f_auto,q_auto/"));
            }
            // Optimizar imagen 4
            if (p.getImagen4() != null && p.getImagen4().contains("cloudinary.com")) {
                p.setImagen4(p.getImagen4().replace("/upload/", "/upload/f_auto,q_auto/"));
            }
            // Optimizar imagen 5
            if (p.getImagen5() != null && p.getImagen5().contains("cloudinary.com")) {
                p.setImagen5(p.getImagen5().replace("/upload/", "/upload/f_auto,q_auto/"));
            }
            // Optimizar imagen 6
            if (p.getImagen6() != null && p.getImagen6().contains("cloudinary.com")) {
                p.setImagen6(p.getImagen6().replace("/upload/", "/upload/f_auto,q_auto/"));
            }
        }

        // 4. Pasar los datos a la vista
        model.addAttribute("productos", productosFiltrados);
        model.addAttribute("categoriaActiva", categoriaActiva);
        model.addAttribute("categorias", CATEGORIAS_VALIDAS);
        model.addAttribute("categoriaDefault", CATEGORIA_DEFAULT);

        return "index";
    }

    // ============================================================
    // DETALLE DE PRODUCTO - CON IMÁGENES OPTIMIZADAS
    // ============================================================
    @GetMapping("/producto/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        Producto producto = productoRepository.findById(id).orElse(null);

        // Optimizar imágenes de Cloudinary para el detalle
        if (producto != null) {
            if (producto.getImagen1() != null && producto.getImagen1().contains("cloudinary.com")) {
                producto.setImagen1(producto.getImagen1().replace("/upload/", "/upload/f_auto,q_auto/"));
            }
            if (producto.getImagen2() != null && producto.getImagen2().contains("cloudinary.com")) {
                producto.setImagen2(producto.getImagen2().replace("/upload/", "/upload/f_auto,q_auto/"));
            }
            if (producto.getImagen3() != null && producto.getImagen3().contains("cloudinary.com")) {
                producto.setImagen3(producto.getImagen3().replace("/upload/", "/upload/f_auto,q_auto/"));
            }
            if (producto.getImagen4() != null && producto.getImagen4().contains("cloudinary.com")) {
                producto.setImagen4(producto.getImagen4().replace("/upload/", "/upload/f_auto,q_auto/"));
            }
            if (producto.getImagen5() != null && producto.getImagen5().contains("cloudinary.com")) {
                producto.setImagen5(producto.getImagen5().replace("/upload/", "/upload/f_auto,q_auto/"));
            }
            if (producto.getImagen6() != null && producto.getImagen6().contains("cloudinary.com")) {
                producto.setImagen6(producto.getImagen6().replace("/upload/", "/upload/f_auto,q_auto/"));
            }
        }

        model.addAttribute("producto", producto);
        return "producto-detalle";
    }

    // ============================================================
    // CARRITO
    // ============================================================
    @GetMapping("/carrito")
    public String carrito() {
        return "carrito";
    }

    // ============================================================
    // ENVÍOS
    // ============================================================
    @GetMapping("/envios")
    public String envios() {
        return "envios";
    }

    // ============================================================
    // SEGUIMIENTO
    // ============================================================
    @GetMapping("/seguimiento")
    public String seguimiento() {
        return "seguimiento";
    }

    // ============================================================
    // PRIVACIDAD
    // ============================================================
    @GetMapping("/privacidad")
    public String privacidad() {
        return "privacidad";
    }

    // ============================================================
    // TÉRMINOS
    // ============================================================
    @GetMapping("/terminos")
    public String terminos() {
        return "terminos";
    }

    // ============================================================
    // CONTACTO
    // ============================================================
    @GetMapping("/contacto")
    public String contacto() {
        return "contacto";
    }

    // ============================================================
    // RUTAS DE ADMINISTRACIÓN
    // ============================================================
    @GetMapping("/admin-login")
    public String adminLogin() {
        return "admin-login";
    }

    @GetMapping("/admin-panel")
    public String adminPanel() {
        return "admin-panel";
    }

    @GetMapping("/admin-form")
    public String adminForm() {
        return "admin-form";
    }

    @GetMapping("/admin-pedidos")
    public String adminPedidos() {
        return "admin-pedidos";
    }

    @GetMapping("/admin-chats")
    public String adminChats() {
        return "admin-chats";
    }
}