package cl.duoc.banco.batch.support;

import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.ColumnMapRowMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Exporta el resultado de una consulta a un archivo CSV (reportes entregables equivalentes a los del legacy). */
public final class ExportSupport {

    private ExportSupport() {
    }

    public static JdbcCursorItemReader<Map<String, Object>> reader(String nombre, DataSource dataSource, String sql,
                                                                   Object... argumentos) {
        return new JdbcCursorItemReaderBuilder<Map<String, Object>>()
                .name(nombre)
                .dataSource(dataSource)
                .sql(sql)
                .queryArguments(argumentos)
                .rowMapper(new ColumnMapRowMapper())
                .build();
    }

    public static FlatFileItemWriter<Map<String, Object>> writer(String nombre, Path archivo, List<String> columnas) {
        try {
            Files.createDirectories(archivo.toAbsolutePath().getParent());
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo crear la carpeta de salida " + archivo.getParent(), ex);
        }
        return new FlatFileItemWriterBuilder<Map<String, Object>>()
                .name(nombre)
                .resource(new FileSystemResource(archivo))
                .encoding("UTF-8")
                .shouldDeleteIfExists(true)
                .headerCallback(w -> w.write(String.join(",", columnas)))
                .lineAggregator(fila -> columnas.stream()
                        .map(c -> formatear(fila.get(c)))
                        .collect(Collectors.joining(",")))
                .build();
    }

    private static String formatear(Object valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor instanceof BigDecimal bd ? bd.toPlainString() : valor.toString();
        if (texto.contains(",") || texto.contains("\"")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
