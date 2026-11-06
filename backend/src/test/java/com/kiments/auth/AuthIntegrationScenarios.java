package com.kiments.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.kiments.auth.config.JwtProperties;
import com.kiments.auth.exception.ApiException;
import com.kiments.auth.roles.RoleRepository;
import com.kiments.auth.sessions.*;
import com.kiments.auth.users.*;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc(
    print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
abstract class AuthIntegrationScenarios {
  static final String PASSWORD = RefreshTokens.generate();
  static final String REPLACEMENT_PASSWORD = RefreshTokens.generate();
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired RoleRepository roles;
  @Autowired UserService userService;
  @Autowired PasswordEncoder passwords;
  @Autowired JwtEncoder encoder;
  @Autowired JwtProperties jwt;
  @Autowired JdbcTemplate jdbc;
  UUID admin;
  String adminToken;

  @DynamicPropertySource
  static void keys(DynamicPropertyRegistry r) throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    var dir = Path.of("target", "test-keys");
    Files.createDirectories(dir);
    var pub = dir.resolve("public.pem");
    var priv = dir.resolve("private.pem");
    Files.writeString(pub, pem("PUBLIC KEY", pair.getPublic().getEncoded()));
    Files.writeString(priv, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
    r.add("app.jwt.public-key", () -> pub.toUri().toString());
    r.add("app.jwt.private-key", () -> priv.toUri().toString());
    r.add("app.jwt.issuer", () -> "https://issuer.kiments.test");
    r.add("app.jwt.audience", () -> "kiments-api");
    r.add("springdoc.api-docs.enabled", () -> "true");
    r.add("springdoc.swagger-ui.enabled", () -> "true");
  }

  static String pem(String type, byte[] bytes) {
    return "-----BEGIN "
        + type
        + "-----\n"
        + Base64.getMimeEncoder(64, new byte[] {10}).encodeToString(bytes)
        + "\n-----END "
        + type
        + "-----\n";
  }

  UUID testBranchId;

  @BeforeEach
  void seed() throws Exception {
    jdbc.execute("UPDATE auth.users SET branch_id = NULL");
    jdbc.execute("TRUNCATE auth.refresh_sessions,auth.users,auth.login_attempts CASCADE");
    testBranchId = UUID.randomUUID();
    testBranchId =
        jdbc.queryForObject(
            "INSERT INTO auth.branches(id, code, name, enabled, created_at, updated_at, version) VALUES (?, 'TB01', 'Test Branch', true, now(), now(), 0) ON CONFLICT (code) DO UPDATE SET enabled = true RETURNING id",
            UUID.class,
            testBranchId);
    admin = seedUser("ADMIN", "admin@test.example", "10000001", true).getId();
    adminToken = login("admin@test.example", PASSWORD).get("accessToken").asText();
  }

  User seedUser(String role, String email, String dni, boolean enabled) {
    var u = new User();
    u.setFirstName("PRUEBA");
    u.setLastName("SVC09");
    u.setEmail(email);
    u.setDni(dni);
    u.setPasswordHash(passwords.encode(PASSWORD));
    u.setRole(roles.findByCode(role).orElseThrow());
    u.setEnabled(enabled);
    if (!role.equals("ADMIN") && testBranchId != null) {
      u.setBranchId(testBranchId);
    }
    return users.saveAndFlush(u);
  }

  JsonNode login(String email, String password) throws Exception {
    var result =
        body(
            mvc.perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn());
    if (email != null
        && email.trim().equalsIgnoreCase("admin@test.example")
        && result.has("accessToken")) {
      adminToken = result.get("accessToken").asText();
    }
    return result;
  }

  JsonNode body(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
    return request.header("Authorization", "Bearer " + adminToken);
  }

  Map<String, Object> createRequest(String email, String dni, String role) {
    var map = new HashMap<String, Object>();
    map.put("firstName", "Lucía");
    map.put("lastName", "García");
    map.put("email", email);
    map.put("dni", dni);
    map.put("password", PASSWORD);
    map.put("role", role);
    map.put("enabled", true);
    if (!role.equals("ADMIN") && testBranchId != null) {
      map.put("branchId", testBranchId);
    }
    return map;
  }

  ResultActions create(Map<String, Object> request) throws Exception {
    return mvc.perform(
        authorized(post("/api/v1/users"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request)));
  }

  ResultActions patchJson(String path, Object request) throws Exception {
    return mvc.perform(
        authorized(patch(path))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request)));
  }

  ResultActions refresh(String raw) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("refreshToken", raw))));
  }

  String signed(
      String issuer, String audience, Instant notBefore, Instant expiry, JwtEncoder signer) {
    return signer
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(jwt.keyId()).build(),
                JwtClaimsSet.builder()
                    .issuer(issuer)
                    .subject(admin.toString())
                    .audience(List.of(audience))
                    .issuedAt(Instant.now().minusSeconds(600))
                    .notBefore(notBefore)
                    .expiresAt(expiry)
                    .id(UUID.randomUUID().toString())
                    .build()))
        .getTokenValue();
  }

  @Test
  void loginAndCurrentProfile() throws Exception {
    login(" ADMIN@test.example ", PASSWORD);
    mvc.perform(authorized(get("/api/v1/auth/me")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value("admin@test.example"))
        .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.permissions").isArray());
    assertThat(users.findById(admin).orElseThrow().getPasswordHash().startsWith("$argon2id$"))
        .isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"wrong-password", "missing", "disabled"})
  void invalidCredentialsAreGeneric(String condition) throws Exception {
    var email = "admin@test.example";
    var pass = "wrong-password";
    if (condition.equals("missing")) email = "missing@test.example";
    if (condition.equals("disabled")) {
      seedUser("VENTAS", "disabled@test.example", "10000002", false);
      email = "disabled@test.example";
      pass = PASSWORD;
    }
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", pass))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Credenciales o sesión inválidas"));
  }

  @Test
  void missingToken() throws Exception {
    mvc.perform(get("/api/v1/users"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"expired", "issuer", "audience", "future", "signature"})
  void invalidJwtIsRejected(String condition) throws Exception {
    var signer = encoder;
    var issuer = jwt.issuer();
    var audience = jwt.audience();
    var nbf = Instant.now().minusSeconds(300);
    var exp = Instant.now().plusSeconds(120);
    switch (condition) {
      case "expired" -> exp = Instant.now().minusSeconds(60);
      case "issuer" -> issuer = "https://wrong.test";
      case "audience" -> audience = "another-api";
      case "future" -> nbf = Instant.now().plusSeconds(60);
      case "signature" -> {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var key = generator.generateKeyPair();
        var rsa =
            new RSAKey.Builder((RSAPublicKey) key.getPublic())
                .privateKey((RSAPrivateKey) key.getPrivate())
                .keyID(jwt.keyId())
                .build();
        signer = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsa)));
      }
    }
    mvc.perform(
            get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + signed(issuer, audience, nbf, exp, signer)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unsignedAndWrongAlgorithmTokensAreRejected() throws Exception {
    for (String algorithm : List.of("none", "HS256")) {
      String header =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(("{\"alg\":\"" + algorithm + "\"}").getBytes());
      String payload =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(("{\"sub\":\"" + admin + "\"}").getBytes());
      mvc.perform(
              get("/api/v1/auth/me")
                  .header("Authorization", "Bearer " + header + "." + payload + ".AAAA"))
          .andExpect(status().isUnauthorized());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"VENTAS", "ALMACEN", "VENTAS_ALMACEN"})
  void operationalRolesCannotAdministerUsers(String role) throws Exception {
    seedUser(role, "operator@test.example", "10000002", true);
    var token = login("operator@test.example", PASSWORD).get("accessToken").asText();
    mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        createRequest("new@test.example", "10000003", "ADMIN"))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanCreateReadEditSearchAndList() throws Exception {
    var created =
        body(
            create(createRequest("New@TEST.example", "10000002", "VENTAS"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn());
    var id = created.get("id").asText();
    assertThat(created.get("email").asText()).isEqualTo("new@test.example");
    mvc.perform(authorized(get("/api/v1/users/" + id))).andExpect(status().isOk());
    patchJson(
            "/api/v1/users/" + id,
            Map.of("firstName", "María", "lastName", "Soto", "phone", "+51999888777"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("María"));
    mvc.perform(
            authorized(get("/api/v1/users"))
                .param("name", "María Soto")
                .param("email", "NEW@test.example")
                .param("dni", "10000002")
                .param("role", "VENTAS")
                .param("enabled", "true")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(id));
  }

  @ParameterizedTest
  @ValueSource(strings = {"email", "dni"})
  void duplicatesAreConflicts(String field) throws Exception {
    create(
            createRequest(
                field.equals("email") ? "ADMIN@test.example" : "new@test.example",
                field.equals("dni") ? "10000001" : "10000002",
                "VENTAS"))
        .andExpect(status().isConflict());
  }

  @Test
  void invalidPayloadsAndMassAssignmentRejected() throws Exception {
    var request = new HashMap<>(createRequest("invalid", "123", "UNKNOWN"));
    request.put("password", "short");
    create(request).andExpect(status().isBadRequest());
    patchJson("/api/v1/users/" + admin, Map.of("passwordHash", "unsafe"))
        .andExpect(status().isBadRequest());
    mvc.perform(authorized(get("/api/v1/users/not-a-uuid"))).andExpect(status().isBadRequest());
    mvc.perform(authorized(get("/api/v1/users")).param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void missingUser() throws Exception {
    mvc.perform(authorized(get("/api/v1/users/" + UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  @Test
  void roleAndStatusChangesRevokeRenewableSessions() throws Exception {
    var operator = seedUser("VENTAS", "operator@test.example", "10000002", true);
    var session = login("operator@test.example", PASSWORD);
    var oldAccess = session.get("accessToken").asText();
    patchJson("/api/v1/users/" + operator.getId() + "/role", Map.of("role", "ALMACEN"))
        .andExpect(status().isOk());
    refresh(session.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldAccess))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.role").value("ALMACEN"));
    patchJson("/api/v1/users/" + operator.getId() + "/status", Map.of("enabled", false))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldAccess))
        .andExpect(status().isUnauthorized());
    patchJson("/api/v1/users/" + operator.getId() + "/status", Map.of("enabled", true))
        .andExpect(status().isOk());
  }

  @Test
  void cannotChangeOwnRoleOrDisableLastAdmin() throws Exception {
    patchJson("/api/v1/users/" + admin + "/role", Map.of("role", "VENTAS"))
        .andExpect(status().isConflict());
    patchJson("/api/v1/users/" + admin + "/status", Map.of("enabled", false))
        .andExpect(status().isConflict());
  }

  @Test
  void currentDatabasePermissionsOverrideJwt() throws Exception {
    Long roleId = roles.findByCode("ADMIN").orElseThrow().getId();
    Long permission =
        jdbc.queryForObject("SELECT id FROM auth.permissions WHERE code='USER_READ'", Long.class);
    try {
      jdbc.update(
          "DELETE FROM auth.role_permissions WHERE role_id=? AND permission_id=?",
          roleId,
          permission);
      mvc.perform(authorized(get("/api/v1/users"))).andExpect(status().isForbidden());
    } finally {
      jdbc.update("INSERT INTO auth.role_permissions VALUES (?,?)", roleId, permission);
    }
  }

  @Test
  void concurrentDisableLeavesOneAdmin() throws Exception {
    var other = seedUser("ADMIN", "other@test.example", "10000002", true);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() -> disableSelf(admin, start));
      var b = pool.submit(() -> disableSelf(other.getId(), start));
      start.countDown();
      assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(users.countByRoleCodeAndEnabledTrue("ADMIN")).isEqualTo(1);
  }

  int disableSelf(UUID id, CountDownLatch start) throws Exception {
    start.await();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new UsernamePasswordAuthenticationToken(id.toString(), "N/A", List.of()));
    SecurityContextHolder.setContext(context);
    try {
      userService.changeStatus(id, false);
      return 200;
    } catch (ApiException e) {
      return e.getStatus().value();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void passwordChangeChecksCurrentAndRevokesSessions() throws Exception {
    var session = login("admin@test.example", PASSWORD);
    mvc.perform(
            authorized(post("/api/v1/auth/change-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "currentPassword", "incorrect", "newPassword", REPLACEMENT_PASSWORD))))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            authorized(post("/api/v1/auth/change-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("currentPassword", PASSWORD, "newPassword", REPLACEMENT_PASSWORD))))
        .andExpect(status().isNoContent());
    refresh(session.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    login("admin@test.example", REPLACEMENT_PASSWORD);
  }

  @Test
  void refreshRotatesAndReplayRevokesSuccessor() throws Exception {
    var original = login("admin@test.example", PASSWORD).get("refreshToken").asText();
    var rotated =
        body(refresh(original).andExpect(status().isOk()).andReturn()).get("refreshToken").asText();
    assertThat(rotated).isNotEqualTo(original);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auth.refresh_sessions WHERE token_hash=?",
                Integer.class,
                original))
        .isZero();
    refresh(original).andExpect(status().isUnauthorized());
    refresh(rotated).andExpect(status().isUnauthorized());
  }

  @Test
  void expiredRefreshRejectedAndLogoutRevokesFamily() throws Exception {
    var expired = login("admin@test.example", PASSWORD).get("refreshToken").asText();
    jdbc.update(
        "UPDATE auth.refresh_sessions SET created_at=now()-interval '2"
            + " days',expires_at=now()-interval '1 day' WHERE token_hash=?",
        RefreshTokens.hash(expired));
    refresh(expired).andExpect(status().isUnauthorized());
    var active = login("admin@test.example", PASSWORD).get("refreshToken").asText();
    mvc.perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", active))))
        .andExpect(status().isNoContent());
    refresh(active).andExpect(status().isUnauthorized());
    // Con sesión única activa, el logout invalida la sesión activa actual del usuario
    mvc.perform(authorized(get("/api/v1/auth/me"))).andExpect(status().isUnauthorized());
    // Un inicio de sesión posterior reactiva una nueva sesión válida
    login("admin@test.example", PASSWORD);
    mvc.perform(authorized(get("/api/v1/auth/me"))).andExpect(status().isOk());
  }

  @Test
  void excessiveLoginsAreLimited() throws Exception {
    for (int i = 0; i < 10; i++)
      mvc.perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"email\":\"unknown@test.example\",\"password\":\"incorrect\"}"))
          .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown@test.example\",\"password\":\"incorrect\"}"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void roleCatalogHealthSwaggerAndPublicKeys() throws Exception {
    mvc.perform(authorized(get("/api/v1/roles")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4));
    mvc.perform(
            authorized(
                get(
                    "/api/v1/roles/"
                        + roles.findByCode("ADMIN").orElseThrow().getId()
                        + "/permissions")))
        .andExpect(status().isOk());
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components").doesNotExist());
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/users']").exists());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/auth/jwks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[0].d").doesNotExist())
        .andExpect(jsonPath("$.keys[0].alg").value("RS256"));
  }

  @Test
  void databaseConstraintsRejectNonNormalizedAndDuplicateData() {
    assertThatThrownBy(
            () -> jdbc.update("UPDATE auth.users SET email='ADMIN@test.example' WHERE id=?", admin))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("UPDATE auth.users SET dni='123' WHERE id=?", admin))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }
}
