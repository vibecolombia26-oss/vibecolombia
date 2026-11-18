package com.flowcolombia.flowcolombia.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba el filtro de privacidad del cliente de Dropi, sin red.
 *
 * <p>El JSON de ejemplo incluye a propósito nombre, teléfono, dirección,
 * correo y documento para demostrar que {@link DropiHttpClient#parsear(String)}
 * <strong>solo</strong> deja pasar los identificadores, el estado y el número
 * de guía.</p>
 */
class DropiHttpClientParseoTest {

    /** Respuesta de ejemplo con datos personales que NO deben sobrevivir. */
    private static final String JSON_CON_PII = """
            [
              {
                "id": 9876,
                "reference": "FLOW-ABC1234567",
                "status": "ENTREGADO",
                "guide_number": "GUIA-123",
                "name": "Cliente Prueba",
                "phone": "3001234567",
                "email": "cliente@example.com",
                "document": "1020304050",
                "address": "Calle Falsa 123",
                "customer": {
                  "name": "Cliente Prueba",
                  "phone": "3001234567"
                }
              }
            ]
            """;

    @Test
    @DisplayName("Del JSON solo salen identificadores, estado y guía")
    void soloSeExtraenLosCamposPermitidos() {
        List<DropiEstado> estados = DropiHttpClient.parsear(JSON_CON_PII);

        assertEquals(1, estados.size(), "debe leerse un pedido");

        DropiEstado estado = estados.get(0);
        assertEquals("FLOW-ABC1234567", estado.referencia(), "la referencia libre se lee aparte");
        assertEquals("9876", estado.idDropi(), "el ID de Dropi se lee aparte");
        assertEquals("9876", estado.identificadorPreferido(), "el ID de Dropi manda como correlador");
        assertEquals("ENTREGADO", estado.estado());
        assertEquals("GUIA-123", estado.numeroGuia());

        // Ni rastro de datos personales en el objeto sanitizado.
        String volcado = estado.toString();
        assertFalse(volcado.contains("Cliente Prueba"), "se coló el nombre");
        assertFalse(volcado.contains("3001234567"), "se coló el teléfono");
        assertFalse(volcado.contains("Calle Falsa"), "se coló la dirección");
        assertFalse(volcado.contains("cliente@example.com"), "se coló el correo");
        assertFalse(volcado.contains("1020304050"), "se coló el documento");
    }

    @Test
    @DisplayName("Acepta el array envuelto en 'data'")
    void aceptaEnvoltorioData() {
        String json = "{\"data\":[{\"id\":555,\"status\":\"EN_CAMINO\"}]}";

        List<DropiEstado> estados = DropiHttpClient.parsear(json);

        assertEquals(1, estados.size());
        assertEquals("555", estados.get(0).idDropi());
        assertNull(estados.get(0).referencia(), "sin campo reference debe quedar null");
        assertEquals("EN_CAMINO", estados.get(0).estado());
        assertNull(estados.get(0).numeroGuia(), "sin guía en el JSON debe quedar null");
    }

    @Test
    @DisplayName("Un pedido sin datos útiles se descarta")
    void pedidoSinDatosUtilesSeDescarta() {
        String json = "[{\"name\":\"Cliente\",\"phone\":\"3001234567\"}]";

        assertTrue(DropiHttpClient.parsear(json).isEmpty());
    }

    @Test
    @DisplayName("Respuestas ilegibles o sin array no rompen nada")
    void respuestaIlegibleDevuelveVacio() {
        assertTrue(DropiHttpClient.parsear("no-es-json").isEmpty());
        assertTrue(DropiHttpClient.parsear("{\"mensaje\":\"ok\"}").isEmpty());
        assertTrue(DropiHttpClient.parsear("").isEmpty());
        assertTrue(DropiHttpClient.parsear(null).isEmpty());
    }
}
