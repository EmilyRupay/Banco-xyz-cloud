package cl.duoc.banco.batch.jobs;

import cl.duoc.banco.batch.config.BankBatchProperties;
import cl.duoc.banco.batch.domain.MovimientoAnual;
import cl.duoc.banco.batch.domain.Procesado;
import cl.duoc.banco.batch.domain.RegistroCsv;
import cl.duoc.banco.batch.domain.ValidadorMovimientoAnual;
import cl.duoc.banco.batch.support.AutoRestartJobListener;
import cl.duoc.banco.batch.support.CsvSupport;
import cl.duoc.banco.batch.support.Escritores;
import cl.duoc.banco.batch.support.ExportSupport;
import cl.duoc.banco.batch.support.LimpiezaListener;
import cl.duoc.banco.batch.support.RechazoSkipListener;
import cl.duoc.banco.batch.support.ResumenEjecucionListener;
import cl.duoc.banco.batch.support.SimuladorFallos;
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
import org.springframework.batch.item.support.SynchronizedItemStreamReader;
import org.springframework.batch.item.support.builder.SynchronizedItemStreamReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.task.TaskExecutor;
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
 * JOB 3 - Generacion de Estados de Cuenta Anuales (insumo de auditorias).
 *
 * <pre>
 * cargarMovimientosAnualesStep (chunk MULTIHILO) CSV -> valida/corrige -> bt_movimiento_anual | rechazos
 * generarEstadosStep           (tasklet)        agrega por cuenta: depositos, retiros, compras, pagos, saldo neto
 * exportarEstadosStep          (chunk)          BD -> estados_cuenta_{anio}.csv
 * </pre>
 * El step multihilo no guarda la posicion de lectura, por eso antes de ejecutarse limpia lo que haya dejado una
 * ejecucion anterior (LimpiezaListener): un reinicio reprocesa el archivo completo sin duplicar filas.
 */
@Configuration
public class EstadosCuentaJobConfig {

    private static final String CLAVE = "anio";

    @Bean(name = JobNames.ESTADOS_ANUALES)
    public Job estadosCuentaAnualesJob(JobRepository jobRepository,
                                       @Qualifier("cargarMovimientosAnualesStep") Step cargar,
                                       @Qualifier("generarEstadosStep") Step generar,
                                       @Qualifier("exportarEstadosStep") Step exportar,
                                       AutoRestartJobListener autoRestart,
                                       ResumenEjecucionListener resumenListener) {
        return new JobBuilder(JobNames.ESTADOS_ANUALES, jobRepository)
                .validator(new DefaultJobParametersValidator(new String[]{CLAVE, "inputDir"}, new String[]{}))
                .listener(resumenListener)
                .listener(autoRestart)
                .start(cargar)
                .next(generar)
                .next(exportar)
                .build();
    }

    @Bean
    public Step cargarMovimientosAnualesStep(JobRepository jobRepository, PlatformTransactionManager tx,
                                             @Qualifier("lectorAnual") SynchronizedItemStreamReader<RegistroCsv> lector,
                                             @Qualifier("procesadorAnual") ProcesadorAnual procesador,
                                             @Qualifier("escritorAnual") ItemWriter<Procesado<MovimientoAnual>> escritor,
                                             @Qualifier("batchTaskExecutor") TaskExecutor taskExecutor,
                                             JdbcTemplate jdbc, BankBatchProperties props, BackOffPolicy backOff) {
        return new StepBuilder("cargarMovimientosAnualesStep", jobRepository)
                .<RegistroCsv, Procesado<MovimientoAnual>>chunk(props.chunkSize(), tx)
                .reader(lector)
                .processor(procesador)
                .writer(escritor)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retry(DataAccessResourceFailureException.class)
                .retryLimit(props.retryLimit())
                .backOffPolicy(backOff)
                .skip(FlatFileParseException.class)
                .skipLimit(props.skipLimit())
                .listener(new RechazoSkipListener(jdbc, ValidadorMovimientoAnual.JOB, CLAVE))
                .listener(new LimpiezaListener(jdbc, CLAVE,
                        "DELETE FROM bt_movimiento_anual WHERE anio = ?",
                        "DELETE FROM bt_registro_rechazado WHERE job = 'ANUAL' AND clave_proceso = CAST(? AS VARCHAR(20))"))
                .taskExecutor(taskExecutor) // varios chunks en paralelo
                .build();
    }

    @Bean
    @StepScope
    public SynchronizedItemStreamReader<RegistroCsv> lectorAnual(ResourceLoader resourceLoader,
                                                                 @Value("#{jobParameters['inputDir']}") String inputDir) {
        FlatFileItemReader<RegistroCsv> delegado = CsvSupport.reader("lectorAnual",
                resourceLoader.getResource(inputDir + "/estados_financieros_anuales.csv"), 5, false);
        return new SynchronizedItemStreamReaderBuilder<RegistroCsv>().delegate(delegado).build();
    }

    @Bean
    @StepScope
    public ProcesadorAnual procesadorAnual(SimuladorFallos simulador,
                                           @Value("#{jobParameters['anio']}") String anio,
                                           @Value("#{jobParameters['simularFalloTransitorioEnLinea']}") String transitoria,
                                           @Value("#{jobParameters['simularFalloCriticoEnLinea']}") String critica) {
        return new ProcesadorAnual(simulador, anio, transitoria, critica);
    }

    @Bean
    public ItemWriter<Procesado<MovimientoAnual>> escritorAnual(DataSource dataSource) throws Exception {
        JdbcBatchItemWriter<Procesado<MovimientoAnual>> validos = new JdbcBatchItemWriterBuilder<Procesado<MovimientoAnual>>()
                .dataSource(dataSource)
                .sql("INSERT INTO bt_movimiento_anual (anio, linea, cuenta_id, fecha, tipo, monto, descripcion) "
                        + "VALUES (:anio, :linea, :cuentaId, :fecha, :tipo, :monto, :descripcion)")
                .itemSqlParameterSourceProvider(p -> {
                    MovimientoAnual m = p.valor();
                    return new MapSqlParameterSource()
                            .addValue("anio", m.anio())
                            .addValue("linea", m.linea())
                            .addValue("cuentaId", m.cuentaId())
                            .addValue("fecha", m.fecha(), Types.DATE)
                            .addValue("tipo", m.tipo())
                            .addValue("monto", m.monto(), Types.NUMERIC)
                            .addValue("descripcion", m.descripcion());
                })
                .build();
        validos.afterPropertiesSet();
        return Escritores.clasificador(validos, Escritores.rechazos(dataSource));
    }

    @Bean
    public Step generarEstadosStep(JobRepository jobRepository, PlatformTransactionManager tx, JdbcTemplate jdbc) {
        return new StepBuilder("generarEstadosStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    int anio = Integer.parseInt((String) chunkContext.getStepContext().getJobParameters().get(CLAVE));
                    jdbc.update("DELETE FROM bt_estado_cuenta_anual WHERE anio = ?", anio);
                    int cuentas = jdbc.update("""
                            INSERT INTO bt_estado_cuenta_anual
                                (anio, cuenta_id, cantidad_movimientos, total_depositos, total_retiros, total_compras,
                                 total_pagos, saldo_neto, primer_movimiento, ultimo_movimiento)
                            SELECT anio, cuenta_id, COUNT(*),
                                   SUM(CASE WHEN tipo = 'deposito' THEN monto ELSE 0 END),
                                   SUM(CASE WHEN tipo = 'retiro'   THEN monto ELSE 0 END),
                                   SUM(CASE WHEN tipo = 'compra'   THEN monto ELSE 0 END),
                                   SUM(CASE WHEN tipo = 'pago'     THEN monto ELSE 0 END),
                                   SUM(CASE WHEN tipo = 'deposito' THEN monto ELSE -monto END),
                                   MIN(fecha), MAX(fecha)
                            FROM bt_movimiento_anual
                            WHERE anio = ?
                            GROUP BY anio, cuenta_id
                            """, anio);
                    contribution.incrementWriteCount(cuentas);
                    return RepeatStatus.FINISHED;
                }, tx)
                .build();
    }

    @Bean
    public Step exportarEstadosStep(JobRepository jobRepository, PlatformTransactionManager tx,
                                    @Qualifier("lectorEstados") JdbcCursorItemReader<Map<String, Object>> lector,
                                    @Qualifier("escritorEstados") FlatFileItemWriter<Map<String, Object>> escritor,
                                    BankBatchProperties props) {
        return new StepBuilder("exportarEstadosStep", jobRepository)
                .<Map<String, Object>, Map<String, Object>>chunk(props.chunkSize(), tx)
                .reader(lector)
                .writer(escritor)
                .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<Map<String, Object>> lectorEstados(DataSource dataSource,
                                                                  @Value("#{jobParameters['anio']}") String anio) {
        return ExportSupport.reader("lectorEstados", dataSource,
                "SELECT cuenta_id, cantidad_movimientos, total_depositos, total_retiros, total_compras, total_pagos, "
                        + "saldo_neto, primer_movimiento, ultimo_movimiento FROM bt_estado_cuenta_anual "
                        + "WHERE anio = ? ORDER BY cuenta_id", Integer.valueOf(anio));
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<Map<String, Object>> escritorEstados(BankBatchProperties props,
                                                                  @Value("#{jobParameters['anio']}") String anio) {
        return ExportSupport.writer("escritorEstados",
                Path.of(props.outputDir(), "estados_cuenta_" + anio + ".csv"),
                List.of("cuenta_id", "cantidad_movimientos", "total_depositos", "total_retiros", "total_compras",
                        "total_pagos", "saldo_neto", "primer_movimiento", "ultimo_movimiento"));
    }
}
