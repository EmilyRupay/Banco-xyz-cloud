package cl.duoc.banco.notifications;

import java.time.Instant;

public record Notification(String eventId, Long cuentaId, String mensaje, Instant recibidaEn) {
}
