package cl.duoc.banco.transactions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "movimientos")
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    private String descripcion;

    protected Movimiento() {
        // requerido por JPA
    }

    public Movimiento(Long cuentaId, LocalDate fecha, String tipo, BigDecimal monto, String descripcion) {
        this.cuentaId = cuentaId;
        this.fecha = fecha;
        this.tipo = tipo;
        this.monto = monto;
        this.descripcion = descripcion;
    }

    public Long getId() { return id; }
    public Long getCuentaId() { return cuentaId; }
    public LocalDate getFecha() { return fecha; }
    public String getTipo() { return tipo; }
    public BigDecimal getMonto() { return monto; }
    public String getDescripcion() { return descripcion; }
}
