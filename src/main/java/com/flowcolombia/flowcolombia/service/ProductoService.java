package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Producto;
import com.flowcolombia.flowcolombia.ProductoImagen;
import com.flowcolombia.flowcolombia.ProductoImagenRepository;
import com.flowcolombia.flowcolombia.ProductoRepository;
import com.flowcolombia.flowcolombia.VarianteProducto;
import com.flowcolombia.flowcolombia.VarianteProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final ProductoImagenRepository productoImagenRepository;
    private final VarianteProductoRepository varianteProductoRepository;

    public ProductoService(ProductoRepository productoRepository,
                           ProductoImagenRepository productoImagenRepository,
                           VarianteProductoRepository varianteProductoRepository) {
        this.productoRepository = productoRepository;
        this.productoImagenRepository = productoImagenRepository;
        this.varianteProductoRepository = varianteProductoRepository;
    }

    // ============================================================
    // MÉTODOS EXISTENTES (sin cambios)
    // ============================================================
    public List<Producto> listarTodos() {
        return productoRepository.findAll();
    }

    public List<Producto> listarPorCategoria(String categoria) {
        return productoRepository.findByCategoria(categoria);
    }

    public List<String> listarCategorias() {
        return productoRepository.findAll()
                .stream()
                .map(Producto::getCategoria)
                .filter(cat -> cat != null && !cat.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public Producto obtenerPorId(Long id) {
        return productoRepository.findById(id).orElse(null);
    }

    public Producto obtenerPorSku(String sku) {
        return productoRepository.findBySku(sku).orElse(null);
    }

    public Producto guardar(Producto producto) {
        return productoRepository.save(producto);
    }

    public void eliminar(Long id) {
        productoImagenRepository.deleteByProductoId(id);
        varianteProductoRepository.deleteByProductoId(id);
        productoRepository.deleteById(id);
    }

    public long contarTodos() {
        return productoRepository.count();
    }

    // ============================================================
    // NUEVO MÉTODO: guardar producto con variantes (TRANSACCIONAL)
    // ============================================================
    @Transactional
    public Producto guardarProductoConVariantes(Producto producto,
                                                List<String> varianteSku,
                                                List<String> varianteColor,
                                                List<String> varianteTalla,
                                                List<Double> variantePrecio,
                                                List<Double> varianteCosto,
                                                List<Integer> varianteStock,
                                                List<Double> variantePeso,
                                                List<Boolean> varianteActivo) {

        // 1. Guardar el producto base
        Producto productoGuardado = productoRepository.save(producto);

        // 2. Obtener variantes actuales
        List<VarianteProducto> variantesActuales = new ArrayList<>(productoGuardado.getVariantes());

        // 3. Mapa de variantes actuales por SKU (para comparar)
        Map<String, VarianteProducto> mapaActual = variantesActuales.stream()
                .collect(Collectors.toMap(VarianteProducto::getSku, v -> v));

        // 4. Lista para almacenar los SKU que se mantienen
        Set<String> skusMantenidos = new HashSet<>();

        // 5. Procesar variantes recibidas
        int size = (varianteSku != null) ? varianteSku.size() : 0;

        for (int i = 0; i < size; i++) {
            String sku = (i < varianteSku.size()) ? varianteSku.get(i) : null;
            if (sku == null || sku.trim().isEmpty()) continue;

            String color = (varianteColor != null && i < varianteColor.size()) ? varianteColor.get(i) : "";
            String talla = (varianteTalla != null && i < varianteTalla.size()) ? varianteTalla.get(i) : "";

            Double precio = (variantePrecio != null && i < variantePrecio.size() && variantePrecio.get(i) != null && variantePrecio.get(i) > 0)
                    ? variantePrecio.get(i)
                    : productoGuardado.getPrecio();

            Double costo = (varianteCosto != null && i < varianteCosto.size() && varianteCosto.get(i) != null)
                    ? varianteCosto.get(i)
                    : 0.0;

            Integer stock = (varianteStock != null && i < varianteStock.size() && varianteStock.get(i) != null)
                    ? varianteStock.get(i)
                    : 0;

            Double peso = (variantePeso != null && i < variantePeso.size() && variantePeso.get(i) != null)
                    ? variantePeso.get(i)
                    : null;

            Boolean activo = (varianteActivo != null && i < varianteActivo.size() && varianteActivo.get(i) != null)
                    ? varianteActivo.get(i)
                    : true;

            // Validar SKU único
            if (mapaActual.containsKey(sku.trim())) {
                // Actualizar variante existente
                VarianteProducto existente = mapaActual.get(sku.trim());
                existente.setColor(color.trim());
                existente.setTalla(talla.trim());
                existente.setPrecio(precio);
                existente.setCosto(costo);
                existente.setStock(stock);
                existente.setPeso(peso);
                existente.setActivo(activo);
                skusMantenidos.add(sku.trim());
            } else {
                // Crear nueva variante
                VarianteProducto nueva = new VarianteProducto();
                nueva.setProducto(productoGuardado);
                nueva.setSku(sku.trim());
                nueva.setColor(color.trim());
                nueva.setTalla(talla.trim());
                nueva.setPrecio(precio);
                nueva.setCosto(costo);
                nueva.setStock(stock);
                nueva.setPeso(peso);
                nueva.setActivo(activo);
                productoGuardado.getVariantes().add(nueva);
                skusMantenidos.add(sku.trim());
            }
        }

        // 6. Eliminar variantes que ya no están en la lista
        productoGuardado.getVariantes().removeIf(v -> !skusMantenidos.contains(v.getSku()));

        // 7. Guardar producto (cascade ALL)
        return productoRepository.save(productoGuardado);
    }

    // ============================================================
    // MÉTODO PARA OBTENER VARIANTE POR SKU (necesario para CarritoController)
    // ============================================================
    public VarianteProducto obtenerVariantePorSku(String sku) {
        return varianteProductoRepository.findBySku(sku).orElse(null);
    }
}