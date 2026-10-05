package cl.duoc.banco.notifications;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Representacion local del evento publicado por transactions-service (contrato por JSON, sin compartir clases). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionEvent(
        String eventId,
        String type,
        Long movimientoId,
        Long cuentaId,
        String tipo,
        BigDecimal monto,
        LocalDate fecha,
        Instant occurredAt) {
}
