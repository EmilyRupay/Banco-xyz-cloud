package cl.duoc.banco.batch.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reglas del "Reporte de Transacciones Diarias" (movimientos_financieros_diarios.csv: id,fecha,monto,tipo).
 *
 * <p>Rechaza: id no numerico o duplicado, fecha invalida, tipo distinto de debito/credito, monto vacio, negativo o cero.
 * <p>Alerta (registro valido pero sospechoso): monto superior al umbral configurado.
 *
 * <p>Es idempotente frente a reintentos: un id solo se considera duplicado si ya fue visto en OTRA linea.
 */
public class ValidadorMovimientoDiario {

    public static final String JOB = "DIARIO";
    private static final Set<String> TIPOS = Set.of("debito", "credito");

    private final BigDecimal umbralMontoAlto;

    public ValidadorMovimientoDiario(BigDecimal umbralMontoAlto) {
        this.umbralMontoAlto = umbralMontoAlto;
    }

    /**
     * @param idsVistos id de origen -> primera linea en que aparecio (compartido durante la ejecucion del step)
     */
    public Procesado<TransaccionDiaria> validar(RegistroCsv r, String fechaProceso, Map<Long, Integer> idsVistos) {
        Optional<Long> id = ReglasLegacy.parsearLong(r.campo(0));
        if (id.isEmpty()) {
            return rechazo(r, fechaProceso, Motivo.FORMATO_INVALIDO, "id='" + r.campo(0) + "'");
        }
        Integer primeraLinea = idsVistos.putIfAbsent(id.get(), r.linea());
        if (primeraLinea != null && primeraLinea != r.linea()) {
            return rechazo(r, fechaProceso, Motivo.DUPLICADO, "id " + id.get() + " ya aparece en linea " + primeraLinea);
        }
        Optional<LocalDate> fecha = ReglasLegacy.parsearFecha(r.campo(1));
        if (fecha.isEmpty()) {
            return rechazo(r, fechaProceso, Motivo.FECHA_INVALIDA, "fecha='" + r.campo(1) + "'");
        }
        String tipo = ReglasLegacy.normalizarTipo(r.campo(3));
        if (!TIPOS.contains(tipo)) {
            return rechazo(r, fechaProceso, Motivo.TIPO_INVALIDO, "tipo='" + r.campo(3) + "'");
        }
        Optional<BigDecimal> monto = ReglasLegacy.parsearMonto(r.campo(2));
        if (monto.isEmpty()) {
            return rechazo(r, fechaProceso, Motivo.MONTO_VACIO, "monto='" + r.campo(2) + "'");
        }
        if (monto.get().signum() < 0) {
            return rechazo(r, fechaProceso, Motivo.MONTO_NEGATIVO, "monto=" + monto.get());
        }
        if (monto.get().signum() == 0) {
            return rechazo(r, fechaProceso, Motivo.MONTO_CERO, "monto=0");
        }

        Motivo alerta = null;
        String detalle = null;
        if (monto.get().compareTo(umbralMontoAlto) > 0) {
            alerta = Motivo.MONTO_ALTO;
            detalle = "monto " + monto.get().toPlainString() + " supera el umbral " + umbralMontoAlto.toPlainString();
        }
        return Procesado.valido(new TransaccionDiaria(fechaProceso, r.linea(), id.get(), fecha.get(), tipo,
                monto.get(), alerta, detalle));
    }

    private static Procesado<TransaccionDiaria> rechazo(RegistroCsv r, String clave, Motivo motivo, String detalle) {
        return Procesado.rechazado(new RegistroRechazado(JOB, clave, r.linea(), motivo, detalle, r.contenido()));
    }
}
