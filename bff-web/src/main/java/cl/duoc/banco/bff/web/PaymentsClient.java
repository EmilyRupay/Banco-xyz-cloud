package cl.duoc.banco.bff.web;

import cl.duoc.banco.bff.web.Dtos.MovimientoDto;
import cl.duoc.banco.bff.web.Dtos.TransferenciaWeb;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class PaymentsClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentsClient.class);
    private final RestClient restClient;

    public PaymentsClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Retry(name = "payments", fallbackMethod = "findByAccountFallback")
    @CircuitBreaker(name = "payments")
    @Bulkhead(name = "payments")
    public ClientResult<List<MovimientoDto>> findByAccount(Long cuentaId, Integer limite, String token) {
        List<MovimientoDto> movimientos = restClient.get()
                .uri("http://payments-service/payments/account/{id}?limite={limite}", cuentaId,
                        limite == null ? 1000 : limite)
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .body(new ParameterizedTypeReference<List<MovimientoDto>>() {
                });
        return ClientResult.ok(movimientos == null ? List.of() : movimientos);
    }

    /**
     * Transferencia: se reenvia la respuesta de payments-service tal cual (201, 422, 409...). Solo los 5xx o la falta
     * de respuesta cuentan como falla para el CircuitBreaker. No hay Retry aqui: el canal reintenta con la misma
     * Idempotency-Key si lo necesita.
     */
    @CircuitBreaker(name = "payments", fallbackMethod = "transferFallback")
    public ResponseEntity<String> transferir(TransferenciaWeb req, String idempotencyKey, String token) {
        ResponseEntity<String> respuesta = restClient.post()
                .uri("http://payments-service/payments/transfers")
                .headers(h -> {
                    h.setBearerAuth(token);
                    h.set("Idempotency-Key", idempotencyKey);
                    h.set("X-Channel", "WEB");
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .exchange((request, response) -> ResponseEntity.status(response.getStatusCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(response.bodyTo(String.class)));
        if (respuesta.getStatusCode().is5xxServerError()) {
            throw new IllegalStateException("payments-service respondio " + respuesta.getStatusCode());
        }
        return respuesta;
    }

    @SuppressWarnings("unused")
    private ClientResult<List<MovimientoDto>> findByAccountFallback(Long cuentaId, Integer limite, String token,
                                                                    Throwable t) {
        log.warn("FALLBACK payments-service (cuenta {}): {}", cuentaId, t.toString());
        return ClientResult.degraded(List.of());
    }

    @SuppressWarnings("unused")
    private ResponseEntity<String> transferFallback(TransferenciaWeb req, String idempotencyKey, String token,
                                                    Throwable t) {
        log.warn("FALLBACK transferencia {}: {}", idempotencyKey, t.toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"estado\":\"NO_PROCESADA\",\"mensaje\":\"No fue posible procesar la transferencia. "
                        + "Reintente con la misma Idempotency-Key.\",\"idempotencyKey\":\"" + idempotencyKey + "\"}");
    }
}
