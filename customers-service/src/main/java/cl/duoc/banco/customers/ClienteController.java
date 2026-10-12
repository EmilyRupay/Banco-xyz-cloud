package cl.duoc.banco.customers;

import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Microservicio de Gestion de Clientes: informacion personal y perfiles. */
@RestController
@RequestMapping("/customers")
public class ClienteController {

    private final ClienteRepository repository;
    private final EventPublisher events;

    public ClienteController(ClienteRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @GetMapping
    public List<Cliente> listar() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Cliente obtener(@PathVariable Long id) {
        return buscar(id);
    }

    @GetMapping("/rut/{rut}")
    public Cliente porRut(@PathVariable String rut) {
        String normalizado = Rut.normalizar(rut);
        return repository.findByRut(normalizado == null ? rut : normalizado)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente " + rut + " no existe"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Cliente crear(@Valid @RequestBody ClienteRequest req) {
        String rut = Rut.normalizar(req.rut());
        if (rut == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RUT invalido: " + req.rut());
        }
        if (repository.existsByRut(rut)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un cliente con RUT " + rut);
        }
        Long id = repository.maxId() + 1;
        return repository.save(new Cliente(id, rut, req.nombre(), req.email(), req.telefono(), req.direccion(),
                req.segmento()));
    }

    @PutMapping("/{id}")
    @Transactional
    public Cliente actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest req) {
        Cliente c = buscar(id);
        boolean cambioContacto = !Objects.equals(c.getEmail(), req.email())
                || !Objects.equals(c.getTelefono(), req.telefono());
        c.actualizar(req.nombre(), req.email(), req.telefono(), req.direccion(), req.segmento());
        Cliente guardado = repository.save(c);
        if (cambioContacto) {
            events.cambioDatosContacto(guardado, "Se modifico email/telefono del cliente " + id);
        }
        return guardado;
    }

    /** Baja logica: el cliente se marca INACTIVO (sus datos se conservan por normativa). */
    @DeleteMapping("/{id}")
    @Transactional
    public Cliente desactivar(@PathVariable Long id) {
        Cliente c = buscar(id);
        c.desactivar();
        return repository.save(c);
    }

    private Cliente buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente " + id + " no existe"));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> conflicto(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "El cliente fue modificado por otra operacion. Reintente."));
    }
}
