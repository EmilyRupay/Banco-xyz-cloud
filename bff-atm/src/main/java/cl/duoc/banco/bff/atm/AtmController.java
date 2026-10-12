package cl.duoc.banco.bff.atm;

import cl.duoc.banco.bff.atm.Dtos.CuentaDto;
import cl.duoc.banco.bff.atm.Dtos.OperacionDto;
import cl.duoc.banco.bff.atm.Dtos.RetiroAtm;
import cl.duoc.banco.bff.atm.Dtos.RetiroRequest;
import cl.duoc.banco.bff.atm.Dtos.SaldoAtm;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;

/** BFF CAJEROS: consulta de saldo y retiro. Respuestas minimas, sin nombre ni datos personales del cliente. */
@RestController
@RequestMapping("/atm")
public class AtmController {

    private final AtmClients clients;
    private final AtmProperties props;

    public AtmController(AtmClients clients, AtmProperties props) {
        this.clients = clients;
        this.props = props;
    }

    @GetMapping("/accounts/{id}/balance")
    public SaldoAtm saldo(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        CuentaDto c = clients.cuenta(id, jwt.getTokenValue());
        if (!"ACTIVA".equals(c.estado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cuenta no operativa");
        }
        return new SaldoAtm(Dtos.enmascarar(c.id()), c.saldo(), "CLP", Instant.now());
    }

    @PostMapping("/withdrawals")
    public ResponseEntity<RetiroAtm> retirar(@Valid @RequestBody RetiroRequest req,
                                             @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                             @RequestHeader(AtmTerminalFilter.HEADER) String terminal,
                                             @AuthenticationPrincipal Jwt jwt) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El header Idempotency-Key es obligatorio en el canal ATM");
        }
        validarMonto(req.monto());

        OperacionDto op = clients.retirar(req, key, terminal, jwt.getTokenValue());
        boolean dispensar = "COMPLETADA".equals(op.estado());
        RetiroAtm body = new RetiroAtm(op.operacionId(), dispensar, req.monto(), op.saldoResultante(),
                dispensar ? "Retire su dinero" : op.mensaje());
        HttpStatus status = dispensar ? HttpStatus.OK
                : "NO_PROCESADA".equals(op.estado()) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(body);
    }

    private void validarMonto(BigDecimal monto) {
        if (monto.compareTo(props.montoMinimo()) < 0 || monto.compareTo(props.montoMaximo()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto debe estar entre $"
                    + props.montoMinimo().toPlainString() + " y $" + props.montoMaximo().toPlainString());
        }
        if (monto.remainder(props.multiplo()).signum() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El monto debe ser multiplo de $" + props.multiplo().toPlainString());
        }
    }
}
