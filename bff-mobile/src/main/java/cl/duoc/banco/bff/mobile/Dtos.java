package cl.duoc.banco.bff.mobile;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class Dtos {

    private Dtos() {
    }

    // ---- Recibidos desde los microservicios (solo los campos que el movil necesita) ----
    public record CuentaDto(Long id, String nombre, BigDecimal saldo, String tipo, String estado) {
    }

    public record MovimientoDto(LocalDate fecha, String tipo, BigDecimal monto) {
    }

    // ---- Respuestas CANAL MOVIL: livianas; los campos null no se envian ----
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MobileSummary(String titular, BigDecimal saldo, List<MovimientoDto> ultimos, Boolean degradado) {
    }

    public record CuentaItem(Long id, String tipo, BigDecimal saldo) {
    }
}
