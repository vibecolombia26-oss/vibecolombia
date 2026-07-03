package com.vibecolombia.vibecolombia;

import jakarta.persistence.*;

@Entity
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private Double precio;

    @Column(length = 5000)
    private String descripcionCorta;

    @Column(length = 3000)
    private String descripcionLarga;

    private String sku;
    private String categoria;

    @Column(columnDefinition = "TEXT")
    private String imagen1;
    @Column(columnDefinition = "TEXT")
    private String imagen2;
    @Column(columnDefinition = "TEXT")
    private String imagen3;
    @Column(columnDefinition = "TEXT")
    private String imagen4;
    @Column(columnDefinition = "TEXT")
    private String imagen5;
    @Column(columnDefinition = "TEXT")
    private String imagen6;
    @Column(columnDefinition = "TEXT")
    private String imagen7;
    @Column(columnDefinition = "TEXT")
    private String imagen8;
    @Column(columnDefinition = "TEXT")
    private String imagen9;
    @Column(columnDefinition = "TEXT")
    private String imagen10;

    private String largo;
    private String ancho;
    private String alto;
    private String peso;

    // Variaciones
    private boolean tieneVariaciones;
    @Column(length = 500)
    private String variacionesDisponibles;

    public Producto() {}

    public Producto(String nombre, Double precio, String descripcionCorta) {
        this.nombre = nombre;
        this.precio = precio;
        this.descripcionCorta = descripcionCorta;
    }

    public Producto(String nombre, Double precio, String descripcionCorta, String descripcionLarga,
                    String sku, String categoria, String imagen1, String imagen2,
                    String largo, String ancho, String alto, String peso) {
        this.nombre = nombre;
        this.precio = precio;
        this.descripcionCorta = descripcionCorta;
        this.descripcionLarga = descripcionLarga;
        this.sku = sku;
        this.categoria = categoria;
        this.imagen1 = imagen1;
        this.imagen2 = imagen2;
        this.largo = largo;
        this.ancho = ancho;
        this.alto = alto;
        this.peso = peso;
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Double getPrecio() { return precio; }
    public void setPrecio(Double precio) { this.precio = precio; }
    public String getDescripcionCorta() { return descripcionCorta; }
    public void setDescripcionCorta(String descripcionCorta) { this.descripcionCorta = descripcionCorta; }
    public String getDescripcionLarga() { return descripcionLarga; }
    public void setDescripcionLarga(String descripcionLarga) { this.descripcionLarga = descripcionLarga; }
    public String getDescripcion() { return descripcionCorta; }
    public void setDescripcion(String descripcion) { this.descripcionCorta = descripcion; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getImagen1() { return imagen1; }
    public void setImagen1(String imagen1) { this.imagen1 = imagen1; }
    public String getImagen2() { return imagen2; }
    public void setImagen2(String imagen2) { this.imagen2 = imagen2; }
    public String getImagen3() { return imagen3; }
    public void setImagen3(String imagen3) { this.imagen3 = imagen3; }
    public String getImagen4() { return imagen4; }
    public void setImagen4(String imagen4) { this.imagen4 = imagen4; }
    public String getImagen5() { return imagen5; }
    public void setImagen5(String imagen5) { this.imagen5 = imagen5; }
    public String getImagen6() { return imagen6; }
    public void setImagen6(String imagen6) { this.imagen6 = imagen6; }
    public String getImagen7() { return imagen7; }
    public void setImagen7(String imagen7) { this.imagen7 = imagen7; }
    public String getImagen8() { return imagen8; }
    public void setImagen8(String imagen8) { this.imagen8 = imagen8; }
    public String getImagen9() { return imagen9; }
    public void setImagen9(String imagen9) { this.imagen9 = imagen9; }
    public String getImagen10() { return imagen10; }
    public void setImagen10(String imagen10) { this.imagen10 = imagen10; }
    public String getLargo() { return largo; }
    public void setLargo(String largo) { this.largo = largo; }
    public String getAncho() { return ancho; }
    public void setAncho(String ancho) { this.ancho = ancho; }
    public String getAlto() { return alto; }
    public void setAlto(String alto) { this.alto = alto; }
    public String getPeso() { return peso; }
    public void setPeso(String peso) { this.peso = peso; }

    // Variaciones
    public boolean isTieneVariaciones() { return tieneVariaciones; }
    public void setTieneVariaciones(boolean tieneVariaciones) { this.tieneVariaciones = tieneVariaciones; }
    public String getVariacionesDisponibles() { return variacionesDisponibles; }
    public void setVariacionesDisponibles(String variacionesDisponibles) { this.variacionesDisponibles = variacionesDisponibles; }
}