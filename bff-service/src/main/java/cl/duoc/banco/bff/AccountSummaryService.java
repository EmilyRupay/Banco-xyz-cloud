package cl.duoc.banco.bff;

import cl.duoc.banco.bff.Dtos.CuentaDto;
import cl.duoc.banco.bff.Dtos.MobileSummary;
import cl.duoc.banco.bff.Dtos.MovimientoDto;
import cl.duoc.banco.bff.Dtos.MovimientoResumen;
import cl.duoc.banco.bff.Dtos.WebSummary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Orquesta (agrega) las respuestas de varios microservicios y las adapta a cada canal. */
@Service
public class AccountSummaryService {

    private static final int MOBILE_MAX_MOVIMIENTOS = 5;

    private final AccountsClient accounts;
    private final TransactionsClient transactions;

    public AccountSummaryService(AccountsClient accounts, TransactionsClient transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    public WebSummary webSummary(Long cuentaId, String token) {
        ClientResult<CuentaDto> cuenta = accounts.findById(cuentaId, token);
        ensureExists(cuenta, cuentaId);
        ClientResult<List<MovimientoDto>> movs = transactions.findByAccount(cuentaId, token);

        List<MovimientoDto> lista = movs.value() == null ? List.of() : movs.value();
        BigDecimal ingresos = sum(lista, true);
        BigDecimal egresos = sum(lista, false);

        List<String> advertencias = new ArrayList<>();
        if (cuenta.degraded()) advertencias.add("El servicio de cuentas no esta disponible: datos de la cuenta omitidos");
        if (movs.degraded()) advertencias.add("El servicio de movimientos no esta disponible: historial omitido");

        return new WebSummary(cuenta.value(), lista, ingresos, egresos,
                cuenta.degraded() || movs.degraded(), advertencias);
    }

    public MobileSummary mobileSummary(Long cuentaId, String token) {
        ClientResult<CuentaDto> cuenta = accounts.findById(cuentaId, token);
        ensureExists(cuenta, cuentaId);
        ClientResult<List<MovimientoDto>> movs = transactions.findByAccount(cuentaId, token);

        List<MovimientoResumen> ultimos = (movs.value() == null ? List.<MovimientoDto>of() : movs.value()).stream()
                .sorted(Comparator.comparing(MovimientoDto::fecha).reversed())
                .limit(MOBILE_MAX_MOVIMIENTOS)
                .map(m -> new MovimientoResumen(m.fecha(), m.tipo(), m.monto()))
                .toList();

        CuentaDto c = cuenta.value();
        return new MobileSummary(c == null ? null : c.nombre(), c == null ? null : c.saldo(), ultimos,
                cuenta.degraded() || movs.degraded());
    }

    private void ensureExists(ClientResult<CuentaDto> cuenta, Long id) {
        if (!cuenta.degraded() && cuenta.value() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta " + id + " no existe");
        }
    }

    private BigDecimal sum(List<MovimientoDto> movimientos, boolean ingresos) {
        return movimientos.stream()
                .filter(m -> "deposito".equals(m.tipo()) == ingresos)
                .map(MovimientoDto::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
