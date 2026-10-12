package cl.duoc.banco.bff.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs: lo que el BFF recibe de los microservicios y lo que entrega al navegador. */
public final class Dtos {

    private Dtos() {
    }

    // ---- Recibidos desde los microservicios ----
    public record CuentaDto(Long id, Long clienteId, String nombre, BigDecimal saldo, Integer edad, String tipo,
                            String estado, LocalDate fechaApertura) {
    }

    public record MovimientoDto(Long id, Long cuentaId, LocalDate fecha, String tipo, BigDecimal monto,
                                String descripcion, String canal) {
    }

    public record ClienteDto(Long id, String rut, String nombre, String email, String telefono, String direccion,
                             String segmento, String estado) {
    }

    // ---- Respuestas CANAL WEB: completas ----
    public record WebSummary(CuentaDto cuenta, List<MovimientoDto> movimientos, BigDecimal totalIngresos,
                             BigDecimal totalEgresos, boolean degraded, List<String> advertencias) {
    }

    public record CuentaConMovimientos(CuentaDto cuenta, List<MovimientoDto> ultimosMovimientos) {
    }

    /** Panel del cliente: perfil + todas sus cuentas + ultimos movimientos + patrimonio. */
    public record Dashboard(ClienteDto cliente, List<CuentaConMovimientos> cuentas, BigDecimal saldoTotal,
                            int cantidadCuentas, boolean degraded, List<String> advertencias) {
    }

    // ---- Operaciones ----
    public record TransferenciaWeb(@NotNull Long cuentaOrigen, @NotNull Long cuentaDestino,
                                   @NotNull @Positive BigDecimal monto, String descripcion) {
    }
}
