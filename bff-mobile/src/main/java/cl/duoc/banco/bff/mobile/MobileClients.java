package cl.duoc.banco.bff.mobile;

import cl.duoc.banco.bff.mobile.Dtos.CuentaDto;
import cl.duoc.banco.bff.mobile.Dtos.MovimientoDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Clientes del canal movil. Timeouts mas cortos y un solo reintento: en movil es preferible responder rapido con
 * datos parciales que hacer esperar al usuario.
 */
@Component
public class MobileClients {

    private static final Logger log = LoggerFactory.getLogger(MobileClients.class);
    private final RestClient restClient;

    public MobileClients(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Retry(name = "accounts", fallbackMethod = "cuentaFallback")
    @CircuitBreaker(name = "accounts")
    public ClientResult<CuentaDto> cuenta(Long id, String token) {
        try {
            return ClientResult.ok(restClient.get().uri("http://accounts-service/accounts/{id}", id)
                    .headers(h -> h.setBearerAuth(token)).retrieve().body(CuentaDto.class));
        } catch (HttpClientErrorException.NotFound ex) {
            return ClientResult.ok(null);
        }
    }

    @Retry(name = "accounts", fallbackMethod = "cuentasFallback")
    @CircuitBreaker(name = "accounts")
    public ClientResult<List<CuentaDto>> cuentasCliente(Long clienteId, String token) {
        List<CuentaDto> lista = restClient.get().uri("http://accounts-service/accounts/customer/{id}", clienteId)
                .headers(h -> h.setBearerAuth(token)).retrieve()
                .body(new ParameterizedTypeReference<List<CuentaDto>>() {
                });
        return ClientResult.ok(lista == null ? List.of() : lista);
    }

    @Retry(name = "payments", fallbackMethod = "movimientosFallback")
    @CircuitBreaker(name = "payments")
    public ClientResult<List<MovimientoDto>> ultimosMovimientos(Long cuentaId, int limite, String token) {
        // limite en el servidor: el BFF pide SOLO lo que mostrara la app
        List<MovimientoDto> lista = restClient.get()
                .uri("http://payments-service/payments/account/{id}?limite={l}", cuentaId, limite)
                .headers(h -> h.setBearerAuth(token)).retrieve()
                .body(new ParameterizedTypeReference<List<MovimientoDto>>() {
                });
        return ClientResult.ok(lista == null ? List.of() : lista);
    }

    @SuppressWarnings("unused")
    private ClientResult<CuentaDto> cuentaFallback(Long id, String token, Throwable t) {
        log.warn("FALLBACK movil accounts (cuenta {}): {}", id, t.toString());
        return ClientResult.degraded(null);
    }

    @SuppressWarnings("unused")
    private ClientResult<List<CuentaDto>> cuentasFallback(Long clienteId, String token, Throwable t) {
        log.warn("FALLBACK movil accounts (cliente {}): {}", clienteId, t.toString());
        return ClientResult.degraded(List.of());
    }

    @SuppressWarnings("unused")
    private ClientResult<List<MovimientoDto>> movimientosFallback(Long cuentaId, int limite, String token,
                                                                  Throwable t) {
        log.warn("FALLBACK movil payments (cuenta {}): {}", cuentaId, t.toString());
        return ClientResult.degraded(List.of());
    }
}
