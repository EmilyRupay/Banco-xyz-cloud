package cl.duoc.banco.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Primera linea de defensa: el Gateway rechaza (401/403) toda peticion sin un JWT valido o sin el scope del canal,
 * antes de que llegue a los BFF o microservicios. Cada servicio vuelve a validar el token (defensa en profundidad).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        // emision de tokens y llaves publicas (el auth-server aplica su propia autenticacion de cliente)
                        .pathMatchers("/oauth2/token", "/oauth2/jwks", "/.well-known/**").permitAll()
                        .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/fallback/**").permitAll()
                        // --- Canales BFF: cada uno exige su scope ---
                        .pathMatchers("/web/**").hasAuthority("SCOPE_web")
                        .pathMatchers("/mobile/**").hasAuthority("SCOPE_mobile")
                        .pathMatchers("/atm/**").hasAuthority("SCOPE_atm")
                        // --- API interna / backoffice ---
                        .pathMatchers(HttpMethod.POST, "/api/batch/**").hasAuthority("SCOPE_batch.admin")
                        .pathMatchers(HttpMethod.GET, "/api/**").hasAuthority("SCOPE_bank.read")
                        .pathMatchers(HttpMethod.POST, "/api/**").hasAuthority("SCOPE_bank.write")
                        .pathMatchers(HttpMethod.PUT, "/api/**").hasAuthority("SCOPE_bank.write")
                        .pathMatchers(HttpMethod.PATCH, "/api/**").hasAuthority("SCOPE_bank.write")
                        .pathMatchers(HttpMethod.DELETE, "/api/**").hasAuthority("SCOPE_bank.write")
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
