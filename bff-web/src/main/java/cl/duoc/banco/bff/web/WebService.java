package cl.duoc.banco.bff.web;

import cl.duoc.banco.bff.web.Dtos.ClienteDto;
import cl.duoc.banco.bff.web.Dtos.CuentaConMovimientos;
import cl.duoc.banco.bff.web.Dtos.CuentaDto;
import cl.duoc.banco.bff.web.Dtos.Dashboard;
import cl.duoc.banco.bff.web.Dtos.MovimientoDto;
import cl.duoc.banco.bff.web.Dtos.WebSummary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Orquesta (agrega) varios microservicios y arma respuestas COMPLETAS para el navegador. */
@Service
public class WebService {

    private static final Set<String> INGRESOS = Set.of("deposito", "transferencia_recibida");
    private static final int MOVIMIENTOS_DASHBOARD = 10;

    private final AccountsClient accounts;
    private final PaymentsClient payments;
    private final CustomersClient customers;

    public WebService(AccountsClient accounts, PaymentsClient payments, CustomersClient customers) {
        this.accounts = accounts;
        this.payments = payments;
        this.customers = customers;
    }

    public WebSummary resumenCuenta(Long cuentaId, String token) {
        ClientResult<CuentaDto> cuenta = accounts.findById(cuentaId, token);
        if (!cuenta.degraded() && cuenta.value() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta " + cuentaId + " no existe");
        }
        ClientResult<List<MovimientoDto>> movs = payments.findByAccount(cuentaId, null, token);
        List<MovimientoDto> lista = movs.value();

        List<String> advertencias = new ArrayList<>();
        if (cuenta.degraded()) advertencias.add("El servicio de cuentas no esta disponible: datos de la cuenta omitidos");
        if (movs.degraded()) advertencias.add("El servicio de pagos no esta disponible: historial omitido");

        return new WebSummary(cuenta.value(), lista, sumar(lista, true), sumar(lista, false),
                cuenta.degraded() || movs.degraded(), advertencias);
    }

    public Dashboard dashboard(Long clienteId, String token) {
        ClientResult<ClienteDto> cliente = customers.findById(clienteId, token);
        if (!cliente.degraded() && cliente.value() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente " + clienteId + " no existe");
        }
        ClientResult<List<CuentaDto>> cuentas = accounts.findByCustomer(clienteId, token);
        List<String> advertencias = new ArrayList<>();
        boolean degradado = cliente.degraded() || cuentas.degraded();
        if (cliente.degraded()) advertencias.add("Perfil del cliente no disponible temporalmente");
        if (cuentas.degraded()) advertencias.add("Cuentas no disponibles temporalmente");

        List<CuentaConMovimientos> detalle = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CuentaDto c : cuentas.value()) {
            ClientResult<List<MovimientoDto>> movs = payments.findByAccount(c.id(), MOVIMIENTOS_DASHBOARD, token);
            if (movs.degraded() && !advertencias.contains("Movimientos no disponibles temporalmente")) {
                advertencias.add("Movimientos no disponibles temporalmente");
                degradado = true;
            }
            detalle.add(new CuentaConMovimientos(c, movs.value()));
            if ("ahorro".equals(c.tipo()) || "corriente".equals(c.tipo())) {
                total = total.add(c.saldo());
            }
        }
        return new Dashboard(cliente.value(), detalle, total, detalle.size(), degradado, advertencias);
    }

    private static BigDecimal sumar(List<MovimientoDto> movimientos, boolean ingresos) {
        return movimientos.stream()
                .filter(m -> INGRESOS.contains(m.tipo()) == ingresos)
                .map(MovimientoDto::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
