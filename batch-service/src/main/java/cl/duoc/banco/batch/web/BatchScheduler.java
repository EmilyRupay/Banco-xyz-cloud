package cl.duoc.banco.batch.web;

import cl.duoc.banco.batch.jobs.JobNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.Map;

/**
 * Calendario equivalente al cron del mainframe (activar con bank.batch.scheduling.enabled=true):
 * diario 01:00 (dia anterior), mensual dia 1 02:00 (mes anterior), anual 2 de enero 03:00 (anio anterior).
 */
@Component
@ConditionalOnProperty(prefix = "bank.batch.scheduling", name = "enabled", havingValue = "true")
public class BatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(BatchScheduler.class);
    private final JobLauncherService service;

    public BatchScheduler(JobLauncherService service) {
        this.service = service;
    }

    @Scheduled(cron = "${bank.batch.scheduling.cron-diario:0 0 1 * * *}")
    public void diario() {
        lanzar(JobNames.REPORTE_DIARIO, Map.of("fechaProceso", LocalDate.now().minusDays(1).toString()));
    }

    @Scheduled(cron = "${bank.batch.scheduling.cron-mensual:0 0 2 1 * *}")
    public void mensual() {
        lanzar(JobNames.INTERESES_MENSUALES, Map.of("periodo", YearMonth.now().minusMonths(1).toString()));
    }

    @Scheduled(cron = "${bank.batch.scheduling.cron-anual:0 0 3 2 1 *}")
    public void anual() {
        lanzar(JobNames.ESTADOS_ANUALES, Map.of("anio", Year.now().minusYears(1).toString()));
    }

    private void lanzar(String job, Map<String, String> parametros) {
        try {
            service.lanzar(job, parametros);
        } catch (Exception ex) {
            log.error("No se pudo lanzar {} programado: {}", job, ex.getMessage());
        }
    }
}
