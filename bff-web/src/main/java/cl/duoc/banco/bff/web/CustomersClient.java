package cl.duoc.banco.bff.web;

import cl.duoc.banco.bff.web.Dtos.ClienteDto;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class CustomersClient {

    private static final Logger log = LoggerFactory.getLogger(CustomersClient.class);
    private final RestClient restClient;

    public CustomersClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Retry(name = "customers", fallbackMethod = "fallback")
    @CircuitBreaker(name = "customers")
    @Bulkhead(name = "customers")
    public ClientResult<ClienteDto> findById(Long id, String token) {
        try {
            return ClientResult.ok(restClient.get()
                    .uri("http://customers-service/customers/{id}", id)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(ClienteDto.class));
        } catch (HttpClientErrorException.NotFound ex) {
            return ClientResult.ok(null);
        }
    }

    @SuppressWarnings("unused")
    private ClientResult<ClienteDto> fallback(Long id, String token, Throwable t) {
        log.warn("FALLBACK customers-service (cliente {}): {}", id, t.toString());
        return ClientResult.degraded(null);
    }
}
