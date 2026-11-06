package com.kiments.auth.users;

import org.springframework.stereotype.Component;

@Component
public class UserMapper {
  public UserDtos.Response toResponse(User u) {
    return new UserDtos.Response(
        u.getId(),
        u.getFirstName(),
        u.getLastName(),
        u.getDni(),
        u.getPhone(),
        u.getEmail(),
        u.getRole().getCode(),
        u.isEnabled(),
        u.getBranchId(),
        u.getCreatedAt(),
        u.getUpdatedAt(),
        u.getLastLoginAt(),
        u.getProfileImageKey() != null && !u.getProfileImageKey().isBlank());
  }
}
