package com.kiments.auth.users;

import com.kiments.auth.branches.Branch;
import com.kiments.auth.branches.BranchRepository;
import com.kiments.auth.exception.ApiException;
import com.kiments.auth.roles.RoleRepository;
import com.kiments.auth.security.CurrentUser;
import com.kiments.auth.sessions.RefreshSessionRepository;
import com.kiments.auth.shared.*;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
  private final UserRepository users;
  private final RoleRepository roles;
  private final BranchRepository branches;
  private final PasswordEncoder passwords;
  private final UserMapper mapper;
  private final CurrentUser current;
  private final SecurityMutationLock lock;
  private final RefreshSessionRepository sessions;

  public UserServiceImpl(
      UserRepository users,
      RoleRepository roles,
      BranchRepository branches,
      PasswordEncoder passwords,
      UserMapper mapper,
      CurrentUser current,
      SecurityMutationLock lock,
      RefreshSessionRepository sessions) {
    this.users = users;
    this.roles = roles;
    this.branches = branches;
    this.passwords = passwords;
    this.mapper = mapper;
    this.current = current;
    this.lock = lock;
    this.sessions = sessions;
  }

  @Override
  @Transactional
  public UserDtos.Response create(UserDtos.Create r) {
    lock.acquire();
    current.requireAdmin("USER_CREATE");
    var email = EmailNormalizer.normalize(r.email());
    if (users.existsByEmail(email)) throw ApiException.conflict("El correo ya está registrado");
    if (users.existsByDni(r.dni())) throw ApiException.conflict("El DNI ya está registrado");

    var role = roles.findByCode(r.role()).orElseThrow(ApiException::notFound);
    UUID branchId = null;

    if (role.getCode().equals("ADMIN")) {
      if (r.branchId() != null) {
        throw ApiException.conflict(
            "Los administradores tienen alcance global y no deben tener una sucursal asignada");
      }
    } else {
      if (r.branchId() == null) {
        throw ApiException.conflict("Los trabajadores operativos deben tener una sucursal asignada");
      }
      var branch = branches.findById(r.branchId()).orElseThrow(ApiException::notFound);
      if (!branch.isEnabled()) {
        throw ApiException.conflict("No se puede asignar una sucursal inactiva");
      }
      branchId = branch.getId();
    }

    var u = new User();
    u.setFirstName(r.firstName().trim());
    u.setLastName(r.lastName().trim());
    u.setDni(r.dni());
    u.setPhone(r.phone());
    u.setEmail(email);
    u.setPasswordHash(passwords.encode(r.password()));
    u.setRole(role);
    u.setBranchId(branchId);
    u.setEnabled(r.enabled());
    return mapper.toResponse(users.saveAndFlush(u));
  }

  @Override
  public UserDtos.Response get(UUID id) {
    current.requireAdmin("USER_READ");
    return mapper.toResponse(find(id));
  }

  @Override
  public UserDtos.PageResponse<UserDtos.Response> list(
      String name,
      String email,
      String dni,
      String role,
      UUID branchId,
      Boolean enabled,
      int page,
      int size) {
    current.requireAdmin("USER_READ");
    if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Invalid pagination");
    var result =
        users.findAll(
            (root, query, cb) -> {
              List<Predicate> conditions = new ArrayList<>();
              if (name != null && !name.isBlank()) {
                String pattern = "%" + escape(name.trim().toLowerCase(Locale.ROOT)) + "%";
                conditions.add(
                    cb.like(
                        cb.lower(
                            cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))),
                        pattern,
                        '\\'));
              }
              if (email != null)
                conditions.add(cb.equal(root.get("email"), EmailNormalizer.normalize(email)));
              if (dni != null) conditions.add(cb.equal(root.get("dni"), dni));
              if (role != null) conditions.add(cb.equal(root.get("role").get("code"), role));
              if (branchId != null) conditions.add(cb.equal(root.get("branchId"), branchId));
              if (enabled != null) conditions.add(cb.equal(root.get("enabled"), enabled));
              return cb.and(conditions.toArray(Predicate[]::new));
            },
            PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id"))));
    return new UserDtos.PageResponse<>(
        result.map(mapper::toResponse).getContent(),
        page,
        size,
        result.getTotalElements(),
        result.getTotalPages());
  }

  private String escape(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  @Override
  @Transactional
  public UserDtos.Response update(UUID id, UserDtos.Update r) {
    lock.acquire();
    current.requireAdmin("USER_UPDATE");
    var u = find(id);
    if (r.firstName() != null) u.setFirstName(r.firstName().trim());
    if (r.lastName() != null) u.setLastName(r.lastName().trim());
    if (r.dni() != null) u.setDni(r.dni());
    if (r.email() != null) u.setEmail(EmailNormalizer.normalize(r.email()));
    if (r.phone() != null) u.setPhone(r.phone().isBlank() ? null : r.phone());
    return mapper.toResponse(users.saveAndFlush(u));
  }

  @Override
  @Transactional
  public UserDtos.Response changeRole(UUID id, UserDtos.ChangeRole r) {
    lock.acquire();
    var actor = current.requireAdmin("USER_ROLE_CHANGE");
    if (actor.getId().equals(id)) throw ApiException.conflict("No puede cambiar su propio rol");
    var u = find(id);
    var newRole = roles.findByCode(r.role()).orElseThrow(ApiException::notFound);
    boolean targetWasAdmin = u.getRole().getCode().equals("ADMIN");
    boolean newRoleIsAdmin = newRole.getCode().equals("ADMIN");

    if (targetWasAdmin && !newRoleIsAdmin) protectLastAdmin(u);

    if (newRoleIsAdmin) {
      u.setBranchId(null);
    } else if (targetWasAdmin) {
      if (r.branchId() == null) {
        throw ApiException.conflict(
            "Debe especificar una sucursal para asignar al usuario un rol operativo");
      }
      var branch = branches.findById(r.branchId()).orElseThrow(ApiException::notFound);
      if (!branch.isEnabled()) {
        throw ApiException.conflict("No se puede asignar una sucursal inactiva");
      }
      u.setBranchId(branch.getId());
    } else {
      // Cambio entre roles operativos
      if (r.branchId() != null) {
        var branch = branches.findById(r.branchId()).orElseThrow(ApiException::notFound);
        if (!branch.isEnabled()) {
          throw ApiException.conflict("No se puede asignar una sucursal inactiva");
        }
        u.setBranchId(branch.getId());
      } else {
        if (u.getBranchId() == null) {
          throw ApiException.conflict(
              "El usuario no tiene una sucursal asignada; debe especificar una sucursal válida");
        }
        var currentBranch = branches.findById(u.getBranchId()).orElse(null);
        if (currentBranch == null || !currentBranch.isEnabled()) {
          throw ApiException.conflict(
              "La sucursal actual del usuario está inactiva o no existe; debe especificar una sucursal activa");
        }
      }
    }

    u.setRole(newRole);
    sessions.revokeUser(id, Instant.now());
    return mapper.toResponse(users.saveAndFlush(u));
  }

  @Override
  @Transactional
  public UserDtos.Response changeBranch(UUID id, UUID branchId) {
    lock.acquire();
    var actor = current.requireAdmin("USER_UPDATE");
    if (actor.getId().equals(id)) throw ApiException.conflict("No puede cambiar su propia sucursal");
    var u = find(id);
    if (u.getRole().getCode().equals("ADMIN")) {
      throw ApiException.conflict(
          "Los administradores tienen alcance global y no pueden ser asignados a una sucursal");
    }
    var branch = branches.findById(branchId).orElseThrow(ApiException::notFound);
    if (!branch.isEnabled()) {
      throw ApiException.conflict("No se puede asignar una sucursal inactiva");
    }
    u.setBranchId(branch.getId());
    sessions.revokeUser(id, Instant.now());
    return mapper.toResponse(users.saveAndFlush(u));
  }

  @Override
  @Transactional
  public UserDtos.Response changeStatus(UUID id, boolean enabled) {
    lock.acquire();
    current.requireAdmin("USER_DISABLE");
    var u = find(id);
    if (!enabled) protectLastAdmin(u);
    u.setEnabled(enabled);
    if (!enabled) {
      u.setActiveFamilyId(null);
      sessions.revokeUser(id, Instant.now());
    }
    return mapper.toResponse(users.saveAndFlush(u));
  }

  private void protectLastAdmin(User u) {
    if (u.isEnabled()
        && u.getRole().getCode().equals("ADMIN")
        && users.countByRoleCodeAndEnabledTrue("ADMIN") <= 1)
      throw ApiException.conflict("Debe existir al menos un administrador activo");
  }

  private User find(UUID id) {
    return users.findById(id).orElseThrow(ApiException::notFound);
  }
}
