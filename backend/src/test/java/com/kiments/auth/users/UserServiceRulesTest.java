package com.kiments.auth.users;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.kiments.auth.branches.Branch;
import com.kiments.auth.branches.BranchRepository;
import com.kiments.auth.exception.ApiException;
import com.kiments.auth.roles.*;
import com.kiments.auth.security.CurrentUser;
import com.kiments.auth.sessions.*;
import com.kiments.auth.shared.SecurityMutationLock;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceRulesTest {
  @Mock UserRepository users;
  @Mock RoleRepository roles;
  @Mock BranchRepository branches;
  @Mock PasswordEncoder encoder;
  @Mock UserMapper mapper;
  @Mock CurrentUser current;
  @Mock SecurityMutationLock lock;
  @Mock RefreshSessionRepository sessions;
  UserService service;

  @BeforeEach
  void setup() {
    service =
        new UserServiceImpl(
            users, roles, branches, encoder, mapper, current, lock, sessions);
  }

  void conflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
    assertThatThrownBy(operation)
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
  }

  void notFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
    assertThatThrownBy(operation)
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  UserDtos.Create request() {
    return new UserDtos.Create(
        "PRUEBA",
        "Unit",
        "90000001",
        null,
        " PRUEBA@EXAMPLE.TEST ",
        RefreshTokens.generate(),
        "VENTAS",
        true,
        null);
  }

  @Test
  void selfRoleChangeIsRejectedBeforeLookingUpTarget() {
    var actor = new User();
    when(current.requireAdmin("USER_ROLE_CHANGE")).thenReturn(actor);
    conflict(() -> service.changeRole(actor.getId(), "VENTAS"));
    verify(lock).acquire();
    verifyNoInteractions(users, roles, sessions);
  }

  @Test
  void lastEnabledAdminCannotBeDisabled() {
    var target = new User();
    var role = mock(Role.class);
    target.setRole(role);
    target.setEnabled(true);
    when(role.getCode()).thenReturn("ADMIN");
    when(users.findById(target.getId())).thenReturn(Optional.of(target));
    when(users.countByRoleCodeAndEnabledTrue("ADMIN")).thenReturn(1L);
    conflict(() -> service.changeStatus(target.getId(), false));
    assertThat(target.isEnabled()).isTrue();
    verify(users, never()).saveAndFlush(any());
    verifyNoInteractions(sessions);
  }

  @Test
  void revokedPermissionBlocksMutationBeforeLoadingUser() {
    when(current.requireAdmin("USER_UPDATE")).thenThrow(ApiException.forbidden());
    assertThatThrownBy(
            () ->
                service.update(
                    UUID.randomUUID(), new UserDtos.Update("PRUEBA", null, null, null, null)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    var order = inOrder(lock, current);
    order.verify(lock).acquire();
    order.verify(current).requireAdmin("USER_UPDATE");
    verifyNoInteractions(users, sessions);
  }

  @Test
  void duplicateNormalizedEmailDoesNotHashOrPersistPassword() {
    when(users.existsByEmail("prueba@example.test")).thenReturn(true);
    conflict(() -> service.create(request()));
    verify(users, never()).saveAndFlush(any());
    verifyNoInteractions(encoder, roles, branches);
  }

  @Test
  void duplicateDniDoesNotHashOrPersistPassword() {
    when(users.existsByDni("90000001")).thenReturn(true);
    conflict(() -> service.create(request()));
    verify(users, never()).saveAndFlush(any());
    verifyNoInteractions(encoder, roles, branches);
  }

  // ── Reglas de Sucursales en Creación ──────────────────────────────────────

  @Test
  void operationalUserWithoutBranchThrowsConflict() {
    var role = mock(Role.class);
    when(role.getCode()).thenReturn("VENTAS");
    when(roles.findByCode("VENTAS")).thenReturn(Optional.of(role));

    conflict(() -> service.create(request()));
    verify(users, never()).saveAndFlush(any());
  }

  @Test
  void adminUserWithBranchThrowsConflict() {
    var role = mock(Role.class);
    when(role.getCode()).thenReturn("ADMIN");
    when(roles.findByCode("ADMIN")).thenReturn(Optional.of(role));

    var req =
        new UserDtos.Create(
            "Admin",
            "Test",
            "90000002",
            null,
            "admin@example.test",
            RefreshTokens.generate(),
            "ADMIN",
            true,
            UUID.randomUUID());

    conflict(() -> service.create(req));
    verify(users, never()).saveAndFlush(any());
  }

  @Test
  void operationalUserWithInactiveBranchThrowsConflict() {
    var role = mock(Role.class);
    when(role.getCode()).thenReturn("ALMACEN");
    when(roles.findByCode("ALMACEN")).thenReturn(Optional.of(role));

    UUID branchId = UUID.randomUUID();
    var branch = new Branch("INACT");
    branch.setEnabled(false);
    when(branches.findById(branchId)).thenReturn(Optional.of(branch));

    var req =
        new UserDtos.Create(
            "Worker",
            "Almacen",
            "90000003",
            null,
            "almacen@example.test",
            RefreshTokens.generate(),
            "ALMACEN",
            true,
            branchId);

    conflict(() -> service.create(req));
    verify(users, never()).saveAndFlush(any());
  }

  @Test
  void operationalUserWithNonexistentBranchThrowsNotFound() {
    var role = mock(Role.class);
    when(role.getCode()).thenReturn("VENTAS_ALMACEN");
    when(roles.findByCode("VENTAS_ALMACEN")).thenReturn(Optional.of(role));

    UUID branchId = UUID.randomUUID();
    when(branches.findById(branchId)).thenReturn(Optional.empty());

    var req =
        new UserDtos.Create(
            "Worker",
            "Dual",
            "90000004",
            null,
            "dual@example.test",
            RefreshTokens.generate(),
            "VENTAS_ALMACEN",
            true,
            branchId);

    notFound(() -> service.create(req));
    verify(users, never()).saveAndFlush(any());
  }

  // ── Reglas de Reasignación de Sucursal ─────────────────────────────────────

  @Test
  void changeBranchToSelfThrowsConflict() {
    var actor = new User();
    when(current.requireAdmin("USER_UPDATE")).thenReturn(actor);
    conflict(() -> service.changeBranch(actor.getId(), UUID.randomUUID()));
    verify(lock).acquire();
    verifyNoInteractions(branches);
  }

  @Test
  void changeBranchForAdminUserThrowsConflict() {
    var actor = new User();
    var target = new User();
    var adminRole = mock(Role.class);
    when(adminRole.getCode()).thenReturn("ADMIN");
    target.setRole(adminRole);

    when(current.requireAdmin("USER_UPDATE")).thenReturn(actor);
    when(users.findById(target.getId())).thenReturn(Optional.of(target));

    conflict(() -> service.changeBranch(target.getId(), UUID.randomUUID()));
    verify(sessions, never()).revokeUser(any(), any());
  }

  @Test
  void changeBranchWithInactiveBranchThrowsConflict() {
    var actor = new User();
    var target = new User();
    var opRole = mock(Role.class);
    when(opRole.getCode()).thenReturn("VENTAS");
    target.setRole(opRole);

    UUID branchId = UUID.randomUUID();
    var branch = new Branch("INACT");
    branch.setEnabled(false);

    when(current.requireAdmin("USER_UPDATE")).thenReturn(actor);
    when(users.findById(target.getId())).thenReturn(Optional.of(target));
    when(branches.findById(branchId)).thenReturn(Optional.of(branch));

    conflict(() -> service.changeBranch(target.getId(), branchId));
    verify(sessions, never()).revokeUser(any(), any());
  }

  @Test
  void changeBranchSuccessUpdatesAndRevokesSession() {
    var actor = new User();
    var target = new User();
    var opRole = mock(Role.class);
    when(opRole.getCode()).thenReturn("VENTAS");
    target.setRole(opRole);

    UUID branchId = UUID.randomUUID();
    var branch = new Branch("ACTIVA");
    branch.setEnabled(true);

    when(current.requireAdmin("USER_UPDATE")).thenReturn(actor);
    when(users.findById(target.getId())).thenReturn(Optional.of(target));
    when(branches.findById(branchId)).thenReturn(Optional.of(branch));
    when(users.saveAndFlush(target)).thenReturn(target);

    service.changeBranch(target.getId(), branchId);

    assertThat(target.getBranchId()).isEqualTo(branch.getId());
    verify(sessions).revokeUser(eq(target.getId()), any());
    verify(users).saveAndFlush(target);
  }

  // ── Reglas de Cambio de Rol y Sucursal ─────────────────────────────────────

  @Test
  void changeRoleFromAdminToOperationalWithoutBranchThrowsConflict() {
    var actor = new User();
    var target = new User();
    var adminRole = mock(Role.class);
    when(adminRole.getCode()).thenReturn("ADMIN");
    target.setRole(adminRole);
    target.setEnabled(true);

    var ventasRole = mock(Role.class);
    when(ventasRole.getCode()).thenReturn("VENTAS");

    when(current.requireAdmin("USER_ROLE_CHANGE")).thenReturn(actor);
    when(users.findById(target.getId())).thenReturn(Optional.of(target));
    when(roles.findByCode("VENTAS")).thenReturn(Optional.of(ventasRole));
    when(users.countByRoleCodeAndEnabledTrue("ADMIN")).thenReturn(2L);

    conflict(() -> service.changeRole(target.getId(), new UserDtos.ChangeRole("VENTAS", null)));
    verify(sessions, never()).revokeUser(any(), any());
  }

  @Test
  void changeRoleFromOperationalToAdminClearsBranch() {
    var actor = new User();
    var target = new User();
    var ventasRole = mock(Role.class);
    when(ventasRole.getCode()).thenReturn("VENTAS");
    target.setRole(ventasRole);
    target.setBranchId(UUID.randomUUID());

    var adminRole = mock(Role.class);
    when(adminRole.getCode()).thenReturn("ADMIN");

    when(current.requireAdmin("USER_ROLE_CHANGE")).thenReturn(actor);
    when(users.findById(target.getId())).thenReturn(Optional.of(target));
    when(roles.findByCode("ADMIN")).thenReturn(Optional.of(adminRole));
    when(users.saveAndFlush(target)).thenReturn(target);

    service.changeRole(target.getId(), new UserDtos.ChangeRole("ADMIN", null));

    assertThat(target.getBranchId()).isNull();
    assertThat(target.getRole()).isEqualTo(adminRole);
    verify(sessions).revokeUser(eq(target.getId()), any());
  }

  @Test
  void changeRoleBetweenOperationalPreservesActiveBranch() {
    var actor = new User();
    var target = new User();
    var ventasRole = mock(Role.class);
    when(ventasRole.getCode()).thenReturn("VENTAS");
    target.setRole(ventasRole);
    UUID currentBranchId = UUID.randomUUID();
    target.setBranchId(currentBranchId);

    var almacenRole = mock(Role.class);
    when(almacenRole.getCode()).thenReturn("ALMACEN");

    var currentBranch = new Branch("ACTIVA");
    currentBranch.setEnabled(true);

    when(current.requireAdmin("USER_ROLE_CHANGE")).thenReturn(actor);
    when(users.findById(target.getId())).thenReturn(Optional.of(target));
    when(roles.findByCode("ALMACEN")).thenReturn(Optional.of(almacenRole));
    when(branches.findById(currentBranchId)).thenReturn(Optional.of(currentBranch));
    when(users.saveAndFlush(target)).thenReturn(target);

    service.changeRole(target.getId(), new UserDtos.ChangeRole("ALMACEN", null));

    assertThat(target.getBranchId()).isEqualTo(currentBranchId);
    assertThat(target.getRole()).isEqualTo(almacenRole);
    verify(sessions).revokeUser(eq(target.getId()), any());
  }

  @Test
  void changeRoleBetweenOperationalRejectsInactiveExistingBranch() {
    var actor = new User();
    var target = new User();
    var ventasRole = mock(Role.class);
    when(ventasRole.getCode()).thenReturn("VENTAS");
    target.setRole(ventasRole);
    UUID currentBranchId = UUID.randomUUID();
    target.setBranchId(currentBranchId);

    var almacenRole = mock(Role.class);
    when(almacenRole.getCode()).thenReturn("ALMACEN");

    var currentBranch = new Branch("INACT");
    currentBranch.setEnabled(false);

    when(current.requireAdmin("USER_ROLE_CHANGE")).thenReturn(actor);
    when(users.findById(target.getId())).thenReturn(Optional.of(target));
    when(roles.findByCode("ALMACEN")).thenReturn(Optional.of(almacenRole));
    when(branches.findById(currentBranchId)).thenReturn(Optional.of(currentBranch));

    conflict(() -> service.changeRole(target.getId(), new UserDtos.ChangeRole("ALMACEN", null)));
    verify(sessions, never()).revokeUser(any(), any());
  }
}
