package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Mensaje;
import com.flowcolombia.flowcolombia.MensajeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MensajeService {

    private final MensajeRepository mensajeRepository;

    public MensajeService(MensajeRepository mensajeRepository) {
        this.mensajeRepository = mensajeRepository;
    }

    public List<Mensaje> listarTodos() {
        return mensajeRepository.findAll();
    }

    public Mensaje obtenerPorId(Long id) {
        return mensajeRepository.findById(id).orElse(null);
    }

    public Mensaje guardar(Mensaje mensaje) {
        return mensajeRepository.save(mensaje);
    }
}