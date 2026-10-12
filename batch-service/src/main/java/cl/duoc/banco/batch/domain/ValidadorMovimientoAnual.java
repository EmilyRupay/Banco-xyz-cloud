package cl.duoc.banco.batch.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

/**
 * Reglas de "Estados de Cuenta Anuales" (estados_financieros_anuales.csv: cuenta_id,fecha,transaccion,monto,descripcion).
 *
 * <p>Rechaza: fecha invalida o fuera del anio, tipo invalido, monto vacio/negativo/cero.
 * <p>Corrige: "depósito" -> "deposito"; descripcion vacia -> "Sin descripcion".
 * <p>No guarda estado: es seguro usarlo en un step multihilo.
 */
public class ValidadorMovimientoAnual {

    public static final String JOB = "ANUAL";
    public static final String SIN_DESCRIPCION = "Sin descripcion";
    private static final Set<String> TIPOS = Set.of("deposito", "retiro", "compra", "pago");

    public Procesado<MovimientoAnual> validar(RegistroCsv r, int anio) {
        String clave = String.valueOf(anio);
        Optional<Long> cuentaId = ReglasLegacy.parsearLong(r.campo(0));
        if (cuentaId.isEmpty()) {
            return rechazo(r, clave, Motivo.FORMATO_INVALIDO, "cuenta_id='" + r.campo(0) + "'");
        }
        Optional<LocalDate> fecha = ReglasLegacy.parsearFecha(r.campo(1));
        if (fecha.isEmpty()) {
            return rechazo(r, clave, Motivo.FECHA_INVALIDA, "fecha='" + r.campo(1) + "'");
        }
        if (fecha.get().getYear() != anio) {
            return rechazo(r, clave, Motivo.FUERA_DE_PERIODO, "fecha " + fecha.get() + " no pertenece a " + anio);
        }
        String tipo = ReglasLegacy.normalizarTipo(r.campo(2));
        if (!TIPOS.contains(tipo)) {
            return rechazo(r, clave, Motivo.TIPO_INVALIDO, "transaccion='" + r.campo(2) + "'");
        }
        Optional<BigDecimal> monto = ReglasLegacy.parsearMonto(r.campo(3));
        if (monto.isEmpty()) {
            return rechazo(r, clave, Motivo.MONTO_VACIO, "monto='" + r.campo(3) + "'");
        }
        if (monto.get().signum() < 0) {
            return rechazo(r, clave, Motivo.MONTO_NEGATIVO, "monto=" + monto.get());
        }
        if (monto.get().signum() == 0) {
            return rechazo(r, clave, Motivo.MONTO_CERO, "monto=0");
        }
        String descripcion = r.campo(4).isBlank() ? SIN_DESCRIPCION : r.campo(4);
        return Procesado.valido(new MovimientoAnual(anio, r.linea(), cuentaId.get(), fecha.get(), tipo, monto.get(),
                descripcion));
    }

    private static Procesado<MovimientoAnual> rechazo(RegistroCsv r, String clave, Motivo motivo, String detalle) {
        return Procesado.rechazado(new RegistroRechazado(JOB, clave, r.linea(), motivo, detalle, r.contenido()));
    }
}
