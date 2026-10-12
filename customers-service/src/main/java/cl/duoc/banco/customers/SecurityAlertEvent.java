package cl.duoc.banco.customers;

import java.time.Instant;

/** Evento publicado en el topico security.alerts (contrato JSON compartido con notification-service). */
public record SecurityAlertEvent(String eventId, String tipo, String severidad, Long cuentaId, Long clienteId,
                                 String detalle, String origen, Instant occurredAt) {
}
