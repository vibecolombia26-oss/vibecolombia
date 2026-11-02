package com.flowcolombia.flowcolombia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    // Obtener todos los pedidos ordenados por fecha (los más recientes primero)
    List<Pedido> findAllByOrderByFechaDesc();

    // (Opcional) Buscar pedidos por estado
    List<Pedido> findByEstadoOrderByFechaDesc(String estado);

    // (Opcional) Contar pedidos por estado
    long countByEstado(String estado);

    // Búsqueda directa por código / teléfono: antes se hacía findAll() y se
    // filtraba en Java, lo que traía toda la tabla de pedidos en cada consulta
    // de seguimiento o del chat.
    Optional<Pedido> findFirstByCodigoOrderByIdDesc(String codigo);

    Optional<Pedido> findFirstByTelefonoOrderByIdDesc(String telefono);
}