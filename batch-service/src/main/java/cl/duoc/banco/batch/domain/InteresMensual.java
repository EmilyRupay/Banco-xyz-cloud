package cl.duoc.banco.batch.domain;

import java.math.BigDecimal;

/** naturaleza: ABONO (el banco paga al cliente, cuentas de ahorro) o CARGO (el cliente debe, creditos). */
public record InteresMensual(String periodo, long cuentaId, String nombre, String tipo, BigDecimal saldoInicial,
                             BigDecimal tasaMensual, BigDecimal interes, BigDecimal saldoFinal, String naturaleza) {
}
