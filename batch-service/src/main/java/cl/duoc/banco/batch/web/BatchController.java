package cl.duoc.banco.batch.web;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * API de operacion de los procesos batch (via Gateway: /api/batch/**).
 * <ul>
 *   <li>POST /batch/jobs/{job}/executions?fechaProceso|periodo|anio=...  lanza un job (scope batch.admin)</li>
 *   <li>POST /batch/executions/{id}/restart                            reinicia un job fallido</li>
 *   <li>GET  /batch/jobs, /batch/jobs/{job}/executions, /batch/executions/{id}</li>
 * </ul>
 */
@RestController
@RequestMapping("/batch")
public class BatchController {

    private final JobLauncherService service;
    private final JobExplorer jobExplorer;

    public BatchController(JobLauncherService service, JobExplorer jobExplorer) {
        this.service = service;
        this.jobExplorer = jobExplorer;
    }

    @GetMapping("/jobs")
    public List<Map<String, Object>> jobs() {
        return service.nombres().stream()
                .map(nombre -> Map.<String, Object>of("job", nombre,
                        "ultimaEjecucion", ultimas(nombre, 1).stream().findFirst().map(Object.class::cast).orElse("-")))
                .toList();
    }

    @PostMapping("/jobs/{jobName}/executions")
    public ResponseEntity<EjecucionDto> lanzar(@PathVariable String jobName,
                                               @RequestParam Map<String, String> parametros)
            throws JobExecutionException {
        JobExecution ejecucion = service.lanzar(jobName, parametros);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(EjecucionDto.de(ejecucion));
    }

    @GetMapping("/jobs/{jobName}/executions")
    public List<EjecucionDto> ejecuciones(@PathVariable String jobName,
                                          @RequestParam(defaultValue = "10") int limite) {
        return ultimas(jobName, limite);
    }

    @GetMapping("/executions/{id}")
    public ResponseEntity<EjecucionDto> ejecucion(@PathVariable long id) {
        JobExecution e = jobExplorer.getJobExecution(id);
        return e == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(EjecucionDto.de(e));
    }

    @PostMapping("/executions/{id}/restart")
    public ResponseEntity<EjecucionDto> reiniciar(@PathVariable long id) throws JobExecutionException {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(EjecucionDto.de(service.reiniciar(id)));
    }

    private List<EjecucionDto> ultimas(String jobName, int limite) {
        List<JobInstance> instancias = jobExplorer.getJobInstances(jobName, 0, limite);
        return instancias.stream()
                .flatMap(i -> jobExplorer.getJobExecutions(i).stream())
                .sorted(Comparator.comparing(JobExecution::getId).reversed())
                .limit(limite)
                .map(EjecucionDto::de)
                .toList();
    }

    // ---------------- manejo de errores ----------------
    @ExceptionHandler(JobInstanceAlreadyCompleteException.class)
    public ResponseEntity<Map<String, String>> yaCompletado(JobInstanceAlreadyCompleteException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error",
                "Esa ejecucion ya termino COMPLETED. Use forzar=true para reprocesar.", "detalle", ex.getMessage()));
    }

    @ExceptionHandler(JobExecutionException.class)
    public ResponseEntity<Map<String, String>> errorJob(JobExecutionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> solicitudInvalida(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
