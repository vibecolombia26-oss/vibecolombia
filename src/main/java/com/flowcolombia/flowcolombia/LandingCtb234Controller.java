package com.flowcolombia.flowcolombia;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Landing standalone del smoke test CTB-234
 * (Destornillador de Impacto Manual x 13 Piezas).
 *
 * <p>Ruta fija, hardcoded: no es un CRUD. Sirve el HTML estático con el copy
 * aprobado por el brief de marketing. Un solo producto, un solo flujo, cero
 * variantes.</p>
 *
 * <p><strong>No hay lógica de negocio aquí</strong>: el carrito y el checkout
 * siguen siendo los de la tienda ({@code /carrito?checkout=1}); esta vista solo
 * presenta el producto y dispara los eventos de medición desde
 * {@code flow-landing-ctb.js}.</p>
 *
 * <p><strong>Acceso:</strong> la ruta es pública. {@code /landing/**} está en
 * la lista blanca {@code TIENDA_PUBLICA} de {@code SecurityConfig} (solo
 * {@code GET}, sin estado y sin privilegios), así que el tráfico orgánico del
 * marketplace entra sin sesión. El panel de seguimiento
 * ({@code /admin/landing/ctb-234}) sigue exigiendo rol ADMIN.</p>
 */
@Controller
public class LandingCtb234Controller {

    private final ProductoRepository productoRepository;

    public LandingCtb234Controller(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    /**
     * Sirve la landing y entrega el <strong>ID real</strong> del producto.
     *
     * <p>El JavaScript lo mete en la línea del carrito para que el pedido
     * viaje con {@code productosIds}. Ese ID es la fuente autoritativa del
     * backend: con él, la CAPI decide si bloquea el {@code Purchase} por el
     * flag de la fila (y el correo de CTB-234 se elige por SKU) sin depender
     * de que el nombre escrito en la landing coincida con el de la base de
     * datos. Sin ID, el backend caería al respaldo por nombre.</p>
     *
     * <p>Coste en Neon: <strong>una</strong> consulta por visita, por columna
     * única ({@code sku}) que devuelve 0 o 1 fila. No hay N+1 ni
     * colecciones: solo se lee el ID.</p>
     *
     * <p>Si el producto todavía no existe en BD (el admin aún no lo ha
     * creado), la landing igual renderiza en modo «próximamente»: el JS no
     * agrega nada al carrito y ofrece WhatsApp.</p>
     *
     * @param model modelo de la vista; recibe {@code productoId} (puede ser
     *              {@code null})
     * @return nombre de la plantilla de la landing
     */
    @GetMapping("/landing/ctb-234")
    public String landing(Model model) {
        // El producto debe existir en BD (lo crea el admin desde el
        // panel). Si no existe todavía, la landing igual renderiza
        // (modo "coming soon") pero no puede agregar al carrito.
        Long productoId = productoRepository.findBySku("CTB-234")
                .map(Producto::getId)
                .orElse(null);
        model.addAttribute("productoId", productoId);
        return "landing-ctb-234";
    }
}
