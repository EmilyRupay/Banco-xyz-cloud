package cl.duoc.banco.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** Reglas simples de deteccion de fraude que generan eventos en el topico security.alerts. */
@Component
public class DetectorFraude {

    private final OperacionRepository operaciones;
    private final EventPublisher events;
    private final BigDecimal montoAlto;
    private final int maxRetirosAtm;
    private final Duration ventana;

    public DetectorFraude(OperacionRepository operaciones, EventPublisher events,
                          @Value("${bank.fraude.monto-alto:1000000}") BigDecimal montoAlto,
                          @Value("${bank.fraude.max-retiros-atm:3}") int maxRetirosAtm,
                          @Value("${bank.fraude.ventana:10m}") Duration ventana) {
        this.operaciones = operaciones;
        this.events = events;
        this.montoAlto = montoAlto;
        this.maxRetirosAtm = maxRetirosAtm;
        this.ventana = ventana;
    }

    public void evaluar(Operacion o) {
        if (o.getMonto().compareTo(montoAlto) >= 0) {
            events.alerta("MONTO_ALTO", "ALTA", o.getCuentaOrigen(),
                    o.getTipo() + " por $" + o.getMonto().toPlainString() + " (umbral $" + montoAlto.toPlainString()
                            + ") operacion " + o.getId());
        }
        if (o.getTipo() == Operacion.Tipo.RETIRO && "ATM".equals(o.getCanal())) {
            long recientes = operaciones.countByCuentaOrigenAndTipoAndCanalAndEstadoAndCreadaEnAfter(
                    o.getCuentaOrigen(), Operacion.Tipo.RETIRO, "ATM", Operacion.Estado.COMPLETADA,
                    Instant.now().minus(ventana));
            if (recientes > maxRetirosAtm) {
                events.alerta("RETIROS_FRECUENTES", "ALTA", o.getCuentaOrigen(),
                        recientes + " retiros en cajero en los ultimos " + ventana.toMinutes() + " minutos");
            }
        }
    }
}
