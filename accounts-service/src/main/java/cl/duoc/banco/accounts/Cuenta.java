package cl.duoc.banco.accounts;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cuentas")
public class Cuenta {

    public enum Estado { ACTIVA, BLOQUEADA, CERRADA }

    @Id
    private Long id;

    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo;

    private Integer edad;

    /** ahorro | prestamo | hipoteca | corriente */
    @Column(nullable = false)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Estado estado = Estado.ACTIVA;

    @Column(name = "fecha_apertura")
    private LocalDate fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDate fechaCierre;

    /** Bloqueo optimista para cambios administrativos concurrentes. */
    @Version
    @JsonIgnore
    private Long version;

    protected Cuenta() {
        // requerido por JPA
    }

    public Cuenta(Long id, Long clienteId, String nombre, BigDecimal saldo, Integer edad, String tipo,
                  LocalDate fechaApertura) {
        this.id = id;
        this.clienteId = clienteId;
        this.nombre = nombre;
        this.saldo = saldo;
        this.edad = edad;
        this.tipo = tipo;
        this.fechaApertura = fechaApertura;
        this.estado = Estado.ACTIVA;
    }

    public void acreditar(BigDecimal monto) {
        this.saldo = this.saldo.add(monto);
    }

    public void debitar(BigDecimal monto) {
        this.saldo = this.saldo.subtract(monto);
    }

    public void cerrar() {
        this.estado = Estado.CERRADA;
        this.fechaCierre = LocalDate.now();
    }

    public void cambiarEstado(Estado nuevo) {
        this.estado = nuevo;
    }

    public void cambiarTipo(String nuevoTipo) {
        this.tipo = nuevoTipo;
    }

    public Long getId() { return id; }
    public Long getClienteId() { return clienteId; }
    public String getNombre() { return nombre; }
    public BigDecimal getSaldo() { return saldo; }
    public Integer getEdad() { return edad; }
    public String getTipo() { return tipo; }
    public Estado getEstado() { return estado; }
    public LocalDate getFechaApertura() { return fechaApertura; }
    public LocalDate getFechaCierre() { return fechaCierre; }
}
