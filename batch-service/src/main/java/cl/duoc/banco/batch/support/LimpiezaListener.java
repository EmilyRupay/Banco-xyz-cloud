package cl.duoc.banco.batch.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Antes de un step que NO puede reanudar desde un punto intermedio (step multihilo), borra lo que haya escrito una
 * ejecucion anterior de la misma clave. Asi, reejecutar el job siempre produce el mismo resultado (idempotencia).
 */
public class LimpiezaListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(LimpiezaListener.class);

    private final JdbcTemplate jdbc;
    private final String parametroClave;
    private final String[] sentencias;

    /** @param sentencias DELETE con un unico parametro "?" que recibe el valor de la clave del proceso */
    public LimpiezaListener(JdbcTemplate jdbc, String parametroClave, String... sentencias) {
        this.jdbc = jdbc;
        this.parametroClave = parametroClave;
        this.sentencias = sentencias;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        String clave = stepExecution.getJobParameters().getString(parametroClave);
        int total = 0;
        for (String sql : sentencias) {
            total += jdbc.update(sql, convertir(clave));
        }
        log.info("Limpieza previa de {}={} -> {} filas eliminadas", parametroClave, clave, total);
    }

    /** anio se guarda como INTEGER; el resto como texto. */
    private Object convertir(String clave) {
        return "anio".equals(parametroClave) ? Integer.valueOf(clave) : clave;
    }
}
