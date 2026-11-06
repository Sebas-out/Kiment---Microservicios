package com.kiments.auth.users;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class UserDtos {
  private UserDtos() {}

  public record Create(
      @NotBlank @Size(max = 80) String firstName,
      @NotBlank @Size(max = 80) String lastName,
      @NotBlank @Pattern(regexp = "[0-9]{8}") String dni,
      @Pattern(regexp = "^\\+?[0-9 ()-]{6,20}$") String phone,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 128) String password,
      @NotBlank @Pattern(regexp = "ADMIN|VENTAS|ALMACEN|VENTAS_ALMACEN") String role,
      @NotNull Boolean enabled,
      UUID branchId) {
    public Create(
        String firstName,
        String lastName,
        String dni,
        String phone,
        String email,
        String password,
        String role,
        Boolean enabled) {
      this(firstName, lastName, dni, phone, email, password, role, enabled, null);
    }

    public Create {
      if (email != null) email = com.kiments.auth.shared.EmailNormalizer.normalize(email);
    }

    @Override
    public String toString() {
      return "Create[REDACTED]";
    }
  }

  public record Update(
      @Size(min = 1, max = 80) @Pattern(regexp = "(?s).*\\S.*") String firstName,
      @Size(min = 1, max = 80) @Pattern(regexp = "(?s).*\\S.*") String lastName,
      @Pattern(regexp = "[0-9]{8}") String dni,
      @Pattern(regexp = "^$|^\\+?[0-9 ()-]{6,20}$") String phone,
      @Email @Size(min = 3, max = 254) String email) {
    public Update {
      if (email != null) email = com.kiments.auth.shared.EmailNormalizer.normalize(email);
    }
  }

  public record ChangeRole(
      @NotBlank @Pattern(regexp = "ADMIN|VENTAS|ALMACEN|VENTAS_ALMACEN") String role,
      UUID branchId) {
    public ChangeRole(String role) {
      this(role, null);
    }
  }

  public record ChangeBranch(@NotNull UUID branchId) {}

  public record ChangeStatus(@NotNull Boolean enabled) {}

  public record Response(
      UUID id,
      String firstName,
      String lastName,
      String dni,
      String phone,
      String email,
      String role,
      boolean enabled,
      UUID branchId,
      Instant createdAt,
      Instant updatedAt,
      Instant lastLoginAt,
      boolean hasProfileImage) {
    public Response(
        UUID id,
        String firstName,
        String lastName,
        String dni,
        String phone,
        String email,
        String role,
        boolean enabled,
        UUID branchId,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt) {
      this(
          id,
          firstName,
          lastName,
          dni,
          phone,
          email,
          role,
          enabled,
          branchId,
          createdAt,
          updatedAt,
          lastLoginAt,
          false);
    }
  }

  public record AvatarResponse(String message, boolean hasProfileImage) {}

  public record PageResponse<T>(
      List<T> content, int page, int size, long totalElements, int totalPages) {}
}
