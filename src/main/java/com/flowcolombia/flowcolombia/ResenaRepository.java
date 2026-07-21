package com.flowcolombia.flowcolombia;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResenaRepository extends JpaRepository<Resena, Long> {
    List<Resena> findByProductoIdAndAprobadoTrueOrderByFechaDesc(Long productoId);
    List<Resena> findByProductoId(Long productoId);
    void deleteByProductoId(Long productoId);
}