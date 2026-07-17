package com.flowcolombia.flowcolombia;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    // 🔥 FILTRAR PRODUCTOS POR CATEGORÍA (NECESARIO PARA EL FILTRO)
    List<Producto> findByCategoria(String categoria);

    // (Opcional) Buscar por nombre (para futuras búsquedas)
    List<Producto> findByNombreContainingIgnoreCase(String nombre);
}