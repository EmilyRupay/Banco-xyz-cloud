package cl.duoc.banco.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Operacion de pago. Su id es la llave de idempotencia (header Idempotency-Key): si el canal reenvia la misma
 * solicitud (por ejemplo un cajero que no recibio la respuesta), se devuelve el resultado original sin cobrar dos veces.
 */
@Entity
@Table(name = "operaciones")
public class Operacion {

    public enum Tipo { DEPOSITO, RETIRO, PAGO, TRANSFERENCIA }

    /** PENDIENTE_REVISION: una transferencia cuya compensacion fallo y requiere conciliacion manual. */
    public enum Estado { PENDIENTE, COMPLETADA, RECHAZADA, REVERTIDA, PENDIENTE_REVISION }

    @Id
    @Column(length = 80)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Tipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "cuenta_origen", nullable = false)
    private Long cuentaOrigen;

    @Column(name = "cuenta_destino")
    private Long cuentaDestino;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 10)
    private String canal;

    private String descripcion;

    private String mensaje;

    @Column(name = "saldo_resultante", precision = 15, scale = 2)
    private BigDecimal saldoResultante;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "actualizada_en")
    private Instant actualizadaEn;

    protected Operacion() {
    }

    public Operacion(String id, Tipo tipo, Long cuentaOrigen, Long cuentaDestino, BigDecimal monto, String canal,
                     String descripcion) {
        this.id = id;
        this.tipo = tipo;
        this.estado = Estado.PENDIENTE;
        this.cuentaOrigen = cuentaOrigen;
        this.cuentaDestino = cuentaDestino;
        this.monto = monto;
        this.canal = canal;
        this.descripcion = descripcion;
        this.creadaEn = Instant.now();
    }

    public void completar(BigDecimal saldo) {
        cambiar(Estado.COMPLETADA, "Operacion realizada con exito");
        this.saldoResultante = saldo;
    }

    public void rechazar(String motivo) {
        cambiar(Estado.RECHAZADA, motivo);
    }

    public void revertir(String motivo) {
        cambiar(Estado.REVERTIDA, motivo);
    }

    public void pendienteRevision(String motivo) {
        cambiar(Estado.PENDIENTE_REVISION, motivo);
    }

    private void cambiar(Estado nuevo, String msg) {
        this.estado = nuevo;
        this.mensaje = msg;
        this.actualizadaEn = Instant.now();
    }

    public String getId() { return id; }
    public Tipo getTipo() { return tipo; }
    public Estado getEstado() { return estado; }
    public Long getCuentaOrigen() { return cuentaOrigen; }
    public Long getCuentaDestino() { return cuentaDestino; }
    public BigDecimal getMonto() { return monto; }
    public String getCanal() { return canal; }
    public String getDescripcion() { return descripcion; }
    public String getMensaje() { return mensaje; }
    public BigDecimal getSaldoResultante() { return saldoResultante; }
    public Instant getCreadaEn() { return creadaEn; }
    public Instant getActualizadaEn() { return actualizadaEn; }
}
