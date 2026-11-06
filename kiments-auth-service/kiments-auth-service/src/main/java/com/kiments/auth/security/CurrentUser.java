package com.kiments.auth.security;

import com.kiments.auth.exception.ApiException;
import com.kiments.auth.users.*;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
  private final UserRepository users;

  public CurrentUser(UserRepository users) {
    this.users = users;
  }

  public User require() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated())
      throw ApiException.unauthorized();
    UUID id;
    try {
      id = UUID.fromString(authentication.getName());
    } catch (IllegalArgumentException e) {
      throw ApiException.unauthorized();
    }
    return users.findById(id).filter(User::isEnabled).orElseThrow(ApiException::unauthorized);
  }

  public User requireAdmin(String permission) {
    var user = require();
    if (!user.getRole().getCode().equals("ADMIN")
        || user.getRole().getPermissions().stream().noneMatch(p -> p.getCode().equals(permission)))
      throw ApiException.forbidden();
    return user;
  }
}
