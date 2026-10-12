package cl.duoc.banco.payments;

import cl.duoc.banco.payments.Dtos.OperacionResponse;
import org.springframework.http.HttpStatus;

/** Error de una operacion de pago con la respuesta que se devuelve al canal. */
public class OperacionException extends RuntimeException {

    private final HttpStatus status;
    private transient OperacionResponse respuesta;

    public OperacionException(HttpStatus status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public OperacionException conRespuesta(OperacionResponse respuesta) {
        this.respuesta = respuesta;
        return this;
    }

    public HttpStatus getStatus() { return status; }
    public OperacionResponse getRespuesta() { return respuesta; }

    /** La operacion es invalida por reglas de negocio (saldo insuficiente, cuenta cerrada...). No se reintenta. */
    public static class Rechazada extends OperacionException {
        public Rechazada(HttpStatus status, String mensaje) {
            super(status, mensaje);
        }
    }

    /** accounts-service no esta disponible (circuito abierto o fallas repetidas). */
    public static class ServicioNoDisponible extends OperacionException {
        public ServicioNoDisponible(String mensaje) {
            super(HttpStatus.SERVICE_UNAVAILABLE, mensaje);
        }
    }
}
