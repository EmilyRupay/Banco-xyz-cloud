package cl.duoc.banco.batch.web;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Vista de una ejecucion para la API (estado, parametros, metricas por step y errores). */
public record EjecucionDto(Long id, String job, Map<String, String> parametros, String estado, String exitCode,
                           LocalDateTime inicio, LocalDateTime fin, Long duracionMs, List<StepDto> steps,
                           List<String> errores) {

    public record StepDto(String nombre, String estado, long leidos, long escritos, long filtrados, long omitidos,
                          long commits, long rollbacks, Long duracionMs) {
    }

    public static EjecucionDto de(JobExecution e) {
        Map<String, String> params = new LinkedHashMap<>();
        e.getJobParameters().getParameters().forEach((k, v) -> params.put(k, String.valueOf(v.getValue())));
        List<StepDto> steps = e.getStepExecutions().stream()
                .sorted(Comparator.comparing(StepExecution::getId))
                .map(s -> new StepDto(s.getStepName(), s.getStatus().name(), s.getReadCount(), s.getWriteCount(),
                        s.getFilterCount(), s.getSkipCount(), s.getCommitCount(), s.getRollbackCount(),
                        duracion(s.getStartTime(), s.getEndTime())))
                .toList();
        List<String> errores = e.getAllFailureExceptions().stream()
                .map(t -> t.getClass().getSimpleName() + ": " + t.getMessage())
                .toList();
        return new EjecucionDto(e.getId(), e.getJobInstance().getJobName(), params, e.getStatus().name(),
                e.getExitStatus().getExitCode(), e.getStartTime(), e.getEndTime(),
                duracion(e.getStartTime(), e.getEndTime()), steps, errores);
    }

    private static Long duracion(LocalDateTime inicio, LocalDateTime fin) {
        return inicio != null && fin != null ? Duration.between(inicio, fin).toMillis() : null;
    }
}
