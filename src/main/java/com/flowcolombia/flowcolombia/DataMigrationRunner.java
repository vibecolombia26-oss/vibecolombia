package com.flowcolombia.flowcolombia;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Migra los campos legacy (imagen1..imagen6 y variacionesDisponibles) a las
 * tablas normalizadas (producto_imagenes y variantes).
 *
 * IMPORTANTE (coste en Neon): antes este runner recorría TODOS los productos
 * en cada arranque y lanzaba 2 consultas por producto (imágenes + variantes).
 * Con 50 productos eso son ~100 consultas contra Neon cada despliegue.
 * Ahora primero pregunta a la BD qué productos están pendientes (2 consultas)
 * y solo trabaja con esos. Si no hay nada pendiente, no toca nada más.
 */
@Component
public class DataMigrationRunner implements CommandLineRunner {

    private final ProductoRepository productoRepository;

    public DataMigrationRunner(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        migrarImagenesYVariantes();
        migrarSlugs();
    }

    /**
     * Rellena el slug SEO de los productos que todavía no lo tienen.
     *
     * <p>Es idempotente y solo trabaja mientras queden pendientes: en un
     * arranque normal hace <strong>una</strong> consulta
     * ({@code findIdsSinSlug}) y termina. Para no gastar consultas, carga de
     * una vez todos los slugs ya usados y resuelve los duplicados en memoria.</p>
     */
    private void migrarSlugs() {
        List<Long> pendientes = productoRepository.findIdsSinSlug();
        if (pendientes.isEmpty()) {
            return;
        }

        Set<String> usados = new HashSet<>(productoRepository.findSlugsParaSitemap());
        int generados = 0;

        for (Long id : pendientes) {
            Producto producto = productoRepository.findById(id).orElse(null);
            if (producto == null || producto.getNombre() == null) {
                continue;
            }

            String base = SlugUtil.generar(producto.getNombre());
            if (base.isEmpty()) {
                base = "producto-" + id;
            }

            String candidato = base;
            int intento = 2;
            while (usados.contains(candidato)) {
                candidato = SlugUtil.conSufijo(base, intento++);
            }

            producto.setSlug(candidato);
            usados.add(candidato);
            productoRepository.save(producto);
            generados++;
        }

        if (generados > 0) {
            System.out.println("✅ Slugs SEO generados: " + generados + " producto(s).");
        }
    }

    /** Migración legacy de imágenes y variantes (solo lo que esté pendiente). */
    private void migrarImagenesYVariantes() {
        Set<Long> idsSinImagenes = new HashSet<>(productoRepository.findIdsSinImagenes());
        Set<Long> idsSinVariantes = new HashSet<>(productoRepository.findIdsSinVariantes());

        if (idsSinImagenes.isEmpty() && idsSinVariantes.isEmpty()) {
            System.out.println("✅ Migración de productos: nada pendiente.");
            return;
        }

        Set<Long> pendientes = new TreeSet<>();
        pendientes.addAll(idsSinImagenes);
        pendientes.addAll(idsSinVariantes);

        for (Long id : pendientes) {
            Producto producto = productoRepository.findById(id).orElse(null);
            if (producto == null) {
                continue;
            }

            boolean necesitaMigracion = false;

            // ============================================================
            // MIGRAR IMÁGENES (si no hay imágenes en la nueva estructura)
            // ============================================================
            if (idsSinImagenes.contains(id)) {
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
            if (idsSinVariantes.contains(id)) {
                String variacionesStr = producto.getVariacionesDisponibles();
                if (variacionesStr != null && variacionesStr.contains("|")) {
                    String[] partes = variacionesStr.split("\\|", -1);
                    String coloresStr = partes.length > 0 ? partes[0] : "";
                    String tallasStr = partes.length > 1 ? partes[1] : "";

                    List<String> colores = Arrays.asList(coloresStr.split(","));
                    List<String> tallas = Arrays.asList(tallasStr.split(","));

                    // Generar variantes solo si hay colores o tallas definidos
                    if (!coloresStr.trim().isEmpty() || !tallasStr.trim().isEmpty()) {

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

        System.out.println("✅ Migración de productos completada (" + pendientes.size() + " producto(s) revisado(s)).");
    }
}
