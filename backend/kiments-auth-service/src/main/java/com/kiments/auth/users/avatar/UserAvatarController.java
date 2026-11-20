package com.kiments.auth.users.avatar;

import com.kiments.auth.users.UserDtos;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/me/avatar")
public class UserAvatarController {
  private final UserAvatarService service;

  public UserAvatarController(UserAvatarService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<UserDtos.AvatarResponse> uploadAvatar(
      @RequestParam("file") MultipartFile file) {
    var response = service.uploadAvatar(file);
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<Resource> getAvatar() {
    var download = service.getAvatar();
    return ResponseEntity.ok()
        .contentType(download.mediaType())
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate().mustRevalidate())
        .body(download.resource());
  }

  @DeleteMapping
  public ResponseEntity<Void> deleteAvatar() {
    service.deleteAvatar();
    return ResponseEntity.noContent().build();
  }
}
