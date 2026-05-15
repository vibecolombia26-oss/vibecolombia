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

        productoRepository.save(new Producto(
                "Reloj Despertador Vintage Alarma Estilo Retro Iluminado",
                57600.0,
                "Reloj vintage con iluminación nocturna y alarma silenciosa. Decorativo y funcional.",
                "El Reloj Despertador Vintage combina funcionalidad clásica con diseño retro. " +
                        "Ideal para mesita de noche o escritorio. Incluye iluminación, alarma confiable, " +
                        "mecanismo silencioso y no requiere enchufe. Medidas: 18x10x18 cm, 246.5g.",
                "REF:3010",
                "Hogar",
                "/images/reloj-vintage-1.png",
                "/images/reloj-vintage-2.png",
                "18 cm", "10 cm", "18 cm", "246.5 g"
        ));

        System.out.println("✅ Producto inicial VIBE COLOMBIA 26 cargado!");
    }
}