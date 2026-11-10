package com.flowcolombia.flowcolombia;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombreCliente;
    private String telefono;
    private String email;
    private String departamento;
    private String ciudad;
    private String direccion;
    private String notas;
    private String tienda;
    @Column(length = 3000)
    private String productos;
    private Double total;
    private String metodoPago;
    private String estado;
    private String codigo;
    private String transportadora;
    private String numeroGuia;
    private LocalDateTime fecha;

    // Nuevo campo para saber si el pedido ha sido "leído" en el panel de admin
    private Boolean leido = false;

    /**
     * IDs de producto que envió el checkout, en el MISMO JSON del pedido.
     *
     * <p>No se persiste ({@code @Transient}): la tabla {@code pedidos} guarda
     * los artículos como texto y no tiene líneas de pedido, así que este dato
     * solo vive durante la petición {@code POST /api/pedido}.</p>
     *
     * <p>Sirve para una única decisión: saber si el pedido contiene algún
     * producto marcado como «ocultar a Meta» y, en tal caso, no enviar el
     * evento {@code Purchase} a la API de Conversiones. Es la fuente
     * autoritativa; el backend además busca los nombres de los productos
     * excluidos dentro del texto, para los carritos antiguos que no mandan
     * esta lista.</p>
     */
    @Transient
    private List<Long> productosIds;

    public Pedido() {}

    // Getters y Setters (incluyendo el nuevo campo 'leido')
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombreCliente() { return nombreCliente; }
    public void setNombreCliente(String nombreCliente) { this.nombreCliente = nombreCliente; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartamento() { return departamento; }
    public void setDepartamento(String departamento) { this.departamento = departamento; }

    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }

    public String getTienda() { return tienda; }
    public void setTienda(String tienda) { this.tienda = tienda; }

    public String getProductos() { return productos; }
    public void setProductos(String productos) { this.productos = productos; }

    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getTransportadora() { return transportadora; }
    public void setTransportadora(String transportadora) { this.transportadora = transportadora; }

    public String getNumeroGuia() { return numeroGuia; }
    public void setNumeroGuia(String numeroGuia) { this.numeroGuia = numeroGuia; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public Boolean getLeido() { return leido; }
    public void setLeido(Boolean leido) { this.leido = leido; }

    /** IDs de producto enviados por el checkout (no se persisten). */
    public List<Long> getProductosIds() { return productosIds; }
    public void setProductosIds(List<Long> productosIds) { this.productosIds = productosIds; }
}