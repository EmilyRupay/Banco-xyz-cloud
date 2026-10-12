package cl.duoc.banco.bff.web;

import cl.duoc.banco.bff.web.Dtos.Dashboard;
import cl.duoc.banco.bff.web.Dtos.TransferenciaWeb;
import cl.duoc.banco.bff.web.Dtos.WebSummary;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * BFF WEB. Respuestas completas: perfil, todas las cuentas, historial completo, totales y advertencias.
 * El navegador consume una sola llamada en vez de 3 o 4 llamadas a microservicios distintos.
 */
@RestController
@RequestMapping("/web")
public class WebController {

    private final WebService service;
    private final PaymentsClient payments;

    public WebController(WebService service, PaymentsClient payments) {
        this.service = service;
        this.payments = payments;
    }

    @GetMapping("/accounts/{id}/summary")
    public WebSummary resumen(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.resumenCuenta(id, jwt.getTokenValue());
    }

    @GetMapping("/customers/{clienteId}/dashboard")
    public Dashboard dashboard(@PathVariable Long clienteId, @AuthenticationPrincipal Jwt jwt) {
        return service.dashboard(clienteId, jwt.getTokenValue());
    }

    @PostMapping("/transfers")
    public ResponseEntity<String> transferir(@Valid @RequestBody TransferenciaWeb req,
                                             @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                             @AuthenticationPrincipal Jwt jwt) {
        String llave = key == null || key.isBlank() ? UUID.randomUUID().toString() : key;
        return payments.transferir(req, llave, jwt.getTokenValue());
    }
}
