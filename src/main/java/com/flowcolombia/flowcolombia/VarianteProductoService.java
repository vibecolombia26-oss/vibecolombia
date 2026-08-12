package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.VarianteProducto;
import com.flowcolombia.flowcolombia.VarianteProductoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class VarianteProductoService {

    private final VarianteProductoRepository varianteProductoRepository;

    public VarianteProductoService(VarianteProductoRepository varianteProductoRepository) {
        this.varianteProductoRepository = varianteProductoRepository;
    }

    public List<VarianteProducto> listarPorProducto(Long productoId) {
        return varianteProductoRepository.findByProductoId(productoId);
    }

    public Optional<VarianteProducto> obtenerPorSku(String sku) {
        return varianteProductoRepository.findBySku(sku);
    }

    public VarianteProducto guardar(VarianteProducto variante) {
        return varianteProductoRepository.save(variante);
    }

    public void eliminarPorProducto(Long productoId) {
        varianteProductoRepository.deleteByProductoId(productoId);
    }

    public void eliminar(Long id) {
        varianteProductoRepository.deleteById(id);
    }
}