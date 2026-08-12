package com.flowcolombia.flowcolombia;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Component
public class DataMigrationRunner implements CommandLineRunner {

    private final ProductoRepository productoRepository;
    private final ProductoImagenRepository productoImagenRepository;
    private final VarianteProductoRepository varianteProductoRepository;

    public DataMigrationRunner(ProductoRepository productoRepository,
                               ProductoImagenRepository productoImagenRepository,
                               VarianteProductoRepository varianteProductoRepository) {
        this.productoRepository = productoRepository;
        this.productoImagenRepository = productoImagenRepository;
        this.varianteProductoRepository = varianteProductoRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<Producto> productos = productoRepository.findAll();

        for (Producto producto : productos) {
            boolean necesitaMigracion = false;

            // ============================================================
            // MIGRAR IMÁGENES (si no hay imágenes en la nueva estructura)
            // ============================================================
            if (productoImagenRepository.findByProductoIdOrderByOrdenAsc(producto.getId()).isEmpty()) {
                List<String> imagenes = Arrays.asList(
                        producto.getImagen1(),
                        producto.getImagen2(),
                        producto.getImagen3(),
                        producto.getImagen4(),
                        producto.getImagen5(),
                        producto.getImagen6()
                );
                int orden = 0;
                for (String url : imagenes) {
                    if (url != null && !url.trim().isEmpty()) {
                        producto.addImagen(url.trim(), orden++);
                        necesitaMigracion = true;
                    }
                }
            }

            // ============================================================
            // MIGRAR VARIANTES (si no hay variantes en la nueva estructura)
            // ============================================================
            if (varianteProductoRepository.findByProductoId(producto.getId()).isEmpty()) {
                String variacionesStr = producto.getVariacionesDisponibles();
                if (variacionesStr != null && variacionesStr.contains("|")) {
                    String[] partes = variacionesStr.split("\\|", -1);
                    String coloresStr = partes.length > 0 ? partes[0] : "";
                    String tallasStr = partes.length > 1 ? partes[1] : "";

                    List<String> colores = Arrays.asList(coloresStr.split(","));
                    List<String> tallas = Arrays.asList(tallasStr.split(","));

                    // Generar variantes solo si hay colores o tallas definidos
                    if ((coloresStr != null && !coloresStr.trim().isEmpty()) ||
                            (tallasStr != null && !tallasStr.trim().isEmpty())) {

                        // Si solo hay colores, crear variantes con color y talla vacía
                        // Si solo hay tallas, crear variantes con color vacío y talla
                        // Si hay ambos, crear combinaciones
                        for (String color : colores) {
                            color = color.trim();
                            for (String talla : tallas) {
                                talla = talla.trim();
                                String sku = producto.getSku() + "-" + color + "-" + talla;
                                // Limpiar caracteres especiales para SKU
                                sku = sku.replaceAll("[^a-zA-Z0-9-]", "");
                                producto.addVariante(sku, color, talla, producto.getPrecio(), 0);
                                necesitaMigracion = true;
                            }
                        }
                    }
                } else {
                    // Producto sin variaciones: una sola variante por defecto
                    String sku = producto.getSku();
                    producto.addVariante(sku, "", "", producto.getPrecio(), 0);
                    necesitaMigracion = true;
                }
            }

            if (necesitaMigracion) {
                productoRepository.save(producto);
                System.out.println("✅ Migrado producto: " + producto.getNombre() + " (ID: " + producto.getId() + ")");
            }
        }

        System.out.println("✅ Migración de productos completada.");
    }
}