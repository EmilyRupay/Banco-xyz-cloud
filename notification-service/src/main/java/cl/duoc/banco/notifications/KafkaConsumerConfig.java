package cl.duoc.banco.notifications;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    /** Reintentos controlados: 3 reintentos, 1 segundo entre cada uno. Luego el mensaje va a "<topico>.DLT". */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }

    @Bean
    public NewTopic transaccionesDlt(@Value("${bank.topics.transaction-completed:transactions.completed}") String t) {
        return TopicBuilder.name(t + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic alertasDlt(@Value("${bank.topics.security-alerts:security.alerts}") String t) {
        return TopicBuilder.name(t + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic cuentasDlt(@Value("${bank.topics.account-events:accounts.events}") String t) {
        return TopicBuilder.name(t + ".DLT").partitions(3).replicas(1).build();
    }

    /** Los topicos de origen tambien se declaran aqui por si el consumidor inicia antes que los productores. */
    @Bean
    public NewTopic transacciones(@Value("${bank.topics.transaction-completed:transactions.completed}") String t) {
        return TopicBuilder.name(t).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic alertas(@Value("${bank.topics.security-alerts:security.alerts}") String t) {
        return TopicBuilder.name(t).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic cuentas(@Value("${bank.topics.account-events:accounts.events}") String t) {
        return TopicBuilder.name(t).partitions(3).replicas(1).build();
    }
}
