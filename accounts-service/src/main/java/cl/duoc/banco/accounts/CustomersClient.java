package cl.duoc.banco.accounts;

import cl.duoc.banco.accounts.Dtos.ClienteDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Llama a customers-service (descubierto via Eureka) para validar al titular antes de abrir una cuenta. */
@Component
public class CustomersClient {

    private static final Logger log = LoggerFactory.getLogger(CustomersClient.class);
    private final RestClient restClient;

    public CustomersClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Retry(name = "customers", fallbackMethod = "fallback")
    @CircuitBreaker(name = "customers")
    public ClienteDto buscar(Long clienteId, String token) {
        try {
            return restClient.get()
                    .uri("http://customers-service/customers/{id}", clienteId)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(ClienteDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El cliente " + clienteId + " no existe");
        }
    }

    /**
     * Comportamiento alternativo: si customers-service no responde NO se abre la cuenta (no se puede validar al
     * titular). Se responde 503 para que el canal reintente mas tarde.
     */
    @SuppressWarnings("unused")
    private ClienteDto fallback(Long clienteId, String token, Throwable t) {
        if (t instanceof ResponseStatusException rse) {
            throw rse; // error de negocio: no es una falla del servicio remoto
        }
        log.warn("FALLBACK customers-service (cliente {}): {}", clienteId, t.toString());
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No es posible validar al cliente en este momento. Intente mas tarde.");
    }
}
