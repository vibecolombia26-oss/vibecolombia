package com.flowcolombia.flowcolombia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {
    List<Mensaje> findByCodigoPedidoOrderByFechaAsc(String codigoPedido);
    List<Mensaje> findByTelefonoOrderByFechaAsc(String telefono);
}