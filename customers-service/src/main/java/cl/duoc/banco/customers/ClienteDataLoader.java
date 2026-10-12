package cl.duoc.banco.customers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Carga inicial de clientes: los titulares que aparecen en los archivos legacy. El id de cada cliente coincide con el
 * catalogo usado por accounts-service para asociar las cuentas migradas a su titular.
 */
@Component
public class ClienteDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClienteDataLoader.class);

    /** Mismo orden que CatalogoTitulares en accounts-service. */
    static final List<String> TITULARES = List.of("John Doe", "Jane Smith", "Bob Johnson", "Alice Brown",
            "Charlie Green", "Diana Prince", "Steve Rogers");

    private final ClienteRepository repository;

    public ClienteDataLoader(ClienteRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            log.info("Clientes ya cargados ({}).", repository.count());
            return;
        }
        for (int i = 0; i < TITULARES.size(); i++) {
            String nombre = TITULARES.get(i);
            long id = i + 1L;
            String usuario = nombre.toLowerCase().replace(' ', '.');
            Cliente.Segmento segmento = i % 3 == 0 ? Cliente.Segmento.PREMIUM : Cliente.Segmento.PERSONA;
            repository.save(new Cliente(id, Rut.formar(15_000_000 + i * 1_111_111), nombre,
                    usuario + "@correo-ejemplo.cl", "+56 9 1234 56" + (10 + i), "Santiago, Chile", segmento));
        }
        log.info("Migracion de titulares legacy -> {} clientes creados", TITULARES.size());
    }
}
