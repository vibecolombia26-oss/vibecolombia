package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Resena;
import com.flowcolombia.flowcolombia.ResenaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResenaService {

    private final ResenaRepository resenaRepository;

    public ResenaService(ResenaRepository resenaRepository) {
        this.resenaRepository = resenaRepository;
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

    public void eliminar(Long id) {
        resenaRepository.deleteById(id);
    }

    public void eliminarPorProducto(Long productoId) {
        resenaRepository.deleteByProductoId(productoId);
    }
}