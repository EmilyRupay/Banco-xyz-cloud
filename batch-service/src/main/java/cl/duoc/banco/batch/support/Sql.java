package cl.duoc.banco.batch.support;

/** Sentencias SQL compartidas. */
public final class Sql {

    public static final String INSERT_RECHAZO = "INSERT INTO bt_registro_rechazado "
            + "(job, clave_proceso, linea, motivo, detalle, contenido) "
            + "VALUES (:job, :claveProceso, :linea, :motivo, :detalle, :contenido)";

    public static final String DELETE_RECHAZOS = "DELETE FROM bt_registro_rechazado WHERE job = ? AND clave_proceso = ?";

    private Sql() {
    }
}
