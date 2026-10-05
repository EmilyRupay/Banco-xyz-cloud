package cl.duoc.banco.transactions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public TransactionEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper,
                                     @Value("${bank.topics.transaction-created:transactions.created}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(TransactionCreatedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            // La clave = cuentaId: todos los eventos de una misma cuenta van a la misma particion (orden garantizado)
            kafkaTemplate.send(topic, String.valueOf(event.cuentaId()), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("No se pudo publicar el evento {} en {}", event.eventId(), topic, ex);
                        } else {
                            log.info("Evento {} publicado en {} (particion {}, offset {})", event.eventId(), topic,
                                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el evento", ex);
        }
    }
}
