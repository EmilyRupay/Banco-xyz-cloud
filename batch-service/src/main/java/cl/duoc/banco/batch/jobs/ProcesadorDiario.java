package cl.duoc.banco.batch.jobs;

import cl.duoc.banco.batch.domain.Procesado;
import cl.duoc.banco.batch.domain.RegistroCsv;
import cl.duoc.banco.batch.domain.TransaccionDiaria;
import cl.duoc.banco.batch.domain.ValidadorMovimientoDiario;
import cl.duoc.banco.batch.support.SimuladorFallos;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Processor del reporte diario (bean @StepScope: una instancia por ejecucion del step).
 * Si el job se REINICIA, reconstruye desde la BD los id ya aceptados para seguir detectando duplicados.
 */
public class ProcesadorDiario implements ItemProcessor<RegistroCsv, Procesado<TransaccionDiaria>> {

    private final ValidadorMovimientoDiario validador;
    private final JdbcTemplate jdbc;
    private final SimuladorFallos simulador;
    private final String fechaProceso;
    private final String lineaTransitoria;
    private final String lineaCritica;
    private volatile Map<Long, Integer> idsVistos;

    public ProcesadorDiario(ValidadorMovimientoDiario validador, JdbcTemplate jdbc, SimuladorFallos simulador,
                            String fechaProceso, String lineaTransitoria, String lineaCritica) {
        this.validador = validador;
        this.jdbc = jdbc;
        this.simulador = simulador;
        this.fechaProceso = fechaProceso;
        this.lineaTransitoria = lineaTransitoria;
        this.lineaCritica = lineaCritica;
    }

    @Override
    public Procesado<TransaccionDiaria> process(RegistroCsv item) {
        simulador.verificar(ValidadorMovimientoDiario.JOB, fechaProceso, item.linea(), lineaTransitoria, lineaCritica);
        return validador.validar(item, fechaProceso, idsVistos());
    }

    private Map<Long, Integer> idsVistos() {
        if (idsVistos == null) {
            synchronized (this) {
                if (idsVistos == null) {
                    Map<Long, Integer> mapa = new ConcurrentHashMap<>();
                    jdbc.query("SELECT id_origen, linea FROM bt_transaccion_diaria WHERE fecha_proceso = ?",
                            rs -> {
                                mapa.put(rs.getLong("id_origen"), rs.getInt("linea"));
                            }, fechaProceso);
                    idsVistos = mapa;
                }
            }
        }
        return idsVistos;
    }
}
