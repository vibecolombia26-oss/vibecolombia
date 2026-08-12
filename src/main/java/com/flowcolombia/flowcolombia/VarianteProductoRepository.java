package com.flowcolombia.flowcolombia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para la entidad VarianteProducto.
 * Proporciona métodos para operaciones CRUD y consultas específicas
 * necesarias para el sistema de variantes de productos.
 */
@Repository
public interface VarianteProductoRepository extends JpaRepository<VarianteProducto, Long> {

    // ============================================================
    // CONSULTAS BÁSICAS (REQUERIDAS)
    // ============================================================

    /**
     * Busca todas las variantes asociadas a un producto específico.
     *
     * @param productoId ID del producto.
     * @return Lista de variantes del producto.
     */
    List<VarianteProducto> findByProductoId(Long productoId);

    /**
     * Busca una variante por su SKU (único en toda la tabla).
     *
     * @param sku SKU de la variante.
     * @return Optional con la variante encontrada.
     */
    Optional<VarianteProducto> findBySku(String sku);

    /**
     * Elimina todas las variantes asociadas a un producto.
     * Útil al eliminar un producto completo.
     *
     * @param productoId ID del producto.
     */
    void deleteByProductoId(Long productoId);

    // ============================================================
    // CONSULTAS ADICIONALES (PARA VALIDACIONES Y NEGOCIO)
    // ============================================================

    /**
     * Verifica si ya existe una variante con el SKU especificado.
     * Útil para validaciones de unicidad antes de guardar.
     *
     * @param sku SKU a verificar.
     * @return true si existe, false en caso contrario.
     */
    boolean existsBySku(String sku);

    /**
     * Busca todas las variantes activas de un producto.
     * Solo las que están marcadas como activas (activo = true).
     *
     * @param productoId ID del producto.
     * @return Lista de variantes activas.
     */
    List<VarianteProducto> findByProductoIdAndActivoTrue(Long productoId);

    /**
     * Busca todas las variantes con stock disponible (stock > 0).
     *
     * @param productoId ID del producto.
     * @param stock      Valor mínimo de stock (excluye cero o negativo).
     * @return Lista de variantes con stock disponible.
     */
    List<VarianteProducto> findByProductoIdAndStockGreaterThan(Long productoId, Integer stock);

    /**
     * Busca variantes por combinación de color y talla (para la tienda).
     *
     * @param color Color de la variante.
     * @param talla Talla de la variante.
     * @return Lista de variantes que coinciden con color y talla (normalmente una sola).
     */
    List<VarianteProducto> findByColorAndTalla(String color, String talla);

    /**
     * Busca variantes de un producto por color.
     *
     * @param productoId ID del producto.
     * @param color      Color a buscar.
     * @return Lista de variantes con ese color.
     */
    List<VarianteProducto> findByProductoIdAndColor(Long productoId, String color);

    /**
     * Busca variantes de un producto por talla.
     *
     * @param productoId ID del producto.
     * @param talla      Talla a buscar.
     * @return Lista de variantes con esa talla.
     */
    List<VarianteProducto> findByProductoIdAndTalla(Long productoId, String talla);
}