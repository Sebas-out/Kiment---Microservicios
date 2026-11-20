package com.kiments.cataloginventory.ports.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.kiments.cataloginventory.config.AuthClientProperties;
import com.kiments.cataloginventory.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class AuthClientAdapter implements AuthClientPort {

    private static final Logger log = LoggerFactory.getLogger(AuthClientAdapter.class);

    private final RestClient restClient;
    private final AuthClientProperties properties;

    public AuthClientAdapter(AuthClientProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public AuthUserProfile validateSession(String bearerToken) {
        if (!properties.isValidateSessionRemotely()) {
            return null;
        }

        try {
            JsonNode root = restClient.get()
                    .uri(properties.getMeUri())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                            throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "Sesión revocada o credenciales inválidas en Auth");
                        }
                        if (res.getStatusCode() == HttpStatus.FORBIDDEN) {
                            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Acceso denegado por el servicio de autenticación");
                        }
                        throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REJECTED", "La autenticación fue rechazada por Auth");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SERVICE_UNAVAILABLE", "El servicio de autenticación falló internamente");
                    })
                    .body(JsonNode.class);

            if (root == null || !root.has("user")) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "Respuesta de sesión inválida");
            }

            JsonNode userNode = root.get("user");
            UUID userId = UUID.fromString(userNode.get("id").asText());
            String firstName = userNode.path("firstName").asText("");
            String lastName = userNode.path("lastName").asText("");
            String email = userNode.path("email").asText("");
            String role = userNode.path("role").asText("");
            boolean enabled = userNode.path("enabled").asBoolean(true);
            UUID branchId = userNode.hasNonNull("branchId") ? UUID.fromString(userNode.get("branchId").asText()) : null;

            List<String> permissions = new ArrayList<>();
            if (root.has("permissions") && root.get("permissions").isArray()) {
                root.get("permissions").forEach(p -> permissions.add(p.asText()));
            }

            return new AuthUserProfile(userId, firstName, lastName, email, role, enabled, branchId, permissions);

        } catch (ResourceAccessException e) {
            log.error("Imposible contactar al servicio de Auth en {}: {}", properties.getMeUri(), e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SERVICE_UNAVAILABLE",
                    "Servicio de autenticación no disponible temporalmente. Intente nuevamente en unos instantes.");
        } catch (RestClientResponseException e) {
            log.warn("Error HTTP de Auth: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "Sesión revocada o credenciales inválidas");
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SERVICE_UNAVAILABLE",
                    "Servicio de autenticación no disponible temporalmente");
        }
    }
}
