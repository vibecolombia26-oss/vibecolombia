package com.vibecolombia.vibecolombia;

import jakarta.persistence.*;

@Entity
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private Double precio;

    @Column(length = 2000)
    private String descripcion;

    private String sku;
    private String categoria;
    private String imagen1;
    private String imagen2;

    // Medidas
    private String largo;
    private String ancho;
    private String alto;
    private String peso;

    // Constructor vacío (OBLIGATORIO para JPA)
    public Producto() {}

    // Constructor simple (productos existentes)
    public Producto(String nombre, Double precio, String descripcion) {
        this.nombre = nombre;
        this.precio = precio;
        this.descripcion = descripcion;
    }

    // Constructor completo (productos con imágenes y detalles)
    public Producto(String nombre, Double precio, String descripcion, String sku, String categoria,
                    String imagen1, String imagen2, String largo, String ancho, String alto, String peso) {
        this.nombre = nombre;
        this.precio = precio;
        this.descripcion = descripcion;
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

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getImagen1() { return imagen1; }
    public void setImagen1(String imagen1) { this.imagen1 = imagen1; }

    public String getImagen2() { return imagen2; }
    public void setImagen2(String imagen2) { this.imagen2 = imagen2; }

    public String getLargo() { return largo; }
    public void setLargo(String largo) { this.largo = largo; }

    public String getAncho() { return ancho; }
    public void setAncho(String ancho) { this.ancho = ancho; }

    public String getAlto() { return alto; }
    public void setAlto(String alto) { this.alto = alto; }

    public String getPeso() { return peso; }
    public void setPeso(String peso) { this.peso = peso; }
}