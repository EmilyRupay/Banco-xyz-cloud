package cl.duoc.banco.batch.jobs;

import java.util.List;

public final class JobNames {

    public static final String REPORTE_DIARIO = "reporteTransaccionesDiariasJob";
    public static final String INTERESES_MENSUALES = "calculoInteresesMensualesJob";
    public static final String ESTADOS_ANUALES = "estadosCuentaAnualesJob";
    public static final List<String> TODOS = List.of(REPORTE_DIARIO, INTERESES_MENSUALES, ESTADOS_ANUALES);

    private JobNames() {
    }
}
