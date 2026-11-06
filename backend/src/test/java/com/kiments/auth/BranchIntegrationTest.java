package com.kiments.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.kiments.auth.users.User;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;

/**
 * Pruebas de integración para Sucursales (Etapa 5A.1).
 *
 * <p>Requiere la variable de entorno {@code STAGE4_TEST_DB_URL} apuntando a una base aislada cuyo
 * nombre termine en {@code _test}. NUNCA apuntar a kiments_db.
 *
 * <pre>{@code
 * $env:STAGE4_TEST_DB_URL      = 'jdbc:postgresql://localhost:5432/kiments_branches_test'
 * $env:STAGE4_TEST_DB_USERNAME = 'kiments_test'
 * $env:STAGE4_TEST_DB_PASSWORD = Read-Host 'Contraseña' -MaskInput
 * .\mvnw.cmd verify
 * }</pre>
 */
@EnabledIfEnvironmentVariable(named = "STAGE4_TEST_DB_URL", matches = ".+")
class BranchIntegrationTest extends AuthIntegrationScenarios {

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

  // ── Utilidades ────────────────────────────────────────────────────────────

  /** Limpia sucursales antes de cada prueba para mantener aislamiento. */
  @BeforeEach
  void cleanBranches() {
    // Desvincular branch_id en usuarios antes de borrar sucursales
    jdbc.execute("UPDATE auth.users SET branch_id = NULL");
    jdbc.execute("DELETE FROM auth.branches WHERE code <> 'TB01'");
  }

  Map<String, Object> branchRequest(String code, String name) {
    return Map.of("code", code, "name", name, "branchType", "VENTA", "enabled", true);
  }

  String createBranch(String code, String name) throws Exception {
    return body(
            mvc.perform(
                    authorized(post("/api/v1/branches"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(branchRequest(code, name))))
                .andExpect(status().isCreated())
                .andReturn())
        .path("id")
        .asText();
  }

  // ── Creación ──────────────────────────────────────────────────────────────

  @Test
  void createBranchReturns201WithLocation() throws Exception {
    var result =
        mvc.perform(
                authorized(post("/api/v1/branches"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(branchRequest("NORTE", "Sucursal Norte"))))
            .andExpect(status().isCreated())
            .andExpect(
                header()
                    .string(
                        "Location",
                        org.hamcrest.Matchers.containsString("/api/v1/branches/")))
            .andReturn();

    JsonNode b = body(result);
    assertThat(b.path("id").asText()).isNotBlank();
    assertThat(b.path("code").asText()).isEqualTo("NORTE");
    assertThat(b.path("name").asText()).isEqualTo("Sucursal Norte");
    assertThat(b.path("enabled").asBoolean()).isTrue();
    assertThat(b.path("createdAt").asText()).isNotBlank();
    assertThat(b.has("passwordHash")).isFalse();
    assertThat(b.has("version")).isFalse();
  }

  @Test
  void createNormalizesCodeToUppercase() throws Exception {
    JsonNode b =
        body(
            mvc.perform(
                    authorized(post("/api/v1/branches"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(branchRequest("sur01", "Sur"))))
                .andExpect(status().isCreated())
                .andReturn());
    assertThat(b.path("code").asText()).isEqualTo("SUR01");
  }

  @Test
  void duplicateCodeIsRejectedWith409() throws Exception {
    createBranch("DUPL", "Primera");
    mvc.perform(
            authorized(post("/api/v1/branches"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(branchRequest("DUPL", "Segunda"))))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @Test
  void duplicateCodeCaseInsensitiveIsRejected() throws Exception {
    createBranch("CENTRAL", "Central");
    mvc.perform(
            authorized(post("/api/v1/branches"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(branchRequest("central", "Central 2"))))
        .andExpect(status().isConflict());
  }

  @ParameterizedTest
  @ValueSource(strings = {"A", "toolongcodeexceedinglimit!!", "con espacios", "ESPECIAL@!"})
  void invalidCodeFormatsAreRejected(String code) throws Exception {
    mvc.perform(
            authorized(post("/api/v1/branches"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("code", code, "name", "X", "branchType", "VENTA", "enabled", true))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void missingRequiredFieldsReturns400WithErrors() throws Exception {
    mvc.perform(
            authorized(post("/api/v1/branches"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  // ── Consulta ──────────────────────────────────────────────────────────────

  @Test
  void getExistingBranchReturnsDetails() throws Exception {
    String id = createBranch("SUR", "Sucursal Sur");
    JsonNode b =
        body(
            mvc.perform(authorized(get("/api/v1/branches/" + id)))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(b.path("id").asText()).isEqualTo(id);
    assertThat(b.path("code").asText()).isEqualTo("SUR");
    assertThat(b.path("name").asText()).isEqualTo("Sucursal Sur");
  }

  @Test
  void getUnknownBranchReturns404() throws Exception {
    mvc.perform(authorized(get("/api/v1/branches/" + UUID.randomUUID())))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  // ── Listado y Paginación ──────────────────────────────────────────────────

  @Test
  void listReturnsPaginatedResults() throws Exception {
    jdbc.execute("DELETE FROM auth.branches WHERE code = 'TB01'");
    createBranch("NORTE", "Norte");
    createBranch("SUR", "Sur");
    createBranch("ESTE", "Este");

    JsonNode b =
        body(
            mvc.perform(
                    authorized(get("/api/v1/branches")).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andReturn());

    assertThat(b.path("totalElements").asLong()).isEqualTo(3);
    assertThat(b.path("totalPages").asInt()).isEqualTo(2);
    assertThat(b.path("content").size()).isEqualTo(2);
  }

  @Test
  void listFiltersByName() throws Exception {
    createBranch("NORTE", "Sucursal Norte");
    createBranch("SUR", "Sucursal Sur");

    JsonNode b =
        body(
            mvc.perform(authorized(get("/api/v1/branches")).param("name", "norte"))
                .andExpect(status().isOk())
                .andReturn());

    assertThat(b.path("totalElements").asLong()).isEqualTo(1);
    assertThat(b.path("content").get(0).path("code").asText()).isEqualTo("NORTE");
  }

  @Test
  void listFiltersByEnabledStatus() throws Exception {
    createBranch("ACTIVA", "Activa");
    String id = createBranch("INACTIVA", "Inactiva");
    patchJson("/api/v1/branches/" + id + "/status", Map.of("enabled", false))
        .andExpect(status().isOk());

    JsonNode b =
        body(
            mvc.perform(authorized(get("/api/v1/branches")).param("enabled", "false"))
                .andExpect(status().isOk())
                .andReturn());

    assertThat(b.path("totalElements").asLong()).isEqualTo(1);
    assertThat(b.path("content").get(0).path("code").asText()).isEqualTo("INACTIVA");
  }

  @Test
  void listFiltersByCode() throws Exception {
    createBranch("NORTE", "Norte");
    createBranch("SUR", "Sur");

    JsonNode b =
        body(
            mvc.perform(authorized(get("/api/v1/branches")).param("code", "SUR"))
                .andExpect(status().isOk())
                .andReturn());

    assertThat(b.path("totalElements").asLong()).isEqualTo(1);
  }

  // ── Edición ───────────────────────────────────────────────────────────────

  @Test
  void updateBranchPersistsChanges() throws Exception {
    String id = createBranch("UPD", "Original");

    JsonNode b =
        body(
            patchJson(
                    "/api/v1/branches/" + id,
                    Map.of("name", "Actualizado", "address", "Calle Nueva 50"))
                .andExpect(status().isOk())
                .andReturn());

    assertThat(b.path("name").asText()).isEqualTo("Actualizado");
    assertThat(b.path("address").asText()).isEqualTo("Calle Nueva 50");
    assertThat(b.path("code").asText()).isEqualTo("UPD"); // código inmutable
  }

  @Test
  void updateUnknownBranchReturns404() throws Exception {
    patchJson("/api/v1/branches/" + UUID.randomUUID(), Map.of("name", "X"))
        .andExpect(status().isNotFound());
  }

  @Test
  void unknownFieldsInPatchAreRejected() throws Exception {
    String id = createBranch("REJ", "Rechazo");
    mvc.perform(
            authorized(patch("/api/v1/branches/" + id))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"NUEVO\",\"name\":\"Intento\"}"))
        .andExpect(status().isBadRequest()); // fail-on-unknown-properties
  }

  // ── Activación / Desactivación ────────────────────────────────────────────

  @Test
  void deactivationSucceedsWhenBranchHasNoActiveWorkers() throws Exception {
    String id = createBranch("VACIA", "Sin trabajadores");

    JsonNode b =
        body(
            patchJson("/api/v1/branches/" + id + "/status", Map.of("enabled", false))
                .andExpect(status().isOk())
                .andReturn());

    assertThat(b.path("enabled").asBoolean()).isFalse();
  }

  @Test
  void deactivationIsBlockedWhenActiveWorkersAreAssigned() throws Exception {
    String branchId = createBranch("BUSY", "Con trabajadores");

    // Asignar un usuario operativo activo a esta sucursal directamente en BD
    User worker = seedUser("VENTAS", "worker.busy@test.example", "20000001", true);
    jdbc.update(
        "UPDATE auth.users SET branch_id = ? WHERE id = ?",
        UUID.fromString(branchId),
        worker.getId());

    patchJson("/api/v1/branches/" + branchId + "/status", Map.of("enabled", false))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("1")))
        .andExpect(jsonPath("$.trace").doesNotExist());

    // La sucursal debe seguir activa
    JsonNode branch =
        body(
            mvc.perform(authorized(get("/api/v1/branches/" + branchId)))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(branch.path("enabled").asBoolean()).isTrue();
  }

  @Test
  void deactivationIsAllowedWhenOnlyAdminIsAssigned() throws Exception {
    String branchId = createBranch("ADMONLY", "Solo admin");

    // Un ADMIN asignado a la sucursal no bloquea la desactivación
    User admin2 = seedUser("ADMIN", "admin2@test.example", "20000002", true);
    jdbc.update(
        "UPDATE auth.users SET branch_id = ? WHERE id = ?",
        UUID.fromString(branchId),
        admin2.getId());

    patchJson("/api/v1/branches/" + branchId + "/status", Map.of("enabled", false))
        .andExpect(status().isOk());
  }

  @Test
  void activationAlwaysSucceeds() throws Exception {
    String id = createBranch("REACT", "Reactivar");
    patchJson("/api/v1/branches/" + id + "/status", Map.of("enabled", false))
        .andExpect(status().isOk());
    JsonNode b =
        body(
            patchJson("/api/v1/branches/" + id + "/status", Map.of("enabled", true))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(b.path("enabled").asBoolean()).isTrue();
  }

  // ── Seguridad / Autorización ──────────────────────────────────────────────

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    mvc.perform(get("/api/v1/branches"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    mvc.perform(
            post("/api/v1/branches").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"VENTAS", "ALMACEN", "VENTAS_ALMACEN"})
  void operationalRolesCannotAccessBranchAdminEndpoints(String role) throws Exception {
    String branchId = createBranch("SEC", "Seguridad");
    // DNI único basado en el rol para evitar colisiones entre iteraciones parametrizadas
    int dniSuffix = role.hashCode() & 0xFFFF;
    String dni = String.format("%08d", dniSuffix);
    User worker = seedUser(role, "sec." + role.toLowerCase() + "@test.example", dni, true);
    jdbc.update(
        "UPDATE auth.users SET branch_id = ? WHERE id = ?",
        UUID.fromString(branchId),
        worker.getId());
    String token =
        login("sec." + role.toLowerCase() + "@test.example", PASSWORD)
            .get("accessToken")
            .asText();

    for (var req :
        java.util.List.of(
            get("/api/v1/branches"),
            get("/api/v1/branches/" + branchId),
            post("/api/v1/branches").content(json.writeValueAsString(branchRequest("SEC2", "Secundaria"))),
            patch("/api/v1/branches/" + branchId).content("{\"name\":\"X\"}"),
            patch("/api/v1/branches/" + branchId + "/status")
                .content("{\"enabled\":false}"))) {
      mvc.perform(
              req.header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"BRANCH_READ", "BRANCH_CREATE", "BRANCH_UPDATE", "BRANCH_DISABLE"})
  void adminWithoutSpecificPermissionIsRejected(String permission) throws Exception {
    String branchId = createBranch("PERM", "Test Permisos");
    Long permId =
        jdbc.queryForObject(
            "SELECT id FROM auth.permissions WHERE code=?", Long.class, permission);
    Long adminRoleId =
        jdbc.queryForObject("SELECT id FROM auth.roles WHERE code='ADMIN'", Long.class);
    jdbc.update(
        "DELETE FROM auth.role_permissions WHERE role_id=? AND permission_id=?",
        adminRoleId,
        permId);
    try {
      var request =
          switch (permission) {
            case "BRANCH_READ" -> get("/api/v1/branches");
            case "BRANCH_CREATE" ->
                post("/api/v1/branches")
                    .content(
                        json.writeValueAsString(
                            Map.of("code", "NW2", "name", "Nueva", "branchType", "VENTA", "enabled", true)));
            case "BRANCH_UPDATE" ->
                patch("/api/v1/branches/" + branchId).content("{\"name\":\"X\"}");
            default ->
                patch("/api/v1/branches/" + branchId + "/status")
                    .content("{\"enabled\":false}");
          };
      mvc.perform(authorized(request).contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    } finally {
      jdbc.update(
          "INSERT INTO auth.role_permissions VALUES (?,?)", adminRoleId, permId);
    }
  }

  // ── Migraciones e Integridad ──────────────────────────────────────────────

  @Test
  void flywayMigrationCreatedBranchPermissions() {
    var codes =
        jdbc.queryForList(
            "SELECT code FROM auth.permissions WHERE code LIKE 'BRANCH_%'", String.class);
    assertThat(codes)
        .containsExactlyInAnyOrder(
            "BRANCH_READ", "BRANCH_CREATE", "BRANCH_UPDATE", "BRANCH_DISABLE");
  }

  @Test
  void adminRoleHasAllBranchPermissions() {
    var codes =
        jdbc.queryForList(
            """
            SELECT p.code FROM auth.role_permissions rp
            JOIN auth.permissions p ON p.id = rp.permission_id
            JOIN auth.roles r ON r.id = rp.role_id
            WHERE r.code = 'ADMIN' AND p.code LIKE 'BRANCH_%'
            """,
            String.class);
    assertThat(codes)
        .containsExactlyInAnyOrder(
            "BRANCH_READ", "BRANCH_CREATE", "BRANCH_UPDATE", "BRANCH_DISABLE");
  }

  @Test
  void responseDoesNotExposeInternalFields() throws Exception {
    String id = createBranch("SAFE", "Seguro");
    JsonNode b =
        body(
            mvc.perform(authorized(get("/api/v1/branches/" + id)))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(b.has("version")).isFalse();
    assertThat(b.has("passwordHash")).isFalse();
  }

  // ── Pruebas Etapa 5B (Flyway V5: SUNAT y Datos Operativos) ───────────────

  @Test
  void createWithSunatAndOperationalDetailsPersistsCorrectly() throws Exception {
    var req =
        Map.of(
            "code", "MIRA01",
            "name", "Sucursal Miraflores",
            "address", "Av. Larco 1020",
            "city", "Lima",
            "ubigeo", "150122",
            "email", "miraflores@kiments.com",
            "branchType", "VENTA",
            "sunatCode", "0002",
            "enabled", true);

    var result =
        mvc.perform(
                authorized(post("/api/v1/branches"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode b = body(result);
    assertThat(b.path("code").asText()).isEqualTo("MIRA01");
    assertThat(b.path("city").asText()).isEqualTo("Lima");
    assertThat(b.path("ubigeo").asText()).isEqualTo("150122");
    assertThat(b.path("email").asText()).isEqualTo("miraflores@kiments.com");
    assertThat(b.path("branchType").asText()).isEqualTo("VENTA");
    assertThat(b.path("sunatCode").asText()).isEqualTo("0002");

    // Verificar persistencia directa en PostgreSQL
    var dbRow =
        jdbc.queryForMap(
            "SELECT city, ubigeo, email, branch_type, sunat_code FROM auth.branches WHERE code = 'MIRA01'");
    assertThat(dbRow.get("city")).isEqualTo("Lima");
    assertThat(dbRow.get("ubigeo")).isEqualTo("150122");
    assertThat(dbRow.get("email")).isEqualTo("miraflores@kiments.com");
    assertThat(dbRow.get("branch_type")).isEqualTo("VENTA");
    assertThat(dbRow.get("sunat_code")).isEqualTo("0002");
  }

  @Test
  void duplicateSunatCodeReturns409() throws Exception {
    var req1 =
        Map.of(
            "code", "SN1",
            "name", "Sucursal Uno",
            "branchType", "VENTA",
            "sunatCode", "0003",
            "enabled", true);
    mvc.perform(
            authorized(post("/api/v1/branches"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req1)))
        .andExpect(status().isCreated());

    var req2 =
        Map.of(
            "code", "SN2",
            "name", "Sucursal Dos",
            "branchType", "ALMACEN",
            "sunatCode", "0003",
            "enabled", true);
    mvc.perform(
            authorized(post("/api/v1/branches"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req2)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  void invalidUbigeoFormatReturns400() throws Exception {
    var req =
        Map.of(
            "code", "BADUBI",
            "name", "Bad Ubigeo",
            "branchType", "VENTA",
            "ubigeo", "1501", // Longitud inválida (debe ser de 6 dígitos)
            "enabled", true);

    mvc.perform(
            authorized(post("/api/v1/branches"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void invalidSunatCodeFormatReturns400() throws Exception {
    var req =
        Map.of(
            "code", "BADSNT",
            "name", "Bad Sunat",
            "branchType", "VENTA",
            "sunatCode", "12", // Longitud inválida (debe ser de 4 dígitos)
            "enabled", true);

    mvc.perform(
            authorized(post("/api/v1/branches"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void missingBranchTypeReturns400() throws Exception {
    var req =
        Map.of(
            "code", "NOTYPE",
            "name", "Sin Tipo",
            "enabled", true);

    mvc.perform(
            authorized(post("/api/v1/branches"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchCleansOptionalFieldsWhenBlankAndConservesWhenOmitted() throws Exception {
    var req =
        Map.of(
            "code", "PATCHV5",
            "name", "Original",
            "city", "Arequipa",
            "ubigeo", "040101",
            "email", "arequipa@kiments.com",
            "branchType", "VENTA",
            "sunatCode", "0004",
            "enabled", true);

    String id =
        body(
                mvc.perform(
                        authorized(post("/api/v1/branches"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andReturn())
            .path("id")
            .asText();

    // Actualizar branchType a MIXTO, limpiar city y ubigeo con "", omitir email y sunatCode (conservar)
    var patchReq =
        Map.of(
            "name", "Modificado",
            "city", "",
            "ubigeo", "",
            "branchType", "MIXTO");

    JsonNode updated =
        body(
            patchJson("/api/v1/branches/" + id, patchReq)
                .andExpect(status().isOk())
                .andReturn());

    assertThat(updated.path("name").asText()).isEqualTo("Modificado");
    assertThat(updated.path("city").isNull()).isTrue();
    assertThat(updated.path("ubigeo").isNull()).isTrue();
    assertThat(updated.path("branchType").asText()).isEqualTo("MIXTO");
    assertThat(updated.path("email").asText()).isEqualTo("arequipa@kiments.com");
    assertThat(updated.path("sunatCode").asText()).isEqualTo("0004");
  }

  @Test
  void listFiltersByBranchTypeCityAndSunatCode() throws Exception {
    var b1 =
        Map.of(
            "code", "FLT1",
            "name", "Tienda Lima",
            "city", "Lima",
            "branchType", "VENTA",
            "sunatCode", "0005",
            "enabled", true);
    var b2 =
        Map.of(
            "code", "FLT2",
            "name", "Almacén Arequipa",
            "city", "Arequipa",
            "branchType", "ALMACEN",
            "sunatCode", "0006",
            "enabled", true);

    mvc.perform(
            authorized(post("/api/v1/branches"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(b1)))
        .andExpect(status().isCreated());
    mvc.perform(
            authorized(post("/api/v1/branches"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(b2)))
        .andExpect(status().isCreated());

    // Filtro por tipo ALMACEN
    JsonNode resType =
        body(
            mvc.perform(authorized(get("/api/v1/branches")).param("type", "ALMACEN"))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(resType.path("content").size()).isGreaterThanOrEqualTo(1);
    assertThat(resType.path("content").get(0).path("code").asText()).isEqualTo("FLT2");

    // Filtro por ciudad Lima
    JsonNode resCity =
        body(
            mvc.perform(authorized(get("/api/v1/branches")).param("city", "Lima"))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(resCity.path("content").size()).isGreaterThanOrEqualTo(1);
    assertThat(resCity.path("content").get(0).path("code").asText()).isEqualTo("FLT1");

    // Filtro por sunatCode 0006
    JsonNode resSunat =
        body(
            mvc.perform(authorized(get("/api/v1/branches")).param("sunatCode", "0006"))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(resSunat.path("content").size()).isEqualTo(1);
    assertThat(resSunat.path("content").get(0).path("code").asText()).isEqualTo("FLT2");
  }
}
