package com.flowcolombia.flowcolombia;

import com.flowcolombia.flowcolombia.service.ProductoService;
import com.flowcolombia.flowcolombia.service.PedidoService;
import com.flowcolombia.flowcolombia.service.ResenaService;
import com.flowcolombia.flowcolombia.service.MensajeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ProductoService productoService;
    private final PedidoService pedidoService;
    private final ResenaService resenaService;
    private final MensajeService mensajeService;

    public AdminController(ProductoService productoService,
                           PedidoService pedidoService,
                           ResenaService resenaService,
                           MensajeService mensajeService) {
        this.productoService = productoService;
        this.pedidoService = pedidoService;
        this.resenaService = resenaService;
        this.mensajeService = mensajeService;
    }

    @GetMapping("/panel")
    public String panel(Model model) {
        model.addAttribute("productos", productoService.listarTodos());
        return "admin-panel";
    }

    @GetMapping("/pedidos")
    public String pedidos(@RequestParam(required = false) String estado,
                          @RequestParam(required = false) Long seleccionado,
                          Model model) {
        List<Pedido> pedidos;
        if (estado != null && !estado.isEmpty()) {
            pedidos = pedidoService.listarPorEstado(estado);
        } else {
            pedidos = pedidoService.listarTodosOrdenados();
        }

        Pedido pedidoSeleccionado = null;
        if (seleccionado != null) {
            pedidoSeleccionado = pedidoService.obtenerPorId(seleccionado);
            if (pedidoSeleccionado != null) {
                pedidoSeleccionado.setLeido(true);
                pedidoService.guardar(pedidoSeleccionado);
            }
        }

        long pendientes = pedidoService.contarPorEstado("Pendiente");
        long procesando = pedidoService.contarPorEstado("Procesando");
        long enviados = pedidoService.contarPorEstado("Enviado");
        long entregados = pedidoService.contarPorEstado("Entregado");

        model.addAttribute("pedidos", pedidos);
        model.addAttribute("estadoSeleccionado", estado);
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("procesando", procesando);
        model.addAttribute("enviados", enviados);
        model.addAttribute("entregados", entregados);
        model.addAttribute("pedidoSeleccionado", pedidoSeleccionado);

        return "admin-pedidos";
    }

    @GetMapping("/chats")
    public String chats(Model model) {
        model.addAttribute("mensajes", mensajeService.listarTodos());
        return "admin-chats";
    }

    @PostMapping("/responder/{id}")
    @ResponseBody
    public Map<String, String> responder(@PathVariable Long id, @RequestParam String respuesta) {
        Map<String, String> result = new HashMap<>();
        Mensaje msgOriginal = mensajeService.obtenerPorId(id);
        if (msgOriginal != null) {
            msgOriginal.setRespuesta(respuesta);
            mensajeService.guardar(msgOriginal);

            Mensaje respuestaMsg = new Mensaje();
            respuestaMsg.setCodigoPedido(msgOriginal.getCodigoPedido());
            respuestaMsg.setTelefono(msgOriginal.getTelefono());
            respuestaMsg.setMensaje(respuesta);
            respuestaMsg.setEsCliente(false);
            respuestaMsg.setFecha(LocalDateTime.now());
            mensajeService.guardar(respuestaMsg);
            result.put("status", "OK");
        }
        return result;
    }

    @PostMapping("/cambiar-estado/{id}")
    public String cambiarEstado(@PathVariable Long id,
                                @RequestParam String estado,
                                @RequestParam(required = false) String transportadora,
                                @RequestParam(required = false) String numeroGuia) {
        Pedido pedido = pedidoService.obtenerPorId(id);
        if (pedido != null) {
            pedido.setEstado(estado);
            if (transportadora != null) pedido.setTransportadora(transportadora);
            if (numeroGuia != null) pedido.setNumeroGuia(numeroGuia);
            pedidoService.guardar(pedido);
        }
        return "redirect:/admin/pedidos";
    }

    @GetMapping("/nuevo")
    public String nuevoProducto(Model model) {
        model.addAttribute("producto", new Producto());
        model.addAttribute("resenas", new ArrayList<>());
        return "admin-form";
    }

    @GetMapping("/editar/{id}")
    public String editarProducto(@PathVariable Long id, Model model) {
        Producto producto = productoService.obtenerPorId(id);
        model.addAttribute("producto", producto);
        model.addAttribute("resenas", resenaService.listarPorProducto(id));
        return "admin-form";
    }

    @PostMapping("/guardar")
    public String guardarProducto(@ModelAttribute Producto producto,
                                  @RequestParam(required = false) String imagen1File,
                                  @RequestParam(required = false) String imagen2File,
                                  @RequestParam(required = false) String imagen3File,
                                  @RequestParam(required = false) String imagen4File,
                                  @RequestParam(required = false) String imagen5File,
                                  @RequestParam(required = false) String imagen6File,
                                  @RequestParam(required = false) String coloresInput,
                                  @RequestParam(required = false) String tallasInput,
                                  @RequestParam(required = false) Boolean tieneColor,
                                  @RequestParam(required = false) Boolean tieneTalla,
                                  RedirectAttributes redirect) {

        if (imagen1File != null && !imagen1File.isEmpty()) producto.setImagen1(imagen1File);
        if (imagen2File != null && !imagen2File.isEmpty()) producto.setImagen2(imagen2File);
        if (imagen3File != null && !imagen3File.isEmpty()) producto.setImagen3(imagen3File);
        if (imagen4File != null && !imagen4File.isEmpty()) producto.setImagen4(imagen4File);
        if (imagen5File != null && !imagen5File.isEmpty()) producto.setImagen5(imagen5File);
        if (imagen6File != null && !imagen6File.isEmpty()) producto.setImagen6(imagen6File);

        if (producto.getId() != null) {
            Producto existente = productoService.obtenerPorId(producto.getId());
            if (existente != null) {
                if (imagen1File == null || imagen1File.isEmpty()) producto.setImagen1(existente.getImagen1());
                if (imagen2File == null || imagen2File.isEmpty()) producto.setImagen2(existente.getImagen2());
                if (imagen3File == null || imagen3File.isEmpty()) producto.setImagen3(existente.getImagen3());
                if (imagen4File == null || imagen4File.isEmpty()) producto.setImagen4(existente.getImagen4());
                if (imagen5File == null || imagen5File.isEmpty()) producto.setImagen5(existente.getImagen5());
                if (imagen6File == null || imagen6File.isEmpty()) producto.setImagen6(existente.getImagen6());
            }
        }

        producto.setTieneColor(tieneColor != null && tieneColor);
        producto.setTieneTalla(tieneTalla != null && tieneTalla);

        String coloresStr = (coloresInput != null && !coloresInput.trim().isEmpty()) ? coloresInput.replace("\\s*,\\s*", ", ") : "";
        String tallasStr = (tallasInput != null && !tallasInput.trim().isEmpty()) ? tallasInput.replace("\\s*,\\s*", ", ") : "";

        if (coloresStr.isEmpty() && tallasStr.isEmpty()) {
            producto.setVariacionesDisponibles("|");
        } else {
            producto.setVariacionesDisponibles(coloresStr + "|" + tallasStr);
        }

        productoService.guardar(producto);
        redirect.addFlashAttribute("mensaje", "✅ Producto guardado correctamente!");
        return "redirect:/admin/panel";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminarProducto(@PathVariable Long id, RedirectAttributes redirect) {
        resenaService.eliminarPorProducto(id);
        productoService.eliminar(id);
        redirect.addFlashAttribute("mensaje", "Producto eliminado!");
        return "redirect:/admin/panel";
    }

    @GetMapping("/eliminar-pedido/{id}")
    public String eliminarPedido(@PathVariable Long id, RedirectAttributes redirect) {
        pedidoService.eliminar(id);
        redirect.addFlashAttribute("mensaje", "Pedido eliminado!");
        return "redirect:/admin/pedidos";
    }

    @PostMapping("/guardar-resena")
    public String guardarResena(@RequestParam Long productoId,
                                @RequestParam String nombreCliente,
                                @RequestParam Integer calificacion,
                                @RequestParam String comentario,
                                @RequestParam(required = false) String imagenUrl,
                                RedirectAttributes redirect) {

        Producto producto = productoService.obtenerPorId(productoId);
        if (producto == null) {
            redirect.addFlashAttribute("mensaje", "❌ Producto no encontrado");
            return "redirect:/admin/panel";
        }

        Resena resena = new Resena();
        resena.setProducto(producto);
        resena.setNombreCliente(nombreCliente);
        resena.setCalificacion(calificacion);
        resena.setComentario(comentario);
        resena.setImagenUrl(imagenUrl);
        resena.setFecha(LocalDateTime.now());
        resena.setAprobado(true);

        resenaService.guardar(resena);
        redirect.addFlashAttribute("mensaje", "✅ Reseña agregada correctamente");
        return "redirect:/producto/" + productoId + "?resena=ok#resenasSection";
    }

    @GetMapping("/eliminar-resena/{id}")
    public String eliminarResena(@PathVariable Long id, RedirectAttributes redirect) {
        Resena resena = resenaService.obtenerPorId(id);
        if (resena != null) {
            Long productoId = resena.getProducto().getId();
            resenaService.eliminar(id);
            redirect.addFlashAttribute("mensaje", "Reseña eliminada");
            return "redirect:/admin/editar/" + productoId;
        }
        redirect.addFlashAttribute("mensaje", "Reseña no encontrada");
        return "redirect:/admin/panel";
    }
}