package com.kiments.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.kiments.auth.sessions.RefreshTokens;
import com.kiments.auth.users.*;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;

/**
 * Pruebas destructivas SOLO para fixtures de una base aislada en el puerto 55439. Nunca usa
 * kiments_db ni la cuenta real. Contraseñas generadas en memoria.
 */
@EnabledIfEnvironmentVariable(named = "STAGE3_TEST_DB_URL", matches = ".+")
class StageThreeIntegrationTest extends AuthIntegrationScenarios {
  @Autowired EntityManagerFactory entityManagers;
  static final Set<String> USER_PERMISSIONS =
      Set.of("USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DISABLE", "USER_ROLE_CHANGE");
  static final Set<String> COMMON = Set.of("PRODUCT_READ", "STOCK_READ");
  static final Set<String> SALES =
      Set.of(
          "SALE_CREATE",
          "QUOTATION_CREATE",
          "CUSTOMER_READ",
          "CUSTOMER_WRITE",
          "PAYMENT_CREATE",
          "INVOICE_READ",
          "INVOICE_CREATE");
  static final Set<String> WAREHOUSE =
      Set.of(
          "PRODUCT_WRITE",
          "CATEGORY_WRITE",
          "VARIANT_WRITE",
          "STOCK_ADJUST",
          "MOVEMENT_READ",
          "STOCK_REPORT_READ");

  @DynamicPropertySource
  static void isolatedDatabase(DynamicPropertyRegistry registry) {
    String url = System.getenv("STAGE3_TEST_DB_URL");
    if (!url.matches(
        "jdbc:postgresql://127\\.0\\.0\\.1:55439/kiments_users_stage3_[a-z0-9_]+_test"))
      throw new IllegalArgumentException(
          "La suite solo admite su base aislada en 55439; nunca kiments_db");
    registry.add("spring.datasource.url", () -> url);
    registry.add("spring.datasource.username", () -> "kiments_test");
    registry.add("spring.datasource.password", () -> "");
    registry.add("app.bootstrap.admin.enabled", () -> false);
    registry.add("app.bootstrap.admin.manual-enabled", () -> false);
  }

  @SafeVarargs
  static Set<String> union(Set<String>... sets) {
    return Arrays.stream(sets).flatMap(Set::stream).collect(Collectors.toSet());
  }

  String createOperator(String email, String dni, String role) throws Exception {
    var request = new HashMap<>(createRequest(email, dni, role));
    request.put("firstName", "PRUEBA Etapa3");
    request.put("lastName", role);
    return body(create(request).andExpect(status().isCreated()).andReturn()).path("id").asText();
  }

  Set<String> codes(JsonNode permissions) {
    var result = new HashSet<String>();
    permissions.forEach(p -> result.add(p.path("code").asText()));
    return result;
  }

  @Test
  void exactPermissionMatrixIsExposedByRoleEndpoints() throws Exception {
    var expected =
        Map.of(
            "ADMIN",
            union(USER_PERMISSIONS, COMMON, SALES, WAREHOUSE, Set.of("REPORT_READ")),
            "VENTAS",
            union(COMMON, SALES),
            "ALMACEN",
            union(COMMON, WAREHOUSE),
            "VENTAS_ALMACEN",
            union(COMMON, SALES, WAREHOUSE));
    var catalog =
        body(mvc.perform(authorized(get("/api/v1/roles"))).andExpect(status().isOk()).andReturn());
    assertThat(catalog.size()).isEqualTo(4);
    for (var role : catalog) {
      var permissions =
          body(
              mvc.perform(
                      authorized(get("/api/v1/roles/" + role.path("id").asLong() + "/permissions")))
                  .andExpect(status().isOk())
                  .andReturn());
      assertThat(codes(permissions)).isEqualTo(expected.get(role.path("code").asText()));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"VENTAS", "ALMACEN", "VENTAS_ALMACEN"})
  void allAdministrativeEndpointsDenyEveryOperationalRole(String role) throws Exception {
    createOperator("prueba.etapa3@svc09.example.test", "90000002", role);
    String token = login("prueba.etapa3@svc09.example.test", PASSWORD).path("accessToken").asText();
    String path = "/api/v1/users/" + admin;
    for (var request :
        List.of(
            get("/api/v1/users"),
            get(path),
            post("/api/v1/users")
                .content(
                    json.writeValueAsString(
                        createRequest("new@svc09.example.test", "90000003", "ADMIN"))),
            patch(path).content("{\"firstName\":\"ATTEMPT\"}"),
            patch(path + "/role").content("{\"role\":\"VENTAS\"}"),
            patch(path + "/status").content("{\"enabled\":false}"),
            get("/api/v1/roles"),
            get("/api/v1/roles/1/permissions"))) {
      mvc.perform(
              request
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }
    var unchanged = users.findById(admin).orElseThrow();
    assertThat(unchanged.isEnabled()).isTrue();
    assertThat(unchanged.getFirstName()).isEqualTo("PRUEBA");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DISABLE", "USER_ROLE_CHANGE"})
  void adminNeedsCurrentSpecificPermission(String permission) throws Exception {
    String id = createOperator("target@svc09.example.test", "90000002", "VENTAS");
    var role = roles.findByCode("ADMIN").orElseThrow();
    Long permissionId =
        jdbc.queryForObject("SELECT id FROM auth.permissions WHERE code=?", Long.class, permission);
    jdbc.update(
        "DELETE FROM auth.role_permissions WHERE role_id=? AND permission_id=?",
        role.getId(),
        permissionId);
    try {
      var request =
          switch (permission) {
            case "USER_READ" -> get("/api/v1/users");
            case "USER_CREATE" ->
                post("/api/v1/users")
                    .content(
                        json.writeValueAsString(
                            createRequest("blocked@svc09.example.test", "90000003", "VENTAS")));
            case "USER_UPDATE" ->
                patch("/api/v1/users/" + id).content("{\"firstName\":\"ATTEMPT\"}");
            case "USER_DISABLE" ->
                patch("/api/v1/users/" + id + "/status").content("{\"enabled\":false}");
            default -> patch("/api/v1/users/" + id + "/role").content("{\"role\":\"ALMACEN\"}");
          };
      mvc.perform(authorized(request).contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    } finally {
      jdbc.update("INSERT INTO auth.role_permissions VALUES (?,?)", role.getId(), permissionId);
    }
  }

  @Test
  void fabricatedAdministrativeClaimsDoNotOverrideDatabaseRole() throws Exception {
    String id = createOperator("claims@svc09.example.test", "90000002", "VENTAS");
    var now = Instant.now();
    String token =
        encoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(SignatureAlgorithm.RS256).keyId(jwt.keyId()).build(),
                    JwtClaimsSet.builder()
                        .issuer(jwt.issuer())
                        .audience(List.of(jwt.audience()))
                        .subject(id)
                        .id(UUID.randomUUID().toString())
                        .issuedAt(now)
                        .notBefore(now)
                        .expiresAt(now.plusSeconds(900))
                        .claim("role", "ADMIN")
                        .claim("permissions", List.copyOf(USER_PERMISSIONS))
                        .build()))
            .getTokenValue();
    mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void duplicateUpdatesAreAtomicAndDoNotLeakSql() throws Exception {
    String a = createOperator("first@svc09.example.test", "90000002", "VENTAS"),
        b = createOperator("second@svc09.example.test", "90000003", "ALMACEN");
    for (var payload :
        List.of(
            Map.of("email", " FIRST@SVC09.EXAMPLE.TEST ", "firstName", "SHOULD_ROLL_BACK"),
            Map.of("dni", "90000002", "firstName", "SHOULD_ROLL_BACK"))) {
      patchJson("/api/v1/users/" + b, payload)
          .andExpect(status().isConflict())
          .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
          .andExpect(jsonPath("$.trace").doesNotExist());
      assertThat(users.findById(UUID.fromString(b)).orElseThrow().getFirstName())
          .isEqualTo("PRUEBA Etapa3");
    }
    assertThat(users.count()).isEqualTo(3);
  }

  @Test
  void paginationFiltersAndLiteralSearchAreCorrect() throws Exception {
    for (int i = 0; i < 6; i++)
      createOperator(
          "page" + i + "@svc09.example.test", "9000000" + (i + 2), i < 3 ? "VENTAS" : "ALMACEN");
    var seen = new HashSet<String>();
    for (int page = 0; page < 4; page++) {
      var result =
          body(
              mvc.perform(
                      authorized(get("/api/v1/users"))
                          .param("page", String.valueOf(page))
                          .param("size", "2"))
                  .andExpect(status().isOk())
                  .andReturn());
      assertThat(result.path("page").asInt()).isEqualTo(page);
      assertThat(result.path("totalElements").asLong()).isEqualTo(7);
      assertThat(result.path("totalPages").asInt()).isEqualTo(4);
      for (var user : result.path("content"))
        assertThat(seen.add(user.path("id").asText())).isTrue();
    }
    assertThat(seen).hasSize(7);
    var filtered =
        body(
            mvc.perform(
                    authorized(get("/api/v1/users"))
                        .param("role", "VENTAS")
                        .param("enabled", "true")
                        .param("name", "prueba etapa3 ventas"))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(filtered.path("totalElements").asLong()).isEqualTo(3);
    mvc.perform(authorized(get("/api/v1/users")).param("page", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
    var target = users.findByEmail("page0@svc09.example.test").orElseThrow();
    patchJson("/api/v1/users/" + target.getId(), Map.of("firstName", "PRUEBA % literal_"))
        .andExpect(status().isOk());
    for (String name : List.of("%", "_"))
      mvc.perform(authorized(get("/api/v1/users")).param("name", name))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.totalElements").value(1));
    patchJson("/api/v1/users/" + target.getId() + "/status", Map.of("enabled", false))
        .andExpect(status().isOk());
    mvc.perform(authorized(get("/api/v1/users")).param("enabled", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @ParameterizedTest
  @ValueSource(strings = {"email", "dni", "role", "status", "unknown", "password", "name"})
  void invalidChangesReturn400(String field) throws Exception {
    String id = createOperator("invalid@svc09.example.test", "90000002", "VENTAS");
    String suffix = field.equals("role") ? "/role" : field.equals("status") ? "/status" : "";
    Object payload =
        switch (field) {
          case "email" -> Map.of("email", "");
          case "dni" -> Map.of("dni", "123");
          case "role" -> Map.of("role", "ROOT");
          case "status" -> Collections.singletonMap("enabled", null);
          case "unknown" -> Map.of("enabled", true);
          case "password" -> Map.of("password", RefreshTokens.generate());
          default -> Map.of("firstName", "   ");
        };
    patchJson("/api/v1/users/" + id + suffix, payload).andExpect(status().isBadRequest());
  }

  @Test
  void phoneCanBeClearedAndAbsentResourcesReturn404() throws Exception {
    String id = createOperator("phone@svc09.example.test", "90000002", "VENTAS");
    patchJson("/api/v1/users/" + id, Map.of("phone", "+51999000111")).andExpect(status().isOk());
    patchJson("/api/v1/users/" + id, Map.of("phone", ""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").isEmpty());
    patchJson("/api/v1/users/" + UUID.randomUUID(), Map.of("firstName", "PRUEBA"))
        .andExpect(status().isNotFound());
    mvc.perform(authorized(get("/api/v1/roles/99999/permissions")))
        .andExpect(status().isNotFound());
  }

  @Test
  void hugePageOffsetsAreRejectedAsBadInput() throws Exception {
    mvc.perform(authorized(get("/api/v1/users")).param("page", "2147483647").param("size", "100"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void physicalDeleteIsUnavailableAndReturns405() throws Exception {
    String id = createOperator("no-delete@svc09.example.test", "90000002", "VENTAS");
    mvc.perform(authorized(delete("/api/v1/users/" + id)))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().exists("Allow"));
    assertThat(users.existsById(UUID.fromString(id))).isTrue();
  }

  @Test
  void unsupportedContentTypeReturns415() throws Exception {
    mvc.perform(
            authorized(post("/api/v1/users")).contentType(MediaType.TEXT_PLAIN).content("invalid"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @ParameterizedTest
  @ValueSource(strings = {"VENTAS", "ALMACEN", "VENTAS_ALMACEN"})
  void operationalUsersCanChangeOnlyTheirOwnPassword(String role) throws Exception {
    String id = createOperator("password@svc09.example.test", "90000002", role);
    var original = login("password@svc09.example.test", PASSWORD);
    String token = original.path("accessToken").asText();
    String next = RefreshTokens.generate();
    for (var payload :
        List.of(
            Map.of("currentPassword", "incorrect", "newPassword", next),
            Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD))) {
      int expected = payload.get("currentPassword").equals("incorrect") ? 401 : 409;
      mvc.perform(
              post("/api/v1/auth/change-password")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(payload)))
          .andExpect(status().is(expected));
    }
    mvc.perform(
            post("/api/v1/auth/change-password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("currentPassword", PASSWORD, "newPassword", next))))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("email", "password@svc09.example.test", "password", PASSWORD))))
        .andExpect(status().isUnauthorized());
    login("password@svc09.example.test", next);
    refresh(original.path("refreshToken").asText()).andExpect(status().isUnauthorized());
    assertThat(
            passwords.matches(
                next, users.findById(UUID.fromString(id)).orElseThrow().getPasswordHash()))
        .isTrue();
  }

  @Test
  void databaseForeignKeysAndRequiredFieldsProtectIntegrity() throws Exception {
    String id = createOperator("integrity@svc09.example.test", "90000002", "VENTAS");
    for (String assignment :
        List.of(
            "role_id=9223372036854775807",
            "email=NULL",
            "enabled=NULL",
            "password_hash=NULL",
            "first_name=''",
            "dni='123'",
            "phone='letters'")) {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE auth.users SET " + assignment + " WHERE id=?", UUID.fromString(id)))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO auth.role_permissions(role_id,permission_id)"
                        + " VALUES(9223372036854775807,1)"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(users.count()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"email", "dni"})
  void concurrentDuplicateCreationHasOneWinner(String field) throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                start.await();
                return create(createRequest("duplicate@svc09.example.test", "90000002", "VENTAS"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return create(
                        createRequest(
                            field.equals("email")
                                ? "DUPLICATE@SVC09.EXAMPLE.TEST"
                                : "other@svc09.example.test",
                            field.equals("dni") ? "90000002" : "90000003",
                            "ALMACEN"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      start.countDown();
      assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
    assertThat(users.count()).isEqualTo(2);
  }

  @Test
  void concurrentPersonalUpdatesDoNotLoseOtherFields() throws Exception {
    String id = createOperator("concurrent@svc09.example.test", "90000002", "VENTAS");
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                start.await();
                return patchJson("/api/v1/users/" + id, Map.of("firstName", "PRUEBA Updated"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return patchJson("/api/v1/users/" + id, Map.of("phone", "+51999000111"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      start.countDown();
      assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsOnly(200);
    }
    var user = users.findById(UUID.fromString(id)).orElseThrow();
    assertThat(user.getFirstName()).isEqualTo("PRUEBA Updated");
    assertThat(user.getPhone()).isEqualTo("+51999000111");
  }

  @Test
  void optimisticVersionRejectsStaleExternalWrites() throws Exception {
    String id = createOperator("version@svc09.example.test", "90000002", "VENTAS");
    try (var a = entityManagers.createEntityManager();
        var b = entityManagers.createEntityManager()) {
      a.getTransaction().begin();
      b.getTransaction().begin();
      var first = a.find(User.class, UUID.fromString(id));
      var stale = b.find(User.class, UUID.fromString(id));
      first.setFirstName("PRUEBA Committed");
      a.getTransaction().commit();
      stale.setEnabled(false);
      assertThatThrownBy(() -> b.getTransaction().commit())
          .isInstanceOf(jakarta.persistence.RollbackException.class);
    }
    assertThat(users.findById(UUID.fromString(id)).orElseThrow().isEnabled()).isTrue();
  }

  @Test
  void concurrentCrossDemotionCannotRemoveAllAdministrators() throws Exception {
    var other = seedUser("ADMIN", "prueba.admin2@svc09.example.test", "90000002", true);
    String secondToken =
        login("prueba.admin2@svc09.example.test", PASSWORD).path("accessToken").asText();
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                start.await();
                return patchJson(
                        "/api/v1/users/" + other.getId() + "/role", Map.of("role", "VENTAS"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      var second =
          pool.submit(
              () -> {
                start.await();
                return mvc.perform(
                        patch("/api/v1/users/" + admin + "/role")
                            .header("Authorization", "Bearer " + secondToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("role", "VENTAS"))))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      start.countDown();
      assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 403);
    }
    assertThat(users.countByRoleCodeAndEnabledTrue("ADMIN")).isEqualTo(1);
  }

  @Test
  void concurrentSelfDisableOverHttpLeavesOneActiveAdmin() throws Exception {
    var other = seedUser("ADMIN", "prueba.admin2@svc09.example.test", "90000002", true);
    String secondToken =
        login("prueba.admin2@svc09.example.test", PASSWORD).path("accessToken").asText();
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                start.await();
                return patchJson("/api/v1/users/" + admin + "/status", Map.of("enabled", false))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      var second =
          pool.submit(
              () -> {
                start.await();
                return mvc.perform(
                        patch("/api/v1/users/" + other.getId() + "/status")
                            .header("Authorization", "Bearer " + secondToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("enabled", false))))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      start.countDown();
      assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(users.countByRoleCodeAndEnabledTrue("ADMIN")).isEqualTo(1);
  }
}
