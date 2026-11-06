package com.kiments.auth.authentication;

import com.kiments.auth.security.LoginRateLimiter;
import com.nimbusds.jose.jwk.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthenticationService service;
  private final LoginRateLimiter limiter;
  private final RSAKey key;

  public AuthController(AuthenticationService service, LoginRateLimiter limiter, RSAKey key) {
    this.service = service;
    this.limiter = limiter;
    this.key = key;
  }

  @PostMapping("/login")
  @SecurityRequirements
  public ResponseEntity<AuthenticationDtos.TokenResponse> login(
      @Valid @RequestBody AuthenticationDtos.Login request, HttpServletRequest http) {
    limiter.check(request.email(), http.getRemoteAddr());
    return secret(service.login(request));
  }

  @GetMapping("/me")
  public ResponseEntity<AuthenticationDtos.Profile> me() {
    return secret(service.me());
  }

  @PostMapping("/change-password")
  public ResponseEntity<Void> password(
      @Valid @RequestBody AuthenticationDtos.ChangePassword request) {
    service.changePassword(request);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/refresh")
  @SecurityRequirements
  public ResponseEntity<AuthenticationDtos.TokenResponse> refresh(
      @Valid @RequestBody AuthenticationDtos.Refresh request) {
    return secret(service.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  @SecurityRequirements
  public ResponseEntity<Void> logout(@Valid @RequestBody AuthenticationDtos.Refresh request) {
    service.logout(request.refreshToken());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/jwks")
  @SecurityRequirements
  public Map<String, Object> jwks() {
    return new JWKSet(key.toPublicJWK()).toJSONObject();
  }

  private <T> ResponseEntity<T> secret(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
