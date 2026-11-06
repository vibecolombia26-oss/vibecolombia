package com.flowcolombia.flowcolombia;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Normalización y hash SHA-256 de datos personales para la API de Conversiones
 * de Meta (CAPI).
 *
 * <p>Meta exige los datos personales <strong>hasheados con SHA-256 en
 * hexadecimal minúsculas</strong> y, antes de hashear, normalizados: sin
 * espacios sobrantes y en minúsculas. Nunca se envía el dato en claro.</p>
 *
 * <p>Los campos que <em>no</em> son PII ({@code fbp}, {@code fbc},
 * {@code client_ip_address}, {@code client_user_agent}) viajan sin hashear:
 * son identificadores que ya emite Meta o datos de contexto de la petición.</p>
 */
public final class MetaHashUtil {

    private MetaHashUtil() {
        // Clase de utilidades: no se instancia.
    }

    /**
     * SHA-256 en hexadecimal minúsculas.
     *
     * @return el hash, o {@code null} si el valor está vacío (así el campo se
     *         omite del JSON en lugar de enviar el hash de una cadena vacía)
     */
    public static String sha256(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM: esto no puede ocurrir.
            return null;
        }
    }

    /** Email normalizado (minúsculas, sin espacios) y hasheado. */
    public static String email(String email) {
        return sha256(normalizar(email));
    }

    /** Nombre o apellido normalizado y hasheado. */
    public static String nombre(String nombre) {
        return sha256(normalizar(nombre));
    }

    /** Texto genérico (por ejemplo un identificador externo) normalizado y hasheado. */
    public static String texto(String texto) {
        return sha256(normalizar(texto));
    }

    /**
     * Teléfono en formato E.164 sin el signo {@code +}, hasheado.
     *
     * <p>Los celulares colombianos se guardan con 10 dígitos (empiezan por 3);
     * Meta espera el país delante, así que se antepone {@code 57}. Si ya viene
     * con el prefijo (12 dígitos) se respeta.</p>
     */
    public static String telefono(String telefono) {
        if (telefono == null) {
            return null;
        }
        String digitos = telefono.replaceAll("\\D", "");
        if (digitos.isEmpty()) {
            return null;
        }
        if (digitos.length() == 10 && digitos.startsWith("3")) {
            digitos = "57" + digitos;
        }
        return sha256(digitos);
    }

    private static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
