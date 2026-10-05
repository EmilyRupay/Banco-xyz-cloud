package cl.duoc.banco.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Primera linea de defensa: el Gateway rechaza (401/403) toda peticion sin un JWT valido
 * o sin el scope requerido, antes de que llegue a los microservicios.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers("/actuator/health/**").permitAll()
                        .pathMatchers("/web/**").hasAuthority("SCOPE_web")
                        .pathMatchers("/mobile/**").hasAuthority("SCOPE_mobile")
                        .pathMatchers(HttpMethod.POST, "/api/transactions/**").hasAuthority("SCOPE_bank.write")
                        .pathMatchers(HttpMethod.GET, "/api/**").hasAuthority("SCOPE_bank.read")
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
