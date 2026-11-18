package com.flowcolombia.flowcolombia.service;

import java.util.Collection;
import java.util.Map;

/**
 * Puerto de salida hacia la API de Dropi.
 *
 * <p>Existe como interfaz por dos motivos:</p>
 * <ol>
 *   <li><strong>Testabilidad sin red</strong>: los tests del sync inyectan una
 *       implementación de mentira y comprueban la regla de privacidad sin
 *       tocar HTTP (el proyecto no usa Mockito).</li>
 *   <li><strong>Aislamiento del contrato</strong>: Dropi no publica
 *       documentación oficial de su API, así que la única pieza que depende de
 *       URLs y nombres de campo es {@link DropiHttpClient}. Si cambian, se
 *       cambia esa clase y nada más.</li>
 * </ol>
 */
public interface DropiClient {

    /**
     * ¿Hay URL y token configurados? Si no, el sync ni siquiera consulta la
     * base de datos: el robot queda dormido y no genera 401/403/404.
     */
    boolean estaConfigurado();

    /**
     * Consulta el estado de los pedidos indicados.
     *
     * @param referencias identificadores propios (el {@code dropiOrderId} que
     *                    pegó el admin o, en su defecto, nuestro {@code FLOW-…})
     * @return mapa {@code identificador solicitado → estado sanitizado}; vacío
     *         si Dropi no está configurado o falla (nunca {@code null})
     */
    Map<String, DropiEstado> consultarEstados(Collection<String> referencias);
}
