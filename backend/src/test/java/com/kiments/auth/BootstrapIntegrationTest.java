package com.kiments.auth;

import static org.assertj.core.api.Assertions.*;

import com.kiments.auth.config.bootstrap.*;
import com.kiments.auth.users.UserRepository;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;

/**
 * Exclusivamente una base PostgreSQL aislada cuyo nombre empieza kiments_bootstrap_ y termina
 * _test. Los fixtures son ADMIN sintéticos; nunca se conecta a kiments_db.
 */
@EnabledIfEnvironmentVariable(named = "STAGE2_TEST_DB_URL", matches = ".+")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.bootstrap.admin.enabled=false", "app.bootstrap.admin.manual-enabled=false"})
class BootstrapIntegrationTest extends AuthIntegrationScenariosKeys {
  @Autowired BootstrapAdminService bootstrap;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired ConfigurableApplicationContext context;
  Map<String, Object> settings;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    String url = System.getenv("STAGE2_TEST_DB_URL");
    if (!url.matches(
        "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/kiments_bootstrap_[A-Za-z0-9_]+_test"))
      throw new IllegalArgumentException(
          "Se requiere la base aislada de bootstrap, nunca kiments_db");
    r.add("spring.datasource.url", () -> url);
    r.add("spring.datasource.username", () -> "kiments_test");
    r.add("spring.datasource.password", () -> "");
  }

  @BeforeEach
  void freshInstall() {
    jdbc.execute("TRUNCATE auth.refresh_sessions,auth.users,auth.admin_initialization");
    jdbc.update("INSERT INTO auth.admin_initialization(id,status) VALUES(1,'PENDING')");
    context.getEnvironment().setActiveProfiles("dev");
    settings = new HashMap<>();
    settings.put("app.bootstrap.admin.enabled", "true");
    settings.put("APP_BOOTSTRAP_ADMIN_ENABLED", "true");
    settings.put("app.bootstrap.admin.manual-enabled", "false");
    settings.put("APP_BOOTSTRAP_ADMIN_FIRST_NAME", "Test");
    settings.put("APP_BOOTSTRAP_ADMIN_LAST_NAME", "Administrator");
    settings.put("APP_BOOTSTRAP_ADMIN_DNI", "90000001");
    settings.put("APP_BOOTSTRAP_ADMIN_EMAIL", "bootstrap@example.test");
    settings.put("APP_BOOTSTRAP_ADMIN_PASSWORD", "Isolated-fixture-password-2026");
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(new MapPropertySource("bootstrapTest", settings));
  }

  @AfterEach
  void cleanupEnvironment() {
    context.getEnvironment().getPropertySources().remove("bootstrapTest");
    context.getEnvironment().setActiveProfiles();
  }

  @Autowired org.springframework.boot.test.web.client.TestRestTemplate http;

  @Test
  void bootstrappedAdminCanLoginAndUseProtectedProfile() {
    bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV);
    var login =
        http.postForEntity(
            "/api/v1/auth/login",
            Map.of("email", "bootstrap@example.test", "password", "Isolated-fixture-password-2026"),
            com.fasterxml.jackson.databind.JsonNode.class);
    assertThat(login.getStatusCode().value()).isEqualTo(200);
    var token = login.getBody().path("accessToken").asText();
    assertThat(token).isNotBlank();
    assertThat(login.getBody().has("passwordHash")).isFalse();
    var headers = new org.springframework.http.HttpHeaders();
    headers.setBearerAuth(token);
    var me =
        http.exchange(
            "/api/v1/auth/me",
            org.springframework.http.HttpMethod.GET,
            new org.springframework.http.HttpEntity<>(headers),
            com.fasterxml.jackson.databind.JsonNode.class);
    assertThat(me.getStatusCode().value()).isEqualTo(200);
    assertThat(me.getBody().path("user").path("role").asText()).isEqualTo("ADMIN");
    assertThat(http.getForEntity("/api/v1/auth/me", String.class).getStatusCode().value())
        .isEqualTo(401);
    headers.setBearerAuth("invalid-token");
    assertThat(
            http.exchange(
                    "/api/v1/auth/me",
                    org.springframework.http.HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(headers),
                    String.class)
                .getStatusCode()
                .value())
        .isEqualTo(401);
    for (String email : java.util.List.of("bootstrap@example.test", "missing@example.test")) {
      assertThat(
              http.postForEntity(
                      "/api/v1/auth/login",
                      Map.of("email", email, "password", "Incorrect-fixture-password"),
                      String.class)
                  .getStatusCode()
                  .value())
          .isEqualTo(401);
    }
  }

  @Test
  void initializesEmptyDevelopmentDatabase() {
    assertThat(bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV))
        .isEqualTo(BootstrapAdminService.Outcome.CREATED);
    var user = users.findByEmail("bootstrap@example.test").orElseThrow();
    assertThat(user.isEnabled()).isTrue();
    assertThat(user.getPasswordHash()).startsWith("$argon2id$");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM auth.admin_initialization WHERE id=1", String.class))
        .isEqualTo("INITIALIZED");
    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void disabledFlagDoesNothing() {
    settings.put("app.bootstrap.admin.enabled", "false");
    assertThat(bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV))
        .isEqualTo(BootstrapAdminService.Outcome.DISABLED);
    assertThat(users.count()).isZero();
  }

  @Test
  void explicitEnvironmentAuthorizationIsRequired() {
    settings.remove("APP_BOOTSTRAP_ADMIN_ENABLED");
    assertThat(bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV))
        .isEqualTo(BootstrapAdminService.Outcome.DISABLED);
    assertThat(users.count()).isZero();
  }

  @Test
  void rejectsProductionAndMixedProfiles() {
    for (String[] profiles :
        List.of(new String[] {"prod"}, new String[] {"dev", "prod"}, new String[] {"default"})) {
      context.getEnvironment().setActiveProfiles(profiles);
      assertThatThrownBy(() -> bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV))
          .isInstanceOf(IllegalStateException.class);
      assertThat(users.count()).isZero();
    }
  }

  @Test
  void missingOrInvalidDataRollsBackWithoutMarkingInitialized() {
    settings.remove("APP_BOOTSTRAP_ADMIN_FIRST_NAME");
    assertThatThrownBy(() -> bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV))
        .isInstanceOf(IllegalStateException.class);
    settings.put("APP_BOOTSTRAP_ADMIN_FIRST_NAME", "Test");
    settings.put("APP_BOOTSTRAP_ADMIN_PASSWORD", "short");
    assertThatThrownBy(() -> bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV))
        .isInstanceOf(IllegalStateException.class);
    assertThat(users.count()).isZero();
    assertThat(jdbc.queryForObject("SELECT status FROM auth.admin_initialization", String.class))
        .isEqualTo("PENDING");
  }

  @Test
  void repeatedInitializationPreservesPasswordAndNeedsNoCredentials() {
    bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV);
    var hash = users.findByEmail("bootstrap@example.test").orElseThrow().getPasswordHash();
    settings.remove("APP_BOOTSTRAP_ADMIN_PASSWORD");
    assertThat(bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV))
        .isEqualTo(BootstrapAdminService.Outcome.ALREADY_RECORDED);
    assertThat(users.count()).isEqualTo(1);
    assertThat(users.findByEmail("bootstrap@example.test").orElseThrow().getPasswordHash())
        .isEqualTo(hash);
  }

  @Test
  void neverReactivatesDisabledAdmin() {
    bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV);
    jdbc.update("UPDATE auth.users SET enabled=false");
    bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV);
    assertThat(users.findByEmail("bootstrap@example.test").orElseThrow().isEnabled()).isFalse();
    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void deletedAdminStillLeavesPersistentEvidence() {
    bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV);
    jdbc.update("DELETE FROM auth.users");
    assertThat(bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV))
        .isEqualTo(BootstrapAdminService.Outcome.ALREADY_RECORDED);
    assertThat(users.count()).isZero();
  }

  @Test
  void existingImportedUsersArePreservedAndCloseInitialization() {
    bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV);
    var original = users.findByEmail("bootstrap@example.test").orElseThrow();
    jdbc.update(
        "UPDATE auth.admin_initialization SET"
            + " status='PENDING',recorded_at=NULL,initial_user_id=NULL,source=NULL");
    settings.put("APP_BOOTSTRAP_ADMIN_EMAIL", "other@example.test");
    assertThat(bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV))
        .isEqualTo(BootstrapAdminService.Outcome.EXISTING_DATA);
    assertThat(users.count()).isEqualTo(1);
    assertThat(users.findByEmail("bootstrap@example.test").orElseThrow().getPasswordHash())
        .isEqualTo(original.getPasswordHash());
    jdbc.update("DELETE FROM auth.users");
    assertThat(bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV))
        .isEqualTo(BootstrapAdminService.Outcome.ALREADY_RECORDED);
  }

  @Test
  void concurrentInitializationCreatesExactlyOneAdmin() throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<BootstrapAdminService.Outcome> operation =
          () -> {
            start.await();
            return bootstrap.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV);
          };
      var a = pool.submit(operation);
      var b = pool.submit(operation);
      start.countDown();
      assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(
              BootstrapAdminService.Outcome.CREATED,
              BootstrapAdminService.Outcome.ALREADY_RECORDED);
    }
    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void manualProductionProcedureCannotRunInHttpServer() {
    context.getEnvironment().setActiveProfiles("prod");
    settings.put("app.bootstrap.admin.manual-enabled", "true");
    assertThatThrownBy(() -> bootstrap.initialize(BootstrapAdminService.Mode.MANUAL))
        .isInstanceOf(IllegalStateException.class);
    assertThat(users.count()).isZero();
  }
}
