package com.kiments.auth.branches;

import com.kiments.auth.exception.ApiException;
import com.kiments.auth.security.CurrentUser;
import com.kiments.auth.shared.SecurityMutationLock;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BranchServiceImpl implements BranchService {

  private final BranchRepository branches;
  private final BranchMapper mapper;
  private final CurrentUser current;
  private final SecurityMutationLock lock;

  public BranchServiceImpl(
      BranchRepository branches,
      BranchMapper mapper,
      CurrentUser current,
      SecurityMutationLock lock) {
    this.branches = branches;
    this.mapper = mapper;
    this.current = current;
    this.lock = lock;
  }

  @Override
  @Transactional
  public BranchDtos.Response create(BranchDtos.Create r) {
    lock.acquire();
    current.requireAdmin("BRANCH_CREATE");
    String code = r.code().toUpperCase(Locale.ROOT);
    if (branches.existsByCodeIgnoreCase(code))
      throw ApiException.conflict("El código de sucursal ya está registrado");

    String sunatCode = normalizeOptional(r.sunatCode());
    if (sunatCode != null && branches.existsBySunatCode(sunatCode))
      throw ApiException.conflict("El código de establecimiento SUNAT ya está registrado");

    var b = new Branch(code);
    b.setName(r.name().trim());
    b.setAddress(normalizeOptional(r.address()));
    b.setPhone(normalizeOptional(r.phone()));
    b.setCity(normalizeOptional(r.city()));
    b.setUbigeo(normalizeOptional(r.ubigeo()));
    b.setEmail(normalizeEmail(r.email()));
    b.setBranchType(r.branchType());
    b.setSunatCode(sunatCode);
    b.setEnabled(r.enabled());
    return mapper.toResponse(branches.saveAndFlush(b));
  }

  @Override
  public BranchDtos.Response get(UUID id) {
    current.requireAdmin("BRANCH_READ");
    return mapper.toResponse(find(id));
  }

  @Override
  public BranchDtos.PageResponse<BranchDtos.Response> list(
      String name,
      String code,
      BranchType type,
      String city,
      String sunatCode,
      Boolean enabled,
      int page,
      int size) {
    current.requireAdmin("BRANCH_READ");
    if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Parámetros de paginación inválidos");

    var result =
        branches.findAll(
            (root, query, cb) -> {
              List<Predicate> conditions = new ArrayList<>();
              if (name != null && !name.isBlank()) {
                String pattern = "%" + escape(name.trim().toLowerCase(Locale.ROOT)) + "%";
                conditions.add(cb.like(cb.lower(root.get("name")), pattern, '\\'));
              }
              if (code != null && !code.isBlank())
                conditions.add(
                    cb.equal(cb.lower(root.get("code")), code.trim().toLowerCase(Locale.ROOT)));
              if (type != null) conditions.add(cb.equal(root.get("branchType"), type));
              if (city != null && !city.isBlank()) {
                String pattern = "%" + escape(city.trim().toLowerCase(Locale.ROOT)) + "%";
                conditions.add(cb.like(cb.lower(root.get("city")), pattern, '\\'));
              }
              if (sunatCode != null && !sunatCode.isBlank())
                conditions.add(cb.equal(root.get("sunatCode"), sunatCode.trim()));
              if (enabled != null) conditions.add(cb.equal(root.get("enabled"), enabled));
              return cb.and(conditions.toArray(Predicate[]::new));
            },
            PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id"))));

    return new BranchDtos.PageResponse<>(
        result.map(mapper::toResponse).getContent(),
        page,
        size,
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Override
  @Transactional
  public BranchDtos.Response update(UUID id, BranchDtos.Update r) {
    lock.acquire();
    current.requireAdmin("BRANCH_UPDATE");
    var b = find(id);

    // name es obligatorio cuando se envía (no se permite vaciarlo)
    if (r.name() != null) b.setName(r.name().trim());

    // Campos opcionales: si se envían no-nulos, diferenciar entre limpiar (vacío -> null) y asignar
    if (r.address() != null) b.setAddress(normalizeOptional(r.address()));
    if (r.phone() != null) b.setPhone(normalizeOptional(r.phone()));
    if (r.city() != null) b.setCity(normalizeOptional(r.city()));
    if (r.ubigeo() != null) b.setUbigeo(normalizeOptional(r.ubigeo()));
    if (r.email() != null) b.setEmail(normalizeEmail(r.email()));
    if (r.branchType() != null) b.setBranchType(r.branchType());

    if (r.sunatCode() != null) {
      String sc = normalizeOptional(r.sunatCode());
      if (sc != null && branches.existsBySunatCodeAndIdNot(sc, id)) {
        throw ApiException.conflict("El código de establecimiento SUNAT ya está registrado");
      }
      b.setSunatCode(sc);
    }

    return mapper.toResponse(branches.saveAndFlush(b));
  }

  @Override
  @Transactional
  public BranchDtos.Response changeStatus(UUID id, boolean enabled) {
    lock.acquire();
    current.requireAdmin("BRANCH_DISABLE");
    var b = find(id);
    if (!enabled) {
      long active = branches.countActiveWorkers(id);
      if (active > 0)
        throw ApiException.conflict(
            "No se puede desactivar la sucursal porque tiene "
                + active
                + " trabajador"
                + (active == 1 ? " activo asignado" : "es activos asignados")
                + ". Reasígnelos o desactívelos primero.");
    }
    b.setEnabled(enabled);
    return mapper.toResponse(branches.saveAndFlush(b));
  }

  // ── Utilidades internas ──────────────────────────────────────────────────

  private Branch find(UUID id) {
    return branches.findById(id).orElseThrow(ApiException::notFound);
  }

  private String normalizeOptional(String val) {
    if (val == null || val.isBlank()) return null;
    return val.trim();
  }

  private String normalizeEmail(String val) {
    if (val == null || val.isBlank()) return null;
    return val.trim().toLowerCase(Locale.ROOT);
  }

  private String escape(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
