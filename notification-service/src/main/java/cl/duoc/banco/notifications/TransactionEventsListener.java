package cl.duoc.banco.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TransactionEventsListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventsListener.class);

    private final ObjectMapper objectMapper;
    private final NotificationStore store;

    public TransactionEventsListener(ObjectMapper objectMapper, NotificationStore store) {
        this.objectMapper = objectMapper;
        this.store = store;
    }

    @KafkaListener(topics = "${bank.topics.transaction-created:transactions.created}")
    public void onTransactionCreated(String payload) throws Exception {
        // Si el JSON es invalido, lanza excepcion -> 3 reintentos -> Dead Letter Topic (ver KafkaConsumerConfig)
        TransactionEvent event = objectMapper.readValue(payload, TransactionEvent.class);
        if (event.eventId() == null || event.cuentaId() == null) {
            throw new IllegalArgumentException("Evento invalido: falta eventId o cuentaId");
        }

        String mensaje = "Se registro un(a) %s de $%s en la cuenta %d"
                .formatted(event.tipo(), event.monto(), event.cuentaId());

        if (store.addIfAbsent(new Notification(event.eventId(), event.cuentaId(), mensaje, Instant.now()))) {
            log.info("NOTIFICACION ENVIADA -> {}", mensaje);
        } else {
            log.warn("Evento duplicado {} ignorado (idempotencia)", event.eventId());
        }
    }
}
