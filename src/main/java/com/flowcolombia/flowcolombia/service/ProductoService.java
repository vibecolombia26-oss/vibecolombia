package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Producto;
import com.flowcolombia.flowcolombia.ProductoImagenRepository;
import com.flowcolombia.flowcolombia.ProductoRepository;
import com.flowcolombia.flowcolombia.VarianteProducto;
import com.flowcolombia.flowcolombia.VarianteProductoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    // MÉTODOS EXISTENTES
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

    public VarianteProducto obtenerVariantePorSku(String sku) {
        if (sku == null || sku.trim().isEmpty()) {
            return null;
        }
        return varianteProductoRepository.findBySku(sku.trim()).orElse(null);
    }

    // ============================================================
    // GUARDAR PRODUCTO CON VARIANTES (TRANSACCIONAL)
    // NOTA: Esta implementación debe ser la misma que ya tienes.
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
                                                List<Boolean> varianteActivo,
                                                List<Long> varianteId) {
        // Mantén aquí tu implementación actual de guardado de producto con variantes.
        // Por brevedad no la repito, pero debe permanecer sin cambios.
        return productoRepository.save(producto);
    }

    // ============================================================
    // PRODUCTOS RELACIONADOS (OPTIMIZADO)
    // ============================================================

    /**
     * Obtiene productos relacionados de la misma categoría, excluyendo el producto actual.
     * Utiliza la categoría proporcionada para evitar una consulta adicional al producto.
     *
     * @param productoId ID del producto a excluir
     * @param categoria  Categoría para filtrar productos relacionados
     * @param limite     Número máximo de productos a devolver (recomendado: 5)
     * @return Lista de productos relacionados (vacía si no hay o si los parámetros son inválidos)
     */
    public List<Producto> obtenerRelacionados(Long productoId, String categoria, int limite) {
        if (productoId == null || categoria == null || categoria.trim().isEmpty() || limite < 1) {
            return new ArrayList<>();
        }
        Pageable pageable = PageRequest.of(0, limite);
        return productoRepository.findRelacionados(productoId, categoria.trim(), pageable);
    }

    /**
     * Método existente que obtiene productos relacionados a partir del ID del producto.
     * Ahora usa el nuevo método para evitar duplicar la lógica y no cargar la categoría completa.
     *
     * @param productoId ID del producto
     * @param limite     Número máximo de productos a devolver
     * @return Lista de productos relacionados
     */
    public List<Producto> obtenerRelacionados(Long productoId, int limite) {
        Producto producto = obtenerPorId(productoId);
        if (producto == null) {
            return new ArrayList<>();
        }
        return obtenerRelacionados(productoId, producto.getCategoria(), limite);
    }
}