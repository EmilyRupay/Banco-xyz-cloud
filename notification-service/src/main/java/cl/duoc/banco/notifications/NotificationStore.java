package cl.duoc.banco.notifications;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacen en memoria. La clave es el eventId, lo que da IDEMPOTENCIA: si Kafka entrega el mismo
 * evento dos veces (semantica at-least-once), la segunda vez se ignora.
 * En produccion esto seria una tabla en BD con restriccion UNIQUE sobre eventId.
 */
@Component
public class NotificationStore {

    private final Map<String, Notification> notifications = new ConcurrentHashMap<>();

    /** @return true si es nueva, false si ya habia sido procesada */
    public boolean addIfAbsent(Notification notification) {
        return notifications.putIfAbsent(notification.eventId(), notification) == null;
    }

    public List<Notification> findAll() {
        return notifications.values().stream()
                .sorted((a, b) -> b.recibidaEn().compareTo(a.recibidaEn()))
                .toList();
    }
}
