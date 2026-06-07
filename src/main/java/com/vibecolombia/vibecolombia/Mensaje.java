package com.vibecolombia.vibecolombia;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mensajes")
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String codigoPedido;
    private String telefono;
    private String mensaje;
    private String respuesta;
    private boolean esCliente;
    private LocalDateTime fecha;

    public Mensaje() {}

    public Mensaje(String codigoPedido, String telefono, String mensaje, boolean esCliente) {
        this.codigoPedido = codigoPedido;
        this.telefono = telefono;
        this.mensaje = mensaje;
        this.esCliente = esCliente;
        this.fecha = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigoPedido() { return codigoPedido; }
    public void setCodigoPedido(String codigoPedido) { this.codigoPedido = codigoPedido; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public String getRespuesta() { return respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }
    public boolean isEsCliente() { return esCliente; }
    public void setEsCliente(boolean esCliente) { this.esCliente = esCliente; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}