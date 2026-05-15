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
    private static final String UPLOAD_DIR_RENDER = "/opt/render/project/src/src/main/resources/static/images/";

    public AdminController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @GetMapping("/login")
    public String login() {
        return "admin-login";
    }

    @PostMapping("/login")
    public String procesarLogin(@RequestParam String password, Model model) {
        if (adminPassword.equals(password)) {
            return "redirect:/admin/panel?key=" + password;
        }
        model.addAttribute("error", "Contraseña incorrecta");
        return "admin-login";
    }

    @GetMapping("/panel")
    public String panel(@RequestParam String key, Model model) {
        if (!adminPassword.equals(key)) {
            return "redirect:/admin/login";
        }
        model.addAttribute("productos", productoRepository.findAll());
        model.addAttribute("key", key);
        return "admin-panel";
    }

    @GetMapping("/nuevo")
    public String nuevoProducto(@RequestParam String key, Model model) {
        if (!adminPassword.equals(key)) {
            return "redirect:/admin/login";
        }
        model.addAttribute("producto", new Producto());
        model.addAttribute("key", key);
        return "admin-form";
    }

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

    @PostMapping("/guardar")
    public String guardarProducto(@ModelAttribute Producto producto,
                                  @RequestParam String key,
                                  @RequestParam(required = false) MultipartFile imagen1File,
                                  @RequestParam(required = false) MultipartFile imagen2File,
                                  @RequestParam(required = false) MultipartFile imagen3File,
                                  @RequestParam(required = false) MultipartFile imagen4File,
                                  @RequestParam(required = false) MultipartFile imagen5File,
                                  @RequestParam(required = false) MultipartFile imagen6File,
                                  @RequestParam(required = false) MultipartFile imagen7File,
                                  @RequestParam(required = false) MultipartFile imagen8File,
                                  @RequestParam(required = false) MultipartFile imagen9File,
                                  @RequestParam(required = false) MultipartFile imagen10File,
                                  RedirectAttributes redirect) {
        if (!adminPassword.equals(key)) {
            return "redirect:/admin/login";
        }

        try {
            String uploadPath = Files.exists(Paths.get(UPLOAD_DIR)) ? UPLOAD_DIR : UPLOAD_DIR_RENDER;
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) uploadDir.mkdirs();

            MultipartFile[] imagenes = {imagen1File, imagen2File, imagen3File, imagen4File, imagen5File,
                    imagen6File, imagen7File, imagen8File, imagen9File, imagen10File};

            if (producto.getId() != null) {
                Producto existente = productoRepository.findById(producto.getId()).orElse(null);
                if (existente != null) {
                    for (int i = 0; i < 10; i++) {
                        if (imagenes[i] == null || imagenes[i].isEmpty()) {
                            try {
                                java.lang.reflect.Method getter = Producto.class.getMethod("getImagen" + (i + 1));
                                java.lang.reflect.Method setter = Producto.class.getMethod("setImagen" + (i + 1), String.class);
                                Object valor = getter.invoke(existente);
                                setter.invoke(producto, valor);
                            } catch (Exception e) { }
                        }
                    }
                }
            }

            for (int i = 0; i < 10; i++) {
                if (imagenes[i] != null && !imagenes[i].isEmpty()) {
                    String filename = UUID.randomUUID().toString() + "_" + imagenes[i].getOriginalFilename();
                    Path path = Paths.get(uploadPath + filename);
                    Files.write(path, imagenes[i].getBytes());
                    try {
                        java.lang.reflect.Method setter = Producto.class.getMethod("setImagen" + (i + 1), String.class);
                        setter.invoke(producto, "/images/" + filename);
                    } catch (Exception e) { }
                }
            }

            productoRepository.save(producto);
            redirect.addFlashAttribute("mensaje", "✅ Producto guardado exitosamente!");
        } catch (IOException e) {
            redirect.addFlashAttribute("error", "❌ Error al subir imágenes: " + e.getMessage());
        }

        return "redirect:/admin/panel?key=" + key;
    }

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