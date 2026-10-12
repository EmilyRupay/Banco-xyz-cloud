package cl.duoc.banco.customers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topicAlertas;

    public EventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                          @Value("${bank.topics.security-alerts:security.alerts}") String topicAlertas) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topicAlertas = topicAlertas;
    }

    /** Un cambio de email/telefono es una senal clasica de toma de cuenta (account takeover): se alerta. */
    public void cambioDatosContacto(Cliente cliente, String detalle) {
        SecurityAlertEvent alerta = new SecurityAlertEvent(UUID.randomUUID().toString(), "CAMBIO_DATOS_CONTACTO",
                "MEDIA", null, cliente.getId(), detalle, "customers-service", Instant.now());
        try {
            kafka.send(topicAlertas, String.valueOf(cliente.getId()), mapper.writeValueAsString(alerta));
            log.info("Alerta de seguridad publicada: {}", alerta);
        } catch (Exception ex) {
            // La actualizacion del cliente no se revierte si la alerta no se puede publicar; queda en el log
            log.error("No se pudo publicar la alerta de seguridad del cliente {}", cliente.getId(), ex);
        }
    }
}
