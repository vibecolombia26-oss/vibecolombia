package com.flowcolombia.flowcolombia;

import com.flowcolombia.flowcolombia.service.DropiSyncService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Disparo manual del sync con Dropi desde el panel.
 *
 * <p>Es un <strong>POST</strong> a propósito: ejecuta una tarea con efectos
 * (escribe en la base de datos) y con CSRF activo un enlace o una imagen no
 * pueden dispararla con la sesión abierta del administrador.</p>
 *
 * <p><strong>No requiere tocar {@code SecurityConfig}:</strong> la ruta cuelga
 * de {@code /admin/**}, que ya exige {@code hasRole("ADMIN")} en la lista de
 * seguridad existente.</p>
 *
 * <p>El trabajo real vive en {@link DropiSyncService} (el mismo que corre en el
 * job de las 6 horas); este controlador solo lo invoca y deja un mensaje en el
 * panel. Si Dropi falla, el servicio devuelve 0 y la tienda no se entera.</p>
 */
@Controller
public class DropiSyncController {

    private final DropiSyncService dropiSyncService;

    public DropiSyncController(DropiSyncService dropiSyncService) {
        this.dropiSyncService = dropiSyncService;
    }

    @PostMapping("/admin/sync-dropi")
    public String sincronizar(RedirectAttributes redirect) {
        int cambios = dropiSyncService.sincronizar();
        redirect.addFlashAttribute("mensaje",
                "🔄 Sync con Dropi: " + cambios + " pedido(s) actualizado(s).");
        return "redirect:/admin/panel";
    }
}
