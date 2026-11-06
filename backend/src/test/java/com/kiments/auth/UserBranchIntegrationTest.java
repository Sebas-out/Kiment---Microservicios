package com.kiments.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.kiments.auth.users.User;
import com.nimbusds.jwt.SignedJWT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Pruebas de integración para Etapa 5A.2:
 * Integración de Usuarios, Sucursales, Roles y Seguridad JWT.
 *
 * <p>Requiere la variable de entorno {@code STAGE4_TEST_DB_URL} apuntando a una base aislada.
 */
@EnabledIfEnvironmentVariable(named = "STAGE4_TEST_DB_URL", matches = ".+")
class UserBranchIntegrationTest extends AuthIntegrationScenarios {

  @DynamicPropertySource
  static void isolatedDatabase(DynamicPropertyRegistry registry) {
    String url = System.getenv("STAGE4_TEST_DB_URL");
    if (!url.matches("jdbc:postgresql://[^/]+/[A-Za-z0-9_]+_test"))
      throw new IllegalArgumentException(
          "STAGE4_TEST_DB_URL debe apuntar a una base exclusiva terminada en _test");
    registry.add("spring.datasource.url", () -> url);
    registry.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("STAGE4_TEST_DB_USERNAME", "kiments_test"));
    registry.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("STAGE4_TEST_DB_PASSWORD", ""));
    registry.add("app.bootstrap.admin.enabled", () -> false);
    registry.add("app.bootstrap.admin.manual-enabled", () -> false);
  }

  String createTestBranch(String code, String name, boolean enabled) throws Exception {
    return body(
            mvc.perform(
                    authorized(post("/api/v1/branches"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                Map.of(
                                    "code", code,
                                    "name", name,
                                    "branchType", "VENTA",
                                    "enabled", enabled))))
                .andExpect(status().isCreated())
                .andReturn())
        .path("id")
        .asText();
  }

  ResultActions createUser(Object request) throws Exception {
    return mvc.perform(
        authorized(post("/api/v1/users"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request)));
  }

  // ── 1. ADMIN sin sucursal ────────────────────────────────────────────────

  @Test
  void adminCreatedWithoutBranchHasNullBranchId() throws Exception {
    var req =
        Map.of(
            "firstName",
            "Nuevo",
            "lastName",
            "Admin",
            "email",
            "newadmin@test.example",
            "dni",
            "88000001",
            "password",
            PASSWORD,
            "role",
            "ADMIN",
            "enabled",
            true);

    var res =
        body(
            mvc.perform(
                    authorized(post("/api/v1/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn());

    assertThat(res.path("role").asText()).isEqualTo("ADMIN");
    assertThat(res.path("branchId").isNull()).isTrue();
  }

  @Test
  void adminCreatedWithBranchIsRejected() throws Exception {
    String branchId = createTestBranch("ADM01", "Branch Adm", true);
    var req =
        Map.of(
            "firstName",
            "Nuevo",
            "lastName",
            "Admin",
            "email",
            "badadmin@test.example",
            "dni",
            "88000002",
            "password",
            PASSWORD,
            "role",
            "ADMIN",
            "enabled",
            true,
            "branchId",
            branchId);

    mvc.perform(
            authorized(post("/api/v1/users"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  // ── 2, 3, 4. Roles operativos con sucursal obligatoria ────────────────────

  @ParameterizedTest
  @ValueSource(strings = {"VENTAS", "ALMACEN", "VENTAS_ALMACEN"})
  void operationalRolesWithoutBranchAreRejected(String role) throws Exception {
    var req =
        Map.of(
            "firstName",
            "Op",
            "lastName",
            "Worker",
            "email",
            "op." + role.toLowerCase() + "@test.example",
            "dni",
            String.format("%08d", Math.abs(role.hashCode() % 10000000)),
            "password",
            PASSWORD,
            "role",
            role,
            "enabled",
            true);

    mvc.perform(
            authorized(post("/api/v1/users"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  // ── 5. Rechazo de sucursal inexistente ────────────────────────────────────

  @Test
  void operationalUserWithNonexistentBranchIsRejected() throws Exception {
    var req =
        Map.of(
            "firstName",
            "Op",
            "lastName",
            "Worker",
            "email",
            "nonexistent.branch@test.example",
            "dni",
            "88000003",
            "password",
            PASSWORD,
            "role",
            "VENTAS",
            "enabled",
            true,
            "branchId",
            UUID.randomUUID().toString());

    mvc.perform(
            authorized(post("/api/v1/users"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  // ── 6. Rechazo de sucursal inactiva ──────────────────────────────────────

  @Test
  void operationalUserWithInactiveBranchIsRejected() throws Exception {
    String branchId = createTestBranch("INACTB", "Branch Inactiva", true);
    patchJson("/api/v1/branches/" + branchId + "/status", Map.of("enabled", false))
        .andExpect(status().isOk());

    var req =
        Map.of(
            "firstName",
            "Op",
            "lastName",
            "Worker",
            "email",
            "inact.branch@test.example",
            "dni",
            "88000004",
            "password",
            PASSWORD,
            "role",
            "VENTAS",
            "enabled",
            true,
            "branchId",
            branchId);

    mvc.perform(
            authorized(post("/api/v1/users"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  // ── 7. Creación exitosa de usuario operativo ──────────────────────────────

  @Test
  void operationalUserWithActiveBranchCreatedSuccessfully() throws Exception {
    String branchId = createTestBranch("ACT01", "Branch Activa", true);
    var req =
        Map.of(
            "firstName",
            "Carlos",
            "lastName",
            "Ventas",
            "email",
            "carlos.ventas@test.example",
            "dni",
            "88000005",
            "password",
            PASSWORD,
            "role",
            "VENTAS",
            "enabled",
            true,
            "branchId",
            branchId);

    var res =
        body(
            mvc.perform(
                    authorized(post("/api/v1/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn());

    assertThat(res.path("role").asText()).isEqualTo("VENTAS");
    assertThat(res.path("branchId").asText()).isEqualTo(branchId);
  }

  // ── 8. Reasignación de sucursal ──────────────────────────────────────────

  @Test
  void reassignBranchSuccess() throws Exception {
    String b1 = createTestBranch("B01", "Branch 1", true);
    String b2 = createTestBranch("B02", "Branch 2", true);

    var req =
        Map.of(
            "firstName",
            "Pedro",
            "lastName",
            "Operativo",
            "email",
            "pedro.op@test.example",
            "dni",
            "88000006",
            "password",
            PASSWORD,
            "role",
            "ALMACEN",
            "enabled",
            true,
            "branchId",
            b1);

    String userId =
        body(
                mvc.perform(
                        authorized(post("/api/v1/users"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andReturn())
            .path("id")
            .asText();

    var updated =
        body(
            patchJson("/api/v1/users/" + userId + "/branch", Map.of("branchId", b2))
                .andExpect(status().isOk())
                .andReturn());

    assertThat(updated.path("branchId").asText()).isEqualTo(b2);
  }

  @Test
  void reassignBranchFailsOnAdminUser() throws Exception {
    String b1 = createTestBranch("B03", "Branch 3", true);
    patchJson("/api/v1/users/" + admin + "/branch", Map.of("branchId", b1))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  // ── 9. Cambio de rol y sucursal en una transacción ───────────────────────

  @Test
  void changeRoleAdminToOperationalRequiresBranch() throws Exception {
    User secondAdmin = seedUser("ADMIN", "second.admin@test.example", "88000007", true);
    patchJson("/api/v1/users/" + secondAdmin.getId() + "/role", Map.of("role", "VENTAS"))
        .andExpect(status().isConflict());
  }

  @Test
  void changeRoleAdminToOperationalWithBranchSucceeds() throws Exception {
    String branchId = createTestBranch("B04", "Branch 4", true);
    User secondAdmin = seedUser("ADMIN", "second.admin2@test.example", "88000008", true);

    var res =
        body(
            patchJson(
                    "/api/v1/users/" + secondAdmin.getId() + "/role",
                    Map.of("role", "VENTAS", "branchId", branchId))
                .andExpect(status().isOk())
                .andReturn());

    assertThat(res.path("role").asText()).isEqualTo("VENTAS");
    assertThat(res.path("branchId").asText()).isEqualTo(branchId);
  }

  // ── 10. Cambio de operativo a ADMIN ──────────────────────────────────────

  @Test
  void changeRoleOperationalToAdminClearsBranch() throws Exception {
    String branchId = createTestBranch("B05", "Branch 5", true);
    var req =
        Map.of(
            "firstName",
            "Promovido",
            "lastName",
            "AAdmin",
            "email",
            "promovido@test.example",
            "dni",
            "88000009",
            "password",
            PASSWORD,
            "role",
            "VENTAS",
            "enabled",
            true,
            "branchId",
            branchId);

    String userId =
        body(
                mvc.perform(
                        authorized(post("/api/v1/users"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andReturn())
            .path("id")
            .asText();

    var res =
        body(
            patchJson("/api/v1/users/" + userId + "/role", Map.of("role", "ADMIN"))
                .andExpect(status().isOk())
                .andReturn());

    assertThat(res.path("role").asText()).isEqualTo("ADMIN");
    assertThat(res.path("branchId").isNull()).isTrue();
  }

  // ── 11. Protección del último ADMIN ──────────────────────────────────────

  @Test
  void protectionOfLastAdminEnforcedOnRoleChange() throws Exception {
    String branchId = createTestBranch("B06", "Branch 6", true);
    patchJson(
            "/api/v1/users/" + admin + "/role",
            Map.of("role", "VENTAS", "branchId", branchId))
        .andExpect(status().isConflict());
  }

  // ── 12, 13. JWT con contexto de sucursal ─────────────────────────────────

  @Test
  void operationalUserLoginReturnsTokenWithBranchIdClaim() throws Exception {
    String branchId = createTestBranch("B07", "Branch 7", true);
    var req =
        Map.of(
            "firstName",
            "Login",
            "lastName",
            "Worker",
            "email",
            "login.worker@test.example",
            "dni",
            "88000010",
            "password",
            PASSWORD,
            "role",
            "VENTAS",
            "enabled",
            true,
            "branchId",
            branchId);

    createUser(req).andExpect(status().isCreated());

    String token =
        login("login.worker@test.example", PASSWORD).path("accessToken").asText();

    SignedJWT signedJWT = SignedJWT.parse(token);
    assertThat(signedJWT.getJWTClaimsSet().getStringClaim("branch_id")).isEqualTo(branchId);
    assertThat(signedJWT.getJWTClaimsSet().getStringClaim("role")).isEqualTo("VENTAS");
  }

  @Test
  void adminLoginReturnsTokenWithoutBranchIdClaim() throws Exception {
    String token = login("admin@test.example", PASSWORD).path("accessToken").asText();

    SignedJWT signedJWT = SignedJWT.parse(token);
    assertThat(signedJWT.getJWTClaimsSet().getClaim("branch_id")).isNull();
    assertThat(signedJWT.getJWTClaimsSet().getStringClaim("role")).isEqualTo("ADMIN");
  }

  // ── 14. Rechazo de token con contexto obsoleto ───────────────────────────

  @Test
  void obsoleteTokenWithDifferentBranchIsRejectedByConverter() throws Exception {
    String b1 = createTestBranch("OB1", "Branch Old", true);
    String b2 = createTestBranch("OB2", "Branch New", true);

    var req =
        Map.of(
            "firstName",
            "Obsoleto",
            "lastName",
            "Worker",
            "email",
            "obsolete@test.example",
            "dni",
            "88000011",
            "password",
            PASSWORD,
            "role",
            "VENTAS",
            "enabled",
            true,
            "branchId",
            b1);

    String userId = body(createUser(req).andExpect(status().isCreated()).andReturn()).path("id").asText();

    // Obtener token con sucursal B1
    String oldToken = login("obsolete@test.example", PASSWORD).path("accessToken").asText();

    // Reasignar trabajador a B2
    patchJson("/api/v1/users/" + userId + "/branch", Map.of("branchId", b2))
        .andExpect(status().isOk());

    // Petición con el token viejo (que tiene claim branch B1 pero en DB es B2)
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldToken))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  // ── 15. Usuario operativo sin sucursal no puede iniciar sesión ────────────

  @Test
  void legacyOperationalUserWithoutBranchCannotLogin() throws Exception {
    // Usuario sin sucursal insertado directamente en base de datos
    User orphan = seedUser("VENTAS", "orphan@test.example", "88000012", true);
    jdbc.update("UPDATE auth.users SET branch_id = NULL WHERE id = ?", orphan.getId());

    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "orphan@test.example", "password", PASSWORD))))
        .andExpect(status().isUnauthorized());
  }

  // ── 16. Filtro por sucursal ──────────────────────────────────────────────

  @Test
  void filterUsersByBranch() throws Exception {
    String b1 = createTestBranch("FL1", "Filter 1", true);
    String b2 = createTestBranch("FL2", "Filter 2", true);

    createUser(
            Map.of(
                "firstName",
                "User",
                "lastName",
                "B1",
                "email",
                "userb1@test.example",
                "dni",
                "88000013",
                "password",
                PASSWORD,
                "role",
                "VENTAS",
                "enabled",
                true,
                "branchId",
                b1))
        .andExpect(status().isCreated());

    createUser(
            Map.of(
                "firstName",
                "User",
                "lastName",
                "B2",
                "email",
                "userb2@test.example",
                "dni",
                "88000014",
                "password",
                PASSWORD,
                "role",
                "VENTAS",
                "enabled",
                true,
                "branchId",
                b2))
        .andExpect(status().isCreated());

    JsonNode listB1 =
        body(
            mvc.perform(authorized(get("/api/v1/users")).param("branchId", b1))
                .andExpect(status().isOk())
                .andReturn());

    assertThat(listB1.path("totalElements").asLong()).isEqualTo(1);
    assertThat(listB1.path("content").get(0).path("email").asText())
        .isEqualTo("userb1@test.example");
  }
}
