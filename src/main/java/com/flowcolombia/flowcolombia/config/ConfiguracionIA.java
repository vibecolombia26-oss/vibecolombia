package com.flowcolombia.flowcolombia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Personalidad de la IA pública, ajustable <strong>sin tocar código</strong>.
 *
 * <p>El tono vive en {@code application.properties}
 * ({@code ia.tono=${IA_TONO:...}}), así que se puede cambiar la voz de la
 * tienda editando una variable de entorno en Render y redeployando: no hay que
 * recompilar ni tocar el prompt.</p>
 *
 * <p>El prompt del asistente lo concatena como una línea más de contexto; el
 * resto del comportamiento (no inventar, responder corto, no pedir datos
 * personales) no depende de este valor.</p>
 */
@Component
public class ConfiguracionIA {

    private final String tono;

    public ConfiguracionIA(@Value("${ia.tono:cercano, premium, colombiano, pícaro}") String tono) {
        this.tono = (tono == null || tono.isBlank())
                ? "cercano, premium, colombiano, pícaro"
                : tono.trim();
    }

    /**
     * Tono de la marca, tal cual se le inyecta al modelo.
     *
     * @return descriptores separados por comas (nunca vacío)
     */
    public String tono() {
        return tono;
    }

    /** Línea lista para el prompt del sistema. */
    public String enPrompt() {
        return "TONO DE LA TIENDA (ajustable sin tocar código): " + tono + ".";
    }
}
