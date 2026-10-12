package cl.duoc.banco.accounts;

import cl.duoc.banco.accounts.Dtos.AccountEvent;
import cl.duoc.banco.accounts.Dtos.SecurityAlertEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Productor Kafka: accounts.events (ciclo de vida de la cuenta) y security.alerts. */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topicCuentas;
    private final String topicAlertas;

    public EventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                          @Value("${bank.topics.account-events:accounts.events}") String topicCuentas,
                          @Value("${bank.topics.security-alerts:security.alerts}") String topicAlertas) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topicCuentas = topicCuentas;
        this.topicAlertas = topicAlertas;
    }

    public void cuenta(String tipoEvento, Cuenta c, String detalle) {
        enviar(topicCuentas, c.getId(), new AccountEvent(UUID.randomUUID().toString(), tipoEvento, c.getId(),
                c.getClienteId(), c.getTipo(), detalle, Instant.now()));
    }

    public void alerta(String tipo, String severidad, Cuenta c, String detalle) {
        enviar(topicAlertas, c.getId(), new SecurityAlertEvent(UUID.randomUUID().toString(), tipo, severidad,
                c.getId(), c.getClienteId(), detalle, "accounts-service", Instant.now()));
    }

    private void enviar(String topic, Long clave, Object evento) {
        try {
            kafka.send(topic, String.valueOf(clave), mapper.writeValueAsString(evento))
                    .whenComplete((r, ex) -> {
                        if (ex != null) {
                            log.error("No se pudo publicar en {}: {}", topic, ex.getMessage());
                        }
                    });
        } catch (Exception ex) {
            log.error("No se pudo serializar el evento para {}", topic, ex);
        }
    }
}
