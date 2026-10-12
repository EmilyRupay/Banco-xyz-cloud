package cl.duoc.banco.batch.jobs;

import cl.duoc.banco.batch.config.BankBatchProperties;
import cl.duoc.banco.batch.domain.Procesado;
import cl.duoc.banco.batch.domain.RegistroCsv;
import cl.duoc.banco.batch.domain.TransaccionDiaria;
import cl.duoc.banco.batch.domain.ValidadorMovimientoDiario;
import cl.duoc.banco.batch.support.AutoRestartJobListener;
import cl.duoc.banco.batch.support.CsvSupport;
import cl.duoc.banco.batch.support.Escritores;
import cl.duoc.banco.batch.support.ExportSupport;
import cl.duoc.banco.batch.support.RechazoSkipListener;
import cl.duoc.banco.batch.support.ResumenEjecucionListener;
import cl.duoc.banco.batch.support.SimuladorFallos;
import cl.duoc.banco.batch.support.Sql;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.DefaultJobParametersValidator;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Types;
import java.util.List;
import java.util.Map;

/**
 * JOB 1 - Reporte de Transacciones Diarias.
 *
 * <pre>
 * prepararDiarioStep        (tasklet)  borra resultados previos de la misma fechaProceso  -> idempotencia
 * validarTransaccionesStep  (chunk)    lee CSV -> valida/detecta anomalias -> BD (validas | rechazadas)
 * resumenDiarioStep         (tasklet)  agrega por fecha: cantidad, creditos, debitos, neto, alertas
 * exportarResumenDiarioStep (chunk)    BD -> reporte_diario_{fecha}.csv
 * exportarAnomaliasStep     (chunk)    BD -> anomalias_diarias_{fecha}.csv
 * </pre>
 */
@Configuration
public class ReporteDiarioJobConfig {

    private static final String CLAVE = "fechaProceso";

    @Bean(name = JobNames.REPORTE_DIARIO)
    public Job reporteTransaccionesDiariasJob(JobRepository jobRepository,
                                              @Qualifier("prepararDiarioStep") Step preparar,
                                              @Qualifier("validarTransaccionesStep") Step validar,
                                              @Qualifier("resumenDiarioStep") Step resumen,
                                              @Qualifier("exportarResumenDiarioStep") Step exportarResumen,
                                              @Qualifier("exportarAnomaliasStep") Step exportarAnomalias,
                                              AutoRestartJobListener autoRestart,
                                              ResumenEjecucionListener resumenListener) {
        return new JobBuilder(JobNames.REPORTE_DIARIO, jobRepository)
                .validator(new DefaultJobParametersValidator(new String[]{CLAVE, "inputDir"}, new String[]{}))
                .listener(resumenListener)
                .listener(autoRestart)
                .start(preparar)
                .next(validar)
                .next(resumen)
                .next(exportarResumen)
                .next(exportarAnomalias)
                .build();
    }

    @Bean
    public Step prepararDiarioStep(JobRepository jobRepository, PlatformTransactionManager tx, JdbcTemplate jdbc) {
        return new StepBuilder("prepararDiarioStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String fecha = (String) chunkContext.getStepContext().getJobParameters().get(CLAVE);
                    jdbc.update("DELETE FROM bt_transaccion_diaria WHERE fecha_proceso = ?", fecha);
                    jdbc.update("DELETE FROM bt_resumen_diario WHERE fecha_proceso = ?", fecha);
                    jdbc.update(Sql.DELETE_RECHAZOS, ValidadorMovimientoDiario.JOB, fecha);
                    return RepeatStatus.FINISHED;
                }, tx)
                .build();
    }

    @Bean
    public Step validarTransaccionesStep(JobRepository jobRepository, PlatformTransactionManager tx,
                                         @Qualifier("lectorDiario") FlatFileItemReader<RegistroCsv> lector,
                                         @Qualifier("procesadorDiario") ProcesadorDiario procesador,
                                         @Qualifier("escritorDiario") ItemWriter<Procesado<TransaccionDiaria>> escritor,
                                         JdbcTemplate jdbc, BankBatchProperties props, BackOffPolicy backOff) {
        return new StepBuilder("validarTransaccionesStep", jobRepository)
                .<RegistroCsv, Procesado<TransaccionDiaria>>chunk(props.chunkSize(), tx)
                .reader(lector)
                .processor(procesador)
                .writer(escritor)
                .faultTolerant()
                // Reintentos ante fallos TEMPORALES (ej. caida momentanea de la conexion a la BD)
                .retry(TransientDataAccessException.class)
                .retry(DataAccessResourceFailureException.class)
                .retryLimit(props.retryLimit())
                .backOffPolicy(backOff)
                // Omision de lineas corruptas (se registran en bt_registro_rechazado)
                .skip(FlatFileParseException.class)
                .skipLimit(props.skipLimit())
                .listener(new RechazoSkipListener(jdbc, ValidadorMovimientoDiario.JOB, CLAVE))
                .startLimit(5)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<RegistroCsv> lectorDiario(ResourceLoader resourceLoader,
                                                        @Value("#{jobParameters['inputDir']}") String inputDir) {
        return CsvSupport.reader("lectorDiario",
                resourceLoader.getResource(inputDir + "/movimientos_financieros_diarios.csv"), 4, true);
    }

    @Bean
    @StepScope
    public ProcesadorDiario procesadorDiario(JdbcTemplate jdbc, SimuladorFallos simulador, BankBatchProperties props,
                                             @Value("#{jobParameters['fechaProceso']}") String fechaProceso,
                                             @Value("#{jobParameters['simularFalloTransitorioEnLinea']}") String transitoria,
                                             @Value("#{jobParameters['simularFalloCriticoEnLinea']}") String critica) {
        return new ProcesadorDiario(new ValidadorMovimientoDiario(props.umbralMontoAlto()), jdbc, simulador,
                fechaProceso, transitoria, critica);
    }

    @Bean
    public ItemWriter<Procesado<TransaccionDiaria>> escritorDiario(DataSource dataSource) throws Exception {
        JdbcBatchItemWriter<Procesado<TransaccionDiaria>> validas =
                new JdbcBatchItemWriterBuilder<Procesado<TransaccionDiaria>>()
                        .dataSource(dataSource)
                        .sql("INSERT INTO bt_transaccion_diaria "
                                + "(fecha_proceso, linea, id_origen, fecha, tipo, monto, alerta, alerta_detalle) "
                                + "VALUES (:fechaProceso, :linea, :idOrigen, :fecha, :tipo, :monto, :alerta, :alertaDetalle)")
                        .itemSqlParameterSourceProvider(p -> {
                            TransaccionDiaria t = p.valor();
                            return new MapSqlParameterSource()
                                    .addValue("fechaProceso", t.fechaProceso())
                                    .addValue("linea", t.linea())
                                    .addValue("idOrigen", t.idOrigen())
                                    .addValue("fecha", t.fecha(), Types.DATE)
                                    .addValue("tipo", t.tipo())
                                    .addValue("monto", t.monto(), Types.NUMERIC)
                                    .addValue("alerta", t.alerta() == null ? null : t.alerta().name(), Types.VARCHAR)
                                    .addValue("alertaDetalle", t.alertaDetalle(), Types.VARCHAR);
                        })
                        .build();
        validas.afterPropertiesSet();
        return Escritores.clasificador(validas, Escritores.rechazos(dataSource));
    }

    @Bean
    public Step resumenDiarioStep(JobRepository jobRepository, PlatformTransactionManager tx, JdbcTemplate jdbc) {
        return new StepBuilder("resumenDiarioStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String fecha = (String) chunkContext.getStepContext().getJobParameters().get(CLAVE);
                    jdbc.update("DELETE FROM bt_resumen_diario WHERE fecha_proceso = ?", fecha);
                    int dias = jdbc.update("""
                            INSERT INTO bt_resumen_diario
                                (fecha_proceso, fecha, cantidad, total_creditos, total_debitos, neto, alertas)
                            SELECT fecha_proceso, fecha, COUNT(*),
                                   SUM(CASE WHEN tipo = 'credito' THEN monto ELSE 0 END),
                                   SUM(CASE WHEN tipo = 'debito'  THEN monto ELSE 0 END),
                                   SUM(CASE WHEN tipo = 'credito' THEN monto ELSE -monto END),
                                   SUM(CASE WHEN alerta IS NOT NULL THEN 1 ELSE 0 END)
                            FROM bt_transaccion_diaria
                            WHERE fecha_proceso = ?
                            GROUP BY fecha_proceso, fecha
                            """, fecha);
                    contribution.incrementWriteCount(dias);
                    return RepeatStatus.FINISHED;
                }, tx)
                .build();
    }

    @Bean
    public Step exportarResumenDiarioStep(JobRepository jobRepository, PlatformTransactionManager tx,
                                          @Qualifier("lectorResumenDiario") JdbcCursorItemReader<Map<String, Object>> lector,
                                          @Qualifier("escritorResumenDiario") FlatFileItemWriter<Map<String, Object>> escritor,
                                          BankBatchProperties props) {
        return new StepBuilder("exportarResumenDiarioStep", jobRepository)
                .<Map<String, Object>, Map<String, Object>>chunk(props.chunkSize(), tx)
                .reader(lector)
                .writer(escritor)
                .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<Map<String, Object>> lectorResumenDiario(DataSource dataSource,
                                                                        @Value("#{jobParameters['fechaProceso']}") String fecha) {
        return ExportSupport.reader("lectorResumenDiario", dataSource,
                "SELECT fecha, cantidad, total_creditos, total_debitos, neto, alertas FROM bt_resumen_diario "
                        + "WHERE fecha_proceso = ? ORDER BY fecha", fecha);
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<Map<String, Object>> escritorResumenDiario(BankBatchProperties props,
                                                                        @Value("#{jobParameters['fechaProceso']}") String fecha) {
        return ExportSupport.writer("escritorResumenDiario",
                Path.of(props.outputDir(), "reporte_diario_" + fecha + ".csv"),
                List.of("fecha", "cantidad", "total_creditos", "total_debitos", "neto", "alertas"));
    }

    @Bean
    public Step exportarAnomaliasStep(JobRepository jobRepository, PlatformTransactionManager tx,
                                      @Qualifier("lectorAnomalias") JdbcCursorItemReader<Map<String, Object>> lector,
                                      @Qualifier("escritorAnomalias") FlatFileItemWriter<Map<String, Object>> escritor,
                                      BankBatchProperties props) {
        return new StepBuilder("exportarAnomaliasStep", jobRepository)
                .<Map<String, Object>, Map<String, Object>>chunk(props.chunkSize(), tx)
                .reader(lector)
                .writer(escritor)
                .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<Map<String, Object>> lectorAnomalias(DataSource dataSource,
                                                                    @Value("#{jobParameters['fechaProceso']}") String fecha) {
        return ExportSupport.reader("lectorAnomalias", dataSource, """
                SELECT linea, clasificacion, motivo, detalle FROM (
                    SELECT linea, 'RECHAZADA' AS clasificacion, motivo, detalle
                    FROM bt_registro_rechazado WHERE job = 'DIARIO' AND clave_proceso = ?
                    UNION ALL
                    SELECT linea, 'ALERTA' AS clasificacion, alerta AS motivo, alerta_detalle AS detalle
                    FROM bt_transaccion_diaria WHERE fecha_proceso = ? AND alerta IS NOT NULL
                ) anomalias
                ORDER BY linea
                """, fecha, fecha);
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<Map<String, Object>> escritorAnomalias(BankBatchProperties props,
                                                                    @Value("#{jobParameters['fechaProceso']}") String fecha) {
        return ExportSupport.writer("escritorAnomalias",
                Path.of(props.outputDir(), "anomalias_diarias_" + fecha + ".csv"),
                List.of("linea", "clasificacion", "motivo", "detalle"));
    }
}
