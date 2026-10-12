package cl.duoc.banco.accounts;

import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /** RestClient con balanceo de carga (Eureka + Spring Cloud LoadBalancer), trazas y timeouts. */
    @Bean
    @LoadBalanced
    public RestClient.Builder restClientBuilder(RestClientBuilderConfigurer configurer) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        return configurer.configure(RestClient.builder()).requestFactory(factory);
    }
}
