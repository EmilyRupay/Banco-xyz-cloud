package cl.duoc.banco.batch.support;

import cl.duoc.banco.batch.domain.Motivo;
import cl.duoc.banco.batch.domain.RegistroCsv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Politica de omision (skip): las lineas que no se pueden leer (columnas faltantes/sobrantes) o items que fallan de
 * forma no recuperable se omiten, pero NUNCA se pierden: quedan registradas en bt_registro_rechazado.
 */
public class RechazoSkipListener implements SkipListener<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(RechazoSkipListener.class);
    private static final String INSERT = "INSERT INTO bt_registro_rechazado "
            + "(job, clave_proceso, linea, motivo, detalle, contenido) VALUES (?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final String job;
    private final String parametroClave;

    public RechazoSkipListener(JdbcTemplate jdbc, String job, String parametroClave) {
        this.jdbc = jdbc;
        this.job = job;
        this.parametroClave = parametroClave;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        if (t instanceof FlatFileParseException ex) {
            registrar(ex.getLineNumber(), ex.getMessage(), ex.getInput());
        } else {
            registrar(0, t.getMessage(), null);
        }
    }

    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        registrar(linea(item), t.getMessage(), contenido(item));
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        registrar(linea(item), t.getMessage(), String.valueOf(item));
    }

    private void registrar(int linea, String detalle, String contenido) {
        String clave = clave();
        log.warn("[{}] Registro omitido (linea {}): {}", job, linea, detalle);
        jdbc.update(INSERT, job, clave, linea, Motivo.FORMATO_INVALIDO.name(), recortar(detalle, 255),
                recortar(contenido, 500));
    }

    private String clave() {
        StepContext ctx = StepSynchronizationManager.getContext();
        if (ctx == null) {
            return "desconocido";
        }
        String valor = ctx.getStepExecution().getJobParameters().getString(parametroClave);
        return valor == null ? "desconocido" : valor;
    }

    private static int linea(Object item) {
        return item instanceof RegistroCsv r ? r.linea() : 0;
    }

    private static String contenido(Object item) {
        return item instanceof RegistroCsv r ? r.contenido() : String.valueOf(item);
    }

    private static String recortar(String texto, int max) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
