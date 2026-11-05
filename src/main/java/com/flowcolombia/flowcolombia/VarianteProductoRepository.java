package com.flowcolombia.flowcolombia;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    // ============================================================
    // RENTABILIDAD (consultas agregadas: nada de N+1)
    // ============================================================

    /**
     * Margen de cada producto con UNA sola consulta: promedio de precio y de
     * costo de sus variantes que tienen costo de proveedor registrado.
     *
     * @return filas {@code [productoId, precioPromedio, costoPromedio]}
     */
    @Query("SELECT v.producto.id, AVG(v.precio), AVG(v.costo) FROM VarianteProducto v " +
           "WHERE v.costo IS NOT NULL AND v.costo > 0 " +
           "GROUP BY v.producto.id")
    List<Object[]> margenPorProducto();

    /**
     * Promedios de todo el catálogo (solo variantes con costo registrado).
     *
     * @return una fila {@code [precioPromedio, costoPromedio, nVariantes]}
     */
    @Query("SELECT AVG(v.precio), AVG(v.costo), COUNT(v) FROM VarianteProducto v " +
           "WHERE v.costo IS NOT NULL AND v.costo > 0")
    List<Object[]> promediosCatalogo();

    /**
     * Índice plano de costos para valorar pedidos ya vendidos.
     *
     * <p>Se piden solo cinco columnas sueltas (sin entidades ni colecciones
     * perezosas) para poder cruzar el texto de los pedidos antiguos sin
     * provocar consultas adicionales.</p>
     *
     * @return filas {@code [nombreProducto, sku, color, talla, costo]}
     */
    @Query("SELECT v.producto.nombre, v.sku, v.color, v.talla, v.costo FROM VarianteProducto v " +
           "WHERE v.costo IS NOT NULL AND v.costo > 0")
    List<Object[]> variantesConCosto();
}