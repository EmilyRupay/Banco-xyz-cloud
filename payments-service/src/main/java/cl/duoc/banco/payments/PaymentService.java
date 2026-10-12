package cl.duoc.banco.payments;

import cl.duoc.banco.payments.Dtos.MovimientoSaldoResponse;
import cl.duoc.banco.payments.Dtos.OperacionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquesta los pagos. Las transferencias siguen el patron SAGA con COMPENSACION:
 * <pre>
 * 1. debitar origen   (accounts-service)
 * 2. acreditar destino (accounts-service)  --falla-->  compensar: re-acreditar origen  -> REVERTIDA
 * 3. registrar movimientos + publicar transactions.completed
 * </pre>
 * Cada paso usa un operacionId distinto derivado de la llave de idempotencia, por lo que los reintentos son seguros.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OperacionRepository operaciones;
    private final MovimientoRepository movimientos;
    private final AccountsClient accounts;
    private final EventPublisher events;
    private final DetectorFraude fraude;

    public PaymentService(OperacionRepository operaciones, MovimientoRepository movimientos, AccountsClient accounts,
                          EventPublisher events, DetectorFraude fraude) {
        this.operaciones = operaciones;
        this.movimientos = movimientos;
        this.accounts = accounts;
        this.events = events;
        this.fraude = fraude;
    }

    public OperacionResponse ejecutar(Operacion.Tipo tipo, Long origen, Long destino, BigDecimal monto,
                                      String descripcion, String canal, String idempotencyKey, String token) {
        String id = idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString() : idempotencyKey.trim();

        Optional<Operacion> existente = operaciones.findById(id);
        if (existente.isPresent()) {
            log.info("Operacion {} repetida: se devuelve el resultado original (idempotencia)", id);
            return OperacionResponse.de(existente.get(), true);
        }
        if (tipo == Operacion.Tipo.TRANSFERENCIA && origen.equals(destino)) {
            throw new OperacionException.Rechazada(HttpStatus.BAD_REQUEST, "La cuenta de origen y destino son iguales");
        }

        Operacion op;
        try {
            op = operaciones.saveAndFlush(new Operacion(id, tipo, origen, destino, monto, canal, descripcion));
        } catch (DataIntegrityViolationException ex) {
            throw new OperacionException.Rechazada(HttpStatus.CONFLICT, "La operacion " + id + " ya esta en curso");
        }

        try {
            MovimientoSaldoResponse resultado = switch (tipo) {
                case DEPOSITO -> accounts.aplicar(origen, id + ":CR", "CREDITO", monto, token);
                case RETIRO, PAGO -> accounts.aplicar(origen, id + ":DB", "DEBITO", monto, token);
                case TRANSFERENCIA -> transferir(op, token);
            };
            registrarMovimientos(op);
            op.completar(resultado.saldoResultante());
            operaciones.save(op);
            events.transaccionCompletada(op);
            fraude.evaluar(op);
            log.info("Operacion {} {} COMPLETADA (cuenta {}, monto {})", tipo, id, origen, monto);
            return OperacionResponse.de(op, false);
        } catch (OperacionException ex) {
            if (op.getEstado() == Operacion.Estado.PENDIENTE) {
                op.rechazar(ex.getMessage());
            }
            operaciones.save(op);
            log.warn("Operacion {} {} {}: {}", tipo, id, op.getEstado(), ex.getMessage());
            throw ex.conRespuesta(OperacionResponse.de(op, false));
        }
    }

    private MovimientoSaldoResponse transferir(Operacion op, String token) {
        String id = op.getId();
        MovimientoSaldoResponse debito = accounts.aplicar(op.getCuentaOrigen(), id + ":DB", "DEBITO", op.getMonto(), token);
        try {
            accounts.aplicar(op.getCuentaDestino(), id + ":CR", "CREDITO", op.getMonto(), token);
            return debito;
        } catch (OperacionException errorDestino) {
            // COMPENSACION: se devuelve el dinero a la cuenta de origen
            try {
                accounts.aplicar(op.getCuentaOrigen(), id + ":RV", "CREDITO", op.getMonto(), token);
                op.revertir("Transferencia revertida: " + errorDestino.getMessage());
                log.warn("Transferencia {} REVERTIDA (compensacion aplicada)", id);
            } catch (OperacionException errorCompensacion) {
                op.pendienteRevision("Fallo la compensacion: " + errorCompensacion.getMessage());
                events.alerta("COMPENSACION_FALLIDA", "CRITICA", op.getCuentaOrigen(),
                        "Transferencia " + id + " debitada sin acreditar; requiere conciliacion");
                log.error("Transferencia {} requiere REVISION manual: fallo la compensacion", id);
            }
            throw errorDestino;
        }
    }

    private void registrarMovimientos(Operacion op) {
        LocalDate hoy = LocalDate.now();
        String desc = op.getDescripcion() == null ? op.getTipo().name().toLowerCase() : op.getDescripcion();
        switch (op.getTipo()) {
            case DEPOSITO -> movimientos.save(new Movimiento(op.getCuentaOrigen(), hoy, "deposito", op.getMonto(), desc,
                    op.getId(), op.getCanal()));
            case RETIRO -> movimientos.save(new Movimiento(op.getCuentaOrigen(), hoy, "retiro", op.getMonto(), desc,
                    op.getId(), op.getCanal()));
            case PAGO -> movimientos.save(new Movimiento(op.getCuentaOrigen(), hoy, "pago", op.getMonto(), desc,
                    op.getId(), op.getCanal()));
            case TRANSFERENCIA -> {
                movimientos.save(new Movimiento(op.getCuentaOrigen(), hoy, "transferencia_enviada", op.getMonto(),
                        desc + " -> cuenta " + op.getCuentaDestino(), op.getId(), op.getCanal()));
                movimientos.save(new Movimiento(op.getCuentaDestino(), hoy, "transferencia_recibida", op.getMonto(),
                        desc + " <- cuenta " + op.getCuentaOrigen(), op.getId(), op.getCanal()));
            }
        }
    }
}
