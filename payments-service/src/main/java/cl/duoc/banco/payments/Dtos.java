package cl.duoc.banco.payments;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class Dtos {

    private Dtos() {
    }

    /** Deposito, retiro o pago sobre una cuenta. */
    public record OperacionRequest(@NotNull Long cuentaId,
                                   @NotNull @Positive @DecimalMax("100000000") BigDecimal monto,
                                   @Size(max = 200) String descripcion) {
    }

    public record TransferenciaRequest(@NotNull Long cuentaOrigen,
                                       @NotNull Long cuentaDestino,
                                       @NotNull @Positive @DecimalMax("100000000") BigDecimal monto,
                                       @Size(max = 200) String descripcion) {
    }

    public record OperacionResponse(String operacionId, String tipo, String estado, Long cuentaOrigen,
                                    Long cuentaDestino, BigDecimal monto, String canal, BigDecimal saldoResultante,
                                    String mensaje, Instant fecha, boolean repetida) {

        public static OperacionResponse de(Operacion o, boolean repetida) {
            return new OperacionResponse(o.getId(), o.getTipo().name(), o.getEstado().name(), o.getCuentaOrigen(),
                    o.getCuentaDestino(), o.getMonto(), o.getCanal(), o.getSaldoResultante(), o.getMensaje(),
                    o.getCreadaEn(), repetida);
        }
    }

    /** Peticion a accounts-service para mover saldo. */
    public record MovimientoSaldoRequest(String operacionId, String tipo, BigDecimal monto) {
    }

    public record MovimientoSaldoResponse(String operacionId, Long cuentaId, String tipo, BigDecimal monto,
                                          BigDecimal saldoResultante, boolean duplicada) {
    }

    /** Evento publicado en transactions.completed. */
    public record TransactionCompletedEvent(String eventId, String type, String operacionId, String tipo,
                                            Long cuentaId, Long cuentaDestino, BigDecimal monto, String canal,
                                            BigDecimal saldoResultante, Instant occurredAt) {
    }

    /** Evento publicado en security.alerts. */
    public record SecurityAlertEvent(String eventId, String tipo, String severidad, Long cuentaId, Long clienteId,
                                     String detalle, String origen, Instant occurredAt) {
    }
}
