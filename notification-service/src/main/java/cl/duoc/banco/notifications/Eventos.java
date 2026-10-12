package cl.duoc.banco.notifications;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/** Representacion local de los eventos (contrato por JSON: no se comparten clases entre microservicios). */
public final class Eventos {

    private Eventos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransactionCompleted(String eventId, String operacionId, String tipo, Long cuentaId,
                                       Long cuentaDestino, BigDecimal monto, String canal,
                                       BigDecimal saldoResultante, Instant occurredAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecurityAlert(String eventId, String tipo, String severidad, Long cuentaId, Long clienteId,
                                String detalle, String origen, Instant occurredAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccountEvent(String eventId, String type, Long cuentaId, Long clienteId, String tipo,
                               String detalle, Instant occurredAt) {
    }
}
