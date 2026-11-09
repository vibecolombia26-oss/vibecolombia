package com.flowcolombia.flowcolombia;

import com.flowcolombia.flowcolombia.service.AnalyticsService;
import com.flowcolombia.flowcolombia.service.MensajeService;
import com.flowcolombia.flowcolombia.service.PedidoService;
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
    private final PedidoService pedidoService;
    private final MensajeService mensajeService;
    private final AnalyticsService analyticsService;

    public ProductoController(ProductoService productoService,
                              ResenaService resenaService,
                              PedidoService pedidoService,
                              MensajeService mensajeService,
                              AnalyticsService analyticsService) {
        this.productoService = productoService;
        this.resenaService = resenaService;
        this.pedidoService = pedidoService;
        this.mensajeService = mensajeService;
        this.analyticsService = analyticsService;
    }

    // ============================================================
    // ADMIN: PANEL, NUEVO, EDITAR, GUARDAR, ELIMINAR
    // ============================================================

    @GetMapping("/panel")
    public String panel(Model model) {
        model.addAttribute("productos", productoService.listarTodos());

        // ============================================================
        // Contadores reales para el dashboard (una consulta por tarjeta).
        // No cambian la lógica de negocio: solo agregan los totales.
        // ============================================================
        model.addAttribute("pedidosPendientes", pedidoService.contarPorEstado("Pendiente"));
        model.addAttribute("pedidosProcesando", pedidoService.contarPorEstado("Procesando"));
        model.addAttribute("pedidosEnviados", pedidoService.contarPorEstado("Enviado"));
        model.addAttribute("pedidosEntregados", pedidoService.contarPorEstado("Entregado"));
        model.addAttribute("ingresosEntregados", pedidoService.sumarIngresosEntregados());
        model.addAttribute("mensajesSinResponder", mensajeService.contarSinResponder());

        // ============================================================
        // RENTABILIDAD (3 consultas agregadas, sin N+1)
        // - gananciaEntregada: dinero real ya entregado menos costo de proveedor
        // - margenCatalogo: promedio de venta menos promedio de costo
        // - margenesPorProducto: margen de cada producto para la tabla
        // ============================================================
        model.addAttribute("gananciaEntregada", analyticsService.gananciaEntregada());
        model.addAttribute("margenCatalogo", analyticsService.margenCatalogo());
        model.addAttribute("margenesPorProducto", analyticsService.margenPorProducto());

        return "admin-panel";
    }

    @GetMapping("/nuevo")
    public String nuevoProducto(Model model) {
        Producto producto = new Producto();
        model.addAttribute("producto", producto);
        model.addAttribute("imagenesTexto", textoImagenes(producto));
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
        model.addAttribute("imagenesTexto", textoImagenes(producto));
        model.addAttribute("resenas", resenas);
        return "admin-form";
    }

    /**
     * Concatena imagen1..imagen6 (solo las que tienen contenido) para
     * precargar el textarea de URLs del formulario, una por línea.
     */
    private String textoImagenes(Producto producto) {
        if (producto == null) {
            return "";
        }
        String[] urls = {
                producto.getImagen1(), producto.getImagen2(), producto.getImagen3(),
                producto.getImagen4(), producto.getImagen5(), producto.getImagen6()
        };
        StringBuilder texto = new StringBuilder();
        for (String url : urls) {
            if (url != null && !url.isBlank()) {
                if (texto.length() > 0) {
                    texto.append('\n');
                }
                texto.append(url.trim());
            }
        }
        return texto.toString();
    }

    @PostMapping("/guardar")
    public String guardarProducto(@ModelAttribute Producto producto,
                                  // Formato NUEVO: filas del editor de imágenes.
                                  // Dos listas paralelas: URL y color opcional.
                                  @RequestParam(required = false) List<String> imagenUrl,
                                  @RequestParam(required = false) List<String> imagenColor,
                                  // Formato anterior: una única textarea con una URL por línea
                                  // (se mantiene para no romper formularios antiguos).
                                  @RequestParam(required = false) String imagenesUrls,
                                  // Formato antiguo (compatibilidad): un campo por imagen.
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
        // 1. IMÁGENES (URL + color opcional por fila)
        // ------------------------------------------------------------
        // Las imágenes ya están alojadas en Cloudinary: aquí solo se
        // guardan cadenas de texto. No hay ficheros ni multipart.
        //
        // `imagenesEditor` es la lista que se pasa al servicio:
        //   - null  → no se tocó el editor (se conservan los colores actuales)
        //   - lista → el editor manda (una fila por imagen, en orden)
        // ============================================================
        List<ProductoService.ImagenFormulario> imagenesEditor = null;

        if (imagenUrl != null && !imagenUrl.isEmpty()) {
            // Formato nuevo: se recorren las dos listas por índice para que el
            // color no se desalinee cuando alguna fila viene vacía.
            imagenesEditor = new ArrayList<>();
            for (int i = 0; i < imagenUrl.size(); i++) {
                String url = imagenUrl.get(i) == null ? "" : imagenUrl.get(i).trim();
                if (url.isEmpty()) {
                    continue;   // fila vacía: no es un error, se ignora
                }
                String color = (imagenColor != null && i < imagenColor.size()) ? imagenColor.get(i) : null;
                imagenesEditor.add(new ProductoService.ImagenFormulario(url, color));
            }
            aplicarImagenesLegacy(producto, imagenesEditor);

        } else if (imagenesUrls != null) {
            // Formato nuevo anterior: textarea "imagenesUrls" (una URL por línea)
            List<String> urls = new ArrayList<>();
            for (String linea : imagenesUrls.split("\\R")) {
                String url = (linea == null) ? "" : linea.trim();
                if (!url.isEmpty()) {
                    urls.add(url);
                }
            }
            producto.setImagen1(urls.size() > 0 ? urls.get(0) : null);
            producto.setImagen2(urls.size() > 1 ? urls.get(1) : null);
            producto.setImagen3(urls.size() > 2 ? urls.get(2) : null);
            producto.setImagen4(urls.size() > 3 ? urls.get(3) : null);
            producto.setImagen5(urls.size() > 4 ? urls.get(4) : null);
            producto.setImagen6(urls.size() > 5 ? urls.get(5) : null);
        } else {
            // Formato antiguo: campos individuales imagen1File..imagen6File
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
        }

        // ============================================================
        // 2. GUARDAR PRODUCTO, IMÁGENES Y VARIANTES
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
                    varianteId,
                    imagenesEditor
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

    /**
     * Copia las primeras 6 URL del editor a los campos legacy
     * {@code imagen1..imagen6}.
     *
     * <p>Siguen existiendo porque los usan la portada, el carrito y el chat de
     * la IA ({@code producto.getImagen1()}) como imagen de la tarjeta. La
     * fuente completa (con colores) es {@code producto_imagen}.</p>
     */
    private void aplicarImagenesLegacy(Producto producto, List<ProductoService.ImagenFormulario> filas) {
        String[] urls = new String[6];
        if (filas != null) {
            for (int i = 0; i < Math.min(6, filas.size()); i++) {
                urls[i] = filas.get(i).url();
            }
        }
        producto.setImagen1(urls[0]);
        producto.setImagen2(urls[1]);
        producto.setImagen3(urls[2]);
        producto.setImagen4(urls[3]);
        producto.setImagen5(urls[4]);
        producto.setImagen6(urls[5]);
    }

    // POST (no GET): un borrado por enlace es vulnerable a CSRF y a que el
    // navegador lo dispare al precargar.
    @PostMapping("/eliminar/{id}")
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

    @PostMapping("/eliminar-resena/{id}")
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