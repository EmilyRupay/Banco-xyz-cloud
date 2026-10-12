package cl.duoc.banco.accounts;

import cl.duoc.banco.accounts.Dtos.AperturaRequest;
import cl.duoc.banco.accounts.Dtos.ClienteDto;
import cl.duoc.banco.accounts.Dtos.MovimientoSaldoRequest;
import cl.duoc.banco.accounts.Dtos.MovimientoSaldoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class CuentaService {

    private static final Logger log = LoggerFactory.getLogger(CuentaService.class);

    private final CuentaRepository cuentas;
    private final OperacionAplicadaRepository operaciones;
    private final CustomersClient customers;
    private final EventPublisher events;

    public CuentaService(CuentaRepository cuentas, OperacionAplicadaRepository operaciones, CustomersClient customers,
                         EventPublisher events) {
        this.cuentas = cuentas;
        this.operaciones = operaciones;
        this.customers = customers;
        this.events = events;
    }

    public Cuenta obtener(Long id) {
        return cuentas.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta " + id + " no existe"));
    }

    /** APERTURA: valida al cliente en customers-service y crea la cuenta. */
    @Transactional
    public Cuenta abrir(AperturaRequest req, String token) {
        ClienteDto cliente = customers.buscar(req.clienteId(), token);
        if (cliente == null || !"ACTIVO".equals(cliente.estado())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El cliente " + req.clienteId() + " no esta activo");
        }
        BigDecimal saldo = req.depositoInicial() == null ? BigDecimal.ZERO : req.depositoInicial();
        Cuenta nueva = cuentas.save(new Cuenta(cuentas.maxId() + 1, cliente.id(), cliente.nombre(), saldo, null,
                req.tipo(), LocalDate.now()));
        events.cuenta("ACCOUNT_OPENED", nueva, "Apertura de cuenta " + req.tipo());
        log.info("Cuenta {} abierta para el cliente {}", nueva.getId(), cliente.id());
        return nueva;
    }

    /** MANTENIMIENTO: cambio de producto. */
    @Transactional
    public Cuenta actualizarTipo(Long id, String tipo) {
        Cuenta c = obtener(id);
        exigirNoCerrada(c);
        c.cambiarTipo(tipo);
        events.cuenta("ACCOUNT_UPDATED", c, "Cambio de tipo a " + tipo);
        return c;
    }

    /** MANTENIMIENTO: bloqueo/desbloqueo. Un bloqueo genera una alerta de seguridad. */
    @Transactional
    public Cuenta cambiarEstado(Long id, Cuenta.Estado estado, String motivo) {
        if (estado == Cuenta.Estado.CERRADA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Para cerrar use DELETE /accounts/{id}");
        }
        Cuenta c = obtener(id);
        exigirNoCerrada(c);
        c.cambiarEstado(estado);
        if (estado == Cuenta.Estado.BLOQUEADA) {
            events.alerta("CUENTA_BLOQUEADA", "ALTA", c, motivo == null ? "Bloqueo preventivo" : motivo);
        }
        events.cuenta("ACCOUNT_" + estado.name(), c, motivo);
        return c;
    }

    /** CIERRE: solo con saldo cero (regla bancaria). */
    @Transactional
    public Cuenta cerrar(Long id) {
        Cuenta c = obtener(id);
        exigirNoCerrada(c);
        if (c.getSaldo().signum() != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede cerrar la cuenta: el saldo debe ser 0 (saldo actual " + c.getSaldo() + ")");
        }
        c.cerrar();
        events.cuenta("ACCOUNT_CLOSED", c, "Cierre de cuenta");
        return c;
    }

    /**
     * Aplica un debito o credito de forma ATOMICA e IDEMPOTENTE:
     * <ul>
     *   <li>SELECT ... FOR UPDATE sobre la cuenta (dos retiros simultaneos no pueden sobregirarla)</li>
     *   <li>el operacionId se registra en la misma transaccion; si llega repetido se devuelve el resultado original</li>
     * </ul>
     */
    @Transactional
    public MovimientoSaldoResponse aplicarMovimiento(Long id, MovimientoSaldoRequest req) {
        Optional<OperacionAplicada> previa = operaciones.findById(req.operacionId());
        if (previa.isPresent()) {
            OperacionAplicada p = previa.get();
            return new MovimientoSaldoResponse(p.getOperacionId(), p.getCuentaId(), p.getTipo(), p.getMonto(),
                    p.getSaldoResultante(), true);
        }
        Cuenta c = cuentas.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta " + id + " no existe"));
        if (c.getEstado() != Cuenta.Estado.ACTIVA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuenta " + id + " esta " + c.getEstado());
        }
        if ("DEBITO".equals(req.tipo())) {
            if (c.getSaldo().compareTo(req.monto()) < 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Saldo insuficiente");
            }
            c.debitar(req.monto());
        } else {
            c.acreditar(req.monto());
        }
        operaciones.save(new OperacionAplicada(req.operacionId(), id, req.tipo(), req.monto(), c.getSaldo()));
        return new MovimientoSaldoResponse(req.operacionId(), id, req.tipo(), req.monto(), c.getSaldo(), false);
    }

    private static void exigirNoCerrada(Cuenta c) {
        if (c.getEstado() == Cuenta.Estado.CERRADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuenta " + c.getId() + " esta cerrada");
        }
    }
}
