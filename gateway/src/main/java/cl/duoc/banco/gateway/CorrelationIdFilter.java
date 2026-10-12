package cl.duoc.banco.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Agrega un X-Correlation-Id a cada peticion (o respeta el que envia el cliente), lo propaga a los servicios y lo
 * devuelve en la respuesta. Ademas registra metodo, ruta, estado y duracion: base para monitoreo y auditoria.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger("ACCESO");
    public static final String HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String id = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        ServerHttpRequest request = exchange.getRequest().mutate().header(HEADER, correlationId).build();
        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        long inicio = System.currentTimeMillis();
        return chain.filter(exchange.mutate().request(request).build())
                .doFinally(signal -> log.info("{} {} {} -> {} ({} ms)", correlationId, request.getMethod(),
                        request.getPath().value(), exchange.getResponse().getStatusCode(),
                        System.currentTimeMillis() - inicio));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
