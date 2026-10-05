package cl.duoc.banco.bff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs: lo que el BFF recibe de los microservicios y lo que entrega a cada canal. */
public final class Dtos {

    private Dtos() {
    }

    // ---- Recibidos desde los microservicios ----
    public record CuentaDto(Long id, String nombre, BigDecimal saldo, Integer edad, String tipo) {
    }

    public record MovimientoDto(Long id, Long cuentaId, LocalDate fecha, String tipo, BigDecimal monto,
                                String descripcion) {
    }

    // ---- Respuesta CANAL WEB: completa ----
    public record WebSummary(CuentaDto cuenta, List<MovimientoDto> movimientos, BigDecimal totalIngresos,
                             BigDecimal totalEgresos, boolean degraded, List<String> advertencias) {
    }

    // ---- Respuesta CANAL MOVIL: liviana ----
    public record MovimientoResumen(LocalDate fecha, String tipo, BigDecimal monto) {
    }

    public record MobileSummary(String titular, BigDecimal saldo, List<MovimientoResumen> ultimosMovimientos,
                                boolean degraded) {
    }
}
