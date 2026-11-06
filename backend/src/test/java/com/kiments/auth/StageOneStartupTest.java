package com.kiments.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Comprobaciones de etapa 1: no crean usuarios, contraseñas ni sesiones POS. Solo se habilitan
 * explícitamente contra la base local preparada para SVC-09.
 */
@EnabledIfEnvironmentVariable(named = "KIMENTS_STAGE1_TESTS", matches = "true")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.bootstrap.enabled=false"})
class StageOneStartupTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired Flyway flyway;
  @Autowired TestRestTemplate http;

  @Test
  void connectsToPreparedLocalDatabaseWithDedicatedRole() {
    assertThat(jdbc.queryForObject("select current_database()", String.class))
        .isEqualTo("kiments_db");
    assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("kiments_auth");
    assertThat(jdbc.queryForObject("select count(*) from auth.roles", Long.class)).isEqualTo(4L);
    assertThat(jdbc.queryForObject("select count(*) from auth.permissions", Long.class))
        .isPositive();
  }

  @Test
  void migrationsValidateAndAreIdempotent() {
    assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    assertThat(
            Arrays.stream(flyway.info().applied())
                .filter(migration -> migration.getVersion() != null)
                .map(migration -> migration.getVersion().toString()))
        .containsExactly("1", "2", "3");
    assertThat(flyway.migrate().migrationsExecuted).isZero();
  }

  @Test
  void healthIsPublicUpWithoutInternalDetails() {
    var response = http.getForEntity("/actuator/health", JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
    assertThat(response.getBody().has("components")).isFalse();
    assertThat(response.getBody().has("details")).isFalse();
  }

  @Test
  void noPosUsersOrRenewableSessionsHaveBeenCreated() {
    assertThat(jdbc.queryForObject("select count(*) from auth.users", Long.class)).isZero();
    assertThat(jdbc.queryForObject("select count(*) from auth.refresh_sessions", Long.class))
        .isZero();
  }
}
