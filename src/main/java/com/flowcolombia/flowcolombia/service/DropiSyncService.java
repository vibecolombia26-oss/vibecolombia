package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Pedido;
import com.flowcolombia.flowcolombia.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sincroniza el estado de los pedidos abiertos con Dropi.
 *
 * <p><strong>Qué hace y qué NO hace:</strong> lee los pedidos en
 * {@code Pendiente}/{@code Procesando} de nuestra base de datos, pregunta a
 * Dropi por ellos y escribe <strong>solo dos campos</strong>: {@code estado} y
 * {@code numeroGuia}. Nada de nombre, dirección ni teléfono: esos datos ni
 * siquiera salen del JSON de Dropi (ver {@link DropiHttpClient}).</p>
 *
 * <p><strong>Coste en Neon (R1):</strong> una consulta (los abiertos) + una
 * escritura en lote, y <em>ninguna</em> conexión retenida durante la llamada
 * HTTP: la consulta a Dropi ocurre entre ambas, fuera de la transacción. Con
 * el pool en {@code maximum-pool-size=3}, abrir una transacción para esperar a
 * un tercero sería ahogar la tienda.</p>
 *
 * <p><strong>Nunca rompe nada:</strong> cualquier excepción (Dropi caído, token
 * inválido, JSON raro) se registra y se traduce en «0 cambios». El endpoint
 * manual y el job comparten este método, y un candado impide que se solapen.</p>
 */
@Service
public class DropiSyncService {

    private static final Logger log = LoggerFactory.getLogger(DropiSyncService.class);

    /** Estados de nuestra BD que tiene sentido refrescar con Dropi. */
    public static final List<String> ESTADOS_A_SINCRONIZAR = List.of("Pendiente", "Procesando");

    /** Estados que maneja el panel; Dropi nunca puede escribir otro. */
    private static final List<String> ESTADOS_VALIDOS =
            List.of("Pendiente", "Procesando", "Enviado", "Entregado");

    /**
     * Equivalencias estado de Dropi → estado nuestro. Se comparan como
     * subcadenas sobre el texto normalizado (minúsculas y sin tildes), en este
     * orden. Los estados que no aparecen aquí (cancelado, novedad, devuelto…)
     * <strong>no se aplican</strong>: el admin decide, no el bot.
     *
     * <p>Ajustar con los estados reales de la cuenta cuando se confirmen; es
     * el único punto de negocio que depende del vocabulario de Dropi.</p>
     */
    private static final Map<String, String> EQUIVALENCIAS = new LinkedHashMap<>();

    static {
        EQUIVALENCIAS.put("entreg", "Entregado");
        EQUIVALENCIAS.put("enviado", "Enviado");
        EQUIVALENCIAS.put("shipped", "Enviado");
        // Más específico primero: «por despachar» debe ganar a «despach».
        EQUIVALENCIAS.put("por despachar", "Pendiente");
        EQUIVALENCIAS.put("despach", "Enviado");
        EQUIVALENCIAS.put("transito", "Enviado");
        EQUIVALENCIAS.put("en camino", "Enviado");
        EQUIVALENCIAS.put("guia generada", "Enviado");
        EQUIVALENCIAS.put("procesando", "Procesando");
        EQUIVALENCIAS.put("preparando", "Procesando");
        EQUIVALENCIAS.put("en preparacion", "Procesando");
        EQUIVALENCIAS.put("pendiente", "Pendiente");
    }

    private final PedidoRepository pedidoRepository;
    private final DropiClient dropiClient;

    /** Evita que el job programado y el botón manual corran a la vez. */
    private final AtomicBoolean enCurso = new AtomicBoolean(false);

    public DropiSyncService(PedidoRepository pedidoRepository, DropiClient dropiClient) {
        this.pedidoRepository = pedidoRepository;
        this.dropiClient = dropiClient;
    }

    /**
     * Job de las 6 horas (00:00, 06:00, 12:00 y 18:00, hora Colombia).
     *
     * <p>En el plan gratuito de Render la instancia se duerme sin tráfico, así
     * que este job puede no ejecutarse hasta que alguien visite la tienda: por
     * eso el disparo manual desde el panel es parte del diseño, no un extra.</p>
     */
    @Scheduled(cron = "0 0 */6 * * *", zone = "America/Bogota")
    public void sincronizarProgramado() {
        sincronizar();
    }

    /**
     * Sincroniza ahora mismo.
     *
     * @return número de pedidos realmente modificados (0 si no hay cambios, no
     *         hay pendientes, ya hay una sincronización en curso o Dropi falla)
     */
    public int sincronizar() {
        // Robot dormido: sin URL y token no se consulta ni la base de datos.
        // Así no se generan 401/403/404 mientras el contrato está pendiente.
        if (!dropiClient.estaConfigurado()) {
            log.debug("Dropi sync: integración sin configurar (DROPI_API_URL / DROPI_API_TOKEN); sin cambios.");
            return 0;
        }

        if (!enCurso.compareAndSet(false, true)) {
            log.info("Dropi sync: ya hay una sincronización en curso; se omite esta.");
            return 0;
        }

        try {
            // 1) Una sola consulta: los pedidos que aún se pueden mover.
            List<Pedido> abiertos = pedidoRepository.findAbiertosParaSync(ESTADOS_A_SINCRONIZAR);
            if (abiertos == null || abiertos.isEmpty()) {
                log.info("Dropi sync: no hay pedidos en Pendiente/Procesando.");
                return 0;
            }

            // Identificador de correlación: el ID que el admin pegó desde el
            // detalle del pedido; si no hay, el código FLOW como respaldo (solo
            // sirve cuando el pedido se creó desde la tienda y Dropi guardó la
            // referencia). Se consulta con el valor original y se casa con el
            // normalizado, para tolerar mayúsculas y espacios.
            Map<String, Pedido> porIdentificador = new LinkedHashMap<>();
            Map<String, Pedido> indiceNormalizado = new LinkedHashMap<>();
            int sinIdentificador = 0;
            for (Pedido pedido : abiertos) {
                String identificador = primerNoVacio(pedido.getDropiOrderId(), pedido.getCodigo());
                if (identificador.isEmpty()) {
                    sinIdentificador++;
                    continue;
                }
                porIdentificador.putIfAbsent(identificador, pedido);
                indiceNormalizado.putIfAbsent(normalizarTexto(identificador), pedido);
            }
            if (sinIdentificador > 0) {
                log.info("Dropi sync: {} pedido(s) sin ID de Dropi ni código; se omiten.", sinIdentificador);
            }
            if (porIdentificador.isEmpty()) {
                log.info("Dropi sync: ningún pedido abierto tiene identificador para consultar Dropi.");
                return 0;
            }

            // 2) HTTP FUERA de cualquier transacción (no se retiene conexión).
            Map<String, DropiEstado> estados = dropiClient.consultarEstados(porIdentificador.keySet());
            if (estados == null || estados.isEmpty()) {
                log.info("Dropi sync: Dropi no devolvió datos para {} pedido(s).", porIdentificador.size());
                return 0;
            }

            // 3) Solo estado y guía. Se acumulan los cambios en memoria.
            List<Pedido> modificados = new ArrayList<>();
            for (Map.Entry<String, DropiEstado> entrada : estados.entrySet()) {
                Pedido pedido = indiceNormalizado.get(normalizarTexto(entrada.getKey()));
                if (pedido == null) {
                    continue;   // referencia que no es nuestra: se ignora
                }

                DropiEstado remoto = entrada.getValue();
                boolean cambio = false;

                String estadoNuevo = traducirEstado(remoto.estado());
                if (estadoNuevo != null && !estadoNuevo.equals(pedido.getEstado())) {
                    pedido.setEstado(estadoNuevo);
                    cambio = true;
                }

                String guia = remoto.numeroGuia();
                if (guia != null && !guia.isBlank() && !guia.trim().equals(pedido.getNumeroGuia())) {
                    pedido.setNumeroGuia(guia.trim());
                    cambio = true;
                }

                if (cambio) {
                    modificados.add(pedido);
                }
            }

            // 4) Una sola escritura en lote.
            if (!modificados.isEmpty()) {
                pedidoRepository.saveAll(modificados);
            }

            log.info("Dropi sync: {} pedido(s) revisado(s), {} actualizado(s).",
                    porIdentificador.size(), modificados.size());
            return modificados.size();

        } catch (Exception e) {
            // La tienda sigue exactamente igual: no se propagó ni se dejó nada a medias.
            log.warn("Dropi sync: se abortó sin efectos ({}: {})",
                    e.getClass().getSimpleName(), e.getMessage());
            return 0;
        } finally {
            enCurso.set(false);
        }
    }

    /**
     * Traduce el estado de Dropi a uno de los cuatro nuestros.
     *
     * <p>Si no lo reconoce devuelve {@code null} y el pedido conserva su estado
     * actual: el sync puede seguir guardando la guía sin inventar estados que
     * romperían los filtros del panel ({@code Pendiente}, {@code Procesando},
     * {@code Enviado}, {@code Entregado}).</p>
     *
     * @param estadoDropi texto tal cual lo devuelve Dropi
     * @return estado válido para nuestra BD o {@code null} si no se reconoce
     */
    static String traducirEstado(String estadoDropi) {
        String normalizado = normalizarTexto(estadoDropi);
        if (normalizado.isEmpty()) {
            return null;
        }

        for (String valido : ESTADOS_VALIDOS) {
            if (normalizado.equals(normalizarTexto(valido))) {
                return valido;
            }
        }
        for (Map.Entry<String, String> equivalencia : EQUIVALENCIAS.entrySet()) {
            if (normalizado.contains(equivalencia.getKey())) {
                return equivalencia.getValue();
            }
        }
        return null;
    }

    /** Primer texto con contenido (para ID de Dropi con respaldo al código). */
    private static String primerNoVacio(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor.trim();
            }
        }
        return "";
    }

    /** Minúsculas, sin tildes y con separadores colapsados en un espacio. */
    private static String normalizarTexto(String valor) {
        if (valor == null) {
            return "";
        }
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    /**
     * Estados que el sync nunca escribe (documental, para soporte).
     *
     * @return copia inmutable de los estados aceptados por el panel
     */
    public static Collection<String> estadosValidos() {
        return List.copyOf(ESTADOS_VALIDOS);
    }
}
