package cl.duoc.banco.notifications;

import java.time.Instant;

/**
 * @param categoria TRANSACCION, CUENTA o SEGURIDAD
 * @param canal     canal simulado de envio (EMAIL, SMS, PUSH)
 */
public record Notification(String eventId, String categoria, String severidad, Long cuentaId, String canal,
                           String mensaje, Instant recibidaEn) {
}
