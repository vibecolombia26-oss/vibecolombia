package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Pedido;
import com.flowcolombia.flowcolombia.PedidoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;

    public PedidoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    public List<Pedido> listarTodosOrdenados() {
        return pedidoRepository.findAllByOrderByFechaDesc();
    }

    public List<Pedido> listarPorEstado(String estado) {
        return pedidoRepository.findByEstadoOrderByFechaDesc(estado);
    }

    public Pedido obtenerPorId(Long id) {
        return pedidoRepository.findById(id).orElse(null);
    }

    public Pedido guardar(Pedido pedido) {
        return pedidoRepository.save(pedido);
    }

    public void eliminar(Long id) {
        pedidoRepository.deleteById(id);
    }

    public long contarPorEstado(String estado) {
        return pedidoRepository.countByEstado(estado);
    }

    /**
     * Dinero real: suma de los totales de los pedidos entregados.
     * La suma se hace en la base de datos (consulta agregada) para no
     * cargar la tabla de pedidos en memoria.
     *
     * @return total en pesos; 0.0 si aún no hay pedidos entregados
     */
    public double sumarIngresosEntregados() {
        Double total = pedidoRepository.sumarTotalPorEstado("Entregado");
        return total != null ? total : 0.0;
    }
}