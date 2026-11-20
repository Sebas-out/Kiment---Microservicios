package com.kiments.auth;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** TEST_DB_URL debe apuntar a una base exclusiva de pruebas: las pruebas vacían sus usuarios. */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class ExternalPostgresTest extends AuthIntegrationScenarios {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    String url = System.getenv("TEST_DB_URL");
    if (!url.matches("jdbc:postgresql://[^/]+/[A-Za-z0-9_]+_test")) {
      throw new IllegalArgumentException(
          "TEST_DB_URL debe apuntar a una base exclusiva terminada en _test, sin parámetros");
    }
    r.add("spring.datasource.url", () -> url);
    r.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("TEST_DB_USERNAME", "kiments_test"));
    r.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
  }
}
