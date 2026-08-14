package com.flowcolombia.flowcolombia;

import com.flowcolombia.flowcolombia.service.ProductoService;
import com.flowcolombia.flowcolombia.service.ResenaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class ProductoController {

    private final ProductoService productoService;
    private final ResenaService resenaService;

    public ProductoController(ProductoService productoService, ResenaService resenaService) {
        this.productoService = productoService;
        this.resenaService = resenaService;
    }

    // ============================================================
    // ADMIN: PANEL, NUEVO, EDITAR, GUARDAR, ELIMINAR
    // ============================================================

    @GetMapping("/panel")
    public String panel(Model model) {
        model.addAttribute("productos", productoService.listarTodos());
        return "admin-panel";
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
        if (producto == null) {
            return "redirect:/admin/panel?error=Producto no encontrado";
        }
        List<Resena> resenas = resenaService.listarPorProducto(id);
        model.addAttribute("producto", producto);
        model.addAttribute("resenas", resenas);
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
                                  @RequestParam(required = false) List<String> varianteSku,
                                  @RequestParam(required = false) List<String> varianteColor,
                                  @RequestParam(required = false) List<String> varianteTalla,
                                  @RequestParam(required = false) List<Double> variantePrecio,
                                  @RequestParam(required = false) List<Double> varianteCosto,
                                  @RequestParam(required = false) List<Integer> varianteStock,
                                  @RequestParam(required = false) List<Double> variantePeso,
                                  @RequestParam(required = false) List<Boolean> varianteActivo,
                                  @RequestParam(required = false) List<Long> varianteId,
                                  RedirectAttributes redirect) {

        // ============================================================
        // 1. ACTUALIZAR IMÁGENES (si se enviaron nuevas)
        // ============================================================
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
        if (imagen1File != null && !imagen1File.isEmpty()) producto.setImagen1(imagen1File);
        if (imagen2File != null && !imagen2File.isEmpty()) producto.setImagen2(imagen2File);
        if (imagen3File != null && !imagen3File.isEmpty()) producto.setImagen3(imagen3File);
        if (imagen4File != null && !imagen4File.isEmpty()) producto.setImagen4(imagen4File);
        if (imagen5File != null && !imagen5File.isEmpty()) producto.setImagen5(imagen5File);
        if (imagen6File != null && !imagen6File.isEmpty()) producto.setImagen6(imagen6File);

        // ============================================================
        // 2. GUARDAR PRODUCTO Y VARIANTES
        // ============================================================
        try {
            productoService.guardarProductoConVariantes(
                    producto,
                    varianteSku,
                    varianteColor,
                    varianteTalla,
                    variantePrecio,
                    varianteCosto,
                    varianteStock,
                    variantePeso,
                    varianteActivo,
                    varianteId
            );
            redirect.addFlashAttribute("mensaje", "✅ Producto guardado correctamente!");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensaje", "❌ Error: " + e.getMessage());
            if (producto.getId() == null) {
                return "redirect:/admin/nuevo";
            } else {
                return "redirect:/admin/editar/" + producto.getId();
            }
        } catch (Exception e) {
            redirect.addFlashAttribute("mensaje", "❌ Ocurrió un error inesperado. Intenta nuevamente.");
            if (producto.getId() == null) {
                return "redirect:/admin/nuevo";
            } else {
                return "redirect:/admin/editar/" + producto.getId();
            }
        }

        return "redirect:/admin/panel";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminarProducto(@PathVariable Long id, RedirectAttributes redirect) {
        productoService.eliminar(id);
        redirect.addFlashAttribute("mensaje", "✅ Producto eliminado correctamente!");
        return "redirect:/admin/panel";
    }

    // ============================================================
    // ADMIN: RESEÑAS
    // ============================================================

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
        return "redirect:/admin/editar/" + productoId;
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

    // ============================================================
    // 🆕 ENDPOINT REST PARA DETALLE DE PRODUCTO (PÚBLICO)
    // ============================================================

    @GetMapping("/api/producto/{id}")
    @ResponseBody
    public Map<String, Object> obtenerProductoDetalle(@PathVariable Long id) {
        try {
            Producto producto = productoService.obtenerPorId(id);
            if (producto == null) {
                return crearRespuestaError("Producto no encontrado");
            }

            Map<String, Object> detalle = new HashMap<>();

            // ============================================================
            // 1. DATOS DEL PRODUCTO
            // ============================================================
            detalle.put("id", producto.getId());
            detalle.put("nombre", producto.getNombre());
            detalle.put("sku", producto.getSku());
            detalle.put("precio", producto.getPrecio());
            detalle.put("categoria", producto.getCategoria());
            detalle.put("descripcion", producto.getDescripcion());
            detalle.put("descripcionLarga", producto.getDescripcionLarga());

            // ============================================================
            // 2. IMÁGENES
            // ============================================================
            List<String> imagenes = new ArrayList<>();
            if (producto.getImagenes() != null && !producto.getImagenes().isEmpty()) {
                imagenes = producto.getImagenes().stream()
                        .map(ProductoImagen::getUrl)
                        .collect(Collectors.toList());
            } else {
                if (producto.getImagen1() != null) imagenes.add(producto.getImagen1());
                if (producto.getImagen2() != null) imagenes.add(producto.getImagen2());
                if (producto.getImagen3() != null) imagenes.add(producto.getImagen3());
                if (producto.getImagen4() != null) imagenes.add(producto.getImagen4());
                if (producto.getImagen5() != null) imagenes.add(producto.getImagen5());
                if (producto.getImagen6() != null) imagenes.add(producto.getImagen6());
            }
            detalle.put("imagenes", imagenes);

            // ============================================================
            // 3. VARIANTES
            // ============================================================
            List<Map<String, Object>> variantes = new ArrayList<>();
            if (producto.getVariantes() != null) {
                for (VarianteProducto v : producto.getVariantes()) {
                    Map<String, Object> vMap = new HashMap<>();
                    vMap.put("sku", v.getSku());
                    vMap.put("color", v.getColor());
                    vMap.put("talla", v.getTalla());
                    vMap.put("precio", v.getPrecio());
                    vMap.put("stock", v.getStock());
                    vMap.put("activo", v.getActivo());
                    variantes.add(vMap);
                }
            }
            detalle.put("variantes", variantes);

            // ============================================================
            // 4. RESEÑAS (solo aprobadas)
            // ============================================================
            List<Resena> resenasAprobadas = resenaService.listarAprobadasPorProducto(id);
            List<Map<String, Object>> resenasList = new ArrayList<>();
            for (Resena r : resenasAprobadas) {
                Map<String, Object> rMap = new HashMap<>();
                rMap.put("nombreCliente", r.getNombreCliente());
                rMap.put("calificacion", r.getCalificacion());
                rMap.put("comentario", r.getComentario());
                rMap.put("fecha", r.getFecha());
                rMap.put("imagenUrl", r.getImagenUrl());
                resenasList.add(rMap);
            }
            detalle.put("resenas", resenasList);

            // ============================================================
            // 5. PROMEDIO Y CANTIDAD
            // ============================================================
            detalle.put("promedioCalificacion", producto.getPromedioCalificacion());
            detalle.put("totalResenas", producto.getCantidadResenas());

            // ============================================================
            // 6. PRODUCTOS RELACIONADOS (optimizado con categoría)
            // ============================================================
            List<Map<String, Object>> relacionados = new ArrayList<>();
            if (producto.getCategoria() != null && !producto.getCategoria().isEmpty()) {
                List<Producto> relacionadosProductos = productoService.obtenerRelacionados(
                        producto.getId(),
                        producto.getCategoria(),
                        5
                );
                for (Producto p : relacionadosProductos) {
                    Map<String, Object> pMap = new HashMap<>();
                    pMap.put("id", p.getId());
                    pMap.put("nombre", p.getNombre());
                    pMap.put("precio", p.getPrecio());
                    pMap.put("imagen", p.getImagen1() != null ? p.getImagen1() : "");
                    relacionados.add(pMap);
                }
            }
            detalle.put("relacionados", relacionados);

            return detalle;

        } catch (Exception e) {
            // Registro del error en logs (se asume logger)
            // logger.error("Error al obtener detalle del producto", e);
            return crearRespuestaError("Error al obtener el detalle del producto");
        }
    }

    private Map<String, Object> crearRespuestaError(String mensaje) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", mensaje);
        return error;
    }
}