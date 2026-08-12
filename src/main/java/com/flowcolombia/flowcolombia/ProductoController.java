package com.flowcolombia.flowcolombia;

import com.flowcolombia.flowcolombia.service.ProductoService;
import com.flowcolombia.flowcolombia.service.ResenaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    // PANEL DE PRODUCTOS
    // ============================================================
    @GetMapping("/panel")
    public String panel(Model model) {
        model.addAttribute("productos", productoService.listarTodos());
        return "admin-panel";
    }

    // ============================================================
    // NUEVO PRODUCTO
    // ============================================================
    @GetMapping("/nuevo")
    public String nuevoProducto(Model model) {
        model.addAttribute("producto", new Producto());
        model.addAttribute("resenas", new ArrayList<>());
        return "admin-form";
    }

    // ============================================================
    // EDITAR PRODUCTO
    // ============================================================
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

    // ============================================================
    // GUARDAR PRODUCTO (con manejo robusto de variantes)
    // ============================================================
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
                                  RedirectAttributes redirect) {

        // ============================================================
        // 1. Guardar imágenes (igual que antes)
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
        // 2. Guardar producto base (necesario para tener ID)
        // ============================================================
        productoService.guardar(producto); // Guardamos para obtener ID (si es nuevo)

        // ============================================================
        // 3. Procesar variantes de forma robusta
        // ============================================================
        // Limpiar variantes existentes
        producto.getVariantes().clear();

        // Si no hay SKU, no hay variantes que procesar
        if (varianteSku != null && !varianteSku.isEmpty()) {
            // Determinar el tamaño máximo (basado en la lista de SKU)
            int size = varianteSku.size();

            for (int i = 0; i < size; i++) {
                String sku = varianteSku.get(i);
                // Saltar SKU vacíos o nulos
                if (sku == null || sku.trim().isEmpty()) continue;

                // Obtener cada campo con valores predeterminados si son null
                String color = (varianteColor != null && i < varianteColor.size()) ? varianteColor.get(i) : "";
                String talla = (varianteTalla != null && i < varianteTalla.size()) ? varianteTalla.get(i) : "";

                // Precio: si es null o 0, usar el precio base del producto
                Double precio = producto.getPrecio();
                if (variantePrecio != null && i < variantePrecio.size() && variantePrecio.get(i) != null && variantePrecio.get(i) > 0) {
                    precio = variantePrecio.get(i);
                }

                // Costo: si es null, usar 0
                Double costo = 0.0;
                if (varianteCosto != null && i < varianteCosto.size() && varianteCosto.get(i) != null) {
                    costo = varianteCosto.get(i);
                }

                // Stock: si es null, usar 0
                Integer stock = 0;
                if (varianteStock != null && i < varianteStock.size() && varianteStock.get(i) != null) {
                    stock = varianteStock.get(i);
                }

                // Peso: si es null, usar null
                Double peso = null;
                if (variantePeso != null && i < variantePeso.size() && variantePeso.get(i) != null) {
                    peso = variantePeso.get(i);
                }

                // Activo: si es null, usar true
                Boolean activo = true;
                if (varianteActivo != null && i < varianteActivo.size() && varianteActivo.get(i) != null) {
                    activo = varianteActivo.get(i);
                }

                // Crear y agregar variante
                VarianteProducto v = new VarianteProducto();
                v.setProducto(producto);
                v.setSku(sku.trim());
                v.setColor(color.trim());
                v.setTalla(talla.trim());
                v.setPrecio(precio);
                v.setCosto(costo);
                v.setStock(stock);
                v.setPeso(peso);
                v.setActivo(activo);

                producto.getVariantes().add(v);
            }
        }

        // Guardar producto con las variantes
        productoService.guardar(producto);

        redirect.addFlashAttribute("mensaje", "✅ Producto guardado correctamente!");
        return "redirect:/admin/panel";
    }

    // ============================================================
    // ELIMINAR PRODUCTO
    // ============================================================
    @GetMapping("/eliminar/{id}")
    public String eliminarProducto(@PathVariable Long id, RedirectAttributes redirect) {
        productoService.eliminar(id);
        redirect.addFlashAttribute("mensaje", "✅ Producto eliminado correctamente!");
        return "redirect:/admin/panel";
    }

    // ============================================================
    // GUARDAR RESEÑA
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

    // ============================================================
    // ELIMINAR RESEÑA
    // ============================================================
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