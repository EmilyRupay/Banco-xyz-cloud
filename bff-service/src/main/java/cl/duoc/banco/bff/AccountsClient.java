package cl.duoc.banco.bff;

import cl.duoc.banco.bff.Dtos.CuentaDto;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Cliente de accounts-service protegido con Resilience4j.
 * Orden de ejecucion: Retry( CircuitBreaker( Bulkhead( llamada ) ) ) y, si todo falla, Fallback.
 */
@Component
public class AccountsClient {

    private static final Logger log = LoggerFactory.getLogger(AccountsClient.class);

    private final RestClient restClient;

    public AccountsClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Retry(name = "accounts", fallbackMethod = "findByIdFallback")
    @CircuitBreaker(name = "accounts")
    @Bulkhead(name = "accounts")
    public ClientResult<CuentaDto> findById(Long id, String token) {
        try {
            CuentaDto cuenta = restClient.get()
                    .uri("http://accounts-service/accounts/{id}", id)
                    .headers(h -> h.setBearerAuth(token)) // se propaga el JWT del usuario
                    .retrieve()
                    .body(CuentaDto.class);
            return ClientResult.ok(cuenta);
        } catch (HttpClientErrorException.NotFound ex) {
            return ClientResult.ok(null); // la cuenta no existe: no es una falla del servicio
        }
    }

    @SuppressWarnings("unused") // invocado por Resilience4j
    private ClientResult<CuentaDto> findByIdFallback(Long id, String token, Throwable t) {
        log.warn("FALLBACK accounts-service (cuenta {}): {} - {}", id, t.getClass().getSimpleName(), t.getMessage());
        return ClientResult.degraded(null);
    }
}
