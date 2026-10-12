package cl.duoc.banco.batch.domain;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de normalizacion compartidas por los tres procesos. Equivalen a las rutinas de limpieza del legacy:
 * cuatro formatos de fecha aceptados, montos numericos y tipos sin tildes ni mayusculas.
 */
public final class ReglasLegacy {

    /** Formatos encontrados en los archivos legacy. STRICT rechaza fechas imposibles (ej. 31-02-2024). */
    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu/MM/dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));

    private ReglasLegacy() {
    }

    public static Optional<LocalDate> parsearFecha(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        for (DateTimeFormatter formato : FORMATOS_FECHA) {
            try {
                return Optional.of(LocalDate.parse(texto.trim(), formato));
            } catch (DateTimeParseException ignored) {
                // se intenta con el siguiente formato
            }
        }
        return Optional.empty();
    }

    /** "Depósito " -> "deposito" */
    public static String normalizarTipo(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase();
    }

    public static Optional<BigDecimal> parsearMonto(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(texto.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public static Optional<Long> parsearLong(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(texto.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
