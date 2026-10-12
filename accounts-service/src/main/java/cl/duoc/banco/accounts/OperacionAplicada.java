package cl.duoc.banco.accounts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro de idempotencia: cada movimiento de saldo trae un operacionId unico. Si payments-service reintenta la misma
 * operacion (por un timeout o un Retry de Resilience4j), el saldo NO se modifica dos veces.
 */
@Entity
@Table(name = "operaciones_aplicadas")
public class OperacionAplicada {

    @Id
    @Column(length = 80)
    private String operacionId;

    @Column(nullable = false)
    private Long cuentaId;

    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoResultante;

    @Column(nullable = false)
    private Instant aplicadaEn;

    protected OperacionAplicada() {
    }

    public OperacionAplicada(String operacionId, Long cuentaId, String tipo, BigDecimal monto,
                             BigDecimal saldoResultante) {
        this.operacionId = operacionId;
        this.cuentaId = cuentaId;
        this.tipo = tipo;
        this.monto = monto;
        this.saldoResultante = saldoResultante;
        this.aplicadaEn = Instant.now();
    }

    public String getOperacionId() { return operacionId; }
    public Long getCuentaId() { return cuentaId; }
    public String getTipo() { return tipo; }
    public BigDecimal getMonto() { return monto; }
    public BigDecimal getSaldoResultante() { return saldoResultante; }
    public Instant getAplicadaEn() { return aplicadaEn; }
}
