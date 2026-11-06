package com.kiments.auth.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.kiments.auth.config.bootstrap.*;
import com.kiments.auth.permissions.Permission;
import com.kiments.auth.roles.*;
import com.kiments.auth.shared.SecurityMutationLock;
import com.kiments.auth.users.*;
import jakarta.validation.Validator;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.*;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class BootstrapGuardsTest {
  @Mock UserRepository users;
  @Mock RoleRepository roles;
  @Mock InitializationRepository state;
  @Mock SecurityMutationLock lock;
  @Mock PasswordEncoder encoder;
  @Mock Validator validator;
  @Mock ApplicationContext context;
  @Mock Flyway flyway;
  MockEnvironment env;
  BootstrapAdminService service;

  @BeforeEach
  void setup() {
    env = new MockEnvironment();
    env.setActiveProfiles("dev");
    service =
        new BootstrapAdminServiceImpl(
            users, roles, state, lock, encoder, validator, env, context, flyway);
  }

  @Test
  void disabledBootstrapDoesNotAccessPersistence() {
    assertThat(service.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV))
        .isEqualTo(BootstrapAdminService.Outcome.DISABLED);
    verifyNoInteractions(users, state, flyway, lock);
  }

  @Test
  void legacyFlagCannotBypassNewControls() {
    env.setProperty("app.bootstrap.enabled", "true");
    assertThatThrownBy(() -> new AdminBootstrap(service, env).run(null))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(users, state, flyway, lock);
  }

  @Test
  void invalidFlywayPreventsAllWrites() {
    env.setProperty("app.bootstrap.admin.enabled", "true");
    env.setProperty("APP_BOOTSTRAP_ADMIN_ENABLED", "true");
    var result = new ValidateResult("test", "test", null, false, 0, List.of(), List.of());
    when(flyway.validateWithResult()).thenReturn(result);
    assertThatThrownBy(() -> service.initialize(BootstrapAdminService.Mode.AUTOMATIC_DEV))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(users, state, lock);
  }

  @Test
  void explicitHeadlessProductionProcedureUsesSameInitializer() {
    env.setActiveProfiles("prod");
    env.setProperty("app.bootstrap.admin.manual-enabled", "true");
    env.setProperty("APP_BOOTSTRAP_ADMIN_FIRST_NAME", "Test");
    env.setProperty("APP_BOOTSTRAP_ADMIN_LAST_NAME", "Operator");
    env.setProperty("APP_BOOTSTRAP_ADMIN_DNI", "90000002");
    env.setProperty("APP_BOOTSTRAP_ADMIN_EMAIL", "operator@example.test");
    env.setProperty("APP_BOOTSTRAP_ADMIN_PASSWORD", "Isolated-manual-fixture-2026");
    var result = new ValidateResult("test", "test", null, true, 3, List.of(), List.of());
    when(flyway.validateWithResult()).thenReturn(result);
    var info = mock(MigrationInfoService.class);
    when(flyway.info()).thenReturn(info);
    when(info.pending()).thenReturn(new MigrationInfo[0]);
    when(state.isPending()).thenReturn(true);
    var role = mock(Role.class);
    var permissions = new HashSet<Permission>();
    for (String code :
        List.of("USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DISABLE", "USER_ROLE_CHANGE")) {
      var permission = mock(Permission.class);
      when(permission.getCode()).thenReturn(code);
      permissions.add(permission);
    }
    when(role.getPermissions()).thenReturn(permissions);
    when(roles.findByCode("ADMIN")).thenReturn(Optional.of(role));
    when(validator.validate(any(UserDtos.Create.class))).thenReturn(Set.of());
    when(encoder.encode(anyString())).thenReturn("encoded-fixture");
    assertThat(service.initialize(BootstrapAdminService.Mode.MANUAL))
        .isEqualTo(BootstrapAdminService.Outcome.CREATED);
    verify(users)
        .saveAndFlush(argThat(u -> u.getEmail().equals("operator@example.test") && u.isEnabled()));
    verify(state).complete(any(UUID.class), eq("MANUAL"));
  }
}
