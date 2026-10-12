package cl.duoc.banco.batch.jobs;

import cl.duoc.banco.batch.config.BankBatchProperties;
import cl.duoc.banco.batch.domain.CalculadoraIntereses;
import cl.duoc.banco.batch.domain.CuentaInteres;
import cl.duoc.banco.batch.domain.InteresMensual;
import cl.duoc.banco.batch.domain.Procesado;
import cl.duoc.banco.batch.domain.RegistroCsv;
import cl.duoc.banco.batch.domain.ValidadorCuenta;
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
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.FlatFileParseException;
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
import java.util.List;
import java.util.Map;

/**
 * JOB 2 - Calculo de Intereses Mensuales.
 *
 * <pre>
 * prepararInteresesStep  (tasklet)      limpia resultados previos del periodo
 * cargarCuentasStep      (chunk)        CSV -> valida, elimina duplicados -> bt_cuenta_staging | rechazos
 * calcularInteresesStep  (PARTICIONADO) N particiones por rango de cuenta_id, en paralelo -> bt_interes_mensual
 * exportarInteresesStep  (chunk)        BD -> intereses_mensuales_{periodo}.csv
 * </pre>
 */
@Configuration
public class InteresesMensualesJobConfig {

    private static final String CLAVE = "periodo";

    @Bean(name = JobNames.INTERESES_MENSUALES)
    public Job calculoInteresesMensualesJob(JobRepository jobRepository,
                                            @Qualifier("prepararInteresesStep") Step preparar,
                                            @Qualifier("cargarCuentasStep") Step cargar,
                                            @Qualifier("calcularInteresesStep") Step calcular,
                                            @Qualifier("exportarInteresesStep") Step exportar,
                                            AutoRestartJobListener autoRestart,
                                            ResumenEjecucionListener resumenListener) {
        return new JobBuilder(JobNames.INTERESES_MENSUALES, jobRepository)
                .validator(new DefaultJobParametersValidator(new String[]{CLAVE, "inputDir"}, new String[]{}))
                .listener(resumenListener)
                .listener(autoRestart)
                .start(preparar)
                .next(cargar)
                .next(calcular)
                .next(exportar)
                .build();
    }

    @Bean
    public Step prepararInteresesStep(JobRepository jobRepository, PlatformTransactionManager tx, JdbcTemplate jdbc) {
        return new StepBuilder("prepararInteresesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String periodo = (String) chunkContext.getStepContext().getJobParameters().get(CLAVE);
                    jdbc.update("DELETE FROM bt_interes_mensual WHERE periodo = ?", periodo);
                    jdbc.update("DELETE FROM bt_cuenta_staging WHERE periodo = ?", periodo);
                    jdbc.update(Sql.DELETE_RECHAZOS, ValidadorCuenta.JOB, periodo);
                    return RepeatStatus.FINISHED;
                }, tx)
                .build();
    }

    // ------------------------------------------------------------------ carga y validacion
    @Bean
    public Step cargarCuentasStep(JobRepository jobRepository, PlatformTransactionManager tx,
                                  @Qualifier("lectorCuentas") FlatFileItemReader<RegistroCsv> lector,
                                  @Qualifier("procesadorCuentas") ProcesadorCuentas procesador,
                                  @Qualifier("escritorCuentas") ItemWriter<Procesado<CuentaInteres>> escritor,
                                  JdbcTemplate jdbc, BankBatchProperties props, BackOffPolicy backOff) {
        return new StepBuilder("cargarCuentasStep", jobRepository)
                .<RegistroCsv, Procesado<CuentaInteres>>chunk(props.chunkSize(), tx)
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
                .listener(new RechazoSkipListener(jdbc, ValidadorCuenta.JOB, CLAVE))
                .startLimit(5)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<RegistroCsv> lectorCuentas(ResourceLoader resourceLoader,
                                                         @Value("#{jobParameters['inputDir']}") String inputDir) {
        return CsvSupport.reader("lectorCuentas",
                resourceLoader.getResource(inputDir + "/intereses_trimestrales.csv"), 5, true);
    }

    @Bean
    @StepScope
    public ProcesadorCuentas procesadorCuentas(JdbcTemplate jdbc, SimuladorFallos simulador,
                                               @Value("#{jobParameters['periodo']}") String periodo,
                                               @Value("#{jobParameters['simularFalloTransitorioEnLinea']}") String transitoria,
                                               @Value("#{jobParameters['simularFalloCriticoEnLinea']}") String critica) {
        return new ProcesadorCuentas(jdbc, simulador, periodo, transitoria, critica);
    }

    @Bean
    public ItemWriter<Procesado<CuentaInteres>> escritorCuentas(DataSource dataSource) throws Exception {
        JdbcBatchItemWriter<Procesado<CuentaInteres>> validas = new JdbcBatchItemWriterBuilder<Procesado<CuentaInteres>>()
                .dataSource(dataSource)
                .sql("INSERT INTO bt_cuenta_staging (periodo, linea, cuenta_id, nombre, saldo, edad, tipo) "
                        + "VALUES (:periodo, :linea, :cuentaId, :nombre, :saldo, :edad, :tipo)")
                .itemSqlParameterSourceProvider(p -> {
                    CuentaInteres c = p.valor();
                    return new MapSqlParameterSource()
                            .addValue("periodo", c.periodo())
                            .addValue("linea", c.linea())
                            .addValue("cuentaId", c.cuentaId())
                            .addValue("nombre", c.nombre())
                            .addValue("saldo", c.saldo())
                            .addValue("edad", c.edad())
                            .addValue("tipo", c.tipo());
                })
                .build();
        validas.afterPropertiesSet();
        return Escritores.clasificador(validas, Escritores.rechazos(dataSource));
    }

    // ------------------------------------------------------------------ calculo particionado
    @Bean
    public Step calcularInteresesStep(JobRepository jobRepository,
                                      @Qualifier("particionadorCuentas") RangoCuentasPartitioner particionador,
                                      @Qualifier("calcularInteresesWorkerStep") Step worker,
                                      @Qualifier("batchTaskExecutor") TaskExecutor taskExecutor,
                                      BankBatchProperties props) {
        return new StepBuilder("calcularInteresesStep", jobRepository)
                .partitioner("calcularInteresesWorkerStep", particionador)
                .step(worker)
                .gridSize(props.gridSize())
                .taskExecutor(taskExecutor)
                .build();
    }

    @Bean
    @StepScope
    public RangoCuentasPartitioner particionadorCuentas(JdbcTemplate jdbc,
                                                        @Value("#{jobParameters['periodo']}") String periodo) {
        return new RangoCuentasPartitioner(jdbc, periodo);
    }

    @Bean
    public Step calcularInteresesWorkerStep(JobRepository jobRepository, PlatformTransactionManager tx,
                                            @Qualifier("lectorStaging") JdbcPagingItemReader<CuentaInteres> lector,
                                            @Qualifier("escritorIntereses") JdbcBatchItemWriter<InteresMensual> escritor,
                                            BankBatchProperties props, BackOffPolicy backOff) {
        CalculadoraIntereses calculadora = new CalculadoraIntereses(props.tasasAnualesEfectivas());
        ItemProcessor<CuentaInteres, InteresMensual> procesador = calculadora::calcular;
        return new StepBuilder("calcularInteresesWorkerStep", jobRepository)
                .<CuentaInteres, InteresMensual>chunk(props.chunkSize(), tx)
                .reader(lector)
                .processor(procesador)
                .writer(escritor)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retry(DataAccessResourceFailureException.class)
                .retryLimit(props.retryLimit())
                .backOffPolicy(backOff)
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<CuentaInteres> lectorStaging(DataSource dataSource, BankBatchProperties props,
                                                             @Value("#{jobParameters['periodo']}") String periodo,
                                                             @Value("#{stepExecutionContext['minId']}") Long minId,
                                                             @Value("#{stepExecutionContext['maxId']}") Long maxId) {
        Map<String, Object> parametros = Map.of("periodo", periodo, "minId", minId, "maxId", maxId);
        return new JdbcPagingItemReaderBuilder<CuentaInteres>()
                .name("lectorStaging")
                .dataSource(dataSource)
                .selectClause("SELECT periodo, linea, cuenta_id, nombre, saldo, edad, tipo")
                .fromClause("FROM bt_cuenta_staging")
                .whereClause("WHERE periodo = :periodo AND cuenta_id BETWEEN :minId AND :maxId")
                .parameterValues(parametros)
                .sortKeys(Map.of("cuenta_id", Order.ASCENDING))
                .rowMapper((rs, i) -> new CuentaInteres(rs.getString("periodo"), rs.getInt("linea"),
                        rs.getLong("cuenta_id"), rs.getString("nombre"), rs.getBigDecimal("saldo"),
                        rs.getInt("edad"), rs.getString("tipo")))
                .pageSize(props.chunkSize())
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<InteresMensual> escritorIntereses(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<InteresMensual>()
                .dataSource(dataSource)
                .sql("INSERT INTO bt_interes_mensual (periodo, cuenta_id, nombre, tipo, saldo_inicial, tasa_mensual, "
                        + "interes, saldo_final, naturaleza) VALUES (:periodo, :cuentaId, :nombre, :tipo, "
                        + ":saldoInicial, :tasaMensual, :interes, :saldoFinal, :naturaleza)")
                .itemSqlParameterSourceProvider(i -> new MapSqlParameterSource()
                        .addValue("periodo", i.periodo())
                        .addValue("cuentaId", i.cuentaId())
                        .addValue("nombre", i.nombre())
                        .addValue("tipo", i.tipo())
                        .addValue("saldoInicial", i.saldoInicial())
                        .addValue("tasaMensual", i.tasaMensual())
                        .addValue("interes", i.interes())
                        .addValue("saldoFinal", i.saldoFinal())
                        .addValue("naturaleza", i.naturaleza()))
                .build();
    }

    // ------------------------------------------------------------------ exportacion
    @Bean
    public Step exportarInteresesStep(JobRepository jobRepository, PlatformTransactionManager tx,
                                      @Qualifier("lectorIntereses") JdbcCursorItemReader<Map<String, Object>> lector,
                                      @Qualifier("escritorInteresesCsv") FlatFileItemWriter<Map<String, Object>> escritor,
                                      BankBatchProperties props) {
        return new StepBuilder("exportarInteresesStep", jobRepository)
                .<Map<String, Object>, Map<String, Object>>chunk(props.chunkSize(), tx)
                .reader(lector)
                .writer(escritor)
                .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<Map<String, Object>> lectorIntereses(DataSource dataSource,
                                                                    @Value("#{jobParameters['periodo']}") String periodo) {
        return ExportSupport.reader("lectorIntereses", dataSource,
                "SELECT cuenta_id, nombre, tipo, saldo_inicial, tasa_mensual, interes, saldo_final, naturaleza "
                        + "FROM bt_interes_mensual WHERE periodo = ? ORDER BY cuenta_id", periodo);
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<Map<String, Object>> escritorInteresesCsv(BankBatchProperties props,
                                                                       @Value("#{jobParameters['periodo']}") String periodo) {
        return ExportSupport.writer("escritorInteresesCsv",
                Path.of(props.outputDir(), "intereses_mensuales_" + periodo + ".csv"),
                List.of("cuenta_id", "nombre", "tipo", "saldo_inicial", "tasa_mensual", "interes", "saldo_final",
                        "naturaleza"));
    }
}
