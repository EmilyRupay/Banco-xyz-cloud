package cl.duoc.banco.batch.support;

import cl.duoc.banco.batch.domain.Procesado;
import cl.duoc.banco.batch.domain.RegistroRechazado;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.support.ClassifierCompositeItemWriter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import javax.sql.DataSource;
import java.sql.Types;

/** Fabrica de writers: los registros validos van a su tabla y los rechazados a bt_registro_rechazado. */
public final class Escritores {

    private Escritores() {
    }

    public static <T> JdbcBatchItemWriter<Procesado<T>> rechazos(DataSource dataSource) throws Exception {
        JdbcBatchItemWriter<Procesado<T>> writer = new JdbcBatchItemWriterBuilder<Procesado<T>>()
                .dataSource(dataSource)
                .sql(Sql.INSERT_RECHAZO)
                .itemSqlParameterSourceProvider(p -> parametros(p.rechazo()))
                .build();
        writer.afterPropertiesSet();
        return writer;
    }

    /** Enruta cada item al writer que corresponde (patron Classifier). */
    public static <T> ClassifierCompositeItemWriter<Procesado<T>> clasificador(ItemWriter<Procesado<T>> validos,
                                                                             ItemWriter<Procesado<T>> rechazos) {
        ClassifierCompositeItemWriter<Procesado<T>> writer = new ClassifierCompositeItemWriter<>();
        writer.setClassifier(p -> p.esValido() ? validos : rechazos);
        return writer;
    }

    private static MapSqlParameterSource parametros(RegistroRechazado r) {
        return new MapSqlParameterSource()
                .addValue("job", r.job())
                .addValue("claveProceso", r.claveProceso())
                .addValue("linea", r.linea())
                .addValue("motivo", r.motivo().name())
                .addValue("detalle", recortar(r.detalle(), 255), Types.VARCHAR)
                .addValue("contenido", recortar(r.contenido(), 500), Types.VARCHAR);
    }

    private static String recortar(String texto, int max) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
