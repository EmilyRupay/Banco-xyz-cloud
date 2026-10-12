package cl.duoc.banco.customers;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "clientes")
public class Cliente {

    public enum Segmento { PERSONA, PREMIUM, PYME }

    public enum Estado { ACTIVO, INACTIVO }

    @Id
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String rut;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(length = 20)
    private String telefono;

    @Column(length = 200)
    private String direccion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Segmento segmento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Estado estado;

    @Column(nullable = false)
    private Instant creadoEn;

    private Instant actualizadoEn;

    /** Bloqueo optimista: evita que dos actualizaciones concurrentes se pisen (consistencia). */
    @Version
    private Long version;

    protected Cliente() {
        // JPA
    }

    public Cliente(Long id, String rut, String nombre, String email, String telefono, String direccion,
                   Segmento segmento) {
        this.id = id;
        this.rut = rut;
        this.nombre = nombre;
        this.email = email;
        this.telefono = telefono;
        this.direccion = direccion;
        this.segmento = segmento;
        this.estado = Estado.ACTIVO;
        this.creadoEn = Instant.now();
    }

    public void actualizar(String nombre, String email, String telefono, String direccion, Segmento segmento) {
        this.nombre = nombre;
        this.email = email;
        this.telefono = telefono;
        this.direccion = direccion;
        this.segmento = segmento;
        this.actualizadoEn = Instant.now();
    }

    public void desactivar() {
        this.estado = Estado.INACTIVO;
        this.actualizadoEn = Instant.now();
    }

    public Long getId() { return id; }
    public String getRut() { return rut; }
    public String getNombre() { return nombre; }
    public String getEmail() { return email; }
    public String getTelefono() { return telefono; }
    public String getDireccion() { return direccion; }
    public Segmento getSegmento() { return segmento; }
    public Estado getEstado() { return estado; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }
}
