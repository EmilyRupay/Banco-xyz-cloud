package cl.duoc.banco.notifications;

import cl.duoc.banco.notifications.Eventos.AccountEvent;
import cl.duoc.banco.notifications.Eventos.SecurityAlert;
import cl.duoc.banco.notifications.Eventos.TransactionCompleted;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consumidores Kafka (procesamiento en tiempo real). Si un mensaje no se puede procesar se reintenta 3 veces y luego se
 * envia a "topico.DLT" (ver KafkaConsumerConfig). Son idempotentes: un evento repetido se ignora.
 */
@Component
public class EventListeners {

    private static final Logger log = LoggerFactory.getLogger(EventListeners.class);

    private final ObjectMapper mapper;
    private final NotificationStore store;
    private final MeterRegistry metrics;

    public EventListeners(ObjectMapper mapper, NotificationStore store, MeterRegistry metrics) {
        this.mapper = mapper;
        this.store = store;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "${bank.topics.transaction-completed:transactions.completed}")
    public void transaccionCompletada(String payload) throws Exception {
        TransactionCompleted e = mapper.readValue(payload, TransactionCompleted.class);
        exigir(e.eventId(), e.cuentaId());
        String mensaje = switch (e.tipo()) {
            case "TRANSFERENCIA" -> "Transferiste $%s de la cuenta %d a la cuenta %d"
                    .formatted(e.monto().toPlainString(), e.cuentaId(), e.cuentaDestino());
            case "RETIRO" -> "Retiro de $%s en %s. Saldo disponible $%s"
                    .formatted(e.monto().toPlainString(), e.canal(), texto(e.saldoResultante()));
            default -> "%s de $%s en la cuenta %d".formatted(e.tipo(), e.monto().toPlainString(), e.cuentaId());
        };
        guardar(new Notification(e.eventId(), "TRANSACCION", "INFO", e.cuentaId(), "PUSH", mensaje, Instant.now()));
    }

    @KafkaListener(topics = "${bank.topics.security-alerts:security.alerts}")
    public void alertaSeguridad(String payload) throws Exception {
        SecurityAlert a = mapper.readValue(payload, SecurityAlert.class);
        exigir(a.eventId(), a.cuentaId() != null ? a.cuentaId() : a.clienteId());
        String mensaje = "[%s] %s (origen %s)".formatted(a.tipo(), a.detalle(), a.origen());
        if (guardar(new Notification(a.eventId(), "SEGURIDAD", a.severidad(), a.cuentaId(), "SMS", mensaje,
                Instant.now()))) {
            log.warn("ALERTA DE SEGURIDAD severidad={} -> {}", a.severidad(), mensaje);
            metrics.counter("bank.security.alerts", "tipo", a.tipo(), "severidad", String.valueOf(a.severidad()))
                    .increment();
        }
    }

    @KafkaListener(topics = "${bank.topics.account-events:accounts.events}")
    public void eventoCuenta(String payload) throws Exception {
        AccountEvent e = mapper.readValue(payload, AccountEvent.class);
        exigir(e.eventId(), e.cuentaId());
        String mensaje = "%s cuenta %d (%s) %s".formatted(e.type(), e.cuentaId(), e.tipo(),
                e.detalle() == null ? "" : e.detalle());
        guardar(new Notification(e.eventId(), "CUENTA", "INFO", e.cuentaId(), "EMAIL", mensaje, Instant.now()));
    }

    private boolean guardar(Notification n) {
        if (store.addIfAbsent(n)) {
            log.info("NOTIFICACION ENVIADA [{}] por {} -> {}", n.categoria(), n.canal(), n.mensaje());
            metrics.counter("bank.notifications", "categoria", n.categoria()).increment();
            return true;
        }
        log.warn("Evento duplicado {} ignorado (idempotencia)", n.eventId());
        return false;
    }

    /** Un evento sin identificadores es invalido: la excepcion lo envia a la Dead Letter Topic tras los reintentos. */
    private static void exigir(String eventId, Long id) {
        if (eventId == null || id == null) {
            throw new IllegalArgumentException("Evento invalido: falta eventId o cuentaId");
        }
    }

    private static String texto(java.math.BigDecimal v) {
        return v == null ? "-" : v.toPlainString();
    }
}
