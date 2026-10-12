package cl.duoc.banco.batch.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Transaccion diaria valida. alerta != null indica una anomalia que no invalida el registro (ej. monto alto). */
public record TransaccionDiaria(String fechaProceso, int linea, long idOrigen, LocalDate fecha, String tipo,
                                BigDecimal monto, Motivo alerta, String alertaDetalle) {
}
