package cl.duoc.banco.batch.jobs;

import cl.duoc.banco.batch.domain.CuentaInteres;
import cl.duoc.banco.batch.domain.Procesado;
import cl.duoc.banco.batch.domain.RegistroCsv;
import cl.duoc.banco.batch.domain.ValidadorCuenta;
import cl.duoc.banco.batch.support.SimuladorFallos;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Valida cuentas y descarta duplicados. En un reinicio reconstruye las cuentas ya aceptadas desde la BD. */
public class ProcesadorCuentas implements ItemProcessor<RegistroCsv, Procesado<CuentaInteres>> {

    private final ValidadorCuenta validador = new ValidadorCuenta();
    private final JdbcTemplate jdbc;
    private final SimuladorFallos simulador;
    private final String periodo;
    private final String lineaTransitoria;
    private final String lineaCritica;
    private volatile Map<Long, Integer> aceptadas;

    public ProcesadorCuentas(JdbcTemplate jdbc, SimuladorFallos simulador, String periodo, String lineaTransitoria,
                             String lineaCritica) {
        this.jdbc = jdbc;
        this.simulador = simulador;
        this.periodo = periodo;
        this.lineaTransitoria = lineaTransitoria;
        this.lineaCritica = lineaCritica;
    }

    @Override
    public Procesado<CuentaInteres> process(RegistroCsv item) {
        simulador.verificar(ValidadorCuenta.JOB, periodo, item.linea(), lineaTransitoria, lineaCritica);
        return validador.validar(item, periodo, aceptadas());
    }

    private Map<Long, Integer> aceptadas() {
        if (aceptadas == null) {
            synchronized (this) {
                if (aceptadas == null) {
                    Map<Long, Integer> mapa = new ConcurrentHashMap<>();
                    jdbc.query("SELECT cuenta_id, linea FROM bt_cuenta_staging WHERE periodo = ?",
                            rs -> {
                                mapa.put(rs.getLong("cuenta_id"), rs.getInt("linea"));
                            }, periodo);
                    aceptadas = mapa;
                }
            }
        }
        return aceptadas;
    }
}
