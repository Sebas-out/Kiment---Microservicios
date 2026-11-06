package com.kiments.auth.roles;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('USER_READ')")
public class RoleController {
  private final RoleService service;

  public RoleController(RoleService service) {
    this.service = service;
  }

  @GetMapping
  public List<RoleService.Response> list() {
    return service.list();
  }

  @GetMapping("/{id}/permissions")
  public List<RoleService.PermissionResponse> permissions(@PathVariable Long id) {
    return service.permissions(id);
  }
}
