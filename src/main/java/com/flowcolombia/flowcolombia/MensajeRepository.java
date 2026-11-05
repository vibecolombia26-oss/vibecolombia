package com.flowcolombia.flowcolombia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {
    List<Mensaje> findByCodigoPedidoOrderByFechaAsc(String codigoPedido);
    List<Mensaje> findByTelefonoOrderByFechaAsc(String telefono);

    /**
     * Consultas de clientes que todavía no tienen respuesta.
     * Se cuenta en la base de datos en lugar de traer todos los mensajes
     * para el dashboard.
     */
    @Query("SELECT COUNT(m) FROM Mensaje m " +
           "WHERE m.esCliente = true AND (m.respuesta IS NULL OR m.respuesta = '')")
    long contarSinResponder();

    /**
     * Solo los últimos 200 mensajes.
     * El panel de administración pedía findAll() (toda la tabla) cada 10 s,
     * es decir cientos de filas innecesarias contra Neon mientras la pestaña
     * estuviera abierta.
     */
    List<Mensaje> findTop200ByOrderByFechaDesc();
}