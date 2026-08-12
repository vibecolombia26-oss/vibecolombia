package com.flowcolombia.flowcolombia;

import com.flowcolombia.flowcolombia.service.ProductoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class CarritoController {

    private final ProductoService productoService;

    public CarritoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping("/carrito")
    public String carrito() {
        return "carrito";
    }

    // ============================================================
    // API para obtener detalles de una variante por SKU
    // ============================================================
    @PostMapping("/api/variante/detalle")
    @ResponseBody
    public Map<String, Object> obtenerDetalleVariante(@RequestBody Map<String, String> request) {
        String sku = request.get("sku");
        Map<String, Object> response = new HashMap<>();

        if (sku == null || sku.trim().isEmpty()) {
            response.put("error", "SKU no proporcionado");
            return response;
        }

        VarianteProducto variante = productoService.obtenerVariantePorSku(sku.trim());
        if (variante == null) {
            response.put("error", "Variante no encontrada");
            return response;
        }

        response.put("sku", variante.getSku());
        response.put("precio", variante.getPrecio());
        response.put("stock", variante.getStock());
        response.put("activo", variante.getActivo());
        response.put("productoId", variante.getProducto().getId());
        response.put("nombre", variante.getProducto().getNombre());
        response.put("imagen", variante.getProducto().getImagen1() != null ? variante.getProducto().getImagen1() : "");
        response.put("color", variante.getColor());
        response.put("talla", variante.getTalla());

        return response;
    }
}