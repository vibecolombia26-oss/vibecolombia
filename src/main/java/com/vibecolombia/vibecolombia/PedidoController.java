package com.vibecolombia.vibecolombia;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller
public class PedidoController {

    private final PedidoRepository pedidoRepository;
    private final MensajeRepository mensajeRepository;

    public PedidoController(PedidoRepository pedidoRepository, MensajeRepository mensajeRepository) {
        this.pedidoRepository = pedidoRepository;
        this.mensajeRepository = mensajeRepository;
    }

    @PostMapping("/api/pedido")
    @ResponseBody
    public Map<String, String> guardarPedido(@RequestBody Pedido pedido) {
        pedido.setEstado("Pendiente");
        pedido.setCodigo("VB-" + System.currentTimeMillis());
        pedidoRepository.save(pedido);
        Map<String, String> response = new HashMap<>();
        response.put("codigo", pedido.getCodigo());
        response.put("status", "OK");
        return response;
    }

    @GetMapping("/api/pedido/{codigo}")
    @ResponseBody
    public Map<String, Object> buscarPedido(@PathVariable String codigo) {
        Pedido pedido = pedidoRepository.findAll().stream()
                .filter(p -> codigo.equals(p.getCodigo())).findFirst().orElse(null);
        Map<String, Object> response = new HashMap<>();
        if (pedido == null) { response.put("error", "Pedido no encontrado"); }
        else {
            response.put("codigo", pedido.getCodigo());
            response.put("estado", pedido.getEstado());
            response.put("productos", pedido.getProductos());
            response.put("total", pedido.getTotal());
            response.put("transportadora", pedido.getTransportadora());
            response.put("numeroGuia", pedido.getNumeroGuia());
            response.put("telefono", pedido.getTelefono());
        }
        return response;
    }

    @GetMapping("/api/pedido/telefono/{telefono}")
    @ResponseBody
    public Map<String, Object> buscarPorTelefono(@PathVariable String telefono) {
        Pedido pedido = pedidoRepository.findAll().stream()
                .filter(p -> telefono.equals(p.getTelefono())).findFirst().orElse(null);
        Map<String, Object> response = new HashMap<>();
        if (pedido == null) { response.put("error", "Pedido no encontrado"); }
        else {
            response.put("codigo", pedido.getCodigo());
            response.put("estado", pedido.getEstado());
            response.put("productos", pedido.getProductos());
            response.put("transportadora", pedido.getTransportadora());
            response.put("numeroGuia", pedido.getNumeroGuia());
            response.put("telefono", pedido.getTelefono());
        }
        return response;
    }

    @PostMapping("/api/chat/enviar")
    @ResponseBody
    public Map<String, String> enviarMensaje(@RequestBody Map<String, String> body) {
        Mensaje msg = new Mensaje(body.get("codigo"), body.get("telefono"), body.get("mensaje"), true);
        mensajeRepository.save(msg);
        Map<String, String> response = new HashMap<>();
        response.put("status", "OK");
        return response;
    }

    @GetMapping("/api/chat/mensajes")
    @ResponseBody
    public List<Mensaje> obtenerMensajes(@RequestParam(required = false) String codigo) {
        if (codigo != null && !codigo.isEmpty()) return mensajeRepository.findByCodigoPedidoOrderByFechaAsc(codigo);
        return List.of();
    }

    @GetMapping("/api/chat/todos")
    @ResponseBody
    public List<Mensaje> obtenerTodosMensajes() {
        return mensajeRepository.findAll();
    }
}