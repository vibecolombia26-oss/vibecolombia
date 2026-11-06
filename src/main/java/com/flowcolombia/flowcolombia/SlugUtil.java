package com.flowcolombia.flowcolombia;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Generador de slugs para URLs amigables.
 *
 * <p>Reglas:</p>
 * <ul>
 *   <li>Quita tildes y diéresis ({@code José} → {@code jose}) descomponiendo
 *       en Unicode y eliminando las marcas.</li>
 *   <li>Los símbolos con significado se traducen antes de limpiar:
 *       {@code +} → {@code plus}, {@code &} → {@code y}.</li>
 *   <li>Todo lo que no sea {@code a-z0-9} se convierte en guion, se colapsan
 *       los guiones repetidos y se recortan los de los extremos.</li>
 *   <li>Se limita la longitud para no generar URLs absurdas, cortando por un
 *       guion para no partir palabras.</li>
 * </ul>
 *
 * <p>Ejemplo: {@code "Combo URO O+"} → {@code "combo-uro-o-plus"}.</p>
 */
public final class SlugUtil {

    /** Longitud máxima del slug (deja sitio de sobra para el sufijo único). */
    private static final int MAX_LARGO = 90;

    private SlugUtil() {
        // Clase de utilidades: no se instancia.
    }

    /**
     * Convierte un texto libre en un slug válido para URL.
     *
     * @param texto nombre o título original
     * @return slug en minúsculas y sin acentos; cadena vacía si no queda nada útil
     */
    public static String generar(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }

        String plano = texto
                .replace("+", " plus ")
                .replace("&", " y ")
                .replace("º", " ");

        plano = Normalizer.normalize(plano, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");

        if (plano.length() > MAX_LARGO) {
            plano = plano.substring(0, MAX_LARGO);
            int ultimoGuion = plano.lastIndexOf('-');
            if (ultimoGuion > 20) {
                plano = plano.substring(0, ultimoGuion);
            }
            plano = plano.replaceAll("-+$", "");
        }

        return plano;
    }

    /**
     * Slug con sufijo numérico para resolver duplicados
     * ({@code combo}, {@code combo-2}, {@code combo-3}…).
     */
    public static String conSufijo(String base, int numero) {
        String raiz = (base == null || base.isBlank()) ? "producto" : base;
        return numero <= 1 ? raiz : raiz + "-" + numero;
    }
}
