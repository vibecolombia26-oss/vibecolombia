package com.vibecolombia.vibecolombia;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    private final ProductoRepository productoRepository;

    public DataLoader(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Override
    public void run(String... args) {

        // 🕰️ Reloj Despertador Vintage
        productoRepository.save(new Producto(
                "Reloj Despertador Vintage Alarma Estilo Retro Iluminado",
                57600.0,
                "Diseño vintage con iluminación nocturna. Alarma confiable, mecanismo silencioso. Decorativo y funcional.",
                "REF:3010",
                "Hogar",
                "/images/reloj-vintage-1.png",
                "/images/reloj-vintage-2.png",
                "18 cm",
                "10 cm",
                "18 cm",
                "246.5 g"
        ));

        System.out.println("✅ Producto inicial VIBE COLOMBIA 26 cargado!");
    }
}