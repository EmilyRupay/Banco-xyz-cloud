package cl.duoc.banco.bff.atm;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public final class Dtos {

    private Dtos() {
    }

    public record CuentaDto(Long id, BigDecimal saldo, String estado) {
    }

    public record RetiroRequest(@NotNull Long cuentaId, @NotNull @Positive BigDecimal monto) {
    }

    /** Peticion a payments-service. */
    public record PagoRequest(Long cuentaId, BigDecimal monto, String descripcion) {
    }

    /** Respuesta de payments-service (solo los campos necesarios). */
    public record OperacionDto(String operacionId, String estado, BigDecimal monto, BigDecimal saldoResultante,
                               String mensaje, boolean repetida) {
    }

    // ---- Respuestas CANAL ATM: minimas y sin datos personales ----
    public record SaldoAtm(String cuenta, BigDecimal saldoDisponible, String moneda, Instant consultadoEn) {
    }

    /** dispensar = true solo si el retiro se confirmo: el cajero entrega billetes unicamente en ese caso. */
    public record RetiroAtm(String operacionId, boolean dispensar, BigDecimal monto, BigDecimal saldoDisponible,
                            String mensaje) {
    }

    public static String enmascarar(Long cuentaId) {
        String s = String.valueOf(cuentaId);
        return "****" + (s.length() <= 4 ? s : s.substring(s.length() - 4));
    }
}
