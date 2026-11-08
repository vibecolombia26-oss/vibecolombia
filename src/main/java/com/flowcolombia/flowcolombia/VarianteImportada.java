package com.flowcolombia.flowcolombia;

/**
 * Una variante leída de un texto de Dropi (color, talla, stock, costo y precio
 * sugerido).
 *
 * <p>Es un <em>value object</em> inmutable y sin relación con JPA: lo produce
 * {@link DropiParserUtil} leyendo el texto pegado por el administrador y lo
 * consume {@code AdminAsistenteService} para hacer el upsert. Al no ser una
 * entidad no hay riesgo de {@code LazyInitializationException} ni de que el
 * chat exponga datos de la base.</p>
 *
 * <p>Los campos pueden ser {@code null} y eso tiene significado:</p>
 * <ul>
 *   <li>{@code stock} nulo: el texto no traía stock. En una variante que ya
 *       existe <strong>no se sobrescribe</strong> el stock actual; en una nueva
 *       se crea con 0.</li>
 *   <li>{@code precioVenta} nulo: no se toca el precio actual (en una nueva se
 *       usa el precio base del producto).</li>
 *   <li>{@code costo} nulo: no se toca el costo registrado.</li>
 * </ul>
 *
 * @param color       color tal como aparece en el texto (sin normalizar)
 * @param talla       talla tal como aparece en el texto (sin normalizar)
 * @param stock       unidades disponibles, o {@code null} si no se detectaron
 * @param costo       costo de proveedor, o {@code null} si no se detectó
 * @param precioVenta precio sugerido de venta, o {@code null} si no se detectó
 */
public record VarianteImportada(String color,
                                String talla,
                                Integer stock,
                                Double costo,
                                Double precioVenta) {

    /**
     * Clave de identidad «Color + Talla» normalizada.
     *
     * <p>Es la que decide si una variante del texto actualiza una fila
     * existente o crea una nueva: {@code "Negro"/"M"} y {@code "negro "/"m"}
     * producen la misma clave.</p>
     */
    public String clave() {
        return DropiParserUtil.clave(color, talla);
    }

    /** Texto corto para la tarjeta de confirmación del chat. */
    public String etiqueta() {
        String c = (color == null || color.isBlank()) ? "sin color" : color;
        String t = (talla == null || talla.isBlank()) ? "talla única" : "talla " + talla;
        return c + " · " + t;
    }

    /** ¿El texto aportó algún dato numérico para esta variante? */
    public boolean tieneDatos() {
        return stock != null || costo != null || precioVenta != null;
    }
}
