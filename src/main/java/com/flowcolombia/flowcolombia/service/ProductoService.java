package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Producto;
import com.flowcolombia.flowcolombia.ProductoImagen;
import com.flowcolombia.flowcolombia.ProductoImagenRepository;
import com.flowcolombia.flowcolombia.ProductoRepository;
import com.flowcolombia.flowcolombia.VarianteProducto;
import com.flowcolombia.flowcolombia.VarianteProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    @Transactional
    public Producto guardar(Producto producto) {
        return productoRepository.save(producto);
    }

    @Transactional
    public void eliminar(Long id) {
        // Eliminar imágenes y variantes asociadas
        productoImagenRepository.deleteByProductoId(id);
        varianteProductoRepository.deleteByProductoId(id);
        productoRepository.deleteById(id);
    }

    public long contarTodos() {
        return productoRepository.count();
    }

    // ============================================================
    // NUEVOS MÉTODOS PARA IMÁGENES
    // ============================================================
    public List<ProductoImagen> obtenerImagenesPorProducto(Long productoId) {
        return productoImagenRepository.findByProductoIdOrderByOrdenAsc(productoId);
    }

    @Transactional
    public void guardarImagenes(Producto producto, List<String> urls) {
        // Limpiar imágenes existentes
        productoImagenRepository.deleteByProductoId(producto.getId());

        // Guardar nuevas imágenes
        int orden = 0;
        for (String url : urls) {
            if (url != null && !url.trim().isEmpty()) {
                producto.addImagen(url.trim(), orden++);
            }
        }
        productoRepository.save(producto);
    }

    // ============================================================
    // NUEVOS MÉTODOS PARA VARIANTES
    // ============================================================
    public List<VarianteProducto> obtenerVariantesPorProducto(Long productoId) {
        return varianteProductoRepository.findByProductoId(productoId);
    }

    public VarianteProducto obtenerVariantePorSku(String sku) {
        return varianteProductoRepository.findBySku(sku).orElse(null);
    }

    @Transactional
    public void guardarVariantes(Producto producto, List<VarianteProducto> variantes) {
        // Limpiar variantes existentes
        varianteProductoRepository.deleteByProductoId(producto.getId());

        // Asignar producto a cada variante y guardar
        for (VarianteProducto v : variantes) {
            v.setProducto(producto);
            varianteProductoRepository.save(v);
        }
    }

    @Transactional
    public void actualizarStock(Long varianteId, Integer nuevoStock) {
        VarianteProducto variante = varianteProductoRepository.findById(varianteId).orElse(null);
        if (variante != null) {
            variante.setStock(nuevoStock);
            if (nuevoStock <= 0) {
                variante.setActivo(false);
            } else {
                variante.setActivo(true);
            }
            varianteProductoRepository.save(variante);
        }
    }
}