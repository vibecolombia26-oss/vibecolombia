package com.flowcolombia.flowcolombia.service;

/**
 * Estado de un pedido tal cual lo entrega Dropi, <strong>ya filtrado</strong>.
 *
 * <p>Este record es la frontera de privacidad del sync: Dropi devuelve nombre,
 * dirección, teléfono, correo y detalles del cliente, y nada de eso puede
 * cruzar hacia nuestra base de datos ni hacia el asistente de IA. Aquí solo
 * existen cuatro campos, y dos de ellos son identificadores:</p>
 * <ul>
 *   <li>{@code referencia}: el campo de referencia libre de Dropi (ahí puede
 *       viajar nuestro {@code FLOW-…} si el pedido se creó desde la tienda).</li>
 *   <li>{@code idDropi}: el ID que Dropi asigna al pedido. Es el correlador
 *       principal cuando el administrador lo pega en el detalle del pedido.</li>
 *   <li>{@code estado}: el estado que reporta Dropi.</li>
 *   <li>{@code numeroGuia}: la guía de la transportadora.</li>
 * </ul>
 *
 * <p>Si algún día hace falta otro dato (transportadora, fecha de entrega), se
 * añade aquí de forma explícita y consciente; nunca pasando el JSON crudo.</p>
 *
 * @param referencia  referencia libre de Dropi (puede ser {@code null})
 * @param idDropi     ID del pedido en Dropi (puede ser {@code null})
 * @param estado      estado reportado por Dropi (puede ser {@code null})
 * @param numeroGuia  guía de rastreo (puede ser {@code null})
 */
public record DropiEstado(String referencia, String idDropi, String estado, String numeroGuia) {

    /** Identificador utilizable para correlacionar: el de Dropi si existe. */
    public String identificadorPreferido() {
        return (idDropi != null && !idDropi.isBlank()) ? idDropi : referencia;
    }
}
