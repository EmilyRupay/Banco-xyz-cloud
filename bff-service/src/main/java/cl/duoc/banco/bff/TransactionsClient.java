package cl.duoc.banco.bff;

import cl.duoc.banco.bff.Dtos.MovimientoDto;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class TransactionsClient {

    private static final Logger log = LoggerFactory.getLogger(TransactionsClient.class);

    private final RestClient restClient;

    public TransactionsClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Retry(name = "transactions", fallbackMethod = "findByAccountFallback")
    @CircuitBreaker(name = "transactions")
    @Bulkhead(name = "transactions")
    public ClientResult<List<MovimientoDto>> findByAccount(Long cuentaId, String token) {
        List<MovimientoDto> movimientos = restClient.get()
                .uri("http://transactions-service/transactions/account/{id}", cuentaId)
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .body(new ParameterizedTypeReference<List<MovimientoDto>>() {
                });
        return ClientResult.ok(movimientos == null ? List.of() : movimientos);
    }

    @SuppressWarnings("unused") // invocado por Resilience4j
    private ClientResult<List<MovimientoDto>> findByAccountFallback(Long cuentaId, String token, Throwable t) {
        log.warn("FALLBACK transactions-service (cuenta {}): {} - {}", cuentaId, t.getClass().getSimpleName(),
                t.getMessage());
        return ClientResult.degraded(List.of());
    }
}
