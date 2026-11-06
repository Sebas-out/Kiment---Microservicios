package com.kiments.auth.users.avatar;

import com.kiments.auth.users.UserDtos;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

public interface UserAvatarService {
  UserDtos.AvatarResponse uploadAvatar(MultipartFile file);

  AvatarDownload getAvatar();

  void deleteAvatar();

  AvatarDownload getUserAvatar(UUID targetUserId);

  void deleteUserAvatar(UUID targetUserId);

  record AvatarDownload(Resource resource, MediaType mediaType) {}
}
