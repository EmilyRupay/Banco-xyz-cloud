package cl.duoc.banco.batch.domain;

/** Registro que no supera las reglas de calidad. Se persiste en bt_registro_rechazado para auditoria. */
public record RegistroRechazado(String job, String claveProceso, int linea, Motivo motivo, String detalle,
                                String contenido) {
}
