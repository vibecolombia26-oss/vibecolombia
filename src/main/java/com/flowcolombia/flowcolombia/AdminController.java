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
    // ELIMINAR PEDIDO
    // ============================================================
    @GetMapping("/eliminar-pedido/{id}")
    public String eliminarPedido(@PathVariable Long id, RedirectAttributes redirect) {
        pedidoService.eliminar(id);
        redirect.addFlashAttribute("mensaje", "Pedido eliminado!");
        return "redirect:/admin/pedidos";
    }

    // ============================================================
    // CHATS
    // ============================================================
    @GetMapping("/chats")
    public String chats(Model model) {
        model.addAttribute("mensajes", mensajeService.listarTodos());
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