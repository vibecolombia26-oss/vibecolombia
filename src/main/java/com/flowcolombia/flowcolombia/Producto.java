package com.flowcolombia.flowcolombia;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entidad principal de producto.
 * Mantiene compatibilidad con los campos legacy para migración progresiva.
 * Incluye nuevas relaciones para imágenes múltiples y variantes (talla/color).
 */
@Entity
@Table(name = "producto")
public class Producto {

    // ============================================================
    // IDENTIFICADOR
    // ============================================================
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============================================================
    // CAMPOS LEGACY (se mantienen para migración)
    // ============================================================

    /**
     * Nombre del producto (legacy y actual).
     */
    @Column(nullable = false)
    private String nombre;

    /**
     * SKU base del producto (puede ser referencia para generar SKU de variantes).
     */
    @Column(nullable = false, unique = true)
    private String sku;

    /**
     * Precio base del producto (se usa como referencia si la variante no define precio propio).
     */
    @Column(nullable = false)
    private Double precio;

    /**
     * Categoría del producto (Ej: Calzado, Hogar, Tecnología, etc.).
     */
    private String categoria;

    /**
     * Descripción corta (legacy y actual).
     */
    @Column(columnDefinition = "TEXT")
    private String descripcion;

    /**
     * Descripción larga o detallada (legacy y actual).
     */
    @Column(columnDefinition = "TEXT")
    private String descripcionLarga;

    /**
     * Imagenes legacy (se mantienen hasta migrar a ProductoImagen).
     * Se conservan para compatibilidad con productos antiguos.
     */
    private String imagen1;
    private String imagen2;
    private String imagen3;
    private String imagen4;
    private String imagen5;
    private String imagen6;

    /**
     * Dimensiones y peso legacy (se mantienen).
     */
    private String largo;
    private String ancho;
    private String alto;
    private String peso;

    /**
     * Campos de variaciones legacy (se mantienen para migración).
     */
    private Boolean tieneVariaciones = false;
    @Column(columnDefinition = "TEXT")
    private String variacionesDisponibles; // Formato: "color1,color2|talla1,talla2"
    private Boolean tieneColor = false;
    private Boolean tieneTalla = false;

    // ============================================================
    // NUEVAS RELACIONES (MODELO NORMALIZADO)
    // ============================================================

    /**
     * Lista de imágenes del producto (múltiples, ordenadas).
     * Se migrarán desde imagen1..imagen6 progresivamente.
     */
    @OneToMany(
            mappedBy = "producto",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("orden ASC")
    private List<ProductoImagen> imagenes = new ArrayList<>();

    /**
     * Lista de variantes del producto (talla, color, precio, stock).
     * Cada variante tiene su propio SKU único.
     */
    @OneToMany(
            mappedBy = "producto",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<VarianteProducto> variantes = new ArrayList<>();

    /**
     * Lista de reseñas del producto (relación existente).
     */
    @OneToMany(
            mappedBy = "producto",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<Resena> resenas = new ArrayList<>();

    // ============================================================
    // CONSTRUCTORES
    // ============================================================

    public Producto() {
        // Constructor vacío requerido por JPA
    }

    /**
     * Constructor para creación rápida de un producto básico.
     */
    public Producto(String nombre, String sku, Double precio, String categoria) {
        this.nombre = nombre;
        this.sku = sku;
        this.precio = precio;
        this.categoria = categoria;
    }

    // ============================================================
    // GETTERS Y SETTERS (TODOS LOS CAMPOS)
    // ============================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public Double getPrecio() {
        return precio;
    }

    public void setPrecio(Double precio) {
        this.precio = precio;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcionLarga() {
        return descripcionLarga;
    }

    public void setDescripcionLarga(String descripcionLarga) {
        this.descripcionLarga = descripcionLarga;
    }

    public String getImagen1() {
        return imagen1;
    }

    public void setImagen1(String imagen1) {
        this.imagen1 = imagen1;
    }

    public String getImagen2() {
        return imagen2;
    }

    public void setImagen2(String imagen2) {
        this.imagen2 = imagen2;
    }

    public String getImagen3() {
        return imagen3;
    }

    public void setImagen3(String imagen3) {
        this.imagen3 = imagen3;
    }

    public String getImagen4() {
        return imagen4;
    }

    public void setImagen4(String imagen4) {
        this.imagen4 = imagen4;
    }

    public String getImagen5() {
        return imagen5;
    }

    public void setImagen5(String imagen5) {
        this.imagen5 = imagen5;
    }

    public String getImagen6() {
        return imagen6;
    }

    public void setImagen6(String imagen6) {
        this.imagen6 = imagen6;
    }

    public String getLargo() {
        return largo;
    }

    public void setLargo(String largo) {
        this.largo = largo;
    }

    public String getAncho() {
        return ancho;
    }

    public void setAncho(String ancho) {
        this.ancho = ancho;
    }

    public String getAlto() {
        return alto;
    }

    public void setAlto(String alto) {
        this.alto = alto;
    }

    public String getPeso() {
        return peso;
    }

    public void setPeso(String peso) {
        this.peso = peso;
    }

    public Boolean getTieneVariaciones() {
        return tieneVariaciones;
    }

    public void setTieneVariaciones(Boolean tieneVariaciones) {
        this.tieneVariaciones = tieneVariaciones;
    }

    public String getVariacionesDisponibles() {
        return variacionesDisponibles;
    }

    public void setVariacionesDisponibles(String variacionesDisponibles) {
        this.variacionesDisponibles = variacionesDisponibles;
    }

    public Boolean getTieneColor() {
        return tieneColor;
    }

    public void setTieneColor(Boolean tieneColor) {
        this.tieneColor = tieneColor;
    }

    public Boolean getTieneTalla() {
        return tieneTalla;
    }

    public void setTieneTalla(Boolean tieneTalla) {
        this.tieneTalla = tieneTalla;
    }

    // ============================================================
    // GETTERS Y SETTERS DE NUEVAS RELACIONES
    // ============================================================

    public List<ProductoImagen> getImagenes() {
        return imagenes;
    }

    public void setImagenes(List<ProductoImagen> imagenes) {
        this.imagenes = imagenes;
    }

    public List<VarianteProducto> getVariantes() {
        return variantes;
    }

    public void setVariantes(List<VarianteProducto> variantes) {
        this.variantes = variantes;
    }

    public List<Resena> getResenas() {
        return resenas;
    }

    public void setResenas(List<Resena> resenas) {
        this.resenas = resenas;
    }

    // ============================================================
    // MÉTODOS DE CALIFICACIÓN (RESEÑAS)
    // ============================================================

    /**
     * Calcula el promedio de calificaciones de las reseñas aprobadas.
     */
    public Double getPromedioCalificacion() {
        if (resenas == null || resenas.isEmpty()) {
            return 0.0;
        }
        return resenas.stream()
                .filter(r -> r.getAprobado() != null && r.getAprobado())
                .mapToInt(Resena::getCalificacion)
                .average()
                .orElse(0.0);
    }

    /**
     * Cuenta el número de reseñas aprobadas.
     */
    public Long getCantidadResenas() {
        if (resenas == null) {
            return 0L;
        }
        return resenas.stream()
                .filter(r -> r.getAprobado() != null && r.getAprobado())
                .count();
    }

    // ============================================================
    // MÉTODOS DE UTILIDAD (IMÁGENES Y VARIANTES)
    // ============================================================

    /**
     * Agrega una imagen al producto.
     *
     * @param url    URL de la imagen en Cloudinary.
     * @param orden  Orden de visualización (0 = primera).
     */
    public void addImagen(String url, Integer orden) {
        ProductoImagen imagen = new ProductoImagen(this, url, orden);
        this.imagenes.add(imagen);
    }

    /**
     * Agrega una variante al producto.
     *
     * @param sku    SKU único de la variante.
     * @param color  Color (puede ser null).
     * @param talla  Talla (puede ser null).
     * @param precio Precio de la variante.
     * @param stock  Stock inicial.
     */
    public void addVariante(String sku, String color, String talla, Double precio, Integer stock) {
        VarianteProducto variante = new VarianteProducto();
        variante.setProducto(this);
        variante.setSku(sku);
        variante.setColor(color);
        variante.setTalla(talla);
        variante.setPrecio(precio);
        variante.setStock(stock != null ? stock : 0);
        variante.setActivo(true);
        this.variantes.add(variante);
    }

    /**
     * Busca una variante por su SKU.
     *
     * @param sku SKU a buscar.
     * @return VarianteProducto o null si no existe.
     */
    public VarianteProducto getVarianteBySku(String sku) {
        if (variantes == null || sku == null) {
            return null;
        }
        return variantes.stream()
                .filter(v -> sku.equals(v.getSku()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Busca una variante por combinación de color y talla.
     *
     * @param color Color (puede ser null).
     * @param talla Talla (puede ser null).
     * @return VarianteProducto o null si no existe.
     */
    public VarianteProducto getVarianteByColorTalla(String color, String talla) {
        if (variantes == null) {
            return null;
        }
        // Limpiar nulls para comparación
        String c = (color != null) ? color.trim() : "";
        String t = (talla != null) ? talla.trim() : "";
        return variantes.stream()
                .filter(v -> {
                    String vc = (v.getColor() != null) ? v.getColor().trim() : "";
                    String vt = (v.getTalla() != null) ? v.getTalla().trim() : "";
                    return vc.equals(c) && vt.equals(t);
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * Devuelve todas las variantes activas y con stock.
     */
    public List<VarianteProducto> getVariantesDisponibles() {
        if (variantes == null) {
            return new ArrayList<>();
        }
        return variantes.stream()
                .filter(VarianteProducto::isDisponible)
                .toList();
    }

    // ============================================================
    // EQUALS Y HASHCODE (basado en ID)
    // ============================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Producto producto = (Producto) o;
        return Objects.equals(id, producto.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // ============================================================
    // TOSTRING (para logs y depuración)
    // ============================================================

    @Override
    public String toString() {
        return "Producto{" +
                "id=" + id +
                ", nombre='" + nombre + '\'' +
                ", sku='" + sku + '\'' +
                ", precio=" + precio +
                ", categoria='" + categoria + '\'' +
                ", variantes=" + (variantes != null ? variantes.size() : 0) +
                ", imagenes=" + (imagenes != null ? imagenes.size() : 0) +
                '}';
    }
}