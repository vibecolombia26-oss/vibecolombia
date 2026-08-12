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
                                  RedirectAttributes redirect) {

        // 1. Guardar imágenes (misma lógica que antes)
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

        // 2. Guardar producto y variantes usando el método transaccional
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
                    varianteActivo
            );
            redirect.addFlashAttribute("mensaje", "✅ Producto guardado correctamente!");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensaje", "❌ Error: " + e.getMessage());
            return "redirect:/admin/editar/" + producto.getId();
        } catch (Exception e) {
            redirect.addFlashAttribute("mensaje", "❌ Error al guardar: " + e.getMessage());
            return "redirect:/admin/editar/" + producto.getId();
        }

        return "redirect:/admin/panel";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminarProducto(@PathVariable Long id, RedirectAttributes redirect) {
        productoService.eliminar(id);
        redirect.addFlashAttribute("mensaje", "✅ Producto eliminado correctamente!");
        return "redirect:/admin/panel";
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
}