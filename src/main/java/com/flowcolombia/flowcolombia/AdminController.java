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
    private final ResenaRepository resenaRepository;
    private String adminPassword = "FlowColombia2026*";

    public AdminController(ProductoRepository productoRepository,
                           PedidoRepository pedidoRepository,
                           MensajeRepository mensajeRepository,
                           ResenaRepository resenaRepository) {
        this.productoRepository = productoRepository;
        this.pedidoRepository = pedidoRepository;
        this.mensajeRepository = mensajeRepository;
        this.resenaRepository = resenaRepository;
    }

    // ============================================================
    // LOGIN
    // ============================================================
    @GetMapping("/login")
    public String login() { return "admin-login"; }

    @PostMapping("/login")
    public String procesarLogin(@RequestParam String password, Model model) {
        if (adminPassword.equals(password)) return "redirect:/admin/panel?key=" + password;
        model.addAttribute("error", "Contraseña incorrecta");
        return "admin-login";
    }

    // ============================================================
    // PANEL PRINCIPAL
    // ============================================================
    @GetMapping("/panel")
    public String panel(@RequestParam String key, Model model) {
        if (!adminPassword.equals(key)) return "redirect:/admin/login";
        model.addAttribute("productos", productoRepository.findAll());
        model.addAttribute("key", key);
        return "admin-panel";
    }

    // ============================================================
    // PEDIDOS
    // ============================================================
    @GetMapping("/pedidos")
    public String pedidos(@RequestParam String key, Model model) {
        if (!adminPassword.equals(key)) return "redirect:/admin/login";
        model.addAttribute("pedidos", pedidoRepository.findAll());
        model.addAttribute("key", key);
        return "admin-pedidos";
    }

    // ============================================================
    // CHATS
    // ============================================================
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

    // ============================================================
    // CAMBIAR ESTADO DE PEDIDO
    // ============================================================
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

    // ============================================================
    // GESTIÓN DE PRODUCTOS
    // ============================================================
    @GetMapping("/nuevo")
    public String nuevoProducto(@RequestParam String key, Model model) {
        if (!adminPassword.equals(key)) return "redirect:/admin/login";
        model.addAttribute("producto", new Producto());
        model.addAttribute("key", key);
        model.addAttribute("resenas", new ArrayList<>());
        return "admin-form";
    }

    @GetMapping("/editar/{id}")
    public String editarProducto(@PathVariable Long id, @RequestParam String key, Model model) {
        if (!adminPassword.equals(key)) return "redirect:/admin/login";
        Producto producto = productoRepository.findById(id).orElse(null);
        model.addAttribute("producto", producto);
        model.addAttribute("key", key);

        // Cargar reseñas del producto
        if (producto != null) {
            model.addAttribute("resenas", resenaRepository.findByProductoId(producto.getId()));
        } else {
            model.addAttribute("resenas", new ArrayList<>());
        }
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
        // Eliminar reseñas asociadas
        resenaRepository.deleteByProductoId(id);
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

    // ============================================================
    // GESTIÓN DE RESEÑAS (DESDE EL ADMIN)
    // ============================================================
    @PostMapping("/guardar-resena")
    public String guardarResena(@RequestParam String key,
                                @RequestParam Long productoId,
                                @RequestParam String nombreCliente,
                                @RequestParam Integer calificacion,
                                @RequestParam String comentario,
                                @RequestParam(required = false) String imagenUrl,
                                RedirectAttributes redirect) {
        if (!adminPassword.equals(key)) return "redirect:/admin/login";

        Producto producto = productoRepository.findById(productoId).orElse(null);
        if (producto == null) {
            redirect.addFlashAttribute("mensaje", "❌ Producto no encontrado");
            return "redirect:/admin/panel?key=" + key;
        }

        Resena resena = new Resena();
        resena.setProducto(producto);
        resena.setNombreCliente(nombreCliente);
        resena.setCalificacion(calificacion);
        resena.setComentario(comentario);
        resena.setImagenUrl(imagenUrl);
        resena.setFecha(LocalDateTime.now());
        resena.setAprobado(true);

        resenaRepository.save(resena);
        redirect.addFlashAttribute("mensaje", "⭐ Reseña agregada correctamente");
        return "redirect:/admin/editar/" + productoId + "?key=" + key;
    }

    @GetMapping("/eliminar-resena/{id}")
    public String eliminarResena(@PathVariable Long id, @RequestParam String key, RedirectAttributes redirect) {
        if (!adminPassword.equals(key)) return "redirect:/admin/login";

        Resena resena = resenaRepository.findById(id).orElse(null);
        if (resena != null) {
            Long productoId = resena.getProducto().getId();
            resenaRepository.deleteById(id);
            redirect.addFlashAttribute("mensaje", "🗑️ Reseña eliminada");
            return "redirect:/admin/editar/" + productoId + "?key=" + key;
        }
        redirect.addFlashAttribute("mensaje", "❌ Reseña no encontrada");
        return "redirect:/admin/panel?key=" + key;
    }
}