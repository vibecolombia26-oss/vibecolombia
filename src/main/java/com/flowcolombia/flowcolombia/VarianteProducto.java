package com.flowcolombia.flowcolombia;

import jakarta.persistence.*;

import java.util.Objects;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "producto_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_variante_producto")
    )
    private Producto producto;

    @Column(
            name = "sku",
            nullable = false,
            unique = true,
            length = 100
    )
    private String sku;

    // 🔥 CAMBIO: longitud de 50 a 255
    @Column(name = "color", length = 255)
    private String color;

    @Column(name = "talla", length = 20)
    private String talla;

    @Column(name = "precio", nullable = false)
    private Double precio;

    @Column(name = "costo")
    private Double costo;

    @Column(name = "stock", nullable = false)
    private Integer stock = 0;

    @Column(name = "peso")
    private Double peso;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    public VarianteProducto() {
    }

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
    // MÉTODOS DE STOCK
    // ============================================================

    public void reducirStock(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("La cantidad no puede ser negativa.");
        }
        if (this.stock < cantidad) {
            throw new IllegalArgumentException("Stock insuficiente. Disponible: " + this.stock);
        }
        this.stock -= cantidad;
    }

    public void incrementarStock(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException("La cantidad no puede ser negativa.");
        }
        this.stock += cantidad;
    }

    public boolean isDisponible() {
        return Boolean.TRUE.equals(activo) && stock != null && stock > 0;
    }

    // ============================================================
    // EQUALS Y HASHCODE
    // ============================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VarianteProducto that = (VarianteProducto) o;

        if (this.id != null && that.id != null) {
            return Objects.equals(this.id, that.id);
        }

        if (this.id != null || that.id != null) {
            return false;
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return (id != null) ? Objects.hash(id) : getClass().hashCode();
    }

    // ============================================================
    // TOSTRING
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