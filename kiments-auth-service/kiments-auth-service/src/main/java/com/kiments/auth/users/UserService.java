package com.kiments.auth.users;

import java.util.UUID;

public interface UserService {
  UserDtos.Response create(UserDtos.Create request);

  UserDtos.Response get(UUID id);

  UserDtos.PageResponse<UserDtos.Response> list(
      String name, String email, String dni, String role, UUID branchId, Boolean enabled, int page, int size);

  UserDtos.Response update(UUID id, UserDtos.Update request);

  UserDtos.Response changeRole(UUID id, UserDtos.ChangeRole request);

  default UserDtos.Response changeRole(UUID id, String role) {
    return changeRole(id, new UserDtos.ChangeRole(role, null));
  }

  UserDtos.Response changeBranch(UUID id, UUID branchId);

  UserDtos.Response changeStatus(UUID id, boolean enabled);
}
