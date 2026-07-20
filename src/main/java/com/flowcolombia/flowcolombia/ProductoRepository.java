package com.flowcolombia.flowcolombia;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    // ============================================================
    // MÉTODOS DE BÚSQUEDA
    // ============================================================

    // 🔥 Filtrar productos por categoría (para el filtro del header)
    List<Producto> findByCategoria(String categoria);

    // 🔥 Buscar producto por SKU (para el producto destacado / oferta del mes)
    Optional<Producto> findBySku(String sku);

    // Buscar productos por nombre (para futuras búsquedas)
    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    // (Opcional) Contar productos por categoría
    long countByCategoria(String categoria);
}