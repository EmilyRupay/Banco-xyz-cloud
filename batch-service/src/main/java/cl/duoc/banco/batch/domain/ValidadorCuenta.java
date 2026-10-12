package cl.duoc.banco.batch.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reglas del "Calculo de Intereses Mensuales" (intereses_trimestrales.csv: cuenta_id,nombre,saldo,edad,tipo).
 *
 * <p>Rechaza: nombre vacio/"Unknown", saldo vacio o <= 0, edad vacia o fuera de 18-100, tipo invalido (-1, unknown)
 * y duplicados: se conserva el PRIMER registro valido de cada cuenta (mismo criterio que el legacy).
 */
public class ValidadorCuenta {

    public static final String JOB = "INTERESES";
    public static final Set<String> TIPOS = Set.of("ahorro", "prestamo", "hipoteca");
    private static final int EDAD_MIN = 18;
    private static final int EDAD_MAX = 100;

    /**
     * @param cuentasAceptadas cuenta_id -> linea del registro aceptado
     */
    public Procesado<CuentaInteres> validar(RegistroCsv r, String periodo, Map<Long, Integer> cuentasAceptadas) {
        Optional<Long> cuentaId = ReglasLegacy.parsearLong(r.campo(0));
        if (cuentaId.isEmpty()) {
            return rechazo(r, periodo, Motivo.FORMATO_INVALIDO, "cuenta_id='" + r.campo(0) + "'");
        }
        String nombre = r.campo(1);
        if (nombre.isBlank() || nombre.equalsIgnoreCase("unknown")) {
            return rechazo(r, periodo, Motivo.NOMBRE_INVALIDO, "nombre='" + nombre + "'");
        }
        Optional<BigDecimal> saldo = ReglasLegacy.parsearMonto(r.campo(2));
        if (saldo.isEmpty() || saldo.get().signum() <= 0) {
            return rechazo(r, periodo, Motivo.SALDO_INVALIDO, "saldo='" + r.campo(2) + "'");
        }
        Optional<Long> edad = ReglasLegacy.parsearLong(r.campo(3));
        if (edad.isEmpty() || edad.get() < EDAD_MIN || edad.get() > EDAD_MAX) {
            return rechazo(r, periodo, Motivo.EDAD_FUERA_RANGO, "edad='" + r.campo(3) + "'");
        }
        String tipo = ReglasLegacy.normalizarTipo(r.campo(4));
        if (!TIPOS.contains(tipo)) {
            return rechazo(r, periodo, Motivo.TIPO_INVALIDO, "tipo='" + r.campo(4) + "'");
        }
        Integer lineaAceptada = cuentasAceptadas.putIfAbsent(cuentaId.get(), r.linea());
        if (lineaAceptada != null && lineaAceptada != r.linea()) {
            return rechazo(r, periodo, Motivo.DUPLICADO,
                    "cuenta " + cuentaId.get() + " ya aceptada en linea " + lineaAceptada);
        }
        return Procesado.valido(new CuentaInteres(periodo, r.linea(), cuentaId.get(), nombre, saldo.get(),
                edad.get().intValue(), tipo));
    }

    private static Procesado<CuentaInteres> rechazo(RegistroCsv r, String clave, Motivo motivo, String detalle) {
        return Procesado.rechazado(new RegistroRechazado(JOB, clave, r.linea(), motivo, detalle, r.contenido()));
    }
}
