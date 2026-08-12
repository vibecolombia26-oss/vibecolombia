package com.flowcolombia.flowcolombia;

import jakarta.persistence.*;

import java.util.Objects;

/**
 * Representa una variante específica de un producto.
 * Cada variante corresponde a una combinación única de:
 * - Color
 * - Talla
 *
 * Además, incluye información de precio, costo, stock, peso
 * y estado activo/inactivo para control de inventario.
 */
@Entity
@Table(
        name = "variante_producto",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_variante_producto_sku",
                        columnNames = "sku"
                )
        }
)
public class VarianteProducto {

    // ============================================================
    // IDENTIFICADOR
    // ============================================================
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============================================================
    // RELACIÓN CON PRODUCTO
    // ============================================================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "producto_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_variante_producto")
    )
    private Producto producto;

    // ============================================================
    // CAMPOS DE LA VARIANTE
    // ============================================================

    /**
     * SKU único de la variante.
     * Es el identificador real que se usa en el carrito, pedidos y seguimiento.
     * No puede ser nulo y debe ser único en toda la tabla.
     */
    @Column(
            name = "sku",
            nullable = false,
            unique = true,
            length = 100
    )
    private String sku;

    /**
     * Color de la variante.
     * Puede ser nulo si el producto no tiene variación de color.
     */
    @Column(name = "color", length = 50)
    private String color;

    /**
     * Talla de la variante.
     * Puede ser nulo si el producto no tiene variación de talla.
     */
    @Column(name = "talla", length = 20)
    private String talla;

    /**
     * Precio de venta de esta variante específica.
     * Puede ser diferente al precio base del producto.
     * Debe ser mayor a 0.
     */
    @Column(name = "precio", nullable = false)
    private Double precio;

    /**
     * Costo de adquisición o fabricación de esta variante.
     * Útil para calcular márgenes de ganancia.
     * Puede ser nulo si no se conoce.
     */
    @Column(name = "costo")
    private Double costo;

    /**
     * Cantidad disponible en inventario para esta variante.
     * No puede ser negativo.
     * Si es 0, la variante se considera agotada.
     */
    @Column(name = "stock", nullable = false)
    private Integer stock = 0;

    /**
     * Peso de la variante en gramos (g).
     * Puede variar entre tallas o colores.
     * Útil para cálculos de envío.
     */
    @Column(name = "peso")
    private Double peso;

    /**
     * Indica si la variante está activa y disponible para la venta.
     * Si es false, no se muestra en la tienda aunque tenga stock.
     */
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    // ============================================================
    // CONSTRUCTORES
    // ============================================================

    public VarianteProducto() {
        // Constructor vacío requerido por JPA
    }

    /**
     * Constructor útil para crear variantes rápidamente.
     */
    public VarianteProducto(Producto producto, String sku, String color, String talla,
                            Double precio, Integer stock) {
        this.producto = producto;
        this.sku = sku;
        this.color = color;
        this.talla = talla;
        this.precio = precio;
        this.stock = stock != null ? stock : 0;
        this.activo = true;
    }

    // ============================================================
    // GETTERS Y SETTERS
    // ============================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Producto getProducto() {
        return producto;
    }

    public void setProducto(Producto producto) {
        this.producto = producto;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getTalla() {
        return talla;
    }

    public void setTalla(String talla) {
        this.talla = talla;
    }

    public Double getPrecio() {
        return precio;
    }

    public void setPrecio(Double precio) {
        this.precio = precio;
    }

    public Double getCosto() {
        return costo;
    }

    public void setCosto(Double costo) {
        this.costo = costo;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Double getPeso() {
        return peso;
    }

    public void setPeso(Double peso) {
        this.peso = peso;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    // ============================================================
    // MÉTODOS DE UTILIDAD
    // ============================================================

    /**
     * Verifica si la variante está disponible para la venta.
     * Una variante está disponible si está activa y tiene stock > 0.
     */
    public boolean isDisponible() {
        return Boolean.TRUE.equals(activo) && stock != null && stock > 0;
    }

    /**
     * Reduce el stock en la cantidad especificada.
     * @throws IllegalArgumentException si la cantidad es negativa o supera el stock disponible.
     */
    public void reducirStock(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("La cantidad no puede ser negativa.");
        }
        if (this.stock < cantidad) {
            throw new IllegalArgumentException("Stock insuficiente. Disponible: " + this.stock);
        }
        this.stock -= cantidad;
    }

    /**
     * Incrementa el stock en la cantidad especificada.
     */
    public void incrementarStock(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("La cantidad no puede ser negativa.");
        }
        this.stock += cantidad;
    }

    // ============================================================
    // EQUALS Y HASHCODE (basado en SKU, que es único)
    // ============================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VarianteProducto that = (VarianteProducto) o;
        return Objects.equals(sku, that.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sku);
    }

    // ============================================================
    // TOSTRING (para logs y depuración)
    // ============================================================

    @Override
    public String toString() {
        return "VarianteProducto{" +
                "id=" + id +
                ", sku='" + sku + '\'' +
                ", color='" + color + '\'' +
                ", talla='" + talla + '\'' +
                ", precio=" + precio +
                ", stock=" + stock +
                ", activo=" + activo +
                '}';
    }
}