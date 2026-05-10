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

        // 🌱 ILUMINACIÓN CULTIVO
        productoRepository.save(new Producto(
                "Luces LED Cultivo Indoor Espectro Completo",
                59850.0,
                "Luz LED azul 460nm para germinación + roja 660nm para floración. Ideal para plantas de interior. Promueve fotosíntesis y antioxidantes."
        ));

        productoRepository.save(new Producto(
                "Panel LED Iluminación Vegetal 50W",
                89900.0,
                "Panel con mezcla de luz roja y azul para todas las etapas de crecimiento. Cobertura 50x50cm."
        ));

        productoRepository.save(new Producto(
                "Kit Luces LED Cultivo + Timer Digital",
                129900.0,
                "Kit completo con 2 paneles LED espectro completo + temporizador programable. Ideal para indoor."
        ));

        // 🛍️ MÁS PRODUCTOS (puedes agregar o modificar estos)
        productoRepository.save(new Producto(
                "Sustrato Premium para Cultivo Indoor",
                45000.0,
                "Mezcla profesional de turba, perlita y vermiculita. Retención óptima de humedad."
        ));

        productoRepository.save(new Producto(
                "Maceta Geotextil 5 Galones",
                25000.0,
                "Maceta transpirable que promueve raíces sanas. Evita enrollamiento radicular."
        ));

        productoRepository.save(new Producto(
                "Fertilizante Líquido NPK 10-10-10",
                35000.0,
                "Nutrición balanceada para crecimiento vegetativo. 1 litro concentrado."
        ));

        System.out.println("✅ 6 productos VIBE COLOMBIA 26 cargados exitosamente!");
    }
}