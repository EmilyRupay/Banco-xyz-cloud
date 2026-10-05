package cl.duoc.banco.bff;

/** Resultado de una llamada remota: distingue "no existe" (value == null) de "servicio degradado" (fallback activado). */
public record ClientResult<T>(T value, boolean degraded) {

    public static <T> ClientResult<T> ok(T value) {
        return new ClientResult<>(value, false);
    }

    public static <T> ClientResult<T> degraded(T fallbackValue) {
        return new ClientResult<>(fallbackValue, true);
    }
}
