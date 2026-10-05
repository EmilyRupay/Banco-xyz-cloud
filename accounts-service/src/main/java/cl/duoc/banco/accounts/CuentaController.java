package cl.duoc.banco.accounts;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/accounts")
public class CuentaController {

    private final CuentaRepository repository;

    public CuentaController(CuentaRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Cuenta> listar() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public Cuenta obtener(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta " + id + " no existe"));
    }
}
