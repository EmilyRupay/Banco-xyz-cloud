package cl.duoc.banco.batch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BatchInfraConfig {

    /**
     * Pool de hilos para el procesamiento paralelo (step multihilo de estados anuales y particiones de intereses).
     * Optimiza el tiempo total cuando el volumen de datos crece.
     */
    @Bean
    public ThreadPoolTaskExecutor batchTaskExecutor(BankBatchProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.threads());
        executor.setMaxPoolSize(props.threads());
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("batch-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    /** Espera exponencial entre reintentos: 500 ms, 1 s, 2 s... (maximo 5 s). */
    @Bean
    public ExponentialBackOffPolicy batchBackOffPolicy() {
        ExponentialBackOffPolicy policy = new ExponentialBackOffPolicy();
        policy.setInitialInterval(500);
        policy.setMultiplier(2.0);
        policy.setMaxInterval(5000);
        return policy;
    }
}
