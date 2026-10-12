package cl.duoc.banco.payments;

import cl.duoc.banco.payments.Dtos.MovimientoSaldoRequest;
import cl.duoc.banco.payments.Dtos.MovimientoSaldoResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Cliente de accounts-service protegido con Resilience4j (Retry + CircuitBreaker + fallback).
 *
 * <p>Reintentar es SEGURO porque accounts-service es idempotente por operacionId.
 * Los errores 4xx (reglas de negocio) se ignoran en el Retry y en el CircuitBreaker (ver config-repo).
 */
@Component
public class AccountsClient {

    private static final Logger log = LoggerFactory.getLogger(AccountsClient.class);

    private final RestClient restClient;
    private final ObjectMapper mapper;

    public AccountsClient(RestClient.Builder builder, ObjectMapper mapper) {
        this.restClient = builder.build();
        this.mapper = mapper;
    }

    @Retry(name = "accounts", fallbackMethod = "fallback")
    @CircuitBreaker(name = "accounts")
    public MovimientoSaldoResponse aplicar(Long cuentaId, String operacionId, String tipo, BigDecimal monto,
                                           String token) {
        try {
            return restClient.post()
                    .uri("http://accounts-service/accounts/{id}/movements", cuentaId)
                    .headers(h -> h.setBearerAuth(token))
                    .body(new MovimientoSaldoRequest(operacionId, tipo, monto))
                    .retrieve()
                    .body(MovimientoSaldoResponse.class);
        } catch (HttpClientErrorException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
            throw new OperacionException.Rechazada(status == null ? HttpStatus.UNPROCESSABLE_ENTITY : status,
                    mensaje(ex));
        }
    }

    /** Comportamiento alternativo: la operacion NO se realiza y se informa al canal que reintente mas tarde. */
    @SuppressWarnings("unused")
    private MovimientoSaldoResponse fallback(Long cuentaId, String operacionId, String tipo, BigDecimal monto,
                                             String token, Throwable t) {
        if (t instanceof OperacionException.Rechazada rechazada) {
            throw rechazada;
        }
        log.warn("FALLBACK accounts-service (operacion {}, cuenta {}): {}", operacionId, cuentaId, t.toString());
        throw new OperacionException.ServicioNoDisponible(
                "El servicio de cuentas no esta disponible. La operacion no se realizo; intente mas tarde.");
    }

    private String mensaje(HttpClientErrorException ex) {
        try {
            JsonNode body = mapper.readTree(ex.getResponseBodyAsString());
            if (body.hasNonNull("message")) {
                return body.get("message").asText();
            }
        } catch (Exception ignored) {
            // cuerpo no JSON
        }
        return "Operacion rechazada por el servicio de cuentas (" + ex.getStatusCode().value() + ")";
    }
}
