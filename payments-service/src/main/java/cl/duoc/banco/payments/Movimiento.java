package cl.duoc.banco.payments;

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

    /** Operacion que origino el movimiento (null para los movimientos migrados del legacy). */
    @Column(name = "operacion_id", length = 80)
    private String operacionId;

    /** WEB, MOBILE, ATM, API o LEGACY */
    @Column(length = 10)
    private String canal;

    protected Movimiento() {
        // requerido por JPA
    }

    public Movimiento(Long cuentaId, LocalDate fecha, String tipo, BigDecimal monto, String descripcion) {
        this.cuentaId = cuentaId;
        this.fecha = fecha;
        this.tipo = tipo;
        this.monto = monto;
        this.descripcion = descripcion;
        this.canal = "LEGACY";
    }

    public Movimiento(Long cuentaId, LocalDate fecha, String tipo, BigDecimal monto, String descripcion,
                      String operacionId, String canal) {
        this(cuentaId, fecha, tipo, monto, descripcion);
        this.operacionId = operacionId;
        this.canal = canal;
    }

    public Long getId() { return id; }
    public Long getCuentaId() { return cuentaId; }
    public LocalDate getFecha() { return fecha; }
    public String getTipo() { return tipo; }
    public BigDecimal getMonto() { return monto; }
    public String getDescripcion() { return descripcion; }
    public String getOperacionId() { return operacionId; }
    public String getCanal() { return canal; }
}
