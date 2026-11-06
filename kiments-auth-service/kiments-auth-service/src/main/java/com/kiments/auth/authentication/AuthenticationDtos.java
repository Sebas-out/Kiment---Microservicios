package com.kiments.auth.authentication;

import com.kiments.auth.users.UserDtos;
import jakarta.validation.constraints.*;
import java.util.List;

public final class AuthenticationDtos {
  private AuthenticationDtos() {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {
    public Login {
      if (email != null) email = com.kiments.auth.shared.EmailNormalizer.normalize(email);
    }

    @Override
    public String toString() {
      return "Login[REDACTED]";
    }
  }

  public record ChangePassword(
      @NotBlank @Size(max = 128) String currentPassword,
      @NotBlank @Size(min = 12, max = 128) String newPassword) {
    @Override
    public String toString() {
      return "ChangePassword[REDACTED]";
    }
  }

  public record Refresh(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String refreshToken) {
    @Override
    public String toString() {
      return "Refresh[REDACTED]";
    }
  }

  public record TokenResponse(
      String accessToken, String tokenType, long expiresIn, String refreshToken) {
    @Override
    public String toString() {
      return "TokenResponse[REDACTED]";
    }
  }

  public record Profile(UserDtos.Response user, List<String> permissions) {}
}
