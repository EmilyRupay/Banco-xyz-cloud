package cl.duoc.banco.bff.atm;

import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /** Balanceo de carga via Eureka ("http://accounts-service/..."), propagacion de trazas y timeouts. */
    @Bean
    @LoadBalanced
    public RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return configurer.configure(RestClient.builder()).requestFactory(factory);
    }
}
