package com.flowcolombia.flowcolombia;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByCategoria(String categoria);

    Optional<Producto> findBySku(String sku);

    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    long countByCategoria(String categoria);

    // ============================================================
    // 🔥 NUEVO MÉTODO: PRODUCTOS RELACIONADOS (EFICIENTE)
    // ============================================================

    /**
     * Obtiene productos de la misma categoría, excluyendo el producto actual,
     * con límite mediante Pageable.
     *
     * @param productoId ID del producto a excluir
     * @param categoria  Categoría para filtrar
     * @param pageable   Objeto de paginación (limita el número de resultados)
     * @return Lista de productos relacionados
     */
    @Query("SELECT p FROM Producto p WHERE p.categoria = :categoria AND p.id != :productoId ORDER BY p.id DESC")
    List<Producto> findRelacionados(@Param("productoId") Long productoId,
                                    @Param("categoria") String categoria,
                                    Pageable pageable);
}