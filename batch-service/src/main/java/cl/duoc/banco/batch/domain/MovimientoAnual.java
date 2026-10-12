package cl.duoc.banco.batch.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovimientoAnual(int anio, int linea, long cuentaId, LocalDate fecha, String tipo, BigDecimal monto,
                              String descripcion) {
}
