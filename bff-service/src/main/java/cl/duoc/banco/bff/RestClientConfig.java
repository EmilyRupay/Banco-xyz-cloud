package cl.duoc.banco.bff;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * RestClient con balanceo de carga: permite llamar a "http://accounts-service/..." y Eureka
     * resuelve la direccion real. Los timeouts evitan que una llamada lenta bloquee hilos indefinidamente.
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        return RestClient.builder().requestFactory(factory);
    }
}
