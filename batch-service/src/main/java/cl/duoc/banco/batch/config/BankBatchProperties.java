package cl.duoc.banco.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Parametros de los procesos batch (se definen en config-repo/batch-service.yml y se pueden cambiar sin recompilar).
 *
 * @param inputDir        carpeta de los CSV legacy (classpath: o file:)
 * @param outputDir       carpeta donde se escriben los reportes CSV
 * @param chunkSize       registros por transaccion (commit interval)
 * @param gridSize        particiones del calculo de intereses
 * @param threads         hilos del pool de procesamiento paralelo
 * @param skipLimit       maximo de lineas mal formadas que se omiten antes de fallar
 * @param retryLimit      reintentos ante fallos transitorios (ej. caida de la conexion a la BD)
 * @param umbralMontoAlto monto sobre el cual una transaccion diaria se marca como anomala
 * @param tasasAnuales    tasa anual por tipo de cuenta (ahorro, prestamo, hipoteca)
 */
@ConfigurationProperties(prefix = "bank.batch")
public record BankBatchProperties(
        @DefaultValue("classpath:legacy-data/semana_3") String inputDir,
        @DefaultValue("./batch-output") String outputDir,
        @DefaultValue("200") int chunkSize,
        @DefaultValue("4") int gridSize,
        @DefaultValue("4") int threads,
        @DefaultValue("1000") int skipLimit,
        @DefaultValue("3") int retryLimit,
        @DefaultValue("2500") BigDecimal umbralMontoAlto,
        Map<String, BigDecimal> tasasAnuales,
        @DefaultValue AutoRestart autoRestart,
        @DefaultValue Demo demo) {

    /** Tasas por defecto si no se configuran: ahorro 3%, prestamo 18%, hipoteca 4,2% anual. */
    public Map<String, BigDecimal> tasasAnualesEfectivas() {
        if (tasasAnuales == null || tasasAnuales.isEmpty()) {
            return Map.of("ahorro", new BigDecimal("0.03"),
                    "prestamo", new BigDecimal("0.18"),
                    "hipoteca", new BigDecimal("0.042"));
        }
        return tasasAnuales;
    }

    /** Politica de reejecucion automatica ante fallos criticos. */
    public record AutoRestart(@DefaultValue("true") boolean enabled,
                              @DefaultValue("2") int maxAttempts,
                              @DefaultValue("20s") Duration delay) {
    }

    /** Valores por defecto para ejecuciones manuales/demo (los datos legacy corresponden a 2024). */
    public record Demo(@DefaultValue("false") boolean runOnStartup,
                       @DefaultValue("2024-12") String periodo,
                       @DefaultValue("2024") String anio) {
    }
}
