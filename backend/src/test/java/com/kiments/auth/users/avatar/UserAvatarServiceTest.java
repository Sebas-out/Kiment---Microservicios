package com.kiments.auth.users.avatar;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.kiments.auth.exception.ApiException;
import com.kiments.auth.security.CurrentUser;
import com.kiments.auth.shared.SecurityMutationLock;
import com.kiments.auth.storage.ProfileStorageService;
import com.kiments.auth.users.User;
import com.kiments.auth.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class UserAvatarServiceTest {

  @Mock private UserRepository users;
  @Mock private ProfileStorageService storage;
  @Mock private CurrentUser current;
  @Mock private SecurityMutationLock lock;

  private UserAvatarService avatarService;
  private User testUser;

  @BeforeEach
  void setUp() {
    avatarService = new UserAvatarServiceImpl(users, storage, current, lock);
    testUser = new User();
  }

  @Test
  void uploadAvatar_success() {
    when(current.require()).thenReturn(testUser);
    MockMultipartFile file =
        new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[] {1, 2, 3});
    String newKey = "abc-123.jpg";
    when(storage.store(file)).thenReturn(newKey);
    when(users.saveAndFlush(testUser)).thenReturn(testUser);

    var response = avatarService.uploadAvatar(file);

    assertThat(response.hasProfileImage()).isTrue();
    assertThat(testUser.getProfileImageKey()).isEqualTo(newKey);
    verify(users).saveAndFlush(testUser);
    verify(lock).acquire();
  }

  @Test
  void uploadAvatar_replacesOldFile() {
    testUser.setProfileImageKey("old-file.jpg");
    when(current.require()).thenReturn(testUser);
    MockMultipartFile file =
        new MockMultipartFile("file", "new.png", "image/png", new byte[] {1, 2, 3});
    String newKey = "new-file.png";
    when(storage.store(file)).thenReturn(newKey);
    when(users.saveAndFlush(testUser)).thenReturn(testUser);

    var response = avatarService.uploadAvatar(file);

    assertThat(response.hasProfileImage()).isTrue();
    assertThat(testUser.getProfileImageKey()).isEqualTo(newKey);
    verify(storage).delete("old-file.jpg");
  }

  @Test
  void uploadAvatar_rollbackDeletesNewFileOnDatabaseFailure() {
    when(current.require()).thenReturn(testUser);
    MockMultipartFile file =
        new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[] {1, 2, 3});
    String newKey = "new-temp.jpg";
    when(storage.store(file)).thenReturn(newKey);
    doThrow(new RuntimeException("DB Connection Timeout")).when(users).saveAndFlush(testUser);

    assertThatThrownBy(() -> avatarService.uploadAvatar(file))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("DB Connection Timeout");

    verify(storage).delete(newKey);
  }

  @Test
  void getAvatar_success() {
    testUser.setProfileImageKey("avatar-key.jpg");
    when(current.require()).thenReturn(testUser);
    Resource dummyResource = new ByteArrayResource(new byte[] {1, 2, 3});
    when(storage.load("avatar-key.jpg")).thenReturn(dummyResource);
    when(storage.resolveMediaType("avatar-key.jpg")).thenReturn(MediaType.IMAGE_JPEG);

    var download = avatarService.getAvatar();

    assertThat(download.resource()).isEqualTo(dummyResource);
    assertThat(download.mediaType()).isEqualTo(MediaType.IMAGE_JPEG);
  }

  @Test
  void getAvatar_withoutPhoto_throwsNotFound() {
    when(current.require()).thenReturn(testUser);

    assertThatThrownBy(() -> avatarService.getAvatar())
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void deleteAvatar_success() {
    testUser.setProfileImageKey("to-delete.jpg");
    when(current.require()).thenReturn(testUser);
    when(users.saveAndFlush(testUser)).thenReturn(testUser);

    avatarService.deleteAvatar();

    assertThat(testUser.getProfileImageKey()).isNull();
    verify(users).saveAndFlush(testUser);
    verify(storage).delete("to-delete.jpg");
  }

  @Test
  void deleteAvatar_whenNoPhoto_doesNothing() {
    when(current.require()).thenReturn(testUser);

    avatarService.deleteAvatar();

    verify(users, never()).saveAndFlush(any());
    verify(storage, never()).delete(any());
  }
}
