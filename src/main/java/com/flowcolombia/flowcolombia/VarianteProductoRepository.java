package com.flowcolombia.flowcolombia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VarianteProductoRepository extends JpaRepository<VarianteProducto, Long> {
    List<VarianteProducto> findByProductoId(Long productoId);
    Optional<VarianteProducto> findBySku(String sku);
    void deleteByProductoId(Long productoId);
}