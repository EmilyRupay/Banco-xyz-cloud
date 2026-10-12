package cl.duoc.banco.batch;

import cl.duoc.banco.batch.jobs.JobNames;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ejecuta los 3 jobs sobre los datos legacy reales (fin_legacy_data/semana_3) y verifica que los resultados sean
 * IDENTICOS a los del proceso legacy. Los valores esperados se obtuvieron ejecutando los scripts de
 * batch-service/legacy-reference/*.sh sobre los mismos archivos.
 */
@SpringBootTest
class EquivalenciaLegacyIntegrationTest {

    private static final String INPUT = "classpath:legacy-data/semana_3";

    @Autowired private JobLauncher jobLauncher;
    @Autowired private JdbcTemplate jdbc;
    @Autowired @Qualifier(JobNames.REPORTE_DIARIO) private Job reporteDiario;
    @Autowired @Qualifier(JobNames.INTERESES_MENSUALES) private Job intereses;
    @Autowired @Qualifier(JobNames.ESTADOS_ANUALES) private Job estados;

    private static JobParametersBuilder base() {
        return new JobParametersBuilder()
                .addString("inputDir", INPUT, false)
                .addLong("ejecucion", System.nanoTime());
    }

    private BigDecimal suma(String sql, Object... args) {
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, args);
        return v == null ? BigDecimal.ZERO : v;
    }

    private int contar(String sql, Object... args) {
        Integer v = jdbc.queryForObject(sql, Integer.class, args);
        return v == null ? 0 : v;
    }

    // ----------------------------------------------------------------------------------------------- JOB 1
    @Test
    void reporteDiarioEsEquivalenteAlLegacy() throws Exception {
        String fecha = "2024-12-31";
        JobExecution e = jobLauncher.run(reporteDiario, base().addString("fechaProceso", fecha).toJobParameters());

        assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verificarReporteDiario(fecha);
        assertThat(Files.exists(Path.of("target/batch-output-test/reporte_diario_" + fecha + ".csv"))).isTrue();
        assertThat(Files.exists(Path.of("target/batch-output-test/anomalias_diarias_" + fecha + ".csv"))).isTrue();
    }

    private void verificarReporteDiario(String fecha) {
        assertThat(contar("SELECT COUNT(*) FROM bt_transaccion_diaria WHERE fecha_proceso = ?", fecha)).isEqualTo(392);
        assertThat(contar("SELECT COUNT(*) FROM bt_transaccion_diaria WHERE fecha_proceso = ? AND alerta IS NOT NULL",
                fecha)).isEqualTo(65);
        assertThat(contar("SELECT COUNT(*) FROM bt_resumen_diario WHERE fecha_proceso = ?", fecha)).isEqualTo(239);
        assertThat(suma("SELECT SUM(total_creditos) FROM bt_resumen_diario WHERE fecha_proceso = ?", fecha))
                .isEqualByComparingTo("244500");
        assertThat(suma("SELECT SUM(total_debitos) FROM bt_resumen_diario WHERE fecha_proceso = ?", fecha))
                .isEqualByComparingTo("284900");
        assertThat(contar("SELECT COUNT(*) FROM bt_registro_rechazado WHERE job = 'DIARIO' AND clave_proceso = ?",
                fecha)).isEqualTo(608);
        assertThat(contar("SELECT COUNT(*) FROM bt_registro_rechazado WHERE job = 'DIARIO' AND clave_proceso = ? "
                + "AND motivo = 'TIPO_INVALIDO'", fecha)).isEqualTo(368);
        assertThat(contar("SELECT COUNT(*) FROM bt_registro_rechazado WHERE job = 'DIARIO' AND clave_proceso = ? "
                + "AND motivo = 'FECHA_INVALIDA'", fecha)).isEqualTo(49);
    }

    /** Fallo critico -> FAILED; al reiniciar continua desde el ultimo commit y el resultado es el mismo. */
    @Test
    void reporteDiarioSeReiniciaTrasUnFalloCriticoSinDuplicarDatos() throws Exception {
        String fecha = "2024-06-30";
        JobParameters params = base().addString("fechaProceso", fecha)
                .addString("simularFalloCriticoEnLinea", "450", false)
                .toJobParameters();

        JobExecution fallida = jobLauncher.run(reporteDiario, params);
        assertThat(fallida.getStatus()).isEqualTo(BatchStatus.FAILED);

        JobExecution reinicio = jobLauncher.run(reporteDiario, params); // misma JobInstance -> RESTART
        assertThat(reinicio.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(reinicio.getJobInstance().getInstanceId()).isEqualTo(fallida.getJobInstance().getInstanceId());
        verificarReporteDiario(fecha);
    }

    // ----------------------------------------------------------------------------------------------- JOB 2
    @Test
    void interesesMensualesSonEquivalentesAlLegacy() throws Exception {
        String periodo = "2024-12";
        JobExecution e = jobLauncher.run(intereses, base().addString("periodo", periodo).toJobParameters());

        assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verificarIntereses(periodo);
        // El calculo se ejecuto en varias particiones en paralelo
        long particiones = e.getStepExecutions().stream()
                .map(StepExecution::getStepName)
                .filter(n -> n.startsWith("calcularInteresesWorkerStep:"))
                .count();
        assertThat(particiones).isGreaterThan(1);
    }

    /** Un fallo transitorio (como una caida momentanea de la BD) se reintenta y el job termina bien. */
    @Test
    void interesesSeRecuperanDeUnFalloTransitorio() throws Exception {
        String periodo = "2024-11";
        JobExecution e = jobLauncher.run(intereses, base().addString("periodo", periodo)
                .addString("simularFalloTransitorioEnLinea", "10", false).toJobParameters());

        assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution carga = e.getStepExecutions().stream()
                .filter(s -> s.getStepName().equals("cargarCuentasStep")).findFirst().orElseThrow();
        assertThat(carga.getRollbackCount()).isGreaterThan(0);
        verificarIntereses(periodo);
    }

    private void verificarIntereses(String periodo) {
        assertThat(contar("SELECT COUNT(*) FROM bt_interes_mensual WHERE periodo = ?", periodo)).isEqualTo(50);
        assertThat(suma("SELECT SUM(interes) FROM bt_interes_mensual WHERE periodo = ? AND tipo = 'ahorro'", periodo))
                .isEqualByComparingTo("285.00");
        assertThat(suma("SELECT SUM(interes) FROM bt_interes_mensual WHERE periodo = ? AND tipo = 'hipoteca'", periodo))
                .isEqualByComparingTo("437.50");
        assertThat(suma("SELECT SUM(interes) FROM bt_interes_mensual WHERE periodo = ? AND tipo = 'prestamo'", periodo))
                .isEqualByComparingTo("2505.00");
        assertThat(contar("SELECT COUNT(*) FROM bt_registro_rechazado WHERE job = 'INTERESES' AND clave_proceso = ?",
                periodo)).isEqualTo(950);
        assertThat(contar("SELECT COUNT(*) FROM bt_registro_rechazado WHERE job = 'INTERESES' AND clave_proceso = ? "
                + "AND motivo = 'DUPLICADO'", periodo)).isEqualTo(347);
    }

    // ----------------------------------------------------------------------------------------------- JOB 3
    @Test
    void estadosAnualesSonEquivalentesAlLegacy() throws Exception {
        JobExecution e = jobLauncher.run(estados, base().addString("anio", "2024").toJobParameters());

        assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verificarEstados();
    }

    /** El step multihilo no guarda posicion: al reiniciar limpia y reprocesa todo, sin duplicar filas. */
    @Test
    void estadosAnualesSeReinicianSinDuplicar() throws Exception {
        JobParameters params = base().addString("anio", "2024")
                .addString("simularFalloCriticoEnLinea", "700", false).toJobParameters();
        assertThat(jobLauncher.run(estados, params).getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(jobLauncher.run(estados, params).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verificarEstados();
    }

    private void verificarEstados() {
        assertThat(contar("SELECT COUNT(*) FROM bt_movimiento_anual WHERE anio = 2024")).isEqualTo(604);
        assertThat(contar("SELECT COUNT(*) FROM bt_estado_cuenta_anual WHERE anio = 2024")).isEqualTo(20);
        assertThat(suma("SELECT SUM(total_depositos) FROM bt_estado_cuenta_anual WHERE anio = 2024"))
                .isEqualByComparingTo("412500");
        assertThat(suma("SELECT SUM(total_retiros) FROM bt_estado_cuenta_anual WHERE anio = 2024"))
                .isEqualByComparingTo("305000");
        assertThat(suma("SELECT SUM(total_compras) FROM bt_estado_cuenta_anual WHERE anio = 2024"))
                .isEqualByComparingTo("394000");
        assertThat(suma("SELECT SUM(total_pagos) FROM bt_estado_cuenta_anual WHERE anio = 2024"))
                .isEqualByComparingTo("77500");
        assertThat(contar("SELECT COUNT(*) FROM bt_registro_rechazado WHERE job = 'ANUAL' AND clave_proceso = '2024'"))
                .isEqualTo(396);
    }
}
