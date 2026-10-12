package cl.duoc.banco.accounts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public final class Dtos {

    private Dtos() {
    }

    /** Apertura de cuenta. */
    public record AperturaRequest(@NotNull Long clienteId,
                                  @NotBlank @Pattern(regexp = "ahorro|corriente|prestamo|hipoteca") String tipo,
                                  @PositiveOrZero BigDecimal depositoInicial) {
    }

    /** Mantenimiento: cambio de tipo de producto. */
    public record ActualizacionRequest(@NotBlank @Pattern(regexp = "ahorro|corriente|prestamo|hipoteca") String tipo) {
    }

    public record EstadoRequest(@NotNull Cuenta.Estado estado, String motivo) {
    }

    /** Movimiento de saldo solicitado por payments-service. */
    public record MovimientoSaldoRequest(@NotBlank String operacionId,
                                         @NotBlank @Pattern(regexp = "DEBITO|CREDITO") String tipo,
                                         @NotNull @Positive BigDecimal monto) {
    }

    public record MovimientoSaldoResponse(String operacionId, Long cuentaId, String tipo, BigDecimal monto,
                                          BigDecimal saldoResultante, boolean duplicada) {
    }

    /** Dato minimo que se obtiene de customers-service. */
    public record ClienteDto(Long id, String nombre, String estado) {
    }

    /** Evento de dominio publicado en el topico accounts.events. */
    public record AccountEvent(String eventId, String type, Long cuentaId, Long clienteId, String tipo,
                               String detalle, Instant occurredAt) {
    }

    /** Evento publicado en el topico security.alerts. */
    public record SecurityAlertEvent(String eventId, String tipo, String severidad, Long cuentaId, Long clienteId,
                                     String detalle, String origen, Instant occurredAt) {
    }
}
