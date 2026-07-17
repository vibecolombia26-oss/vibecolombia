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
            productosFiltrados = productoRepository.findByCategoria(categoriaActiva);
        }

        // 3. Pasar los datos a la vista
        model.addAttribute("productos", productosFiltrados);
        model.addAttribute("categoriaActiva", categoriaActiva);
        model.addAttribute("categorias", CATEGORIAS_VALIDAS);
        model.addAttribute("categoriaDefault", CATEGORIA_DEFAULT);

        return "index";
    }

    // ============================================================
    // DETALLE DE PRODUCTO
    // ============================================================
    @GetMapping("/producto/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        model.addAttribute("producto", productoRepository.findById(id).orElse(null));
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