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

    /**
     * Categorías distintas calculadas en la base de datos.
     * Antes se hacía findAll() + stream en Java (leía toda la tabla en
     * cada carga de la home). Con esto Neon devuelve solo las cadenas.
     */
    @Query("SELECT DISTINCT p.categoria FROM Producto p " +
           "WHERE p.categoria IS NOT NULL AND p.categoria <> '' ORDER BY p.categoria")
    List<String> findCategoriasDistintas();

    /**
     * IDs de productos que aún no tienen filas en la tabla de imágenes.
     * Permite que DataMigrationRunner trabaje solo con lo pendiente en
     * lugar de recorrer todos los productos en cada arranque.
     */
    @Query("SELECT p.id FROM Producto p WHERE NOT EXISTS " +
           "(SELECT i.id FROM ProductoImagen i WHERE i.producto.id = p.id)")
    List<Long> findIdsSinImagenes();

    /**
     * IDs de productos que aún no tienen filas en la tabla de variantes.
     */
    @Query("SELECT p.id FROM Producto p WHERE NOT EXISTS " +
           "(SELECT v.id FROM VarianteProducto v WHERE v.producto.id = p.id)")
    List<Long> findIdsSinVariantes();

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