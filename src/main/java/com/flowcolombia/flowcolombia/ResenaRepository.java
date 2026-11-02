package com.flowcolombia.flowcolombia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResenaRepository extends JpaRepository<Resena, Long> {
    List<Resena> findByProductoIdAndAprobadoTrueOrderByFechaDesc(Long productoId);
    List<Resena> findByProductoId(Long productoId);
    void deleteByProductoId(Long productoId);

    /**
     * Total y promedio de calificaciones aprobadas de un producto,
     * calculados en la base de datos.
     *
     * Evita cargar la colección completa de reseñas del producto solo
     * para mostrar las estrellas (una consulta extra + una conexión
     * más contra Neon por cada visita a la ficha de producto).
     *
     * @return fila única [Long total, Double promedio]
     */
    @Query("SELECT COUNT(r), COALESCE(AVG(r.calificacion), 0.0) FROM Resena r " +
           "WHERE r.producto.id = :productoId AND r.aprobado = true")
    List<Object[]> estadisticasAprobadas(@Param("productoId") Long productoId);
}
