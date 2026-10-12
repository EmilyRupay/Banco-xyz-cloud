package cl.duoc.banco.payments;

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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Migra cuentas_anuales.csv a la base de datos aplicando reglas de calidad:
 *  - normaliza 4 formatos de fecha (yyyy-MM-dd, dd-MM-yyyy, yyyy/MM/dd, dd/MM/yyyy)
 *  - normaliza "depósito" -> "deposito"
 *  - descarta montos vacios, cero o negativos, y tipos de transaccion invalidos
 *  - descripcion vacia -> "Sin descripcion"
 *  - elimina duplicados exactos
 */
@Component
public class MovimientoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MovimientoDataLoader.class);
    private static final Set<String> TIPOS_VALIDOS = Set.of("deposito", "retiro", "compra", "pago");
    private static final List<DateTimeFormatter> FORMATOS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"));

    private final MovimientoRepository repository;

    public MovimientoDataLoader(MovimientoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (repository.count() > 0) {
            log.info("Movimientos ya cargados ({} registros). Se omite la migracion del CSV.", repository.count());
            return;
        }

        Set<String> vistos = new LinkedHashSet<>();
        List<Movimiento> validos = new java.util.ArrayList<>();
        int leidas = 0;
        int descartadas = 0;
        int duplicadas = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/cuentas_anuales.csv").getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // encabezado
            String linea;
            while ((linea = reader.readLine()) != null) {
                leidas++;
                Optional<Movimiento> mov = parsear(linea);
                if (mov.isEmpty()) {
                    descartadas++;
                    continue;
                }
                Movimiento m = mov.get();
                String clave = m.getCuentaId() + "|" + m.getFecha() + "|" + m.getTipo() + "|" + m.getMonto() + "|" + m.getDescripcion();
                if (vistos.add(clave)) {
                    validos.add(m);
                } else {
                    duplicadas++;
                }
            }
        }

        repository.saveAll(validos);
        log.info("Migracion cuentas_anuales.csv -> filas leidas={}, descartadas={}, duplicadas={}, movimientos guardados={}",
                leidas, descartadas, duplicadas, validos.size());
    }

    private Optional<Movimiento> parsear(String linea) {
        String[] c = linea.split(",", -1);
        if (c.length < 5) {
            return Optional.empty();
        }
        try {
            Long cuentaId = Long.parseLong(c[0].trim());
            LocalDate fecha = parsearFecha(c[1].trim());
            String tipo = c[2].trim().toLowerCase().replace("\u00f3", "o");
            String montoTxt = c[3].trim();
            String descripcion = c[4].trim().isEmpty() ? "Sin descripcion" : c[4].trim();

            if (fecha == null || montoTxt.isEmpty() || !TIPOS_VALIDOS.contains(tipo)) return Optional.empty();
            BigDecimal monto = new BigDecimal(montoTxt);
            if (monto.signum() <= 0) return Optional.empty();

            return Optional.of(new Movimiento(cuentaId, fecha, tipo, monto, descripcion));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private LocalDate parsearFecha(String texto) {
        for (DateTimeFormatter formato : FORMATOS) {
            try {
                return LocalDate.parse(texto, formato);
            } catch (DateTimeParseException ignored) {
                // se prueba el siguiente formato
            }
        }
        return null;
    }
}
