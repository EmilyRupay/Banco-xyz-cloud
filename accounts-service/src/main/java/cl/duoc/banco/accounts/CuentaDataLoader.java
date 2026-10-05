package cl.duoc.banco.accounts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Migra los datos legacy (intereses.csv) a la base de datos aplicando reglas de calidad:
 *  - descarta saldo vacio o <= 0
 *  - descarta edades vacias o fuera de rango (18-100)
 *  - descarta tipos de cuenta invalidos (-1, unknown)
 *  - descarta nombres "Unknown"
 *  - elimina duplicados: conserva el primer registro valido de cada cuenta_id
 */
@Component
public class CuentaDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CuentaDataLoader.class);
    private static final Set<String> TIPOS_VALIDOS = Set.of("prestamo", "hipoteca", "ahorro");

    private final CuentaRepository repository;

    public CuentaDataLoader(CuentaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (repository.count() > 0) {
            log.info("Cuentas ya cargadas ({} registros). Se omite la migracion del CSV.", repository.count());
            return;
        }

        Map<Long, Cuenta> unicas = new LinkedHashMap<>();
        int leidas = 0;
        int descartadas = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/intereses.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // encabezado
            String linea;
            while ((linea = reader.readLine()) != null) {
                leidas++;
                Optional<Cuenta> cuenta = parsear(linea);
                if (cuenta.isEmpty()) {
                    descartadas++;
                } else {
                    unicas.putIfAbsent(cuenta.get().getId(), cuenta.get());
                }
            }
        }

        repository.saveAll(unicas.values());
        log.info("Migracion intereses.csv -> filas leidas={}, descartadas={}, duplicadas={}, cuentas guardadas={}",
                leidas, descartadas, leidas - descartadas - unicas.size(), unicas.size());
    }

    private Optional<Cuenta> parsear(String linea) {
        String[] c = linea.split(",", -1);
        if (c.length < 5) {
            return Optional.empty();
        }
        try {
            Long id = Long.parseLong(c[0].trim());
            String nombre = c[1].trim();
            String saldoTxt = c[2].trim();
            String edadTxt = c[3].trim();
            String tipo = c[4].trim().toLowerCase();

            if (nombre.isEmpty() || nombre.equalsIgnoreCase("unknown")) return Optional.empty();
            if (saldoTxt.isEmpty() || edadTxt.isEmpty()) return Optional.empty();
            if (!TIPOS_VALIDOS.contains(tipo)) return Optional.empty();

            BigDecimal saldo = new BigDecimal(saldoTxt);
            int edad = Integer.parseInt(edadTxt);
            if (saldo.signum() <= 0 || edad < 18 || edad > 100) return Optional.empty();

            return Optional.of(new Cuenta(id, nombre, saldo, edad, tipo));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
