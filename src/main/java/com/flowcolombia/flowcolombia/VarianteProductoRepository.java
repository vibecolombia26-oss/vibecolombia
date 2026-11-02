package com.flowcolombia.flowcolombia;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VarianteProductoRepository extends JpaRepository<VarianteProducto, Long> {

    List<VarianteProducto> findByProductoId(Long productoId);

    /**
     * Trae la variante junto con su producto en la misma consulta.
     * El endpoint /api/variante/detalle hace variante.getProducto(), que con
     * carga perezosa generaba una segunda consulta (y otra conexión a Neon)
     * por cada interacción del carrito.
     */
    @EntityGraph(attributePaths = "producto")
    Optional<VarianteProducto> findBySku(String sku);

    void deleteByProductoId(Long productoId);

    boolean existsBySku(String sku);

    // Valida existencia de SKU excluyendo una variante específica (para actualizaciones)
    boolean existsBySkuAndIdNot(String sku, Long id);

    List<VarianteProducto> findByProductoIdAndActivoTrue(Long productoId);

    List<VarianteProducto> findByProductoIdAndStockGreaterThan(Long productoId, Integer stock);

    List<VarianteProducto> findByColorAndTalla(String color, String talla);
}