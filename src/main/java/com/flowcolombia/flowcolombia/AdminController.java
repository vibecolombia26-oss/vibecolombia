package com.flowcolombia.flowcolombia;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ProductoRepository productoRepository;
    private final PedidoRepository pedidoRepository;
    private final MensajeRepository mensajeRepository;
    private String adminPassword = "2026Vibecolombia*";

    public AdminController(ProductoRepository productoRepository, PedidoRepository pedidoRepository, MensajeRepository mensajeRepository) {
        this.productoRepository = productoRepository;
        this.pedidoRepository = pedidoRepository;
        this.mensajeRepository = mensajeRepository;
    }

    @GetMapping("/login") public String login() { return "admin-login"; }

    @PostMapping("/login")
    public String procesarLogin(@RequestParam String password, Model model) {
        if (adminPassword.equals(password)) return "redirect:/admin/panel?key=" + password;
        model.addAttribute("error", "Contraseña incorrecta");
        return "admin-login";
    }

    @GetMapping("/panel")
    public String panel(@RequestParam String key, Model model) {
        if (!adminPassword.equals(key)) return "redirect:/admin/login";
        model.addAttribute("productos", productoRepository.findAll());
        model.addAttribute("key", key);
        return "admin-panel";
    }

    @GetMapping("/pedidos")
    public String pedidos(@RequestParam String key, Model model) {
        if (!adminPassword.equals(key)) return "redirect:/admin/login";
        model.addAttribute("pedidos", pedidoRepository.findAll());
        model.addAttribute("key", key);
        return "admin-pedidos";
    }

    @GetMapping("/chats")
    public String chats(@RequestParam String key, Model model) {
        if (!adminPassword.equals(key)) return "redirect:/admin/login";
        model.addAttribute("mensajes", mensajeRepository.findAll());
        model.addAttribute("key", key);
        return "admin-chats";
    }

    @PostMapping("/responder/{id}")
    @ResponseBody
    public Map<String, String> responder(@PathVariable Long id, @RequestParam String key, @RequestParam String respuesta) {
        Map<String, String> result = new HashMap<>();
        if (!adminPassword.equals(key)) { result.put("error", "No autorizado"); return result; }
        Mensaje msgOriginal = mensajeRepository.findById(id).orElse(null);
        if (msgOriginal != null) {
            msgOriginal.setRespuesta(respuesta);
            mensajeRepository.save(msgOriginal);
            Mensaje respuestaMsg = new Mensaje();
            respuestaMsg.setCodigoPedido(msgOriginal.getCodigoPedido());
            respuestaMsg.setTelefono(msgOriginal.getTelefono());
            respuestaMsg.setMensaje(respuesta);
            respuestaMsg.setEsCliente(false);
            respuestaMsg.setFecha(LocalDateTime.now());
            mensajeRepository.save(respuestaMsg);
            result.put("status", "OK");
        }
        return result;
    }

    @PostMapping("/cambiar-estado/{id}")
    public String cambiarEstado(@PathVariable Long id, @RequestParam String key, @RequestParam String estado,
                                @RequestParam(required = false) String transportadora,
                                @RequestParam(required = false) String numeroGuia) {
        if (!adminPassword.equals(key)) return "redirect:/admin/login";
        Pedido pedido = pedidoRepository.findById(id).orElse(null);
        if (pedido != null) {
            pedido.setEstado(estado);
            if (transportadora != null) pedido.setTransportadora(transportadora);
            if (numeroGuia != null) pedido.setNumeroGuia(numeroGuia);
            pedidoRepository.save(pedido);
        }
        return "redirect:/admin/pedidos?key=" + key;
    }

    @GetMapping("/nuevo")
    public String nuevoProducto(@RequestParam String key, Model model) {
        if (!adminPassword.equals(key)) return "redirect:/admin/login";
        model.addAttribute("producto", new Producto());
        model.addAttribute("key", key);
        return "admin-form";
    }

    @GetMapping("/editar/{id}")
    public String editarProducto(@PathVariable Long id, @RequestParam String key, Model model) {
        if (!adminPassword.equals(key)) return "redirect:/admin/login";
        model.addAttribute("producto", productoRepository.findById(id).orElse(null));
        model.addAttribute("key", key);
        return "admin-form";
    }

    @PostMapping("/guardar")
    public String guardarProducto(@ModelAttribute Producto producto, @RequestParam String key,
                                  @RequestParam(required = false) String imagen1File,
                                  @RequestParam(required = false) String imagen2File,
                                  @RequestParam(required = false) String imagen3File,
                                  @RequestParam(required = false) String imagen4File,
                                  @RequestParam(required = false) String imagen5File,
                                  @RequestParam(required = false) String imagen6File,
                                  RedirectAttributes redirect) {
        if (!adminPassword.equals(key)) return "redirect:/admin/login";
        // Guardar URLs directamente
        if (imagen1File != null && !imagen1File.isEmpty()) producto.setImagen1(imagen1File);
        if (imagen2File != null && !imagen2File.isEmpty()) producto.setImagen2(imagen2File);
        if (imagen3File != null && !imagen3File.isEmpty()) producto.setImagen3(imagen3File);
        if (imagen4File != null && !imagen4File.isEmpty()) producto.setImagen4(imagen4File);
        if (imagen5File != null && !imagen5File.isEmpty()) producto.setImagen5(imagen5File);
        if (imagen6File != null && !imagen6File.isEmpty()) producto.setImagen6(imagen6File);

        // Mantener imágenes existentes si no se cambian
        if (producto.getId() != null) {
            Producto existente = productoRepository.findById(producto.getId()).orElse(null);
            if (existente != null) {
                if (imagen1File == null || imagen1File.isEmpty()) producto.setImagen1(existente.getImagen1());
                if (imagen2File == null || imagen2File.isEmpty()) producto.setImagen2(existente.getImagen2());
                if (imagen3File == null || imagen3File.isEmpty()) producto.setImagen3(existente.getImagen3());
                if (imagen4File == null || imagen4File.isEmpty()) producto.setImagen4(existente.getImagen4());
                if (imagen5File == null || imagen5File.isEmpty()) producto.setImagen5(existente.getImagen5());
                if (imagen6File == null || imagen6File.isEmpty()) producto.setImagen6(existente.getImagen6());
            }
        }
        productoRepository.save(producto);
        redirect.addFlashAttribute("mensaje", "✅ Producto guardado!");
        return "redirect:/admin/panel?key=" + key;
    }

    @GetMapping("/eliminar/{id}")
    public String eliminarProducto(@PathVariable Long id, @RequestParam String key, RedirectAttributes redirect) {
        if (!adminPassword.equals(key)) return "redirect:/admin/login";
        productoRepository.deleteById(id);
        redirect.addFlashAttribute("mensaje", "🗑️ Producto eliminado!");
        return "redirect:/admin/panel?key=" + key;
    }

    @GetMapping("/eliminar-pedido/{id}")
    public String eliminarPedido(@PathVariable Long id, @RequestParam String key, RedirectAttributes redirect) {
        if (!adminPassword.equals(key)) return "redirect:/admin/login";
        pedidoRepository.deleteById(id);
        redirect.addFlashAttribute("mensaje", "🗑️ Pedido eliminado!");
        return "redirect:/admin/pedidos?key=" + key;
    }
}