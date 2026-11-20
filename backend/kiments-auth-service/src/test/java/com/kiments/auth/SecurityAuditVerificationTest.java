package com.kiments.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named = "STAGE4_TEST_DB_URL", matches = ".+")
class SecurityAuditVerificationTest extends AuthIntegrationScenarios {

  private static final Path tempStoragePath;

  static {
    try {
      tempStoragePath = Files.createTempDirectory("kiments_audit_test_");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @DynamicPropertySource
  static void isolatedDatabase(DynamicPropertyRegistry registry) {
    String url = System.getenv("STAGE4_TEST_DB_URL");
    if (!url.matches("jdbc:postgresql://[^/]+/[A-Za-z0-9_]+_test")) {
      throw new IllegalArgumentException(
          "STAGE4_TEST_DB_URL debe apuntar a una base exclusiva terminada en _test");
    }
    registry.add("spring.datasource.url", () -> url);
    registry.add(
        "spring.datasource.username",
        () ->
            System.getenv("STAGE4_TEST_DB_USERNAME") != null
                ? System.getenv("STAGE4_TEST_DB_USERNAME")
                : "kiments_auth");
    registry.add(
        "spring.datasource.password",
        () ->
            System.getenv("STAGE4_TEST_DB_PASSWORD") != null
                ? System.getenv("STAGE4_TEST_DB_PASSWORD")
                : "admin");
    registry.add("app.storage.profiles.path", () -> tempStoragePath.toAbsolutePath().toString());
  }

  @AfterAll
  static void cleanupStorage() {
    try {
      if (Files.exists(tempStoragePath)) {
        try (var stream = Files.walk(tempStoragePath)) {
          stream
              .sorted(Comparator.reverseOrder())
              .forEach(
                  p -> {
                    try {
                      Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                  });
        }
      }
    } catch (Exception ignored) {
    }
  }

  @Test
  void singleActiveSession_edgeLoginImmediatelyInvalidatesChromeAccessTokenAndRefresh()
      throws Exception {
    String email = "single_session_" + System.currentTimeMillis() + "@test.example";
    seedUser("ADMIN", email, "99112233", true);

    // 1. Iniciar sesión desde Chrome
    var sessionChrome = login(email, PASSWORD);
    String tokenChrome = sessionChrome.get("accessToken").asText();
    String refreshChrome = sessionChrome.get("refreshToken").asText();

    // Chrome puede acceder inmediatamente
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokenChrome))
        .andExpect(status().isOk());

    // 2. Iniciar sesión desde Edge para el mismo usuario
    var sessionEdge = login(email, PASSWORD);
    String tokenEdge = sessionEdge.get("accessToken").asText();
    String refreshEdge = sessionEdge.get("refreshToken").asText();

    assertThat(refreshChrome).isNotEqualTo(refreshEdge);
    assertThat(tokenChrome).isNotEqualTo(tokenEdge);

    // 3. El token de acceso previo de Chrome queda INMEDIATAMENTE INVALIDADO (401)
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokenChrome))
        .andExpect(status().isUnauthorized());

    // 4. El refresh token de Chrome queda INMEDIATAMENTE INVALIDADO (401)
    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshChrome + "\"}"))
        .andExpect(status().isUnauthorized());

    // 5. La sesión de Edge está ACTIVA y funciona tanto para acceso como para refresh
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokenEdge))
        .andExpect(status().isOk());

    var refreshResponseEdge =
        mvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"" + refreshEdge + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode newEdge = json.readTree(refreshResponseEdge.getResponse().getContentAsString());
    assertThat(newEdge.get("accessToken").asText()).isNotEmpty();
  }

  @Test
  void singleActiveSession_logoutImmediatelyInvalidatesAccessTokenAndRefreshToken()
      throws Exception {
    String email = "logout_single_" + System.currentTimeMillis() + "@test.example";
    seedUser("ADMIN", email, "99112234", true);

    var session = login(email, PASSWORD);
    String token = session.get("accessToken").asText();
    String refreshToken = session.get("refreshToken").asText();

    // Token funciona antes del logout
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // Logout
    mvc.perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isNoContent());

    // Token de acceso queda invalidado inmediatamente (401)
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());

    // Refresh token queda invalidado (401)
    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void singleActiveSession_changePasswordImmediatelyInvalidatesAccessTokenAndAllSessions()
      throws Exception {
    String email = "pwd_single_" + System.currentTimeMillis() + "@test.example";
    seedUser("ADMIN", email, "99112235", true);

    var session = login(email, PASSWORD);
    String token = session.get("accessToken").asText();
    String refreshToken = session.get("refreshToken").asText();

    // Cambiar contraseña
    String newPassword = REPLACEMENT_PASSWORD;
    mvc.perform(
            post("/api/v1/auth/change-password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\""
                        + PASSWORD
                        + "\",\"newPassword\":\""
                        + newPassword
                        + "\"}"))
        .andExpect(status().isNoContent());

    // Token anterior de acceso queda invalidado de inmediato (401)
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());

    // Refresh token queda invalidado (401)
    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isUnauthorized());
  }
}
