package com.kiments.auth.users;

import com.kiments.auth.users.avatar.UserAvatarService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {
  private final UserService service;
  private final UserAvatarService avatarService;

  public UserController(UserService service, UserAvatarService avatarService) {
    this.service = service;
    this.avatarService = avatarService;
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('USER_CREATE')")
  public ResponseEntity<UserDtos.Response> create(@Valid @RequestBody UserDtos.Create request) {
    var user = service.create(request);
    return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(user);
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('USER_READ')")
  public UserDtos.PageResponse<UserDtos.Response> list(
      @RequestParam(required = false) @Size(max = 160) String name,
      @RequestParam(required = false) @Size(max = 254) String email,
      @RequestParam(required = false) @Pattern(regexp = "[0-9]{8}") String dni,
      @RequestParam(required = false) @Pattern(regexp = "ADMIN|VENTAS|ALMACEN|VENTAS_ALMACEN")
          String role,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(name, email, dni, role, branchId, enabled, page, size);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('USER_READ')")
  public UserDtos.Response get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('USER_UPDATE')")
  public UserDtos.Response update(
      @PathVariable UUID id, @Valid @RequestBody UserDtos.Update request) {
    return service.update(id, request);
  }

  @PatchMapping("/{id}/role")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('USER_ROLE_CHANGE')")
  public UserDtos.Response role(
      @PathVariable UUID id, @Valid @RequestBody UserDtos.ChangeRole request) {
    return service.changeRole(id, request);
  }

  @PatchMapping("/{id}/branch")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('USER_UPDATE')")
  public UserDtos.Response branch(
      @PathVariable UUID id, @Valid @RequestBody UserDtos.ChangeBranch request) {
    return service.changeBranch(id, request.branchId());
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('USER_DISABLE')")
  public UserDtos.Response status(
      @PathVariable UUID id, @Valid @RequestBody UserDtos.ChangeStatus request) {
    return service.changeStatus(id, request.enabled());
  }

  @GetMapping("/{id}/avatar")
  @PreAuthorize("(hasRole('ADMIN') and hasAuthority('USER_READ')) or authentication.name == #id.toString()")
  public ResponseEntity<Resource> getAvatar(@PathVariable UUID id) {
    var download = avatarService.getUserAvatar(id);
    return ResponseEntity.ok()
        .contentType(download.mediaType())
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate().mustRevalidate())
        .body(download.resource());
  }

  @DeleteMapping("/{id}/avatar")
  @PreAuthorize("(hasRole('ADMIN') and hasAuthority('USER_UPDATE')) or authentication.name == #id.toString()")
  public ResponseEntity<Void> deleteAvatar(@PathVariable UUID id) {
    avatarService.deleteUserAvatar(id);
    return ResponseEntity.noContent().build();
  }
}
