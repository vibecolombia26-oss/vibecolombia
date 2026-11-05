package com.flowcolombia.flowcolombia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Suma de los totales de los pedidos en un estado concreto.
     * Se resuelve en la base de datos con una única consulta agregada,
     * sin traer las filas al servidor.
     */
    @Query("SELECT SUM(p.total) FROM Pedido p WHERE p.estado = :estado")
    Double sumarTotalPorEstado(@Param("estado") String estado);

    // Búsqueda directa por código / teléfono: antes se hacía findAll() y se
    // filtraba en Java, lo que traía toda la tabla de pedidos en cada consulta
    // de seguimiento o del chat.
    Optional<Pedido> findFirstByCodigoOrderByIdDesc(String codigo);

    Optional<Pedido> findFirstByTelefonoOrderByIdDesc(String telefono);

    /**
     * Doble candado del seguimiento público: el código y el teléfono deben
     * coincidir en el MISMO pedido.
     *
     * <p>Una sola consulta que resuelve la base de datos: no se trae la lista
     * de pedidos ni se compara en memoria, así que no despierta Neon de más.</p>
     */
    Optional<Pedido> findFirstByCodigoIgnoreCaseAndTelefono(String codigo, String telefono);
}