package cl.duoc.banco.batch.domain;

import java.math.BigDecimal;

/** Cuenta valida (sin duplicados) sobre la que se calculara el interes del periodo. */
public record CuentaInteres(String periodo, int linea, long cuentaId, String nombre, BigDecimal saldo, int edad,
                            String tipo) {
}
