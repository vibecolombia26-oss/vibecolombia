package com.flowcolombia.flowcolombia;

import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
public class PedidoController {

    private final PedidoRepository pedidoRepository;
    private final MensajeRepository mensajeRepository;
    private final EmailService emailService;

    public PedidoController(PedidoRepository pedidoRepository, MensajeRepository mensajeRepository, EmailService emailService) {
        this.pedidoRepository = pedidoRepository;
        this.mensajeRepository = mensajeRepository;
        this.emailService = emailService;
    }

    @PostMapping("/api/pedido")
    @ResponseBody
    public Map<String, String> guardarPedido(@RequestBody Pedido pedido) {
        // 🔍 LOGS DE DEPURACIÓN
        System.out.println("=========================================");
        System.out.println("📦 NUEVO PEDIDO RECIBIDO:");
        System.out.println("  Cliente: " + pedido.getNombreCliente());
        System.out.println("  Teléfono: " + pedido.getTelefono());
        System.out.println("  Total recibido: " + pedido.getTotal());
        System.out.println("  Productos: " + pedido.getProductos());
        System.out.println("=========================================");

        // Establecer valores por defecto
        pedido.setEstado("Pendiente");
        pedido.setCodigo("FLOW-" + System.currentTimeMillis());
        pedido.setFecha(LocalDateTime.now());

        // ✅ Asegurar que el total no sea null
        if (pedido.getTotal() == null) {
            System.out.println("⚠️ Total es NULL, asignando 0.0");
            pedido.setTotal(0.0);
        }

        // ✅ Asegurar que productos no sea null
        if (pedido.getProductos() == null || pedido.getProductos().isEmpty()) {
            System.out.println("⚠️ Productos está vacío");
            pedido.setProductos("Productos no especificados");
        }

        // Guardar el pedido
        Pedido pedidoGuardado = pedidoRepository.save(pedido);
        System.out.println("✅ Pedido guardado con ID: " + pedidoGuardado.getId());
        System.out.println("   Total guardado: " + pedidoGuardado.getTotal());
        System.out.println("   Productos guardados: " + pedidoGuardado.getProductos());
        System.out.println("=========================================");

        // Enviar correo si hay email
        if (pedido.getEmail() != null && !pedido.getEmail().isEmpty()) {
            try {
                emailService.enviarConfirmacion(pedido.getEmail(), pedido.getNombreCliente(), pedido.getProductos(), pedido.getTotal());
            } catch (Exception e) {
                System.err.println("❌ Error al enviar correo: " + e.getMessage());
            }
        }

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
        if (pedido == null) {
            response.put("error", "Pedido no encontrado");
        } else {
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
        if (pedido == null) {
            response.put("error", "Pedido no encontrado");
        } else {
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
        if (codigo != null && !codigo.isEmpty()) {
            return mensajeRepository.findByCodigoPedidoOrderByFechaAsc(codigo);
        }
        return List.of();
    }

    @GetMapping("/api/chat/todos")
    @ResponseBody
    public List<Mensaje> obtenerTodosMensajes() {
        return mensajeRepository.findAll();
    }
}