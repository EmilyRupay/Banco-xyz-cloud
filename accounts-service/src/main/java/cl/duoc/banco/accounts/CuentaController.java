package cl.duoc.banco.accounts;

import cl.duoc.banco.accounts.Dtos.ActualizacionRequest;
import cl.duoc.banco.accounts.Dtos.AperturaRequest;
import cl.duoc.banco.accounts.Dtos.EstadoRequest;
import cl.duoc.banco.accounts.Dtos.MovimientoSaldoRequest;
import cl.duoc.banco.accounts.Dtos.MovimientoSaldoResponse;
import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Microservicio de Gestion de Cuentas: apertura, cierre, mantenimiento y saldos. */
@RestController
@RequestMapping("/accounts")
public class CuentaController {

    private final CuentaRepository repository;
    private final CuentaService service;

    public CuentaController(CuentaRepository repository, CuentaService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping
    public List<Cuenta> listar() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Cuenta obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @GetMapping("/customer/{clienteId}")
    public List<Cuenta> porCliente(@PathVariable Long clienteId) {
        return repository.findByClienteIdOrderByIdAsc(clienteId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Cuenta abrir(@Valid @RequestBody AperturaRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.abrir(req, jwt.getTokenValue());
    }

    @PutMapping("/{id}")
    public Cuenta actualizar(@PathVariable Long id, @Valid @RequestBody ActualizacionRequest req) {
        return service.actualizarTipo(id, req.tipo());
    }

    @PatchMapping("/{id}/estado")
    public Cuenta estado(@PathVariable Long id, @Valid @RequestBody EstadoRequest req) {
        return service.cambiarEstado(id, req.estado(), req.motivo());
    }

    @DeleteMapping("/{id}")
    public Cuenta cerrar(@PathVariable Long id) {
        return service.cerrar(id);
    }

    /** Uso interno (payments-service): debito/credito atomico e idempotente. */
    @PostMapping("/{id}/movements")
    public MovimientoSaldoResponse movimiento(@PathVariable Long id, @Valid @RequestBody MovimientoSaldoRequest req) {
        return service.aplicarMovimiento(id, req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> conflicto(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "La cuenta fue modificada por otra operacion. Reintente."));
    }
}
