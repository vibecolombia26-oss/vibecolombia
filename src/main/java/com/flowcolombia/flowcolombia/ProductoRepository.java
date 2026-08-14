package com.flowcolombia.flowcolombia;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    // Carga las imágenes junto con los productos de la categoría
    @EntityGraph(attributePaths = "imagenes")
    List<Producto> findByCategoria(String categoria);

    Optional<Producto> findBySku(String sku);

    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    long countByCategoria(String categoria);

    // Productos relacionados
    @Query("SELECT p FROM Producto p WHERE p.categoria = :categoria AND p.id != :productoId ORDER BY p.id DESC")
    List<Producto> findRelacionados(
            @Param("productoId") Long productoId,
            @Param("categoria") String categoria,
            Pageable pageable
    );
}