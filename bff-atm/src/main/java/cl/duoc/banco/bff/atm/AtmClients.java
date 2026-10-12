package cl.duoc.banco.bff.atm;

import cl.duoc.banco.bff.atm.Dtos.CuentaDto;
import cl.duoc.banco.bff.atm.Dtos.OperacionDto;
import cl.duoc.banco.bff.atm.Dtos.PagoRequest;
import cl.duoc.banco.bff.atm.Dtos.RetiroRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Clientes del canal ATM. Regla de oro: ante cualquier duda NO se dispensa dinero.
 * Los fallbacks nunca devuelven datos "viejos": responden 503 y el cajero muestra "servicio no disponible".
 */
@Component
public class AtmClients {

    private static final Logger log = LoggerFactory.getLogger(AtmClients.class);
    private final RestClient restClient;
    private final ObjectMapper mapper;

    public AtmClients(RestClient.Builder builder, ObjectMapper mapper) {
        this.restClient = builder.build();
        this.mapper = mapper;
    }

    @Retry(name = "accounts", fallbackMethod = "cuentaFallback")
    @CircuitBreaker(name = "accounts")
    public CuentaDto cuenta(Long id, String token) {
        try {
            return restClient.get().uri("http://accounts-service/accounts/{id}", id)
                    .headers(h -> h.setBearerAuth(token)).retrieve().body(CuentaDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada");
        }
    }

    /**
     * Retiro: se reenvia la Idempotency-Key generada por el cajero. Si el cajero no recibe respuesta y reintenta con
     * la MISMA llave, payments-service devuelve el resultado original: nunca se debita dos veces.
     */
    @CircuitBreaker(name = "payments", fallbackMethod = "retiroFallback")
    public OperacionDto retirar(RetiroRequest req, String idempotencyKey, String terminal, String token) {
        return restClient.post()
                .uri("http://payments-service/payments/withdrawals")
                .headers(h -> {
                    h.setBearerAuth(token);
                    h.set("Idempotency-Key", idempotencyKey);
                    h.set("X-Channel", "ATM");
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PagoRequest(req.cuentaId(), req.monto(), "Retiro en cajero " + terminal))
                .exchange((request, response) -> {
                    if (response.getStatusCode().is5xxServerError()) {
                        throw new IllegalStateException("payments-service respondio " + response.getStatusCode());
                    }
                    String texto = response.bodyTo(String.class);
                    if (texto == null || texto.isBlank()) {
                        throw new ResponseStatusException(response.getStatusCode(), "Solicitud rechazada");
                    }
                    JsonNode cuerpo = mapper.readTree(texto);
                    if (response.getStatusCode().is4xxClientError() && !cuerpo.hasNonNull("estado")) {
                        // error de validacion/autorizacion sin operacion registrada
                        throw new ResponseStatusException(response.getStatusCode(),
                                cuerpo.path("error").asText("Solicitud rechazada"));
                    }
                    return mapper.treeToValue(cuerpo, OperacionDto.class);
                });
    }

    @SuppressWarnings("unused")
    private CuentaDto cuentaFallback(Long id, String token, Throwable t) {
        if (t instanceof ResponseStatusException rse) {
            throw rse;
        }
        log.warn("FALLBACK ATM saldo (cuenta {}): {}", id, t.toString());
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio temporalmente no disponible");
    }

    @SuppressWarnings("unused")
    private OperacionDto retiroFallback(RetiroRequest req, String key, String terminal, String token, Throwable t) {
        if (t instanceof ResponseStatusException rse) {
            throw rse;
        }
        log.error("FALLBACK ATM retiro {} (cuenta {}): {} -> NO SE DISPENSA DINERO", key, req.cuentaId(), t.toString());
        return new OperacionDto(key, "NO_PROCESADA", req.monto(), null,
                "Operacion no realizada. No se dispenso dinero. Intente mas tarde.", false);
    }
}
