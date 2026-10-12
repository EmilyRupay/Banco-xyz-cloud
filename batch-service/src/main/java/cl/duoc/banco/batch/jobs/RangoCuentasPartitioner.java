package cl.duoc.banco.batch.jobs;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Divide las cuentas del periodo en rangos de cuenta_id. Cada rango se procesa en paralelo en su propio hilo y con su
 * propio StepExecution (si una particion falla, al reiniciar solo se reprocesa esa particion).
 */
public class RangoCuentasPartitioner implements Partitioner {

    private final JdbcTemplate jdbc;
    private final String periodo;

    public RangoCuentasPartitioner(JdbcTemplate jdbc, String periodo) {
        this.jdbc = jdbc;
        this.periodo = periodo;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, Object> rango = jdbc.queryForMap(
                "SELECT MIN(cuenta_id) AS minimo, MAX(cuenta_id) AS maximo FROM bt_cuenta_staging WHERE periodo = ?",
                periodo);
        Map<String, ExecutionContext> particiones = new HashMap<>();
        if (rango.get("minimo") == null) {
            particiones.put("particion0", contexto(0, -1)); // sin datos: una particion vacia
            return particiones;
        }
        long min = ((Number) rango.get("minimo")).longValue();
        long max = ((Number) rango.get("maximo")).longValue();
        long tamano = (max - min) / Math.max(gridSize, 1) + 1;

        int numero = 0;
        for (long inicio = min; inicio <= max; inicio += tamano) {
            particiones.put("particion" + numero++, contexto(inicio, Math.min(inicio + tamano - 1, max)));
        }
        return particiones;
    }

    private static ExecutionContext contexto(long minId, long maxId) {
        ExecutionContext ctx = new ExecutionContext();
        ctx.putLong("minId", minId);
        ctx.putLong("maxId", maxId);
        return ctx;
    }
}
