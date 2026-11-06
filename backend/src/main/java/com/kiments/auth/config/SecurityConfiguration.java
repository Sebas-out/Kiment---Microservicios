package com.kiments.auth.config;

import com.kiments.auth.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      DatabaseJwtAuthenticationConverter converter,
      SecurityProblemWriter problems)
      throws Exception {
    return http.csrf(csrf -> csrf.disable()) // API solo Bearer; no autentica mediante cookies.
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(c -> c.disable())
        .formLogin(f -> f.disable())
        .httpBasic(b -> b.disable())
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        HttpMethod.GET,
                        "/actuator/health",
                        "/api/v1/auth/jwks",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/auth/me",
                        "/api/v1/auth/change-password",
                        "/api/v1/users/**",
                        "/api/v1/roles/**",
                        "/api/v1/branches/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((q, r, x) -> problems.write(r, HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler((q, r, x) -> problems.write(r, HttpStatus.FORBIDDEN)))
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> j.jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint(
                        (q, r, x) -> problems.write(r, HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler((q, r, x) -> problems.write(r, HttpStatus.FORBIDDEN)))
        .build();
  }

  @Bean
  CorsConfigurationSource cors(@Value("${app.cors-origins:}") String origins) {
    var config = new CorsConfiguration();
    var allowed =
        Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    if (allowed.stream().anyMatch(s -> s.contains("*")))
      throw new IllegalStateException("CORS requiere orígenes explícitos");
    config.setAllowedOrigins(allowed);
    config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(false);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
