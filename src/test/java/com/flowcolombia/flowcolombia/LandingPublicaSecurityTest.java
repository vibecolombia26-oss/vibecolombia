package com.flowcolombia.flowcolombia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que {@code /landing/**} está en la lista blanca de rutas públicas.
 *
 * <p>Si alguien la quita en el futuro sin darse cuenta, la landing vuelve a
 * pedir login y el smoke test muere (el tráfico pagado aterriza en el
 * formulario de administración). Es una regla de producto, no un detalle
 * técnico, así que se comprueba.</p>
 *
 * <p>Se inspecciona el array {@code TIENDA_PUBLICA} por reflexión, sin
 * arrancar el contexto de Spring (que requeriría DB y {@code ADMIN_PASSWORD},
 * ver Hallazgo A).</p>
 */
class LandingPublicaSecurityTest {

    @Test
    @DisplayName("/landing/** sigue en la lista blanca de rutas públicas")
    void landingEstaEnListaBlanca() throws Exception {
        Field campo = com.flowcolombia.flowcolombia.config.SecurityConfig.class
                .getDeclaredField("TIENDA_PUBLICA");
        campo.setAccessible(true);
        String[] rutas = (String[]) campo.get(null);

        assertNotNull(rutas, "TIENDA_PUBLICA no puede ser nulo");

        boolean encontrada = false;
        for (String ruta : rutas) {
            if ("/landing/**".equals(ruta)) {
                encontrada = true;
                break;
            }
        }

        assertTrue(encontrada,
                "/landing/** debe estar en TIENDA_PUBLICA. Si no está, la landing CTB-234 "
                        + "devuelve 302 al login y el smoke test no funciona.");
    }
}
