package com.flowcolombia.flowcolombia;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class WebController {

    private final ProductoRepository productoRepository;

    public WebController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("productos", productoRepository.findAll());
        return "index";
    }

    @GetMapping("/producto/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        model.addAttribute("producto", productoRepository.findById(id).orElse(null));
        return "producto-detalle";
    }

    @GetMapping("/carrito")
    public String carrito() { return "carrito"; }

    @GetMapping("/envios")
    public String envios() {
        return "envios";
    }

    @GetMapping("/seguimiento")
    public String seguimiento() { return "seguimiento"; }

    @GetMapping("/privacidad")
    public String privacidad() { return "privacidad"; }

    @GetMapping("/terminos")
    public String terminos() { return "terminos"; }

    @GetMapping("/contacto")
    public String contacto() { return "contacto"; }

    // ============================================================
    // RUTAS DE ADMINISTRACIÓN (NUEVAS)
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