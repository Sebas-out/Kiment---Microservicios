package com.kiments.cataloginventory.config;

import com.kiments.cataloginventory.security.CatalogJwtAuthenticationConverter;
import com.kiments.cataloginventory.security.SecurityProblemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CatalogJwtAuthenticationConverter converter,
            SecurityProblemWriter problemWriter) throws Exception {

        return http
                // Justificación CSRF: La API autentica exclusivamente mediante tokens Bearer en la cabecera Authorization.
                // No utiliza cookies de sesión en el navegador para autenticar peticiones directas, por lo que no es susceptible a CSRF.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                problemWriter.write(req, res, HttpStatus.UNAUTHORIZED, "Autenticación requerida o token inválido"))
                        .accessDeniedHandler((req, res, ex) ->
                                problemWriter.write(req, res, HttpStatus.FORBIDDEN, "Operación no autorizada para el rol o sucursal actual"))
                )
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, ex) ->
                                problemWriter.write(req, res, HttpStatus.UNAUTHORIZED, "Token inválido, expirado o con firma no reconocida"))
                        .accessDeniedHandler((req, res, ex) ->
                                problemWriter.write(req, res, HttpStatus.FORBIDDEN, "Operación no autorizada"))
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origins:http://localhost:3000}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (allowedOrigins.stream().anyMatch(s -> s.contains("*"))) {
            throw new IllegalStateException("CORS requiere orígenes explícitos sin comodines cuando se manejan datos autorizados");
        }

        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "Content-Disposition"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
