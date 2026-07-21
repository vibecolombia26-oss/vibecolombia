package com.flowcolombia.flowcolombia;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "producto")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String sku;
    private Double precio;
    private String categoria;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(columnDefinition = "TEXT")
    private String descripcionLarga;

    private String imagen1;
    private String imagen2;
    private String imagen3;
    private String imagen4;
    private String imagen5;
    private String imagen6;

    private String largo;
    private String ancho;
    private String alto;
    private String peso;

    private Boolean tieneVariaciones = false;
    private String variacionesDisponibles; // Formato: "color1,color2|talla1,talla2"

    // ============================================================
    // RELACIÓN CON RESEÑAS
    // ============================================================
    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Resena> resenas = new ArrayList<>();

    public Producto() {}

    // ============================================================
    // GETTERS Y SETTERS
    // ============================================================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public Double getPrecio() { return precio; }
    public void setPrecio(Double precio) { this.precio = precio; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getDescripcionLarga() { return descripcionLarga; }
    public void setDescripcionLarga(String descripcionLarga) { this.descripcionLarga = descripcionLarga; }

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

    public String getLargo() { return largo; }
    public void setLargo(String largo) { this.largo = largo; }

    public String getAncho() { return ancho; }
    public void setAncho(String ancho) { this.ancho = ancho; }

    public String getAlto() { return alto; }
    public void setAlto(String alto) { this.alto = alto; }

    public String getPeso() { return peso; }
    public void setPeso(String peso) { this.peso = peso; }

    public Boolean getTieneVariaciones() { return tieneVariaciones; }
    public void setTieneVariaciones(Boolean tieneVariaciones) { this.tieneVariaciones = tieneVariaciones; }

    public String getVariacionesDisponibles() { return variacionesDisponibles; }
    public void setVariacionesDisponibles(String variacionesDisponibles) { this.variacionesDisponibles = variacionesDisponibles; }

    public List<Resena> getResenas() { return resenas; }
    public void setResenas(List<Resena> resenas) { this.resenas = resenas; }

    // ============================================================
    // MÉTODOS PARA CALIFICACIONES
    // ============================================================
    public Double getPromedioCalificacion() {
        if (resenas == null || resenas.isEmpty()) return 0.0;
        return resenas.stream()
                .filter(r -> r.getAprobado() != null && r.getAprobado())
                .mapToInt(Resena::getCalificacion)
                .average()
                .orElse(0.0);
    }

    public Long getCantidadResenas() {
        if (resenas == null) return 0L;
        return resenas.stream().filter(r -> r.getAprobado() != null && r.getAprobado()).count();
    }
}