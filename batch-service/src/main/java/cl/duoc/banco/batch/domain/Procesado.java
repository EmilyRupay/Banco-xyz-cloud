package cl.duoc.banco.batch.domain;

/**
 * Resultado de procesar un registro: o es valido (valor != null) o fue rechazado (rechazo != null).
 * Permite enrutar cada item a un writer distinto (ClassifierCompositeItemWriter) sin perder ningun registro.
 */
public record Procesado<T>(T valor, RegistroRechazado rechazo) {

    public static <T> Procesado<T> valido(T valor) {
        return new Procesado<>(valor, null);
    }

    public static <T> Procesado<T> rechazado(RegistroRechazado rechazo) {
        return new Procesado<>(null, rechazo);
    }

    public boolean esValido() {
        return rechazo == null;
    }
}
