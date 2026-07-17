package com.flowcolombia.flowcolombia;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    // 🔥 NUEVO: Filtrar productos por categoría
    List<Producto> findByCategoria(String categoria);

    // (Opcional) Si quieres buscar por nombre también
    List<Producto> findByNombreContainingIgnoreCase(String nombre);
}