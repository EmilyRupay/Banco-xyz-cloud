package cl.duoc.banco.payments;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** 3 particiones por topico: permite hasta 3 consumidores en paralelo por grupo (escalabilidad horizontal). */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic transactionCompletedTopic(
            @Value("${bank.topics.transaction-completed:transactions.completed}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic securityAlertsTopic(@Value("${bank.topics.security-alerts:security.alerts}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
