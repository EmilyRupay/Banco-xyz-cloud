package cl.duoc.banco.batch.support;

import cl.duoc.banco.batch.config.BankBatchProperties;
import cl.duoc.banco.batch.web.JobLauncherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Politica de REEJECUCION AUTOMATICA ante fallos criticos.
 *
 * <p>Cuando un job termina FAILED se programa un reinicio (misma JobInstance, mismos parametros) despues de un tiempo
 * de espera. Spring Batch reanuda desde el ultimo chunk confirmado, sin duplicar datos. Se limita la cantidad de
 * intentos y NO se reintenta si la causa es de calidad de datos (se supero el skipLimit): eso requiere revision humana.
 */
@Component
public class AutoRestartJobListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(AutoRestartJobListener.class);

    private final BankBatchProperties props;
    private final JobExplorer jobExplorer;
    private final TaskScheduler scheduler;
    private final ObjectProvider<JobLauncherService> launcher;

    public AutoRestartJobListener(BankBatchProperties props, JobExplorer jobExplorer, TaskScheduler scheduler,
                                  ObjectProvider<JobLauncherService> launcher) {
        this.props = props;
        this.jobExplorer = jobExplorer;
        this.scheduler = scheduler;
        this.launcher = launcher;
    }

    @Override
    public void afterJob(JobExecution ejecucion) {
        if (ejecucion.getStatus() != BatchStatus.FAILED || !props.autoRestart().enabled()) {
            return;
        }
        String job = ejecucion.getJobInstance().getJobName();
        if (esErrorDeCalidadDeDatos(ejecucion)) {
            log.error("Job {} (ejecucion {}) FALLO por calidad de datos (skipLimit superado). "
                    + "No se reintenta automaticamente: requiere revision.", job, ejecucion.getId());
            return;
        }
        int ejecuciones = jobExplorer.getJobExecutions(ejecucion.getJobInstance()).size();
        if (ejecuciones > props.autoRestart().maxAttempts()) {
            log.error("Job {} FALLO y se agotaron los {} reinicios automaticos. Se requiere intervencion manual: "
                    + "POST /batch/executions/{}/restart", job, props.autoRestart().maxAttempts(), ejecucion.getId());
            return;
        }
        log.warn("Job {} (ejecucion {}) FALLO. Reinicio automatico {}/{} en {} s",
                job, ejecucion.getId(), ejecuciones, props.autoRestart().maxAttempts(),
                props.autoRestart().delay().toSeconds());
        scheduler.schedule(() -> reiniciar(ejecucion.getId()), Instant.now().plus(props.autoRestart().delay()));
    }

    private void reiniciar(long ejecucionId) {
        try {
            launcher.getObject().reiniciar(ejecucionId);
        } catch (Exception ex) {
            log.error("No se pudo reiniciar la ejecucion {}: {}", ejecucionId, ex.getMessage(), ex);
        }
    }

    private static boolean esErrorDeCalidadDeDatos(JobExecution ejecucion) {
        for (Throwable t : ejecucion.getAllFailureExceptions()) {
            for (Throwable c = t; c != null; c = c.getCause()) {
                if (c instanceof SkipLimitExceededException) {
                    return true;
                }
            }
        }
        return false;
    }
}
