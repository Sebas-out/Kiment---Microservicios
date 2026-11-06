package com.kiments.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.kiments.auth.authentication.*;
import com.kiments.auth.branches.Branch;
import com.kiments.auth.branches.BranchRepository;
import com.kiments.auth.exception.ApiException;
import com.kiments.auth.roles.Role;
import com.kiments.auth.security.*;
import com.kiments.auth.sessions.*;
import com.kiments.auth.shared.SecurityMutationLock;
import com.kiments.auth.users.*;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {
  @Mock UserRepository users;
  @Mock BranchRepository branches;
  @Mock RefreshSessionRepository sessions;
  @Mock PasswordEncoder passwords;
  @Mock TokenService tokens;
  @Mock CurrentUser current;
  @Mock SecurityMutationLock lock;
  AuthenticationServiceImpl service;

  @BeforeEach
  void setup() {
    when(passwords.encode(anyString())).thenReturn("dummy-hash");
    service =
        new AuthenticationServiceImpl(
            users,
            branches,
            sessions,
            passwords,
            tokens,
            current,
            new UserMapper(),
            lock,
            Duration.ofDays(7));
  }

  @Test
  void missingUserStillVerifiesDummyPassword() {
    when(users.findByEmail("missing@test.example")).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> service.login(new AuthenticationDtos.Login(" MISSING@test.example ", "bad")))
        .isInstanceOf(ApiException.class);
    verify(passwords).matches("bad", "dummy-hash");
    verifyNoInteractions(tokens, sessions);
  }

  @Test
  void disabledUserCannotLoginEvenWithCorrectPassword() {
    var user = new User();
    user.setPasswordHash("stored");
    user.setEnabled(false);
    when(users.findByEmail("disabled@test.example")).thenReturn(Optional.of(user));
    when(passwords.matches("good", "stored")).thenReturn(true);
    assertThatThrownBy(
            () -> service.login(new AuthenticationDtos.Login("disabled@test.example", "good")))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(tokens, sessions);
  }

  @Test
  void operationalUserWithoutBranchCannotLogin() {
    var user = new User();
    user.setPasswordHash("stored");
    user.setEnabled(true);
    var role = mock(Role.class);
    when(role.getCode()).thenReturn("VENTAS");
    user.setRole(role);
    user.setBranchId(null);

    when(users.findByEmail("worker@test.example")).thenReturn(Optional.of(user));
    when(passwords.matches("good", "stored")).thenReturn(true);

    assertThatThrownBy(
            () -> service.login(new AuthenticationDtos.Login("worker@test.example", "good")))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(tokens);
  }

  @Test
  void operationalUserWithInactiveBranchCannotLogin() {
    var user = new User();
    user.setPasswordHash("stored");
    user.setEnabled(true);
    var role = mock(Role.class);
    when(role.getCode()).thenReturn("VENTAS");
    user.setRole(role);
    UUID branchId = UUID.randomUUID();
    user.setBranchId(branchId);

    var branch = new Branch("INACT");
    branch.setEnabled(false);

    when(users.findByEmail("worker@test.example")).thenReturn(Optional.of(user));
    when(passwords.matches("good", "stored")).thenReturn(true);
    when(branches.findById(branchId)).thenReturn(Optional.of(branch));

    assertThatThrownBy(
            () -> service.login(new AuthenticationDtos.Login("worker@test.example", "good")))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(tokens);
  }

  @Test
  void operationalUserWithActiveBranchCanLogin() {
    var user = new User();
    user.setPasswordHash("stored");
    user.setEnabled(true);
    var role = mock(Role.class);
    when(role.getCode()).thenReturn("VENTAS");
    user.setRole(role);
    UUID branchId = UUID.randomUUID();
    user.setBranchId(branchId);

    var branch = new Branch("ACTIVA");
    branch.setEnabled(true);

    when(users.findByEmail("worker@test.example")).thenReturn(Optional.of(user));
    when(passwords.matches("good", "stored")).thenReturn(true);
    when(branches.findById(branchId)).thenReturn(Optional.of(branch));
    when(tokens.issue(user)).thenReturn("jwt-token");
    when(tokens.expiresIn()).thenReturn(900L);

    var response = service.login(new AuthenticationDtos.Login("worker@test.example", "good"));
    assertThat(response.accessToken()).isEqualTo("jwt-token");
  }

  @Test
  void adminUserWithoutBranchCanLogin() {
    var user = new User();
    user.setPasswordHash("stored");
    user.setEnabled(true);
    var role = mock(Role.class);
    when(role.getCode()).thenReturn("ADMIN");
    user.setRole(role);
    user.setBranchId(null);

    when(users.findByEmail("admin@test.example")).thenReturn(Optional.of(user));
    when(passwords.matches("good", "stored")).thenReturn(true);
    when(tokens.issue(user)).thenReturn("admin-jwt");
    when(tokens.expiresIn()).thenReturn(900L);

    var response = service.login(new AuthenticationDtos.Login("admin@test.example", "good"));
    assertThat(response.accessToken()).isEqualTo("admin-jwt");
    verifyNoInteractions(branches);
  }
}
