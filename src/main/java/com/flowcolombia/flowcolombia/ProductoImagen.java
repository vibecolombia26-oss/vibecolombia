package com.flowcolombia.flowcolombia;

import jakarta.persistence.*;

@Entity
@Table(name = "producto_imagen")
public class ProductoImagen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private String url;

    /**
     * Color al que pertenece la imagen (opcional).
     *
     * <p>Se usa para la galería por variante: si vale {@code "Negro"}, la ficha
     * muestra esa foto al elegir el color Negro. {@code null} (o vacío) significa
     * «general»: se muestra cuando el color elegido no tiene fotos propias.</p>
     *
     * <p>La columna es anulable y no única: dos fotos del mismo color conviven
     * sin problema, y varias imágenes pueden ser generales a la vez.</p>
     */
    @Column(name = "color", length = 120)
    private String color;

    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    // Constructores
    public ProductoImagen() {}

    public ProductoImagen(Producto producto, String url, Integer orden) {
        this(producto, url, null, orden);
    }

    public ProductoImagen(Producto producto, String url, String color, Integer orden) {
        this.producto = producto;
        this.url = url;
        this.color = normalizarColor(color);
        this.orden = orden;
    }

    /**
     * Color listo para guardar: sin espacios sobrantes y {@code null} cuando
     * está vacío (el requisito: «si el campo color está vacío, guárdalo como
     * null», para que la galería general se detecte de una sola forma).
     */
    public static String normalizarColor(String color) {
        if (color == null) {
            return null;
        }
        String limpio = color.trim().replaceAll("\\s+/\\s*", "/");
        return limpio.isEmpty() ? null : limpio;
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = normalizarColor(color); }

    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }
}