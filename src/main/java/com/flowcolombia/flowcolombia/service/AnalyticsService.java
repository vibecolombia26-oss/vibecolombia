package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.PedidoRepository;
import com.flowcolombia.flowcolombia.VarianteProductoRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rentabilidad del negocio: márgenes por producto y ganancia real de los
 * pedidos ya entregados.
 *
 * <p><strong>Coste en Neon:</strong> todo se resuelve con consultas
 * agregadas ({@code AVG}, {@code GROUP BY}) y proyecciones de columnas
 * sueltas. Se hace un número fijo de consultas por carga del panel, nunca una
 * por producto ni una por pedido (nada de N+1).</p>
 *
 * <p><strong>Por qué la ganancia se calcula en Java:</strong> el pedido guarda
 * los productos como un texto ({@code "Nombre x2 (Talla: M) - $89.900"}), así
 * que no hay forma razonable de hacer el {@code JOIN} en SQL. Se traen solo las
 * filas de pedidos entregados (detalle + total) y el texto se cruza contra un
 * índice de costos construido con <em>una</em> consulta.</p>
 */
@Service
public class AnalyticsService {

    // ============================================================
    // Formatos del detalle guardado en el pedido
    // ============================================================
    /** "Mochila Compact x2 ..." → 2 unidades */
    private static final Pattern CANTIDAD = Pattern.compile("\\s+x\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    /** "(Color: Negro)" */
    private static final Pattern COLOR = Pattern.compile("\\(\\s*color\\s*:\\s*([^)]*)\\)", Pattern.CASE_INSENSITIVE);
    /** "(Talla: M)" */
    private static final Pattern TALLA = Pattern.compile("\\(\\s*talla\\s*:\\s*([^)]*)\\)", Pattern.CASE_INSENSITIVE);
    /** Cualquier paréntesis que quede dentro del nombre (formatos antiguos) */
    private static final Pattern PARENTESIS = Pattern.compile("\\([^)]*\\)");
    /** Importe al final de la línea: "- $ 89.900" o "- $89.900" */
    private static final Pattern IMPORTE = Pattern.compile("-\\s*\\$?\\s*([0-9][0-9.,]*)\\s*$");

    private final PedidoRepository pedidoRepository;
    private final VarianteProductoRepository varianteProductoRepository;

    public AnalyticsService(PedidoRepository pedidoRepository,
                            VarianteProductoRepository varianteProductoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.varianteProductoRepository = varianteProductoRepository;
    }

    // ============================================================
    // DTOs de salida
    // ============================================================

    /**
     * Margen de una unidad.
     *
     * @param hayDatos {@code false} cuando no hay ningún costo registrado
     */
    public record MargenVista(Double precioPromedio,
                              Double costoPromedio,
                              Double margen,
                              Integer porcentaje,
                              boolean hayDatos) {
    }

    /**
     * Ganancia real de los pedidos entregados.
     *
     * @param ingresos   suma de los totales de los pedidos entregados
     * @param costos     costo de proveedor estimado de lo vendido
     * @param ganancia   {@code ingresos - costos}
     * @param pedidos    número de pedidos entregados considerados
     * @param cobertura  fracción del ingreso cuyo costo se pudo resolver (0-1)
     * @param porcentaje margen sobre ingresos (0-100)
     */
    public record GananciaEntregada(Double ingresos,
                                    Double costos,
                                    Double ganancia,
                                    long pedidos,
                                    double cobertura,
                                    int porcentaje) {
    }

    // ============================================================
    // Margen del catálogo
    // ============================================================

    /**
     * Margen promedio del catálogo: precio promedio de venta menos costo
     * promedio de proveedor, calculado sobre las variantes que tienen costo.
     * Una sola consulta.
     */
    public MargenVista margenCatalogo() {
        List<Object[]> filas = varianteProductoRepository.promediosCatalogo();
        if (filas == null || filas.isEmpty() || filas.get(0) == null) {
            return margenVacio();
        }

        Object[] fila = filas.get(0);
        Double precio = numero(fila, 0);
        Double costo = numero(fila, 1);

        if (precio == null || precio <= 0 || costo == null) {
            return margenVacio();
        }

        double margen = precio - costo;
        return new MargenVista(
                redondear(precio),
                redondear(costo),
                redondear(margen),
                porcentaje(margen, precio),
                true);
    }

    /**
     * Margen por producto para la columna de la tabla del catálogo.
     * Una sola consulta con {@code GROUP BY}: no hay N+1.
     *
     * @return mapa {@code productoId → margen}, sin entradas para productos
     *         cuyas variantes no tienen costo registrado
     */
    public Map<Long, MargenVista> margenPorProducto() {
        List<Object[]> filas = varianteProductoRepository.margenPorProducto();
        Map<Long, MargenVista> margenes = new LinkedHashMap<>();

        if (filas == null) {
            return margenes;
        }

        for (Object[] fila : filas) {
            if (fila == null || fila.length < 3 || !(fila[0] instanceof Number crudoId)) {
                continue;
            }

            Double precio = numero(fila, 1);
            Double costo = numero(fila, 2);

            if (precio == null || precio <= 0 || costo == null) {
                continue;
            }

            double margen = precio - costo;
            margenes.put(crudoId.longValue(), new MargenVista(
                    redondear(precio),
                    redondear(costo),
                    redondear(margen),
                    porcentaje(margen, precio),
                    true));
        }

        return margenes;
    }

    // ============================================================
    // Ganancia de los pedidos entregados
    // ============================================================

    /**
     * Ganancia neta de lo ya entregado (dinero real):
     * {@code sum(total de entregados) - sum(costo de proveedor de lo vendido)}.
     *
     * <p>Se apoya en dos consultas: la lista de pedidos entregados
     * (detalle + total) y el índice de costos de las variantes. El cruce se
     * hace en memoria porque el detalle es texto libre.</p>
     *
     * <p>Si una línea del pedido no se puede asociar a un producto con costo,
     * su coste se considera desconocido: la ganancia saldría sobreestimada,
     * así que se informa en {@link GananciaEntregada#cobertura()} qué parte
     * del ingreso está realmente valorada.</p>
     */
    public GananciaEntregada gananciaEntregada() {
        List<Object[]> pedidos = pedidoRepository.findEntregadosParaMargen();
        if (pedidos == null || pedidos.isEmpty()) {
            return new GananciaEntregada(0.0, 0.0, 0.0, 0L, 0.0, 0);
        }

        IndiceCostos indice = construirIndiceCostos();

        double ingresos = 0.0;
        double costos = 0.0;
        double ingresosValorados = 0.0;

        for (Object[] fila : pedidos) {
            if (fila == null) {
                continue;
            }

            String detalle = fila[0] instanceof String texto ? texto : null;
            Double totalFila = numero(fila, 1);
            double total = (totalFila == null || totalFila < 0) ? 0.0 : totalFila;

            ingresos += total;

            double costoPedido = 0.0;
            double valoradoPedido = 0.0;

            for (String linea : separarLineas(detalle)) {
                LineaPedido parseada = parsearLinea(linea);
                if (parseada == null) {
                    continue;
                }

                Double costoUnitario = indice.costoDe(parseada.nombre(), parseada.color(), parseada.talla());
                if (costoUnitario == null) {
                    continue;
                }

                costoPedido += costoUnitario * parseada.cantidad();
                if (parseada.importe() > 0) {
                    valoradoPedido += parseada.importe();
                }
            }

            costos += costoPedido;
            // Nunca se valora más ingreso que el total real del pedido.
            ingresosValorados += Math.min(valoradoPedido, total);
        }

        double ganancia = ingresos - costos;
        double cobertura = ingresos > 0 ? Math.min(1.0, ingresosValorados / ingresos) : 0.0;

        return new GananciaEntregada(
                redondear(ingresos),
                redondear(costos),
                redondear(ganancia),
                pedidos.size(),
                cobertura,
                ingresos > 0 ? (int) Math.round(ganancia / ingresos * 100) : 0);
    }

    // ============================================================
    // Índice de costos (una consulta)
    // ============================================================
    private record IndiceCostos(Map<String, Double> porVariante,
                                Map<String, double[]> acumuladoPorNombre) {

        /** Coste unitario: primero la variante exacta, si no el promedio del producto. */
        Double costoDe(String nombre, String color, String talla) {
            Double exacto = porVariante.get(claveVariante(nombre, color, talla));
            if (exacto != null) {
                return exacto;
            }
            double[] acumulado = acumuladoPorNombre.get(normalizar(nombre));
            if (acumulado == null || acumulado[1] <= 0) {
                return null;
            }
            return acumulado[0] / acumulado[1];
        }
    }

    private IndiceCostos construirIndiceCostos() {
        Map<String, Double> porVariante = new HashMap<>();
        Map<String, double[]> acumuladoPorNombre = new HashMap<>();

        List<Object[]> filas = varianteProductoRepository.variantesConCosto();
        if (filas == null) {
            return new IndiceCostos(porVariante, acumuladoPorNombre);
        }

        for (Object[] fila : filas) {
            if (fila == null || fila.length < 5) {
                continue;
            }

            String nombre = fila[0] instanceof String texto ? texto : "";
            String color = fila[2] instanceof String texto ? texto : "";
            String talla = fila[3] instanceof String texto ? texto : "";
            Double costo = numero(fila, 4);

            if (costo == null || costo <= 0 || nombre.isBlank()) {
                continue;
            }

            porVariante.putIfAbsent(claveVariante(nombre, color, talla), costo);

            double[] acumulado = acumuladoPorNombre.computeIfAbsent(normalizar(nombre), clave -> new double[2]);
            acumulado[0] += costo;
            acumulado[1] += 1;
        }

        return new IndiceCostos(porVariante, acumuladoPorNombre);
    }

    // ============================================================
    // Lectura del detalle del pedido
    // ============================================================
    private record LineaPedido(String nombre, String color, String talla, int cantidad, double importe) {
    }

    /**
     * Interpreta una línea del detalle. Tolera los dos formatos históricos:
     * con cantidad ("Nombre x2 ...") y sin ella ("Nombre - $50.000").
     *
     * @return {@code null} si la línea no aporta información útil
     */
    private LineaPedido parsearLinea(String linea) {
        if (linea == null) {
            return null;
        }

        String texto = linea.trim();
        if (texto.isEmpty() || texto.equalsIgnoreCase("Productos no especificados")) {
            return null;
        }

        Matcher cantidad = CANTIDAD.matcher(texto);
        String nombre;
        int unidades;

        if (cantidad.find()) {
            unidades = parsearEntero(cantidad.group(1));
            nombre = texto.substring(0, cantidad.start()).trim();
        } else {
            // Formato antiguo sin "xN": se asume una unidad por línea.
            unidades = 1;
            int corte = texto.indexOf(" - ");
            nombre = corte > 0 ? texto.substring(0, corte).trim() : texto;
        }

        String color = extraer(COLOR, texto);
        String talla = extraer(TALLA, texto);

        // En el formato antiguo los paréntesis pueden haber quedado en el nombre.
        nombre = PARENTESIS.matcher(nombre).replaceAll(" ").replaceAll("\\s+", " ").trim();

        if (nombre.isEmpty()) {
            return null;
        }

        return new LineaPedido(nombre, color, talla, Math.max(1, unidades), importeDe(texto));
    }

    private static List<String> separarLineas(String detalle) {
        if (detalle == null || detalle.isBlank()) {
            return List.of();
        }
        List<String> lineas = new ArrayList<>();
        for (String linea : detalle.split("\\r?\\n")) {
            if (!linea.isBlank()) {
                lineas.add(linea);
            }
        }
        return lineas;
    }

    private static String extraer(Pattern patron, String texto) {
        Matcher matcher = patron.matcher(texto);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    /** Importe de la línea: se queda solo con los dígitos (formato es-CO). */
    private static double importeDe(String texto) {
        Matcher matcher = IMPORTE.matcher(texto);
        if (!matcher.find()) {
            return 0.0;
        }
        String digitos = matcher.group(1).replaceAll("[^0-9]", "");
        if (digitos.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(digitos);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static int parsearEntero(String valor) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    // ============================================================
    // Utilidades
    // ============================================================
    private static MargenVista margenVacio() {
        return new MargenVista(0.0, 0.0, 0.0, 0, false);
    }

    private static int porcentaje(double margen, double precio) {
        if (precio <= 0) {
            return 0;
        }
        return (int) Math.round(margen / precio * 100);
    }

    private static double redondear(double valor) {
        return (double) Math.round(valor);
    }

    private static Double numero(Object[] fila, int indice) {
        if (fila == null || indice < 0 || indice >= fila.length) {
            return null;
        }
        Object valor = fila[indice];
        if (valor instanceof Number numero) {
            return numero.doubleValue();
        }
        if (valor instanceof String texto && !texto.isBlank()) {
            try {
                return Double.parseDouble(texto.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String claveVariante(String nombre, String color, String talla) {
        return normalizar(nombre) + '|' + normalizar(color) + '|' + normalizar(talla);
    }
}
