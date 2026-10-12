package cl.duoc.banco.batch.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Consulta de los resultados generados por los jobs (scope bank.read). */
@RestController
@RequestMapping("/batch/reports")
public class ReportController {

    private final JdbcTemplate jdbc;

    public ReportController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/resumen-diario")
    public List<Map<String, Object>> resumenDiario(@RequestParam String fechaProceso) {
        return jdbc.queryForList("SELECT fecha, cantidad, total_creditos, total_debitos, neto, alertas "
                + "FROM bt_resumen_diario WHERE fecha_proceso = ? ORDER BY fecha", fechaProceso);
    }

    @GetMapping("/anomalias")
    public List<Map<String, Object>> anomalias(@RequestParam String fechaProceso) {
        return jdbc.queryForList("SELECT linea, alerta AS motivo, alerta_detalle AS detalle, monto "
                + "FROM bt_transaccion_diaria WHERE fecha_proceso = ? AND alerta IS NOT NULL ORDER BY linea", fechaProceso);
    }

    @GetMapping("/intereses")
    public List<Map<String, Object>> intereses(@RequestParam String periodo,
                                               @RequestParam(required = false) Long cuentaId) {
        if (cuentaId != null) {
            return jdbc.queryForList("SELECT * FROM bt_interes_mensual WHERE periodo = ? AND cuenta_id = ?",
                    periodo, cuentaId);
        }
        return jdbc.queryForList("SELECT * FROM bt_interes_mensual WHERE periodo = ? ORDER BY cuenta_id", periodo);
    }

    @GetMapping("/estados-cuenta")
    public List<Map<String, Object>> estados(@RequestParam int anio, @RequestParam(required = false) Long cuentaId) {
        if (cuentaId != null) {
            return jdbc.queryForList("SELECT * FROM bt_estado_cuenta_anual WHERE anio = ? AND cuenta_id = ?",
                    anio, cuentaId);
        }
        return jdbc.queryForList("SELECT * FROM bt_estado_cuenta_anual WHERE anio = ? ORDER BY cuenta_id", anio);
    }

    /** Conciliacion: cuantos registros se rechazaron por cada motivo (para comparar con el legacy). */
    @GetMapping("/rechazos")
    public List<Map<String, Object>> rechazos(@RequestParam String job, @RequestParam String clave) {
        return jdbc.queryForList("SELECT motivo, COUNT(*) AS cantidad FROM bt_registro_rechazado "
                + "WHERE job = ? AND clave_proceso = ? GROUP BY motivo ORDER BY motivo", job.toUpperCase(), clave);
    }
}
