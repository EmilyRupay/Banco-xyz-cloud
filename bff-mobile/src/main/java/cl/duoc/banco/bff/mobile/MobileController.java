package cl.duoc.banco.bff.mobile;

import cl.duoc.banco.bff.mobile.Dtos.CuentaDto;
import cl.duoc.banco.bff.mobile.Dtos.CuentaItem;
import cl.duoc.banco.bff.mobile.Dtos.MobileSummary;
import cl.duoc.banco.bff.mobile.Dtos.MovimientoDto;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * BFF MOVIL. Optimizaciones de ancho de banda:
 * <ul>
 *   <li>solo campos esenciales (sin edad, descripcion, ids internos...) y los null se omiten</li>
 *   <li>como maximo 5 movimientos, filtrados en el servidor de origen</li>
 *   <li>compresion gzip y ETag: si los datos no cambiaron el servidor responde 304 sin cuerpo</li>
 *   <li>Cache-Control privado de 15 s para evitar llamadas repetidas al refrescar la pantalla</li>
 * </ul>
 */
@RestController
@RequestMapping("/mobile")
public class MobileController {

    private static final int MAX_MOVIMIENTOS = 5;
    private static final CacheControl CACHE = CacheControl.maxAge(15, TimeUnit.SECONDS).cachePrivate();

    private final MobileClients clients;

    public MobileController(MobileClients clients) {
        this.clients = clients;
    }

    @GetMapping("/accounts/{id}/summary")
    public ResponseEntity<MobileSummary> resumen(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        ClientResult<CuentaDto> cuenta = clients.cuenta(id, jwt.getTokenValue());
        if (!cuenta.degraded() && cuenta.value() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no existe");
        }
        ClientResult<List<MovimientoDto>> movs = clients.ultimosMovimientos(id, MAX_MOVIMIENTOS, jwt.getTokenValue());
        CuentaDto c = cuenta.value();
        boolean degradado = cuenta.degraded() || movs.degraded();
        MobileSummary body = new MobileSummary(c == null ? null : c.nombre(), c == null ? null : c.saldo(),
                movs.value(), degradado ? Boolean.TRUE : null);
        // una respuesta degradada no se cachea
        return ResponseEntity.ok().cacheControl(degradado ? CacheControl.noStore() : CACHE).body(body);
    }

    @GetMapping("/customers/{clienteId}/accounts")
    public ResponseEntity<List<CuentaItem>> cuentas(@PathVariable Long clienteId, @AuthenticationPrincipal Jwt jwt) {
        ClientResult<List<CuentaDto>> cuentas = clients.cuentasCliente(clienteId, jwt.getTokenValue());
        if (cuentas.degraded()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Intenta nuevamente en unos segundos");
        }
        List<CuentaItem> items = cuentas.value().stream()
                .filter(c -> !"CERRADA".equals(c.estado()))
                .map(c -> new CuentaItem(c.id(), c.tipo(), c.saldo()))
                .toList();
        return ResponseEntity.ok().cacheControl(CACHE).body(items);
    }
}
