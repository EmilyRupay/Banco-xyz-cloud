package cl.duoc.banco.transactions;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
public class MovimientoController {

    private static final Set<String> TIPOS_VALIDOS = Set.of("deposito", "retiro", "compra", "pago");

    private final MovimientoRepository repository;
    private final TransactionEventPublisher publisher;

    public MovimientoController(MovimientoRepository repository, TransactionEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @GetMapping("/account/{cuentaId}")
    public List<Movimiento> porCuenta(@PathVariable Long cuentaId) {
        return repository.findByCuentaIdOrderByFechaDescIdDesc(cuentaId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Movimiento crear(@Valid @RequestBody CreateTransactionRequest request) {
        String tipo = request.tipo().trim().toLowerCase();
        if (!TIPOS_VALIDOS.contains(tipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo invalido. Valores permitidos: " + TIPOS_VALIDOS);
        }

        Movimiento guardado = repository.save(new Movimiento(
                request.cuentaId(), LocalDate.now(), tipo, request.monto(), request.descripcion()));

        // Arquitectura orientada a eventos: se notifica de forma asincrona a quien le interese
        publisher.publish(new TransactionCreatedEvent(
                UUID.randomUUID().toString(), "TRANSACTION_CREATED", guardado.getId(), guardado.getCuentaId(),
                guardado.getTipo(), guardado.getMonto(), guardado.getFecha(), Instant.now()));

        return guardado;
    }
}
