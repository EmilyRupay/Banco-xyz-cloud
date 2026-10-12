package cl.duoc.banco.batch.web;

import cl.duoc.banco.batch.jobs.JobNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/** Para la demo: al iniciar ejecuta los 3 jobs uno tras otro (bank.batch.demo.run-on-startup=true). */
@Component
@ConditionalOnProperty(prefix = "bank.batch.demo", name = "run-on-startup", havingValue = "true")
public class DemoStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoStartupRunner.class);

    private final JobLauncherService service;
    private final JobExplorer jobExplorer;

    public DemoStartupRunner(JobLauncherService service, JobExplorer jobExplorer) {
        this.service = service;
        this.jobExplorer = jobExplorer;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread hilo = new Thread(() -> {
            ejecutar(JobNames.REPORTE_DIARIO, Map.of("fechaProceso", LocalDate.now().toString()));
            ejecutar(JobNames.INTERESES_MENSUALES, Map.of());
            ejecutar(JobNames.ESTADOS_ANUALES, Map.of());
        }, "demo-batch");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void ejecutar(String job, Map<String, String> parametros) {
        try {
            JobExecution e = service.lanzar(job, parametros);
            while (true) {
                Thread.sleep(500);
                JobExecution actual = jobExplorer.getJobExecution(e.getId());
                if (actual != null && !actual.isRunning()) {
                    break;
                }
            }
        } catch (JobInstanceAlreadyCompleteException ex) {
            log.info("{} ya fue ejecutado con esos parametros; se omite en el arranque", job);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.error("Error ejecutando {} en el arranque: {}", job, ex.getMessage());
        }
    }
}
