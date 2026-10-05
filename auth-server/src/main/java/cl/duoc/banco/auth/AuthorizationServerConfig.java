package cl.duoc.banco.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

/**
 * Servidor de autorizacion OAuth 2.0 / OpenID Connect (Spring Authorization Server).
 *
 * Emite tokens JWT firmados con RSA. Los microservicios (Resource Servers) validan la firma
 * consultando el endpoint JWKS: /oauth2/jwks
 */
@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    /** Cadena 1: endpoints del protocolo OAuth2/OIDC (/oauth2/token, /oauth2/authorize, /oauth2/jwks...). */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults());
        http.exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** Cadena 2: login de usuarios (formulario) para el flujo authorization_code. */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder(); // bcrypt por defecto
    }

    /** Usuarios de ejemplo (solo para el flujo authorization_code). En produccion: BD o LDAP. */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails ejecutivo = User.withUsername("ejecutivo")
                .password(encoder.encode("ejecutivo123"))
                .roles("EJECUTIVO")
                .build();
        UserDetails cliente = User.withUsername("cliente")
                .password(encoder.encode("cliente123"))
                .roles("CLIENTE")
                .build();
        return new InMemoryUserDetailsManager(ejecutivo, cliente);
    }

    /**
     * Clientes OAuth2 registrados.
     *  - web-client / mobile-client : aplicaciones frontend (client_credentials), cada una con su scope de canal.
     *  - admin-client               : acceso completo (lectura y escritura).
     *  - postman-client             : flujo authorization_code con usuario (para probar con Postman).
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(PasswordEncoder encoder) {
        TokenSettings tokens = TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(30))
                .build();

        RegisteredClient web = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("web-client")
                .clientSecret(encoder.encode("web-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("web").scope("bank.read")
                .tokenSettings(tokens)
                .build();

        RegisteredClient mobile = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("mobile-client")
                .clientSecret(encoder.encode("mobile-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("mobile").scope("bank.read")
                .tokenSettings(tokens)
                .build();

        RegisteredClient admin = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("admin-client")
                .clientSecret(encoder.encode("admin-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("web").scope("mobile").scope("bank.read").scope("bank.write")
                .tokenSettings(tokens)
                .build();

        RegisteredClient postman = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("postman-client")
                .clientSecret(encoder.encode("postman-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("https://oauth.pstmn.io/v1/callback")
                .scope(OidcScopes.OPENID)
                .scope("web").scope("mobile").scope("bank.read").scope("bank.write")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(tokens)
                .build();

        return new InMemoryRegisteredClientRepository(web, mobile, admin, postman);
    }

    /** Llave RSA generada al iniciar. Si el servidor se reinicia, los tokens anteriores dejan de ser validos. */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("No fue posible generar el par de llaves RSA", ex);
        }
    }
}
