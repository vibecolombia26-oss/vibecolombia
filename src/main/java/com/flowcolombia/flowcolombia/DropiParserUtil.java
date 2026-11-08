package com.flowcolombia.flowcolombia;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lector determinista de inventarios de Dropi.
 *
 * <h2>Por qué esto no lo hace la IA</h2>
 * <p>Cuando el administrador pega el inventario de un proveedor, el texto es
 * largo, repetitivo y con formato irregular. Si se lo mandáramos al modelo para
 * que «lo entienda», pagaríamos tokens por cada importación, el resultado
 * cambiaría entre ejecuciones (temperatura &gt; 0) y un número mal copiado
 * terminaría en la base de datos. Aquí el texto se lee con expresiones
 * regulares: el resultado es <strong>idéntico siempre</strong>, no cuesta un
 * solo token y se puede probar con tests unitarios.</p>
 *
 * <h2>Formatos que entiende</h2>
 * <ol>
 *   <li><strong>Bloques con etiquetas</strong> (lo que muestra Dropi al copiar
 *       una ficha):
 * <pre>
 * Color: Negro Tallas: 38, 39, 40
 * Stock: 12
 * Costo: $45.000
 * Precio sugerido: $89.900
 * ID: 1234567
 * </pre>
 *       Acepta también una talla (o un color) por línea, varias tallas en la
 *       misma etiqueta, y etiquetas alternativas
 *       ({@code Inventario}, {@code Cantidad}, {@code Costo unitario},
 *       {@code Precio de venta}, {@code PVP}…).</li>
 *   <li><strong>Tablas pegadas</strong> (Dropi exporta y el navegador copia
 *       como tabla enriquecida). Se detecta la fila de encabezado y se leen las
 *       filas por posición de columna, separadas por {@code |}, tabulador o dos
 *       o más espacios.</li>
 * </ol>
 *
 * <h2>Tolerancia a fallos</h2>
 * <p>Ninguna línea puede hacer fallar el proceso: si una etiqueta está mal
 * escrita, su valor no es un número o una fila de la tabla viene incompleta,
 * esa pieza se descarta y el resto se sigue leyendo. Los bloques se cierran por
 * etiquetas, no por número de líneas, así que una línea en blanco o un texto
 * suelto en medio no rompe el bloque.</p>
 *
 * <h2>Ruido que se ignora</h2>
 * <p>Identificadores de Dropi ({@code ID}, {@code SKU}, {@code Referencia},
 * {@code Peso}), líneas vacías y todo lo que no sea una etiqueta conocida.</p>
 *
 * <p>Esta clase no toca la base de datos ni conoce entidades JPA: solo texto.</p>
 */
public final class DropiParserUtil {

    /** Tope de texto que se analiza (protege CPU de un pegado gigante). */
    private static final int MAX_TEXTO = 60_000;

    /** Tope de variantes por importación (protege la escritura en Neon). */
    private static final int MAX_VARIANTES = 400;

    /** Longitud de la columna {@code variante_producto.color}. */
    private static final int MAX_COLOR = 255;

    /** Longitud de la columna {@code variante_producto.talla}. */
    private static final int MAX_TALLA = 20;

    /** Un color es una palabra corta: más largo que esto es prosa, no un color. */
    private static final int MAX_NOMBRE_COLOR = 40;

    /**
     * Etiquetas reconocidas. Van ordenadas de lo más específico a lo más
     * general porque la regex se queda con la primera alternativa que encaje.
     */
    private static final String ETIQUETAS =
            "(?:colou?r(?:es)?"
            + "|tallas?|talle"
            + "|stock|inventario|cantidad|disponibles?|unidades"
            + "|costo(?:\\s+(?:unitario|de\\s+compra|proveedor|del\\s+proveedor))?"
            + "|precio(?:\\s+(?:sugerido|de\\s+venta|venta|al\\s+p[uú]blico|final|unitario))?"
            + "|pvp"
            + "|sku|referencia|peso|id(?:\\s+(?:de\\s+)?producto)?)";

    /**
     * Etiqueta + separador. El separador puede ser {@code :}, {@code =},
     * un guion o simplemente un espacio, porque Dropi alterna entre
     * {@code "Color: Negro"}, {@code "Color - Negro"} y {@code "Color   Negro"}.
     */
    private static final Pattern ETIQUETA = Pattern.compile(
            "(?iu)\\b" + ETIQUETAS + "(?:\\s*[:=]\\s*|\\s*[\\-\u2013\u2014]\\s*|\\s+)");

    /**
     * Tallas válidas: números de calzado (35-46, 2-16 infantil) y las tallas
     * de letra del textil. La lista es cerrada a propósito: así una frase como
     * «la talla M de la mochila negra» no inventa las tallas «la», «mochila»
     * ni «negra».
     */
    private static final Pattern TALLA_VALIDA = Pattern.compile(
            "(?iu)^(?:\\d{1,4}"
            + "|xxxs|xxs|xs|s|m|l|xl|xxl|xxxl|xxxxl|\\d?xl|[2-6]xl"
            + "|[uú]nic[ao]|est[aá]ndar)$");

    /** Palabras que nunca son una talla aunque caigan en el filtro anterior. */
    private static final Pattern TALLA_PROHIBIDA = Pattern.compile(
            "(?iu)^(?:y|e|o|u|de|del|la|el|los|las|un|una|para|con|sin|talla|tallas)$");

    private DropiParserUtil() {
        // Clase de utilidades: no se instancia.
    }

    // ============================================================
    // API PÚBLICA
    // ============================================================

    /**
     * Lee el texto crudo y devuelve las variantes detectadas.
     *
     * <p>Primero intenta la lectura de tabla (si hay encabezado) y, si no
     * encontró nada, la lectura por etiquetas. Nunca lanza excepciones: ante
     * una entrada inservible devuelve la lista vacía.</p>
     *
     * @param textoCrudo lo que el administrador pegó en el chat
     * @return variantes en el orden en que aparecen, sin repetir Color+Talla
     */
    public static List<VarianteImportada> parsear(String textoCrudo) {
        if (textoCrudo == null || textoCrudo.isBlank()) {
            return List.of();
        }

        String texto = textoCrudo.length() > MAX_TEXTO
                ? textoCrudo.substring(0, MAX_TEXTO)
                : textoCrudo;

        Map<String, VarianteImportada> encontradas = new LinkedHashMap<>();

        try {
            parsearTabla(texto, encontradas);
        } catch (RuntimeException e) {
            // Tolerante: una tabla mal formada no debe abortar el proceso.
            encontradas.clear();
        }

        if (encontradas.isEmpty()) {
            try {
                parsearEtiquetas(texto, encontradas);
            } catch (RuntimeException e) {
                encontradas.clear();
            }
        }

        return new ArrayList<>(encontradas.values());
    }

    /**
     * ¿El texto parece un inventario y no una frase normal?
     *
     * <p>Se usa para decidir dos cosas: no enviarle el texto entero al modelo
     * (ahorra tokens) y no confundir «sube el stock de MOCH-M a 20» con una
     * importación. Ante la duda es conservador: exige varias variantes o que la
     * única detectada traiga algún número.</p>
     */
    public static boolean pareceInventario(String texto) {
        return esInventario(parsear(texto));
    }

    /**
     * Igual que {@link #pareceInventario(String)} pero sobre variantes ya
     * leídas, para no analizar el mismo texto dos veces.
     */
    public static boolean esInventario(List<VarianteImportada> variantes) {
        if (variantes == null || variantes.isEmpty()) {
            return false;
        }
        if (variantes.size() >= 2) {
            return true;
        }
        return variantes.get(0).tieneDatos();
    }

    /**
     * Convierte un valor de dinero escrito a mano («99.999», «$ 60.000»,
     * «99999») al número que usan las herramientas.
     *
     * <p>Es exactamente la misma regla con la que se leen los precios del texto
     * de Dropi, así que un override escrito por el administrador y un precio
     * leído del texto nunca se interpretan distinto. También sirve cuando el
     * modelo manda el override como texto en vez de como número.</p>
     *
     * @return el valor, o {@code null} si el texto no contiene cifras legibles
     */
    public static Double numeroColombiano(String texto) {
        return numero(texto);
    }

    /**
     * Clave de identidad de una variante: «color|talla» normalizada.
     *
     * <p>La usan el parser (para no repetir variantes dentro del mismo texto) y
     * el servicio (para emparejar el texto con las filas que ya existen en la
     * base). Al normalizar, {@code "Negro"} y {@code " negro "} son lo mismo.</p>
     */
    public static String clave(String color, String talla) {
        return normalizar(color) + "|" + normalizar(talla);
    }

    // ============================================================
    // ESTRATEGIA 2: TABLAS PEGADAS (encabezado + filas)
    // ============================================================

    /**
     * Detecta una fila de encabezado con al menos tres columnas conocidas y
     * lee las filas siguientes por posición de columna.
     */
    private static void parsearTabla(String texto, Map<String, VarianteImportada> salida) {
        String[] lineas = texto.split("\\R", -1);

        for (int i = 0; i < lineas.length; i++) {
            String[] cabecera = dividirCeldas(lineas[i]);
            if (cabecera.length < 3) {
                continue;
            }

            int iColor = -1;
            int iTalla = -1;
            int iStock = -1;
            int iCosto = -1;
            int iPrecio = -1;

            for (int c = 0; c < cabecera.length; c++) {
                switch (tipoEtiqueta(cabecera[c])) {
                    case "COLOR" -> iColor = (iColor < 0) ? c : iColor;
                    case "TALLA" -> iTalla = (iTalla < 0) ? c : iTalla;
                    case "STOCK" -> iStock = (iStock < 0) ? c : iStock;
                    case "COSTO" -> iCosto = (iCosto < 0) ? c : iCosto;
                    case "PRECIO" -> iPrecio = (iPrecio < 0) ? c : iPrecio;
                    default -> { /* columna no relevante (ID, SKU, peso…) */ }
                }
            }

            int conocidas = (iColor >= 0 ? 1 : 0) + (iTalla >= 0 ? 1 : 0) + (iStock >= 0 ? 1 : 0)
                    + (iCosto >= 0 ? 1 : 0) + (iPrecio >= 0 ? 1 : 0);

            if (conocidas < 3) {
                continue;   // esta línea no es un encabezado
            }

            // Filas de datos de esta tabla.
            for (int j = i + 1; j < lineas.length; j++) {
                String linea = lineas[j];
                if (linea == null || linea.isBlank()) {
                    continue;
                }

                String[] celdas = dividirCeldas(linea);
                if (celdas.length < 2 || esCabecera(celdas)) {
                    continue;   // línea suelta o el encabezado repetido
                }

                Borrador fila = new Borrador();
                fila.colores.addAll(iColor >= 0 && iColor < celdas.length
                        ? dividirColores(celdas[iColor])
                        : List.of());
                if (iTalla >= 0 && iTalla < celdas.length) {
                    fila.tallas.addAll(dividirTallas(celdas[iTalla]));
                }
                fila.stock = iStock >= 0 && iStock < celdas.length ? entero(celdas[iStock]) : null;
                fila.costo = iCosto >= 0 && iCosto < celdas.length ? numero(celdas[iCosto]) : null;
                fila.precio = iPrecio >= 0 && iPrecio < celdas.length ? numero(celdas[iPrecio]) : null;

                cerrar(fila, salida, false);
            }

            return;   // solo se procesa la primera tabla encontrada
        }
    }

    // ============================================================
    // ESTRATEGIA 1: BLOQUES CON ETIQUETAS
    // ============================================================

    /**
     * Recorre línea a línea buscando etiquetas y arma variantes.
     *
     * <p>El valor de cada etiqueta es el texto que va desde el final de la
     * etiqueta hasta el comienzo de la siguiente <em>en la misma línea</em>. Por
     * eso funciona igual con una etiqueta por línea que con todo en una sola:
     * {@code "Color: Negro Tallas: 38, 39 Stock: 4"}.</p>
     */
    private static void parsearEtiquetas(String texto, Map<String, VarianteImportada> salida) {
        Borrador actual = new Borrador();
        String[] lineas = texto.split("\\R", -1);

        for (String linea : lineas) {
            if (linea == null || linea.isBlank()) {
                continue;
            }

            Matcher m = ETIQUETA.matcher(linea);
            List<int[]> posiciones = new ArrayList<>();
            List<String> etiquetas = new ArrayList<>();

            while (m.find()) {
                posiciones.add(new int[]{ m.start(), m.end() });
                etiquetas.add(m.group());
            }

            if (posiciones.isEmpty()) {
                continue;   // línea sin etiquetas: ruido, se ignora
            }

            for (int i = 0; i < posiciones.size(); i++) {
                int desde = posiciones.get(i)[1];
                int hasta = (i + 1 < posiciones.size()) ? posiciones.get(i + 1)[0] : linea.length();
                if (hasta <= desde) {
                    continue;   // etiqueta sin valor
                }

                String valor = linea.substring(desde, hasta).trim();
                if (valor.isEmpty()) {
                    continue;
                }

                switch (tipoEtiqueta(etiquetas.get(i))) {
                    case "COLOR" -> {
                        List<String> colores = dividirColores(valor);
                        if (!colores.isEmpty()) {
                            cerrar(actual, salida, true);
                            actual = new Borrador();
                            actual.colores.addAll(colores);
                        }
                    }
                    case "TALLA" -> {
                        List<String> tallas = dividirTallas(valor);
                        if (!tallas.isEmpty()) {
                            // Si el bloque ya traía stock o precios, esta talla
                            // empieza una variante nueva del mismo color.
                            if (actual.tieneDatos() || !actual.tallas.isEmpty()) {
                                cerrar(actual, salida, true);
                                Borrador nueva = new Borrador();
                                nueva.colores.addAll(actual.colores);
                                actual = nueva;
                            }
                            actual.tallas.addAll(tallas);
                        }
                    }
                    case "STOCK" -> {
                        Integer valorStock = entero(valor);
                        if (valorStock != null) {
                            actual.stock = valorStock;
                        }
                    }
                    case "COSTO" -> {
                        Double valorCosto = numero(valor);
                        if (valorCosto != null && valorCosto > 0) {
                            actual.costo = valorCosto;
                        }
                    }
                    case "PRECIO" -> {
                        Double valorPrecio = numero(valor);
                        if (valorPrecio != null && valorPrecio > 0) {
                            actual.precio = valorPrecio;
                        }
                    }
                    default -> {
                        // SKU, ID, referencia, peso: se ignoran a propósito.
                    }
                }
            }
        }

        cerrar(actual, salida, true);
    }

    // ============================================================
    // BORRADOR Y CIERRE DE BLOQUES
    // ============================================================

    /** Variante en construcción mientras se leen etiquetas. */
    private static final class Borrador {
        final List<String> colores = new ArrayList<>();
        final List<String> tallas = new ArrayList<>();
        Integer stock;
        Double costo;
        Double precio;

        boolean tieneDatos() {
            return stock != null || costo != null || precio != null;
        }

        boolean vacio() {
            return colores.isEmpty() && tallas.isEmpty() && !tieneDatos();
        }
    }

    /**
     * Convierte el borrador en una o varias {@link VarianteImportada} y lo
     * guarda en el mapa (que deduplica por Color+Talla).
     *
     * @param exigirColor {@code true} en la lectura por etiquetas: sin un color
     *                    reconocido no se acepta nada, porque una frase suelta
     *                    («la talla M») no es una variante de inventario
     */
    private static void cerrar(Borrador borrador, Map<String, VarianteImportada> salida, boolean exigirColor) {
        if (borrador == null || borrador.vacio() || salida.size() >= MAX_VARIANTES) {
            return;
        }
        if (exigirColor && borrador.colores.isEmpty()) {
            return;
        }
        if (borrador.colores.isEmpty() && borrador.tallas.isEmpty()) {
            return;
        }

        List<String> colores = borrador.colores.isEmpty()
                ? Collections.singletonList(null)
                : borrador.colores;
        List<String> tallas = borrador.tallas.isEmpty()
                ? Collections.singletonList(null)
                : borrador.tallas;

        for (String color : colores) {
            for (String talla : tallas) {
                if (salida.size() >= MAX_VARIANTES) {
                    return;
                }
                VarianteImportada variante = new VarianteImportada(
                        color, talla, borrador.stock, borrador.costo, borrador.precio);
                // La última lectura de una misma variante gana (y conserva el
                // orden de aparición, que es el que ve el administrador).
                salida.put(variante.clave(), variante);
            }
        }
    }

    // ============================================================
    // TROCEADO DE LÍNEAS Y VALORES
    // ============================================================

    /** ¿Esta fila es otra vez el encabezado de la tabla? */
    private static boolean esCabecera(String[] celdas) {
        int conocidas = 0;
        for (String celda : celdas) {
            if (!"OTRO".equals(tipoEtiqueta(celda))) {
                conocidas++;
            }
        }
        return conocidas >= 3;
    }

    /**
     * Parte una línea en celdas. Orden de preferencia: {@code |} (tabla
     * Markdown/HTML pegada), tabulador (copiar de Excel) y dos o más espacios
     * (tabla alineada a mano).
     */
    private static String[] dividirCeldas(String linea) {
        if (linea == null) {
            return new String[0];
        }
        String limpia = linea.strip();
        if (limpia.isEmpty()) {
            return new String[0];
        }

        String separador;
        if (limpia.indexOf('|') >= 0) {
            separador = "\\s*\\|\\s*";
            limpia = limpia.replaceAll("^\\|+\\s*", "").replaceAll("\\s*\\|+$", "");
            if (limpia.isBlank()) {
                return new String[0];
            }
        } else if (limpia.indexOf('\t') >= 0) {
            separador = "\\t+";
        } else if (limpia.matches(".*\\s{2,}.*")) {
            separador = "\\s{2,}";
        } else {
            return new String[]{ limpia };
        }

        String[] partes = limpia.split(separador, -1);
        String[] celdas = new String[partes.length];
        for (int i = 0; i < partes.length; i++) {
            celdas[i] = partes[i].strip();
        }
        return celdas;
    }

    /** Quita la etiqueta de una celda que llegó como {@code "Color: Negro"}. */
    private static String quitarEtiqueta(String celda) {
        if (celda == null || celda.isBlank()) {
            return "";
        }
        return ETIQUETA.matcher(celda).replaceFirst("").strip();
    }

    /** Divide un valor de color en varios colores ({@code "Negro, Blanco"}). */
    private static List<String> dividirColores(String valor) {
        List<String> colores = new ArrayList<>();
        if (valor == null) {
            return colores;
        }

        String limpio = quitarEtiqueta(valor);
        for (String parte : limpio.split("[,/]")) {
            String color = limpiarTexto(parte, MAX_COLOR);
            if (color == null || color.length() > MAX_NOMBRE_COLOR) {
                continue;   // demasiado largo: era una frase, no un color
            }
            colores.add(color);
        }
        return colores;
    }

    /**
     * Divide un valor de talla en las tallas válidas que contenga.
     *
     * <p>Acepta separadores {@code , ; / |} y espacios, y descarta cualquier
     * palabra que no sea una talla real (ver {@link #TALLA_VALIDA}).</p>
     */
    private static List<String> dividirTallas(String valor) {
        List<String> tallas = new ArrayList<>();
        if (valor == null) {
            return tallas;
        }

        String limpio = quitarEtiqueta(valor).replaceAll("(?i)\\b(?:tallas?|talles?)\\b", " ");

        for (String trozo : limpio.split("[,;/\u2022|\u00b7]")) {
            for (String token : trozo.strip().split("\\s+")) {
                String talla = limpiarTexto(token, MAX_TALLA);
                if (talla == null) {
                    continue;
                }
                if (TALLA_PROHIBIDA.matcher(talla).matches()) {
                    continue;
                }
                if (!TALLA_VALIDA.matcher(talla).matches()) {
                    continue;   // no es una talla: se ignora sin romper nada
                }
                if (talla.length() > MAX_TALLA) {
                    talla = talla.substring(0, MAX_TALLA).strip();
                }
                tallas.add(talla);
                if (tallas.size() >= 60) {
                    return tallas;
                }
            }
        }
        return tallas;
    }

    /** Recorta espacios y signos de adorno en los extremos. */
    private static String limpiarTexto(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        String limpio = valor
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\s\\-\u2013\u2014\u2022\u00b7|,;:.]+", "")
                .replaceAll("[\\s\\-\u2013\u2014\u2022\u00b7|,;:.]+$", "")
                .strip();

        if (limpio.isEmpty()) {
            return null;
        }
        return limpio.length() > maximo ? limpio.substring(0, maximo).strip() : limpio;
    }

    // ============================================================
    // NÚMEROS Y MONEDA
    // ============================================================

    /**
     * Convierte un texto de dinero colombiano en número.
     *
     * <p>Reglas: el <strong>último</strong> separador seguido de una o dos
     * cifras es el decimal ({@code 89.900,50} → 89900.5); cualquier otro punto
     * o coma es separador de miles ({@code 45.000} → 45000). Se descartan
     * símbolos y palabras ({@code $}, {@code COP}, {@code pesos}).</p>
     *
     * @return el número, o {@code null} si el texto no contiene cifras legibles
     */
    private static Double numero(String valor) {
        if (valor == null) {
            return null;
        }

        String soloCifras = valor
                .replaceAll("(?i)\\b(?:cop|col\\$|pesos?|unds?|unidades?|uds?)\\b", " ")
                .replaceAll("[^0-9.,]", "");

        if (soloCifras.isEmpty() || soloCifras.matches("[.,]+")) {
            return null;
        }

        int ultimo = Math.max(soloCifras.lastIndexOf('.'), soloCifras.lastIndexOf(','));
        boolean decimales = false;
        if (ultimo >= 0 && ultimo < soloCifras.length() - 1) {
            int cifrasTrasSeparador = soloCifras.length() - ultimo - 1;
            decimales = cifrasTrasSeparador == 1 || cifrasTrasSeparador == 2;
        }

        String normalizado = decimales
                ? soloCifras.substring(0, ultimo).replaceAll("[.,]", "")
                  + "." + soloCifras.substring(ultimo + 1)
                : soloCifras.replaceAll("[.,]", "");

        try {
            double numero = Double.parseDouble(normalizado);
            return Double.isFinite(numero) ? numero : null;
        } catch (NumberFormatException e) {
            return null;   // texto raro: se descarta, no se rompe el proceso
        }
    }

    /** Igual que {@link #numero(String)} pero devuelve unidades enteras. */
    private static Integer entero(String valor) {
        Double numero = numero(valor);
        if (numero == null || numero < 0) {
            return null;
        }
        if (numero > 1_000_000) {
            return 1_000_000;
        }
        return (int) Math.round(numero);
    }

    // ============================================================
    // ETIQUETAS
    // ============================================================

    /**
     * Clasifica una etiqueta (o el título de una columna) en un tipo conocido.
     *
     * @return {@code COLOR}, {@code TALLA}, {@code STOCK}, {@code COSTO},
     *         {@code PRECIO} u {@code OTRO}
     */
    private static String tipoEtiqueta(String etiqueta) {
        if (etiqueta == null) {
            return "OTRO";
        }
        String limpia = sinAcentos(etiqueta).toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");

        if (limpia.isEmpty()) {
            return "OTRO";
        }
        if (limpia.startsWith("color")) {
            return "COLOR";
        }
        if (limpia.startsWith("talla") || limpia.startsWith("talle")) {
            return "TALLA";
        }
        if (limpia.startsWith("stock") || limpia.startsWith("inventario")
                || limpia.startsWith("cantidad") || limpia.startsWith("disponible")
                || limpia.startsWith("unidad")) {
            return "STOCK";
        }
        if (limpia.startsWith("costo")) {
            return "COSTO";
        }
        if (limpia.startsWith("precio") || limpia.startsWith("pvp")) {
            return "PRECIO";
        }
        return "OTRO";
    }

    /** Minúsculas, sin acentos y con espacios colapsados. */
    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return sinAcentos(texto)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }

    /** Quita tildes y diéresis descomponiendo en Unicode. */
    private static String sinAcentos(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
