package com.flowcolombia.flowcolombia.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la decisión «¿esta ficha es calzado?».
 *
 * <p>Es la única lógica nueva del CRO y la que decide qué experiencia ve el
 * cliente, así que se prueba sin Spring ni base de datos: son funciones puras
 * sobre la categoría y las tallas de las variantes.</p>
 *
 * <p>Importa el caso contrario tanto como el positivo: si un producto de ropa
 * se clasificara como calzado, su botón de compra quedaría bloqueado esperando
 * una confirmación de talla que no tiene sentido — y eso cuesta ventas.</p>
 */
class ProductoServiceCalzadoTest {

    private static List<Map<String, Object>> variantesConTallas(String... tallas) {
        List<Map<String, Object>> variantes = new ArrayList<>();
        for (String talla : tallas) {
            Map<String, Object> variante = new HashMap<>();
            variante.put("sku", "SKU-" + talla);
            variante.put("color", "Negro");
            variante.put("talla", talla);
            variante.put("precio", 89900d);
            variante.put("stock", 5);
            variante.put("activo", true);
            variantes.add(variante);
        }
        return variantes;
    }

    @Test
    @DisplayName("Categoría de calzado + tallas → ficha de calzado")
    void categoriaDeCalzado() {
        assertTrue(ProductoService.esCalzado("Calzado", variantesConTallas("40", "41")));
        assertTrue(ProductoService.esCalzado("CALZADO DEPORTIVO", variantesConTallas("42")));
        assertTrue(ProductoService.esCalzado("Zapatos", variantesConTallas("38")));
        assertTrue(ProductoService.esCalzado("Tenis y sneakers", variantesConTallas("39")));
        assertTrue(ProductoService.esCalzado("Botas", variantesConTallas("37")));
    }

    @Test
    @DisplayName("Categoría mal escrita pero tallas de calzado (15-48) → también")
    void tallasNumericasDecidenCuandoLaCategoriaNoAyuda() {
        assertTrue(ProductoService.esCalzado("Otros", variantesConTallas("38", "39", "40")));
        assertTrue(ProductoService.esCalzado(null, variantesConTallas("42")));
        assertTrue(ProductoService.esCalzado("", variantesConTallas("38.5")));
        // Tallas infantiles (20-33) también son calzado
        assertTrue(ProductoService.esCalzado("Accesorios", variantesConTallas("22", "24")));
    }

    @Test
    @DisplayName("Ropa con S/M/L no se confunde con calzado")
    void ropaNoEsCalzado() {
        assertFalse(ProductoService.esCalzado("Ropa", variantesConTallas("S", "M", "L")));
        assertFalse(ProductoService.esCalzado("Camisetas", variantesConTallas("XL")));
        // Tallas infantiles de ropa (2, 4, 6) quedan fuera del rango de calzado
        assertFalse(ProductoService.esCalzado("Ropa de niño", variantesConTallas("2", "4", "6")));
    }

    @Test
    @DisplayName("Sin tallas no se activa la ficha de calzado (no se bloquea la compra)")
    void sinTallasNoEsCalzado() {
        assertFalse(ProductoService.esCalzado("Calzado", List.of()));
        assertFalse(ProductoService.esCalzado("Calzado", null));
        assertFalse(ProductoService.esCalzado("Calzado", variantesConTallas("", "")));

        List<Map<String, Object>> sinTalla = new ArrayList<>();
        Map<String, Object> variante = new HashMap<>();
        variante.put("sku", "BOLSO-1");
        variante.put("color", "Negro");
        variante.put("talla", "");
        sinTalla.add(variante);
        assertFalse(ProductoService.esCalzado("Bolsos", sinTalla));
    }

    @Test
    @DisplayName("El rango de tallas de calzado es 15-48")
    void rangoDeTallas() {
        assertEquals(38, ProductoService.numeroDeCalzado("38"));
        assertEquals(40, ProductoService.numeroDeCalzado("Talla 40"));
        assertEquals(38, ProductoService.numeroDeCalzado("38.5"));
        assertEquals(22, ProductoService.numeroDeCalzado("22"));
        assertNull(ProductoService.numeroDeCalzado("M"));
        assertNull(ProductoService.numeroDeCalzado("XL"));
        assertNull(ProductoService.numeroDeCalzado("6"));
        assertNull(ProductoService.numeroDeCalzado("60"));
        assertNull(ProductoService.numeroDeCalzado(null));
        assertNull(ProductoService.numeroDeCalzado(""));
    }

    @Test
    @DisplayName("La categoría se compara sin tildes ni mayúsculas")
    void categoriaNormalizada() {
        assertTrue(ProductoService.esCategoriaDeCalzado("Calzadó"));
        assertTrue(ProductoService.esCategoriaDeCalzado("ZAPATILLAS"));
        assertTrue(ProductoService.esCategoriaDeCalzado("Sandalias de playa"));
        assertFalse(ProductoService.esCategoriaDeCalzado("Bolsos y morrales"));
        assertFalse(ProductoService.esCategoriaDeCalzado(null));
        assertFalse(ProductoService.esCategoriaDeCalzado("  "));
    }
}
