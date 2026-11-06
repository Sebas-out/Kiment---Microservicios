package com.kiments.auth.roles;

import com.kiments.auth.exception.ApiException;
import com.kiments.auth.security.CurrentUser;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoleServiceImpl implements RoleService {
  private final RoleRepository roles;
  private final CurrentUser current;

  public RoleServiceImpl(RoleRepository roles, CurrentUser current) {
    this.roles = roles;
    this.current = current;
  }

  public List<Response> list() {
    current.requireAdmin("USER_READ");
    return roles.findAll().stream()
        .map(r -> new Response(r.getId(), r.getCode(), r.getName(), r.getDescription()))
        .sorted(Comparator.comparing(Response::code))
        .toList();
  }

  public List<PermissionResponse> permissions(Long id) {
    current.requireAdmin("USER_READ");
    return roles.findById(id).orElseThrow(ApiException::notFound).getPermissions().stream()
        .map(p -> new PermissionResponse(p.getCode(), p.getDescription()))
        .sorted(Comparator.comparing(PermissionResponse::code))
        .toList();
  }
}
