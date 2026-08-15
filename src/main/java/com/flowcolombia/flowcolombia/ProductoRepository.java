package com.flowcolombia.flowcolombia;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    // Fase 1: carga imágenes en la home
    @EntityGraph(attributePaths = "imagenes")
    List<Producto> findByCategoria(String categoria);

    // Fase 2: carga solo imágenes (evita multiple bag fetch)
    @Query("SELECT p FROM Producto p LEFT JOIN FETCH p.imagenes WHERE p.id = :id")
    Optional<Producto> findDetalleById(@Param("id") Long id);

    // Fase 2: carga solo variantes (se ejecuta después de findDetalleById)
    @Query("SELECT p FROM Producto p LEFT JOIN FETCH p.variantes WHERE p.id = :id")
    Optional<Producto> findWithVariantes(@Param("id") Long id);

    Optional<Producto> findBySku(String sku);

    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    long countByCategoria(String categoria);

    // ============================================================
    // PRODUCTOS RELACIONADOS (EFICIENTE)
    // ============================================================

    @Query("SELECT p FROM Producto p WHERE p.categoria = :categoria AND p.id != :productoId ORDER BY p.id DESC")
    List<Producto> findRelacionados(@Param("productoId") Long productoId,
                                    @Param("categoria") String categoria,
                                    Pageable pageable);
}