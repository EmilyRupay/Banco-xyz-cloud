package cl.duoc.banco.bff;

import cl.duoc.banco.bff.Dtos.MobileSummary;
import cl.duoc.banco.bff.Dtos.WebSummary;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backend for Frontend: estrategia "endpoints personalizados" + delegacion a microservicios.
 * Cada canal tiene su propia ruta y su propio formato de respuesta.
 */
@RestController
public class BffController {

    private final AccountSummaryService service;

    public BffController(AccountSummaryService service) {
        this.service = service;
    }

    @GetMapping("/web/accounts/{id}/summary")
    public WebSummary web(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.webSummary(id, jwt.getTokenValue());
    }

    @GetMapping("/mobile/accounts/{id}/summary")
    public MobileSummary mobile(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.mobileSummary(id, jwt.getTokenValue());
    }
}
