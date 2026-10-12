package cl.duoc.banco.batch.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Interes mensual = saldo x (tasa anual / 12).
 *
 * <p>Redondeo HALF_UP a 2 decimales: equivale a la clausula ROUNDED de COBOL que usaba el legacy, por lo que los
 * resultados son identicos centavo a centavo.
 */
public class CalculadoraIntereses {

    private static final BigDecimal MESES = BigDecimal.valueOf(12);
    private final Map<String, BigDecimal> tasasAnuales;

    public CalculadoraIntereses(Map<String, BigDecimal> tasasAnuales) {
        for (String tipo : ValidadorCuenta.TIPOS) {
            if (!tasasAnuales.containsKey(tipo)) {
                throw new IllegalArgumentException("Falta la tasa anual para el tipo " + tipo);
            }
        }
        this.tasasAnuales = Map.copyOf(tasasAnuales);
    }

    public BigDecimal tasaMensual(String tipo) {
        return tasasAnuales.get(tipo).divide(MESES, 8, RoundingMode.HALF_UP);
    }

    public InteresMensual calcular(CuentaInteres c) {
        BigDecimal tasaMensual = tasaMensual(c.tipo());
        BigDecimal interes = c.saldo().multiply(tasaMensual).setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoFinal = c.saldo().add(interes).setScale(2, RoundingMode.HALF_UP);
        String naturaleza = "ahorro".equals(c.tipo()) ? "ABONO" : "CARGO";
        return new InteresMensual(c.periodo(), c.cuentaId(), c.nombre(), c.tipo(),
                c.saldo().setScale(2, RoundingMode.HALF_UP), tasaMensual, interes, saldoFinal, naturaleza);
    }
}
