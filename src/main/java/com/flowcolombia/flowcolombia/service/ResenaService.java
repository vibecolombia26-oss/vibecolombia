package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Resena;
import com.flowcolombia.flowcolombia.ResenaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResenaService {

    private final ResenaRepository resenaRepository;

    public ResenaService(ResenaRepository resenaRepository) {
        this.resenaRepository = resenaRepository;
    }

    /**
     * Resumen de calificaciones calculado en la base de datos.
     */
    public record EstadisticasResenas(long total, double promedio) {}

    /**
     * Devuelve total y promedio de reseñas aprobadas con UNA sola consulta
     * (antes: cargar la colección de reseñas del producto + recorrerla).
     */
    public EstadisticasResenas estadisticasAprobadas(Long productoId) {
        if (productoId == null) {
            return new EstadisticasResenas(0L, 0.0);
        }
        List<Object[]> filas = resenaRepository.estadisticasAprobadas(productoId);
        if (filas == null || filas.isEmpty() || filas.get(0) == null) {
            return new EstadisticasResenas(0L, 0.0);
        }
        Object[] fila = filas.get(0);
        long total = (fila[0] instanceof Number n) ? n.longValue() : 0L;
        double promedio = (fila.length > 1 && fila[1] instanceof Number n) ? n.doubleValue() : 0.0;
        return new EstadisticasResenas(total, promedio);
    }

    public List<Resena> listarPorProducto(Long productoId) {
        return resenaRepository.findByProductoId(productoId);
    }

    public List<Resena> listarAprobadasPorProducto(Long productoId) {
        return resenaRepository.findByProductoIdAndAprobadoTrueOrderByFechaDesc(productoId);
    }

    public Resena obtenerPorId(Long id) {
        return resenaRepository.findById(id).orElse(null);
    }

    public Resena guardar(Resena resena) {
        return resenaRepository.save(resena);
    }

    /**
     * Borra una reseña.
     *
     * <p>Con {@code @Transactional} declarado: {@code deleteById} ya abre la
     * suya, pero {@link #eliminarPorProducto(Long)} usa una consulta derivada
     * ({@code deleteByProductoId}) que <strong>no</strong> la abre, y sin
     * transacción activa Hibernate responde
     * «No EntityManager with actual transaction available».</p>
     */
    @Transactional
    public void eliminar(Long id) {
        if (id == null) {
            return;
        }
        resenaRepository.deleteById(id);
    }

    /** Borra todas las reseñas de un producto (consulta derivada: exige transacción). */
    @Transactional
    public void eliminarPorProducto(Long productoId) {
        if (productoId == null) {
            return;
        }
        resenaRepository.deleteByProductoId(productoId);
    }
}