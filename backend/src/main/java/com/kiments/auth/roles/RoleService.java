package com.kiments.auth.roles;

import java.util.List;

public interface RoleService {
  record Response(Long id, String code, String name, String description) {}

  record PermissionResponse(String code, String description) {}

  List<Response> list();

  List<PermissionResponse> permissions(Long id);
}
