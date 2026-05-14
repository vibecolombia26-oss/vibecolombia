package com.vibecolombia.vibecolombia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ProductoRepository productoRepository;

    @Value("${admin.password}")
    private String adminPassword;

    private static final String UPLOAD_DIR = System.getProperty("user.dir") + "/src/main/resources/static/images/";

    // Para Render (producción)
    private static final String UPLOAD_DIR_RENDER = "/opt/render/project/src/src/main/resources/static/images/";

    public AdminController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    // Página de login
    @GetMapping("/login")
    public String login() {
        return "admin-login";
    }

    // Procesar login
    @PostMapping("/login")
    public String procesarLogin(@RequestParam String password, Model model) {
        if (adminPassword.equals(password)) {
            model.addAttribute("password", password);
            return "redirect:/admin/panel?key=" + password;
        }
        model.addAttribute("error", "Contraseña incorrecta");
        return "admin-login";
    }

    // Panel principal
    @GetMapping("/panel")
    public String panel(@RequestParam String key, Model model) {
        if (!adminPassword.equals(key)) {
            return "redirect:/admin/login";
        }
        model.addAttribute("productos", productoRepository.findAll());
        model.addAttribute("key", key);
        return "admin-panel";
    }

    // Formulario para nuevo producto
    @GetMapping("/nuevo")
    public String nuevoProducto(@RequestParam String key, Model model) {
        if (!adminPassword.equals(key)) {
            return "redirect:/admin/login";
        }
        model.addAttribute("producto", new Producto());
        model.addAttribute("key", key);
        return "admin-form";
    }

    // Formulario para editar
    @GetMapping("/editar/{id}")
    public String editarProducto(@PathVariable Long id, @RequestParam String key, Model model) {
        if (!adminPassword.equals(key)) {
            return "redirect:/admin/login";
        }
        Producto producto = productoRepository.findById(id).orElse(null);
        model.addAttribute("producto", producto);
        model.addAttribute("key", key);
        return "admin-form";
    }

    // Guardar producto (nuevo o editado)
    @PostMapping("/guardar")
    public String guardarProducto(@ModelAttribute Producto producto,
                                  @RequestParam String key,
                                  @RequestParam(required = false) MultipartFile imagen1File,
                                  @RequestParam(required = false) MultipartFile imagen2File,
                                  RedirectAttributes redirect) {
        if (!adminPassword.equals(key)) {
            return "redirect:/admin/login";
        }

        try {
            String uploadPath = Files.exists(Paths.get(UPLOAD_DIR)) ? UPLOAD_DIR : UPLOAD_DIR_RENDER;
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) uploadDir.mkdirs();

            if (imagen1File != null && !imagen1File.isEmpty()) {
                String filename1 = UUID.randomUUID().toString() + "_" + imagen1File.getOriginalFilename();
                Path path1 = Paths.get(uploadPath + filename1);
                Files.write(path1, imagen1File.getBytes());
                producto.setImagen1("/images/" + filename1);
            }

            if (imagen2File != null && !imagen2File.isEmpty()) {
                String filename2 = UUID.randomUUID().toString() + "_" + imagen2File.getOriginalFilename();
                Path path2 = Paths.get(uploadPath + filename2);
                Files.write(path2, imagen2File.getBytes());
                producto.setImagen2("/images/" + filename2);
            }

            // Si no se subió imagen, mantener las existentes
            if (producto.getId() != null && (imagen1File == null || imagen1File.isEmpty())) {
                Producto existente = productoRepository.findById(producto.getId()).orElse(null);
                if (existente != null) {
                    if (producto.getImagen1() == null) producto.setImagen1(existente.getImagen1());
                    if (producto.getImagen2() == null) producto.setImagen2(existente.getImagen2());
                }
            }

            productoRepository.save(producto);
            redirect.addFlashAttribute("mensaje", "✅ Producto guardado exitosamente!");
        } catch (IOException e) {
            redirect.addFlashAttribute("error", "❌ Error al subir imágenes: " + e.getMessage());
        }

        return "redirect:/admin/panel?key=" + key;
    }

    // Eliminar producto
    @GetMapping("/eliminar/{id}")
    public String eliminarProducto(@PathVariable Long id, @RequestParam String key, RedirectAttributes redirect) {
        if (!adminPassword.equals(key)) {
            return "redirect:/admin/login";
        }
        productoRepository.deleteById(id);
        redirect.addFlashAttribute("mensaje", "🗑️ Producto eliminado!");
        return "redirect:/admin/panel?key=" + key;
    }
}