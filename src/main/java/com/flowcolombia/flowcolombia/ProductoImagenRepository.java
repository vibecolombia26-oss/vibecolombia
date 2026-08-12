package com.flowcolombia.flowcolombia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductoImagenRepository extends JpaRepository<ProductoImagen, Long> {
    List<ProductoImagen> findByProductoIdOrderByOrdenAsc(Long productoId);
    void deleteByProductoId(Long productoId);
}