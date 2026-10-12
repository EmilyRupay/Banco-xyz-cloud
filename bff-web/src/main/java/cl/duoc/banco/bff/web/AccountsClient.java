package cl.duoc.banco.bff.web;

import cl.duoc.banco.bff.web.Dtos.CuentaDto;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

/** Cliente de accounts-service protegido con Resilience4j: Retry( CircuitBreaker( Bulkhead( llamada ) ) ) + Fallback. */
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
            return ClientResult.ok(restClient.get()
                    .uri("http://accounts-service/accounts/{id}", id)
                    .headers(h -> h.setBearerAuth(token)) // se propaga el JWT del usuario
                    .retrieve()
                    .body(CuentaDto.class));
        } catch (HttpClientErrorException.NotFound ex) {
            return ClientResult.ok(null); // la cuenta no existe: no es una falla del servicio
        }
    }

    @Retry(name = "accounts", fallbackMethod = "findByCustomerFallback")
    @CircuitBreaker(name = "accounts")
    @Bulkhead(name = "accounts")
    public ClientResult<List<CuentaDto>> findByCustomer(Long clienteId, String token) {
        List<CuentaDto> cuentas = restClient.get()
                .uri("http://accounts-service/accounts/customer/{id}", clienteId)
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .body(new ParameterizedTypeReference<List<CuentaDto>>() {
                });
        return ClientResult.ok(cuentas == null ? List.of() : cuentas);
    }

    @SuppressWarnings("unused")
    private ClientResult<CuentaDto> findByIdFallback(Long id, String token, Throwable t) {
        log.warn("FALLBACK accounts-service (cuenta {}): {}", id, t.toString());
        return ClientResult.degraded(null);
    }

    @SuppressWarnings("unused")
    private ClientResult<List<CuentaDto>> findByCustomerFallback(Long clienteId, String token, Throwable t) {
        log.warn("FALLBACK accounts-service (cliente {}): {}", clienteId, t.toString());
        return ClientResult.degraded(List.of());
    }
}
