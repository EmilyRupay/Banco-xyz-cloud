package cl.duoc.banco.payments;

import cl.duoc.banco.payments.Dtos.OperacionRequest;
import cl.duoc.banco.payments.Dtos.OperacionResponse;
import cl.duoc.banco.payments.Dtos.TransferenciaRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Microservicio de Procesamiento de Pagos: depositos, retiros, pagos y transferencias.
 * Headers opcionales: Idempotency-Key (evita cobros duplicados) y X-Channel (WEB, MOBILE, ATM).
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Set<String> CANALES = Set.of("WEB", "MOBILE", "ATM", "API");

    private final PaymentService service;
    private final MovimientoRepository movimientos;
    private final OperacionRepository operaciones;

    public PaymentController(PaymentService service, MovimientoRepository movimientos,
                             OperacionRepository operaciones) {
        this.service = service;
        this.movimientos = movimientos;
        this.operaciones = operaciones;
    }

    @GetMapping("/account/{cuentaId}")
    public List<Movimiento> movimientos(@PathVariable Long cuentaId,
                                        @RequestParam(required = false) Integer limite) {
        return limite == null
                ? movimientos.findByCuentaIdOrderByFechaDescIdDesc(cuentaId)
                : movimientos.findByCuentaIdOrderByFechaDescIdDesc(cuentaId, PageRequest.of(0, limite));
    }

    @GetMapping("/{operacionId}")
    public OperacionResponse operacion(@PathVariable String operacionId) {
        return operaciones.findById(operacionId).map(o -> OperacionResponse.de(o, false))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Operacion no existe"));
    }

    @PostMapping("/deposits")
    public ResponseEntity<OperacionResponse> depositar(@Valid @RequestBody OperacionRequest req,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                       @RequestHeader(value = "X-Channel", required = false) String canal,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return creada(service.ejecutar(Operacion.Tipo.DEPOSITO, req.cuentaId(), null, req.monto(), req.descripcion(),
                canal(canal), key, jwt.getTokenValue()));
    }

    @PostMapping("/withdrawals")
    public ResponseEntity<OperacionResponse> retirar(@Valid @RequestBody OperacionRequest req,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                     @RequestHeader(value = "X-Channel", required = false) String canal,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return creada(service.ejecutar(Operacion.Tipo.RETIRO, req.cuentaId(), null, req.monto(), req.descripcion(),
                canal(canal), key, jwt.getTokenValue()));
    }

    @PostMapping("/bills")
    public ResponseEntity<OperacionResponse> pagar(@Valid @RequestBody OperacionRequest req,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                   @RequestHeader(value = "X-Channel", required = false) String canal,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return creada(service.ejecutar(Operacion.Tipo.PAGO, req.cuentaId(), null, req.monto(), req.descripcion(),
                canal(canal), key, jwt.getTokenValue()));
    }

    @PostMapping("/transfers")
    public ResponseEntity<OperacionResponse> transferir(@Valid @RequestBody TransferenciaRequest req,
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                        @RequestHeader(value = "X-Channel", required = false) String canal,
                                                        @AuthenticationPrincipal Jwt jwt) {
        return creada(service.ejecutar(Operacion.Tipo.TRANSFERENCIA, req.cuentaOrigen(), req.cuentaDestino(),
                req.monto(), req.descripcion(), canal(canal), key, jwt.getTokenValue()));
    }

    private static String canal(String canal) {
        String c = canal == null ? "API" : canal.trim().toUpperCase();
        return CANALES.contains(c) ? c : "API";
    }

    private static ResponseEntity<OperacionResponse> creada(OperacionResponse r) {
        return ResponseEntity.status(r.repetida() ? HttpStatus.OK : HttpStatus.CREATED).body(r);
    }

    @ExceptionHandler(OperacionException.class)
    public ResponseEntity<Object> error(OperacionException ex) {
        Object cuerpo = ex.getRespuesta() != null ? ex.getRespuesta() : Map.of("error", ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(cuerpo);
    }
}
