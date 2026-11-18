package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.Pedido;
import com.flowcolombia.flowcolombia.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba el sync con Dropi sin red, sin Spring y sin Mockito.
 *
 * <p>El repositorio se simula con un {@link Proxy} (patrón del proyecto) y el
 * cliente de Dropi con una implementación de mentira. Lo que se verifica es la
 * regla de seguridad del brief: <strong>solo se escriben {@code estado} y
 * {@code numeroGuia}</strong>, ningún fallo de Dropi puede romper la tienda, y
 * sin configuración el robot no toca ni la base de datos.</p>
 */
class DropiSyncServiceTest {

    /** Pedidos que el repositorio devolverá como «abiertos». */
    private List<Pedido> abiertos;

    /** Pedidos que el servicio intentó guardar. */
    private final List<Pedido> guardados = new ArrayList<>();

    /** Respuesta simulada de Dropi, indexada por el identificador consultado. */
    private Map<String, DropiEstado> respuestaDropi;

    private boolean dropiFalla;
    private boolean dropiConfigurado;

    /** Cuántas veces el servicio consultó la base de datos. */
    private int consultasAlRepositorio;

    private DropiSyncService servicio;

    @BeforeEach
    void preparar() {
        abiertos = new ArrayList<>();
        guardados.clear();
        respuestaDropi = new HashMap<>();
        dropiFalla = false;
        dropiConfigurado = true;
        consultasAlRepositorio = 0;

        DropiClient clienteFalso = new DropiClient() {
            @Override
            public boolean estaConfigurado() {
                return dropiConfigurado;
            }

            @Override
            public Map<String, DropiEstado> consultarEstados(java.util.Collection<String> referencias) {
                if (dropiFalla) {
                    throw new IllegalStateException("Dropi no responde");
                }
                Map<String, DropiEstado> filtrado = new HashMap<>();
                for (String referencia : referencias) {
                    DropiEstado estado = respuestaDropi.get(referencia);
                    if (estado != null) {
                        filtrado.put(referencia, estado);
                    }
                }
                return filtrado;
            }
        };

        servicio = new DropiSyncService(repositorioSimulado(), clienteFalso);
    }

    private static Pedido pedido(String codigo, String dropiOrderId, String estado, String guia) {
        Pedido pedido = new Pedido();
        pedido.setCodigo(codigo);
        pedido.setDropiOrderId(dropiOrderId);
        pedido.setEstado(estado);
        pedido.setNumeroGuia(guia);
        pedido.setNombreCliente("Cliente Prueba");
        pedido.setTelefono("3001234567");
        pedido.setDireccion("Calle Falsa 123");
        pedido.setTotal(89900d);
        return pedido;
    }

    @Test
    @DisplayName("Solo cambian estado y número de guía; los datos del cliente quedan intactos")
    void actualizaSoloEstadoYGuia() {
        Pedido pedido = pedido("FLOW-ABC1234567", null, "Pendiente", null);
        abiertos.add(pedido);

        respuestaDropi.put("FLOW-ABC1234567",
                new DropiEstado("FLOW-ABC1234567", null, "EN_TRANSITO", "GUIA-999"));

        int cambios = servicio.sincronizar();

        assertEquals(1, cambios, "debe contar un pedido actualizado");
        assertEquals(1, guardados.size(), "debe guardar el pedido modificado");
        assertEquals("Enviado", pedido.getEstado(), "EN_TRANSITO se traduce a Enviado");
        assertEquals("GUIA-999", pedido.getNumeroGuia());

        // La regla de privacidad: nada personal se toca con datos de Dropi.
        assertEquals("Cliente Prueba", pedido.getNombreCliente());
        assertEquals("3001234567", pedido.getTelefono());
        assertEquals("Calle Falsa 123", pedido.getDireccion());
    }

    @Test
    @DisplayName("Con ID de Dropi se consulta ese ID, no el código FLOW")
    void usaDropiOrderIdCuandoExiste() {
        Pedido pedido = pedido("FLOW-ABC1234567", "9876", "Pendiente", null);
        abiertos.add(pedido);

        // La respuesta llega indexada por el ID de Dropi.
        respuestaDropi.put("9876",
                new DropiEstado(null, "9876", "ENTREGADO", "GUIA-1"));

        int cambios = servicio.sincronizar();

        assertEquals(1, cambios);
        assertEquals("Entregado", pedido.getEstado());
        assertEquals("GUIA-1", pedido.getNumeroGuia());
    }

    @Test
    @DisplayName("Sin diferencias no se escribe nada en la base de datos")
    void sinCambiosNoGuarda() {
        Pedido pedido = pedido("FLOW-ABC1234567", null, "Pendiente", null);
        abiertos.add(pedido);

        respuestaDropi.put("FLOW-ABC1234567",
                new DropiEstado("FLOW-ABC1234567", null, "PENDIENTE", null));

        int cambios = servicio.sincronizar();

        assertEquals(0, cambios);
        assertTrue(guardados.isEmpty(), "un sync sin cambios no debe escribir");
    }

    @Test
    @DisplayName("Un identificador que no es nuestro se ignora")
    void referenciaAjenaSeIgnora() {
        Pedido pedido = pedido("FLOW-ABC1234567", null, "Pendiente", null);
        abiertos.add(pedido);

        respuestaDropi.put("FLOW-OTRO999999",
                new DropiEstado("FLOW-OTRO999999", null, "ENTREGADO", "GUIA-000"));

        int cambios = servicio.sincronizar();

        assertEquals(0, cambios);
        assertTrue(guardados.isEmpty());
        assertEquals("Pendiente", pedido.getEstado());
    }

    @Test
    @DisplayName("Si Dropi falla, no se propaga la excepción ni se guarda nada")
    void falloDeDropiNoRompeNiGuarda() {
        abiertos.add(pedido("FLOW-ABC1234567", null, "Pendiente", null));
        dropiFalla = true;

        int cambios = servicio.sincronizar();

        assertEquals(0, cambios, "un fallo de Dropi se traduce en 0 cambios");
        assertTrue(guardados.isEmpty(), "no debe quedar ninguna escritura a medias");
    }

    @Test
    @DisplayName("Un estado desconocido no se aplica, pero la guía sí se guarda")
    void estadoDesconocidoNoSeAplica() {
        Pedido pedido = pedido("FLOW-ABC1234567", null, "Procesando", null);
        abiertos.add(pedido);

        respuestaDropi.put("FLOW-ABC1234567",
                new DropiEstado("FLOW-ABC1234567", null, "CANCELADO", "GUIA-777"));

        int cambios = servicio.sincronizar();

        assertEquals(1, cambios);
        assertEquals("Procesando", pedido.getEstado(), "un estado no reconocido no reescribe el del panel");
        assertEquals("GUIA-777", pedido.getNumeroGuia(), "la guía sí es dato seguro de guardar");
    }

    @Test
    @DisplayName("Sin pedidos abiertos no se consulta a Dropi")
    void sinAbiertosNoConsulta() {
        int cambios = servicio.sincronizar();

        assertEquals(0, cambios);
        assertTrue(guardados.isEmpty());
    }

    @Test
    @DisplayName("Sin DROPI_API_URL/TOKEN el robot duerme y no toca la base de datos")
    void robotDormidoNoConsultaLaBase() {
        dropiConfigurado = false;
        abiertos.add(pedido("FLOW-ABC1234567", null, "Pendiente", null));

        int cambios = servicio.sincronizar();

        assertEquals(0, cambios);
        assertEquals(0, consultasAlRepositorio,
                "sin configuración no se debe consultar Neon ni llamar a Dropi");
        assertTrue(guardados.isEmpty());
    }

    @Test
    @DisplayName("Traducción de estados de Dropi a los del panel")
    void traduccionDeEstados() {
        assertEquals("Entregado", DropiSyncService.traducirEstado("ENTREGADO"));
        assertEquals("Entregado", DropiSyncService.traducirEstado("entregado"));
        assertEquals("Enviado", DropiSyncService.traducirEstado("EN_TRANSITO"));
        assertEquals("Enviado", DropiSyncService.traducirEstado("GUIA_GENERADA"));
        assertEquals("Enviado", DropiSyncService.traducirEstado("shipped"));
        assertEquals("Procesando", DropiSyncService.traducirEstado("EN_PREPARACION"));
        assertEquals("Pendiente", DropiSyncService.traducirEstado("por despachar"));

        assertNull(DropiSyncService.traducirEstado("CANCELADO"), "cancelado no lo aplica el bot");
        assertNull(DropiSyncService.traducirEstado(null));
        assertNull(DropiSyncService.traducirEstado("   "));
        assertNotNull(DropiSyncService.estadosValidos());
    }

    /** Repositorio de mentira: devuelve los «abiertos» y captura los saveAll. */
    @SuppressWarnings("unchecked")
    private PedidoRepository repositorioSimulado() {
        return (PedidoRepository) Proxy.newProxyInstance(
                PedidoRepository.class.getClassLoader(),
                new Class<?>[]{PedidoRepository.class},
                (proxy, metodo, argumentos) -> {
                    switch (metodo.getName()) {
                        case "findAbiertosParaSync":
                            consultasAlRepositorio++;
                            return abiertos;
                        case "saveAll":
                            List<Pedido> lista = (List<Pedido>) argumentos[0];
                            guardados.addAll(lista);
                            return lista;
                        default:
                            if (metodo.getReturnType() == List.class) return List.of();
                            if (metodo.getReturnType() == boolean.class) return false;
                            if (metodo.getReturnType() == long.class) return 0L;
                            return null;
                    }
                });
    }
}
