package com.flowcolombia.flowcolombia;

import com.flowcolombia.flowcolombia.service.PedidoService;
import com.flowcolombia.flowcolombia.service.MensajeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final PedidoService pedidoService;
    private final MensajeService mensajeService;

    public AdminController(PedidoService pedidoService, MensajeService mensajeService) {
        this.pedidoService = pedidoService;
        this.mensajeService = mensajeService;
    }

    // ============================================================
    // PEDIDOS
    // ============================================================
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

    // ============================================================
    // CAMBIAR ESTADO DE PEDIDO
    // ============================================================
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

    // ============================================================
    // ID DEL PEDIDO EN DROPI (correlador del sync)
    // ============================================================
    /**
     * Guarda el ID que Dropi asignó al pedido.
     *
     * <p>Nuestro {@code FLOW-…} no existe en Dropi, así que este es el único
     * vínculo fiable entre las dos plataformas: el administrador lo pega en el
     * detalle y el job de sincronización pregunta por ese ID exacto. Vaciar el
     * campo lo borra (el pedido queda fuera del sync hasta que se vuelva a
     * vincular).</p>
     */
    @PostMapping("/dropi-id/{id}")
    public String guardarDropiId(@PathVariable Long id,
                                 @RequestParam(required = false) String dropiOrderId,
                                 RedirectAttributes redirect) {
        Pedido pedido = pedidoService.obtenerPorId(id);
        if (pedido == null) {
            redirect.addFlashAttribute("mensaje", "❌ Pedido no encontrado.");
            return "redirect:/admin/pedidos";
        }

        String limpio = dropiOrderId == null ? "" : dropiOrderId.trim();
        pedido.setDropiOrderId(limpio.isEmpty() ? null : limpio);
        pedidoService.guardar(pedido);

        redirect.addFlashAttribute("mensaje", limpio.isEmpty()
                ? "🔗 ID de Dropi borrado del pedido " + pedido.getCodigo() + "."
                : "🔗 ID de Dropi guardado: " + limpio + ".");
        return "redirect:/admin/pedidos?seleccionado=" + id;
    }

    // ============================================================
    // ELIMINAR PEDIDO
    // ============================================================
    // POST (no GET): con GET, un enlace o una imagen en un correo podía
    // borrar pedidos aprovechando la sesión abierta del administrador.
    @PostMapping("/eliminar-pedido/{id}")
    public String eliminarPedido(@PathVariable Long id, RedirectAttributes redirect) {
        pedidoService.eliminar(id);
        redirect.addFlashAttribute("mensaje", "Pedido eliminado!");
        return "redirect:/admin/pedidos";
    }

    // ============================================================
    // CHATS
    // ============================================================
    @GetMapping("/chats")
    public String chats() {
        // La vista carga las conversaciones con una llamada bajo demanda
        // (sin polling). Antes se enviaba aquí la tabla completa de mensajes
        // y además se volvía a pedir por AJAX: dos consultas para lo mismo.
        return "admin-chats";
    }

    // ============================================================
    // RESPONDER MENSAJE (API)
    // ============================================================
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
        } else {
            result.put("error", "Mensaje no encontrado");
        }
        return result;
    }
}