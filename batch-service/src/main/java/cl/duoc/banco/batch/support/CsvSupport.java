package cl.duoc.banco.batch.support;

import cl.duoc.banco.batch.domain.LectorCsv;
import cl.duoc.banco.batch.domain.RegistroCsv;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.core.io.Resource;

import java.util.List;

/** Lectura de los CSV legacy. Una linea con un numero de columnas incorrecto lanza FlatFileParseException (skippable). */
public final class CsvSupport {

    private CsvSupport() {
    }

    public static LineMapper<RegistroCsv> lineMapper(int columnasEsperadas) {
        return (linea, numeroLinea) -> {
            List<String> campos = LectorCsv.separar(linea);
            if (campos.size() != columnasEsperadas) {
                throw new FlatFileParseException("Se esperaban " + columnasEsperadas + " columnas y llegaron "
                        + campos.size(), linea, numeroLinea);
            }
            return new RegistroCsv(numeroLinea, linea, campos);
        };
    }

    /**
     * @param saveState true guarda la posicion de lectura en el JobRepository (permite reanudar un job fallido
     *                  desde el ultimo commit); debe ser false en steps multihilo.
     */
    public static FlatFileItemReader<RegistroCsv> reader(String nombre, Resource recurso, int columnas,
                                                         boolean saveState) {
        return new FlatFileItemReaderBuilder<RegistroCsv>()
                .name(nombre)
                .resource(recurso)
                .encoding("UTF-8")
                .linesToSkip(1)
                .lineMapper(lineMapper(columnas))
                .saveState(saveState)
                .strict(true)
                .build();
    }
}
