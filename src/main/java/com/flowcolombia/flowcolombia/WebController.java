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

    @GetMapping("/seguimiento")
    public String seguimiento() { return "seguimiento"; }
}