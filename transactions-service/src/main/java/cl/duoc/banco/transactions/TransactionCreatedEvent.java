package cl.duoc.banco.transactions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Evento de dominio publicado en Kafka cuando se registra un movimiento. eventId permite deduplicar en el consumidor. */
public record TransactionCreatedEvent(
        String eventId,
        String type,
        Long movimientoId,
        Long cuentaId,
        String tipo,
        BigDecimal monto,
        LocalDate fecha,
        Instant occurredAt) {
}
