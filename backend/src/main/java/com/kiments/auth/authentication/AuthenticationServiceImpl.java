package com.kiments.auth.authentication;

import com.kiments.auth.branches.Branch;
import com.kiments.auth.branches.BranchRepository;
import com.kiments.auth.exception.*;
import com.kiments.auth.security.*;
import com.kiments.auth.sessions.*;
import com.kiments.auth.shared.*;
import com.kiments.auth.users.*;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthenticationServiceImpl implements AuthenticationService {
  private final UserRepository users;
  private final BranchRepository branches;
  private final RefreshSessionRepository sessions;
  private final PasswordEncoder passwords;
  private final TokenService tokens;
  private final CurrentUser current;
  private final UserMapper mapper;
  private final SecurityMutationLock lock;
  private final Duration refreshTtl;
  private final String dummyHash;

  public AuthenticationServiceImpl(
      UserRepository users,
      BranchRepository branches,
      RefreshSessionRepository sessions,
      PasswordEncoder passwords,
      TokenService tokens,
      CurrentUser current,
      UserMapper mapper,
      SecurityMutationLock lock,
      @Value("${app.refresh-ttl:P7D}") Duration refreshTtl) {
    this.users = users;
    this.branches = branches;
    this.sessions = sessions;
    this.passwords = passwords;
    this.tokens = tokens;
    this.current = current;
    this.mapper = mapper;
    this.lock = lock;
    this.refreshTtl = refreshTtl;
    this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    if (refreshTtl.isZero() || refreshTtl.isNegative())
      throw new IllegalArgumentException("Invalid refresh TTL");
  }

  @Override
  @Transactional
  public AuthenticationDtos.TokenResponse login(AuthenticationDtos.Login r) {
    lock.acquire();
    var found = users.findByEmail(EmailNormalizer.normalize(r.email()));
    boolean matches =
        passwords.matches(r.password(), found.map(User::getPasswordHash).orElse(dummyHash));
    if (found.isEmpty() || !matches || !found.get().isEnabled()) throw ApiException.unauthorized();
    var user = found.get();

    if (!user.getRole().getCode().equals("ADMIN")) {
      if (user.getBranchId() == null) {
        throw ApiException.unauthorized();
      }
      var branch = branches.findById(user.getBranchId()).orElse(null);
      if (branch == null || !branch.isEnabled()) {
        throw ApiException.unauthorized();
      }
    }

    var now = Instant.now();
    user.setLastLoginAt(now);
    if (passwords.upgradeEncoding(user.getPasswordHash()))
      user.setPasswordHash(passwords.encode(r.password()));

    // Invalidar todas las sesiones refresh previas del usuario
    sessions.revokeUser(user.getId(), now);

    // Asignar nueva familia de sesión activa
    UUID newFamilyId = UUID.randomUUID();
    user.setActiveFamilyId(newFamilyId);
    users.saveAndFlush(user);

    return issue(user, newFamilyId, now.plus(refreshTtl));
  }

  @Override
  public AuthenticationDtos.Profile me() {
    var user = current.require();
    return new AuthenticationDtos.Profile(
        mapper.toResponse(user),
        user.getRole().getPermissions().stream().map(p -> p.getCode()).sorted().toList());
  }

  @Override
  @Transactional
  public void changePassword(AuthenticationDtos.ChangePassword r) {
    lock.acquire();
    var user = current.require();
    if (!passwords.matches(r.currentPassword(), user.getPasswordHash()))
      throw ApiException.unauthorized();
    if (r.currentPassword().equals(r.newPassword()))
      throw ApiException.conflict("La nueva contraseña debe ser diferente");
    user.setPasswordHash(passwords.encode(r.newPassword()));
    user.setActiveFamilyId(null);
    users.saveAndFlush(user);
    sessions.revokeUser(user.getId(), Instant.now());
  }

  @Override
  @Transactional(noRollbackFor = SessionRejectedException.class)
  public AuthenticationDtos.TokenResponse refresh(String token) {
    lock.acquire();
    var session =
        sessions
            .findByTokenHash(RefreshTokens.hash(token))
            .orElseThrow(SessionRejectedException::new);
    var now = Instant.now();
    if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)) {
      sessions.revokeFamily(session.getFamilyId(), now);
      throw new SessionRejectedException();
    }
    var user = users.findById(session.getUserId()).filter(User::isEnabled).orElse(null);
    if (user == null) {
      sessions.revokeFamily(session.getFamilyId(), now);
      throw new SessionRejectedException();
    }

    // Validar que el refresh token pertenezca a la familia de sesión activa del usuario
    if (user.getActiveFamilyId() == null || !user.getActiveFamilyId().equals(session.getFamilyId())) {
      sessions.revokeFamily(session.getFamilyId(), now);
      throw new SessionRejectedException();
    }

    if (!user.getRole().getCode().equals("ADMIN")) {
      if (user.getBranchId() == null) {
        sessions.revokeFamily(session.getFamilyId(), now);
        throw new SessionRejectedException();
      }
      var branch = branches.findById(user.getBranchId()).orElse(null);
      if (branch == null || !branch.isEnabled()) {
        sessions.revokeFamily(session.getFamilyId(), now);
        throw new SessionRejectedException();
      }
    }

    var next = createSession(user.getId(), session.getFamilyId(), session.getExpiresAt());
    session.setRevokedAt(now);
    session.setReplacedBy(next.session().getId());
    return response(user, next.raw());
  }

  @Override
  @Transactional
  public void logout(String token) {
    lock.acquire();
    var now = Instant.now();
    sessions
        .findByTokenHash(RefreshTokens.hash(token))
        .ifPresent(
            s -> {
              sessions.revokeFamily(s.getFamilyId(), now);
              users
                  .findById(s.getUserId())
                  .ifPresent(
                      u -> {
                        if (s.getFamilyId().equals(u.getActiveFamilyId())) {
                          u.setActiveFamilyId(null);
                          users.saveAndFlush(u);
                        }
                      });
            });
  }

  private AuthenticationDtos.TokenResponse issue(User user, UUID family, Instant expires) {
    return response(user, createSession(user.getId(), family, expires).raw());
  }

  private AuthenticationDtos.TokenResponse response(User user, String raw) {
    return new AuthenticationDtos.TokenResponse(
        tokens.issue(user), "Bearer", tokens.expiresIn(), raw);
  }

  private IssuedSession createSession(UUID user, UUID family, Instant expires) {
    String raw = RefreshTokens.generate();
    var session = new RefreshSession();
    session.setUserId(user);
    session.setFamilyId(family);
    session.setTokenHash(RefreshTokens.hash(raw));
    session.setCreatedAt(Instant.now());
    session.setExpiresAt(expires);
    sessions.saveAndFlush(session);
    return new IssuedSession(session, raw);
  }

  private record IssuedSession(RefreshSession session, String raw) {}
}
