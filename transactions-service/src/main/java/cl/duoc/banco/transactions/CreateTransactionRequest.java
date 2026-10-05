package cl.duoc.banco.transactions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        @NotNull Long cuentaId,
        @NotBlank String tipo,
        @NotNull @Positive BigDecimal monto,
        String descripcion) {
}
