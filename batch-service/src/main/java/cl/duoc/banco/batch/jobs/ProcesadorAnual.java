package cl.duoc.banco.batch.jobs;

import cl.duoc.banco.batch.domain.MovimientoAnual;
import cl.duoc.banco.batch.domain.Procesado;
import cl.duoc.banco.batch.domain.RegistroCsv;
import cl.duoc.banco.batch.domain.ValidadorMovimientoAnual;
import cl.duoc.banco.batch.support.SimuladorFallos;
import org.springframework.batch.item.ItemProcessor;

/** Processor sin estado (thread-safe): se usa en el step multihilo de estados de cuenta anuales. */
public class ProcesadorAnual implements ItemProcessor<RegistroCsv, Procesado<MovimientoAnual>> {

    private final ValidadorMovimientoAnual validador = new ValidadorMovimientoAnual();
    private final SimuladorFallos simulador;
    private final int anio;
    private final String lineaTransitoria;
    private final String lineaCritica;

    public ProcesadorAnual(SimuladorFallos simulador, String anio, String lineaTransitoria, String lineaCritica) {
        this.simulador = simulador;
        this.anio = Integer.parseInt(anio);
        this.lineaTransitoria = lineaTransitoria;
        this.lineaCritica = lineaCritica;
    }

    @Override
    public Procesado<MovimientoAnual> process(RegistroCsv item) {
        simulador.verificar(ValidadorMovimientoAnual.JOB, String.valueOf(anio), item.linea(), lineaTransitoria,
                lineaCritica);
        return validador.validar(item, anio);
    }
}
