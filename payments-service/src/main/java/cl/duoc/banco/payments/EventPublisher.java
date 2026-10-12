package cl.duoc.banco.payments;

import cl.duoc.banco.payments.Dtos.SecurityAlertEvent;
import cl.duoc.banco.payments.Dtos.TransactionCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Productor Kafka. Clave del mensaje = cuentaId: todos los eventos de una cuenta van a la misma particion, por lo que
 * los consumidores los reciben en orden.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topicTransacciones;
    private final String topicAlertas;

    public EventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                          @Value("${bank.topics.transaction-completed:transactions.completed}") String topicTransacciones,
                          @Value("${bank.topics.security-alerts:security.alerts}") String topicAlertas) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topicTransacciones = topicTransacciones;
        this.topicAlertas = topicAlertas;
    }

    public void transaccionCompletada(Operacion o) {
        enviar(topicTransacciones, o.getCuentaOrigen(), new TransactionCompletedEvent(UUID.randomUUID().toString(),
                "TRANSACTION_COMPLETED", o.getId(), o.getTipo().name(), o.getCuentaOrigen(), o.getCuentaDestino(),
                o.getMonto(), o.getCanal(), o.getSaldoResultante(), Instant.now()));
    }

    public void alerta(String tipo, String severidad, Long cuentaId, String detalle) {
        enviar(topicAlertas, cuentaId, new SecurityAlertEvent(UUID.randomUUID().toString(), tipo, severidad,
                cuentaId, null, detalle, "payments-service", Instant.now()));
    }

    private void enviar(String topic, Long clave, Object evento) {
        try {
            String json = mapper.writeValueAsString(evento);
            kafka.send(topic, String.valueOf(clave), json).whenComplete((r, ex) -> {
                if (ex != null) {
                    log.error("No se pudo publicar en {}: {}", topic, ex.getMessage());
                } else {
                    log.info("Evento publicado en {} (particion {}, offset {})", topic,
                            r.getRecordMetadata().partition(), r.getRecordMetadata().offset());
                }
            });
        } catch (Exception ex) {
            log.error("No se pudo serializar el evento para {}", topic, ex);
        }
    }
}
