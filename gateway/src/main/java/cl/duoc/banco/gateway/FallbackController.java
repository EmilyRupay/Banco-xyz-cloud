package cl.duoc.banco.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** Respuesta alternativa del Gateway cuando un BFF/servicio completo no esta disponible (CircuitBreaker abierto). */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{servicio}")
    public ResponseEntity<Map<String, Object>> fallback(@PathVariable String servicio) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "Servicio temporalmente no disponible",
                "servicio", servicio,
                "sugerencia", "Intente nuevamente en unos segundos",
                "timestamp", Instant.now().toString()));
    }
}
