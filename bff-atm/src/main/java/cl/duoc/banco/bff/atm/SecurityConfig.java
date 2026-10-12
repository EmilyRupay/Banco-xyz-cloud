package cl.duoc.banco.bff.atm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;

/**
 * Seguridad del canal ATM (la mas estricta):
 * token OAuth2 con scope "atm" + terminal registrado (X-ATM-ID) + retiros con scope bank.write.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AtmProperties props) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/atm/**").hasAuthority("SCOPE_atm")
                        .requestMatchers(HttpMethod.POST, "/atm/**").access(
                                new WebExpressionAuthorizationManager("hasAuthority('SCOPE_atm') and hasAuthority('SCOPE_bank.write')"))
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                // el terminal se valida despues de autenticar el token
                .addFilterAfter(new AtmTerminalFilter(props), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
