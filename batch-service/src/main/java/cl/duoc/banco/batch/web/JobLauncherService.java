package cl.duoc.banco.batch.web;

import cl.duoc.banco.batch.config.BankBatchProperties;
import cl.duoc.banco.batch.jobs.JobNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Lanza, reinicia y consulta jobs. Las ejecuciones son asincronas: la API responde 202 de inmediato. */
@Service
public class JobLauncherService {

    private static final Logger log = LoggerFactory.getLogger(JobLauncherService.class);
    private static final List<String> PARAMETROS_SIMULACION =
            List.of("simularFalloTransitorioEnLinea", "simularFalloCriticoEnLinea");

    private final Map<String, Job> jobs;
    private final JobExplorer jobExplorer;
    private final BankBatchProperties props;
    private final TaskExecutorJobLauncher launcher;

    public JobLauncherService(List<Job> jobs, JobRepository jobRepository, JobExplorer jobExplorer,
                              BankBatchProperties props) throws Exception {
        this.jobs = jobs.stream().collect(Collectors.toMap(Job::getName, Function.identity()));
        this.jobExplorer = jobExplorer;
        this.props = props;
        this.launcher = new TaskExecutorJobLauncher();
        this.launcher.setJobRepository(jobRepository);
        this.launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("job-"));
        this.launcher.afterPropertiesSet();
    }

    public List<String> nombres() {
        return JobNames.TODOS;
    }

    public JobExecution lanzar(String jobName, Map<String, String> solicitud) throws JobExecutionException {
        Job job = buscar(jobName);
        JobParameters parametros = construirParametros(jobName, solicitud);
        log.info("Lanzando {} con {}", jobName, parametros);
        return launcher.run(job, parametros);
    }

    /** Reinicia una ejecucion FAILED/STOPPED: misma JobInstance, continua desde el ultimo commit. */
    public JobExecution reiniciar(long ejecucionId) throws JobExecutionException {
        JobExecution anterior = jobExplorer.getJobExecution(ejecucionId);
        if (anterior == null) {
            throw new IllegalArgumentException("No existe la ejecucion " + ejecucionId);
        }
        if (anterior.getStatus() != BatchStatus.FAILED && anterior.getStatus() != BatchStatus.STOPPED) {
            throw new IllegalStateException("Solo se reinician ejecuciones FAILED o STOPPED (estado actual: "
                    + anterior.getStatus() + ")");
        }
        Job job = buscar(anterior.getJobInstance().getJobName());
        log.warn("Reiniciando {} (ejecucion previa {})", job.getName(), ejecucionId);
        return launcher.run(job, anterior.getJobParameters());
    }

    private Job buscar(String jobName) {
        Job job = jobs.get(jobName);
        if (job == null) {
            throw new IllegalArgumentException("Job desconocido: " + jobName + ". Disponibles: " + jobs.keySet());
        }
        return job;
    }

    JobParameters construirParametros(String jobName, Map<String, String> solicitud) {
        JobParametersBuilder b = new JobParametersBuilder();
        switch (jobName) {
            case JobNames.REPORTE_DIARIO -> {
                String fecha = solicitud.getOrDefault("fechaProceso", LocalDate.now().toString());
                validar(() -> LocalDate.parse(fecha), "fechaProceso debe tener formato aaaa-mm-dd");
                b.addString("fechaProceso", fecha);
            }
            case JobNames.INTERESES_MENSUALES -> {
                String periodo = solicitud.getOrDefault("periodo", props.demo().periodo());
                validar(() -> YearMonth.parse(periodo), "periodo debe tener formato aaaa-mm");
                b.addString("periodo", periodo);
            }
            case JobNames.ESTADOS_ANUALES -> {
                String anio = solicitud.getOrDefault("anio", props.demo().anio());
                if (!anio.matches("\\d{4}")) {
                    throw new IllegalArgumentException("anio debe tener 4 digitos");
                }
                b.addString("anio", anio);
            }
            default -> throw new IllegalArgumentException("Job desconocido: " + jobName);
        }
        // Parametros NO identificadores: no cambian la JobInstance (un reinicio los conserva)
        b.addString("inputDir", solicitud.getOrDefault("inputDir", props.inputDir()), false);
        for (String p : PARAMETROS_SIMULACION) {
            if (solicitud.containsKey(p)) {
                b.addString(p, solicitud.get(p), false);
            }
        }
        // forzar=true crea una NUEVA instancia aunque la anterior haya terminado COMPLETED (reproceso)
        if (Boolean.parseBoolean(solicitud.getOrDefault("forzar", "false"))) {
            b.addLong("ejecucion", System.currentTimeMillis());
        }
        return b.toJobParameters();
    }

    private static void validar(Runnable parser, String mensaje) {
        try {
            parser.run();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(mensaje);
        }
    }
}
