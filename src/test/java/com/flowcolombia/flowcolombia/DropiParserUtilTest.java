package com.flowcolombia.flowcolombia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del lector de inventarios de Dropi.
 *
 * <p>Son pruebas <strong>puras</strong> (sin Spring ni base de datos): si el
 * texto se lee mal, se ve aquí y no en producción. Cada caso reproduce un
 * formato real de copiar y pegar desde Dropi, incluidos los rotos, porque la
 * promesa del parser es que una línea mal escrita no arrastre al resto.</p>
 */
class DropiParserUtilTest {

    /** Índice por «color|talla» para comparar sin depender del orden. */
    private static Map<String, VarianteImportada> porClave(List<VarianteImportada> variantes) {
        return variantes.stream()
                .collect(Collectors.toMap(VarianteImportada::clave, Function.identity(), (a, b) -> b));
    }

    @Test
    @DisplayName("Lee bloques con etiquetas, varias tallas y varios colores")
    void leeBloquesConEtiquetas() {
        String texto = """
                Mochila Táctica 40L
                Color: Negro
                Tallas: 38, 39, 40
                Stock: 12
                Costo: $45.000
                Precio sugerido: $89.900
                ID: 1234567

                Color: Verde Militar
                Talla: 42
                Inventario: 3
                Costo: $46.500
                Precio: $92.000
                """;

        List<VarianteImportada> variantes = DropiParserUtil.parsear(texto);
        assertEquals(4, variantes.size(), "3 tallas del primer color + 1 del segundo");

        Map<String, VarianteImportada> indice = porClave(variantes);

        VarianteImportada negro38 = indice.get("negro|38");
        assertNotNull(negro38);
        assertEquals(12, negro38.stock());
        assertEquals(45000d, negro38.costo());
        assertEquals(89900d, negro38.precioVenta());
        assertNotNull(indice.get("negro|39"));
        assertNotNull(indice.get("negro|40"));

        VarianteImportada verde42 = indice.get("verde militar|42");
        assertNotNull(verde42);
        assertEquals(3, verde42.stock());
        assertEquals(46500d, verde42.costo());
        assertEquals(92000d, verde42.precioVenta());

        // El identificador de Dropi no se convierte en una variante.
        assertTrue(indice.keySet().stream().noneMatch(k -> k.contains("1234567")));
    }

    @Test
    @DisplayName("Lee varias etiquetas en la misma línea")
    void leeEtiquetasEnUnaSolaLinea() {
        String texto = "Color: Blanco Tallas: S, M, L Stock: 7 Costo: 20.000 Precio sugerido: 55.000";

        List<VarianteImportada> variantes = DropiParserUtil.parsear(texto);
        assertEquals(3, variantes.size());

        Map<String, VarianteImportada> indice = porClave(variantes);
        for (String talla : List.of("s", "m", "l")) {
            VarianteImportada variante = indice.get("blanco|" + talla);
            assertNotNull(variante, "falta la talla " + talla);
            assertEquals(7, variante.stock());
            assertEquals(20000d, variante.costo());
            assertEquals(55000d, variante.precioVenta());
        }
    }

    @Test
    @DisplayName("Lee una tabla pegada con encabezado")
    void leeTablaPegada() {
        String texto = """
                | Color | Talla | Stock | Costo | Precio sugerido |
                | Negro | M | 10 | 45000 | 89900 |
                | Blanco | L | 4 | 46000 | 92000 |
                """;

        List<VarianteImportada> variantes = DropiParserUtil.parsear(texto);
        assertEquals(2, variantes.size());

        Map<String, VarianteImportada> indice = porClave(variantes);
        assertEquals(10, indice.get("negro|m").stock());
        assertEquals(89900d, indice.get("negro|m").precioVenta());
        assertEquals(4, indice.get("blanco|l").stock());
        assertEquals(46000d, indice.get("blanco|l").costo());
    }

    @Test
    @DisplayName("Lee una tabla separada por dos espacios")
    void leeTablaConEspacios() {
        String texto = """
                Color  Talla  Stock  Costo  Precio
                Rojo   XL     6      38000  75000
                """;

        List<VarianteImportada> variantes = DropiParserUtil.parsear(texto);
        assertEquals(1, variantes.size());
        assertEquals("Rojo", variantes.get(0).color());
        assertEquals("XL", variantes.get(0).talla());
        assertEquals(6, variantes.get(0).stock());
        assertEquals(75000d, variantes.get(0).precioVenta());
    }

    @Test
    @DisplayName("Un valor roto no arrastra al resto del inventario")
    void toleraValoresRotos() {
        String texto = """
                Color: Negro
                Tallas: 38
                Stock: abc
                Costo: $45.000
                Precio: $89.900

                Color: Azul
                Tallas: 40
                Stock: 5
                Costo: no aplica
                """;

        List<VarianteImportada> variantes = DropiParserUtil.parsear(texto);
        assertEquals(2, variantes.size(), "las dos variantes se leen, con huecos");

        VarianteImportada negro = porClave(variantes).get("negro|38");
        assertNull(negro.stock(), "«abc» no es un stock: se deja en null para no borrar el actual");
        assertEquals(45000d, negro.costo());
        assertEquals(89900d, negro.precioVenta());

        VarianteImportada azul = porClave(variantes).get("azul|40");
        assertEquals(5, azul.stock());
        assertNull(azul.costo());
        assertNull(azul.precioVenta());
    }

    @Test
    @DisplayName("Entiende los formatos de moneda colombianos")
    void entiendeMonedaColombiana() {
        assertEquals(45000d, precioDe("Precio: 45.000"));
        assertEquals(89900.5d, precioDe("Precio: 89.900,50"));
        assertEquals(92000d, precioDe("Precio: $ 92.000 COP"));
        assertEquals(25000d, precioDe("Precio: 25000"));
        assertEquals(1250d, precioDe("Precio: 1.250"));
    }

    /** Precio leído de un texto de una sola variante. */
    private static Double precioDe(String lineaPrecio) {
        List<VarianteImportada> variantes =
                DropiParserUtil.parsear("Color: Negro\nTalla: 38\n" + lineaPrecio);
        assertEquals(1, variantes.size(), "no se leyó la variante de " + lineaPrecio);
        return variantes.get(0).precioVenta();
    }

    @Test
    @DisplayName("No repite una variante que aparece dos veces")
    void noRepiteVariantes() {
        String texto = """
                Color: Negro
                Tallas: 38
                Stock: 5
                Color: Negro
                Tallas: 38
                Stock: 9
                """;

        List<VarianteImportada> variantes = DropiParserUtil.parsear(texto);
        assertEquals(1, variantes.size());
        assertEquals(9, variantes.get(0).stock(), "gana la última lectura");
    }

    @Test
    @DisplayName("Una frase normal no se confunde con un inventario")
    void fraseNormalNoEsInventario() {
        assertFalse(DropiParserUtil.pareceInventario("sube el stock de MOCH-M a 20"));
        assertFalse(DropiParserUtil.pareceInventario("actualiza la talla M de la mochila negra a 10"));
        assertFalse(DropiParserUtil.pareceInventario("cambia el precio del bolso a 89900"));
        assertFalse(DropiParserUtil.pareceInventario(""));
        assertFalse(DropiParserUtil.pareceInventario((String) null));

        assertTrue(DropiParserUtil.pareceInventario("""
                Color: Negro
                Tallas: 38
                Stock: 4
                """));
        assertTrue(DropiParserUtil.pareceInventario("""
                Color: Negro Tallas: 38, 39
                """));
    }

    @Test
    @DisplayName("La clave normaliza color y talla")
    void claveNormaliza() {
        assertEquals(DropiParserUtil.clave("Negro", "M"), DropiParserUtil.clave(" negro ", "m"));
        assertEquals(DropiParserUtil.clave("Verde Militar", "40"),
                DropiParserUtil.clave("verde  militar", "40"));
        assertEquals("|", DropiParserUtil.clave(null, null));
        assertEquals(DropiParserUtil.clave(null, null), DropiParserUtil.clave("", " "));
    }

    @Test
    @DisplayName("Normaliza un override escrito a mano igual que los precios del texto")
    void normalizaOverridesEscritosAMano() {
        // El administrador escribe «99.999» en el chat; si el modelo lo reenvía
        // como texto, debe leerse 99999 y no 99,999.
        assertEquals(99999d, DropiParserUtil.numeroColombiano("99.999"));
        assertEquals(60000d, DropiParserUtil.numeroColombiano("$ 60.000"));
        assertEquals(99999d, DropiParserUtil.numeroColombiano("99999"));
        assertEquals(89900.5d, DropiParserUtil.numeroColombiano("89.900,50"));
        assertNull(DropiParserUtil.numeroColombiano("sin número"));
        assertNull(DropiParserUtil.numeroColombiano(null));
    }

    @Test
    @DisplayName("Un texto vacío o inservible devuelve lista vacía sin lanzar")
    void textoInservible() {
        assertTrue(DropiParserUtil.parsear(null).isEmpty());
        assertTrue(DropiParserUtil.parsear("").isEmpty());
        assertTrue(DropiParserUtil.parsear("   \n\n \t ").isEmpty());
        assertTrue(DropiParserUtil.parsear("null null null").isEmpty());
        assertTrue(DropiParserUtil.parsear("Color:\nTalla:\nStock:").isEmpty(),
                "etiquetas sin valor no crean variantes");
    }
}
