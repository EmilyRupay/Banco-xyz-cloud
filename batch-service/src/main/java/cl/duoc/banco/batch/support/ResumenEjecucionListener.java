package cl.duoc.banco.batch.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Registra en el log un resumen de cada ejecucion (monitoreo y comparacion de tiempos con el legacy). */
@Component
public class ResumenEjecucionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ResumenEjecucionListener.class);

    @Override
    public void beforeJob(JobExecution ejecucion) {
        log.info(">>> Inicia {} (ejecucion {}) parametros={}", ejecucion.getJobInstance().getJobName(),
                ejecucion.getId(), ejecucion.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution ejecucion) {
        long ms = ejecucion.getStartTime() != null && ejecucion.getEndTime() != null
                ? Duration.between(ejecucion.getStartTime(), ejecucion.getEndTime()).toMillis() : -1;
        log.info("<<< Fin {} (ejecucion {}) estado={} duracion={} ms", ejecucion.getJobInstance().getJobName(),
                ejecucion.getId(), ejecucion.getStatus(), ms);
        for (StepExecution s : ejecucion.getStepExecutions()) {
            log.info("    step={} estado={} leidos={} escritos={} omitidos={} commits={} rollbacks={}",
                    s.getStepName(), s.getStatus(), s.getReadCount(), s.getWriteCount(), s.getSkipCount(),
                    s.getCommitCount(), s.getRollbackCount());
        }
    }
}
