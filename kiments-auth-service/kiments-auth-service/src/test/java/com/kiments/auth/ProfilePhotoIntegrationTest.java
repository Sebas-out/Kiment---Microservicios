package com.kiments.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.kiments.auth.users.User;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named = "STAGE4_TEST_DB_URL", matches = ".+")
class ProfilePhotoIntegrationTest extends AuthIntegrationScenarios {

  private static final Path tempStoragePath;

  static {
    try {
      tempStoragePath = Files.createTempDirectory("kiments_profiles_test_");
    } catch (IOException e) {
      throw new RuntimeException("Failed to create temp directory for tests", e);
    }
  }

  @DynamicPropertySource
  static void isolatedDatabase(DynamicPropertyRegistry registry) {
    String url = System.getenv("STAGE4_TEST_DB_URL");
    if (!url.matches("jdbc:postgresql://[^/]+/[A-Za-z0-9_]+_test")) {
      throw new IllegalArgumentException(
          "STAGE4_TEST_DB_URL debe apuntar a una base exclusiva terminada en _test");
    }
    registry.add("spring.datasource.url", () -> url);
    registry.add(
        "spring.datasource.username",
        () ->
            System.getenv("STAGE4_TEST_DB_USERNAME") != null
                ? System.getenv("STAGE4_TEST_DB_USERNAME")
                : "kiments_auth");
    registry.add(
        "spring.datasource.password",
        () ->
            System.getenv("STAGE4_TEST_DB_PASSWORD") != null
                ? System.getenv("STAGE4_TEST_DB_PASSWORD")
                : "admin");
    registry.add("app.storage.profiles.path", () -> tempStoragePath.toAbsolutePath().toString());
  }

  @AfterAll
  static void cleanupStorage() {
    try {
      if (Files.exists(tempStoragePath)) {
        try (var stream = Files.walk(tempStoragePath)) {
          stream
              .sorted(Comparator.reverseOrder())
              .forEach(
                  p -> {
                    try {
                      Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                  });
        }
      }
    } catch (Exception ignored) {
    }
  }

  @BeforeEach
  void cleanPhotos() {
    jdbc.update("UPDATE auth.users SET profile_image_key = NULL");
  }

  @Test
  void uploadValidJpeg_shouldSucceedAndPersist() throws Exception {
    byte[] jpegBytes = createSampleImage("jpg", 120, 120, Color.RED);
    MockMultipartFile file =
        new MockMultipartFile("file", "my_avatar.jpg", MediaType.IMAGE_JPEG_VALUE, jpegBytes);

    String respStr =
        mvc.perform(
                multipart("/api/v1/users/me/avatar")
                    .file(file)
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode node = json.readTree(respStr);
    assertThat(node.get("hasProfileImage").asBoolean()).isTrue();

    String storedKey =
        jdbc.queryForObject(
            "SELECT profile_image_key FROM auth.users WHERE id = ?", String.class, admin);
    assertThat(storedKey).isNotNull().endsWith(".jpg");
    assertThat(Files.exists(tempStoragePath.resolve(storedKey))).isTrue();

    // Verify /api/v1/auth/me reflects hasProfileImage
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.hasProfileImage").value(true));
  }

  @Test
  void getAvatar_shouldReturnBinaryWithContentTypeAndCacheControl() throws Exception {
    byte[] jpegBytes = createSampleImage("jpg", 100, 100, Color.GREEN);
    MockMultipartFile file =
        new MockMultipartFile("file", "avatar.jpg", MediaType.IMAGE_JPEG_VALUE, jpegBytes);

    mvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(file)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    byte[] downloadedBytes =
        mvc.perform(get("/api/v1/users/me/avatar").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", MediaType.IMAGE_JPEG_VALUE))
            .andExpect(header().exists("Cache-Control"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    assertThat(downloadedBytes).isNotEmpty();
  }

  @Test
  void replaceAvatarWithPng_shouldDeleteOldFileAndSaveNew() throws Exception {
    // 1. Upload initial JPEG
    byte[] jpegBytes = createSampleImage("jpg", 100, 100, Color.BLACK);
    mvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(
                    new MockMultipartFile(
                        "file", "initial.jpg", MediaType.IMAGE_JPEG_VALUE, jpegBytes))
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    String oldKey =
        jdbc.queryForObject(
            "SELECT profile_image_key FROM auth.users WHERE id = ?", String.class, admin);
    assertThat(Files.exists(tempStoragePath.resolve(oldKey))).isTrue();

    // 2. Replace with PNG
    byte[] pngBytes = createSampleImage("png", 150, 150, Color.YELLOW);
    mvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(
                    new MockMultipartFile("file", "new.png", MediaType.IMAGE_PNG_VALUE, pngBytes))
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    String newKey =
        jdbc.queryForObject(
            "SELECT profile_image_key FROM auth.users WHERE id = ?", String.class, admin);
    assertThat(newKey).isNotEqualTo(oldKey).endsWith(".png");

    // Old file deleted, new file exists
    assertThat(Files.exists(tempStoragePath.resolve(oldKey))).isFalse();
    assertThat(Files.exists(tempStoragePath.resolve(newKey))).isTrue();

    // GET returns PNG Content-Type
    mvc.perform(get("/api/v1/users/me/avatar").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE));
  }

  @Test
  void deleteAvatar_shouldClearDatabaseAndRemoveFile() throws Exception {
    byte[] jpegBytes = createSampleImage("jpg", 80, 80, Color.CYAN);
    mvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(
                    new MockMultipartFile(
                        "file", "avatar.jpg", MediaType.IMAGE_JPEG_VALUE, jpegBytes))
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    String key =
        jdbc.queryForObject(
            "SELECT profile_image_key FROM auth.users WHERE id = ?", String.class, admin);
    assertThat(Files.exists(tempStoragePath.resolve(key))).isTrue();

    // DELETE avatar
    mvc.perform(delete("/api/v1/users/me/avatar").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNoContent());

    String afterDeleteKey =
        jdbc.queryForObject(
            "SELECT profile_image_key FROM auth.users WHERE id = ?", String.class, admin);
    assertThat(afterDeleteKey).isNull();
    assertThat(Files.exists(tempStoragePath.resolve(key))).isFalse();

    // GET avatar now returns 404
    mvc.perform(get("/api/v1/users/me/avatar").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());

    // /auth/me returns hasProfileImage false
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.hasProfileImage").value(false));
  }

  @Test
  void getAvatar_withoutPhoto_shouldReturn404() throws Exception {
    mvc.perform(get("/api/v1/users/me/avatar").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteAvatar_whenNoPhoto_shouldReturn204() throws Exception {
    mvc.perform(delete("/api/v1/users/me/avatar").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNoContent());
  }

  @Test
  void uploadEmptyFile_shouldReturn400() throws Exception {
    MockMultipartFile emptyFile =
        new MockMultipartFile("file", "empty.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);

    mvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(emptyFile)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void uploadOversizedFile_shouldReturn413() throws Exception {
    byte[] oversized = new byte[2 * 1024 * 1024 + 10];
    MockMultipartFile largeFile =
        new MockMultipartFile("file", "large.jpg", MediaType.IMAGE_JPEG_VALUE, oversized);

    mvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(largeFile)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void uploadSpoofedImage_shouldReturn400() throws Exception {
    byte[] textBytes = "This is not a real JPEG image".getBytes();
    MockMultipartFile spoofed =
        new MockMultipartFile("file", "spoof.jpg", MediaType.IMAGE_JPEG_VALUE, textBytes);

    mvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(spoofed)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void uploadCorruptedImage_shouldReturn400() throws Exception {
    byte[] corrupted = new byte[] {(byte) 0xFF, (byte) 0xD8, 0x01, 0x02};
    MockMultipartFile corruptFile =
        new MockMultipartFile("file", "corrupt.jpg", MediaType.IMAGE_JPEG_VALUE, corrupted);

    mvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(corruptFile)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void uploadWebp_shouldReturn400() throws Exception {
    MockMultipartFile webpFile =
        new MockMultipartFile("file", "avatar.webp", "image/webp", new byte[] {0x52, 0x49, 0x46, 0x46});

    mvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(webpFile)
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unauthenticatedRequests_shouldReturn401() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test.jpg", MediaType.IMAGE_JPEG_VALUE, createSampleImage("jpg", 50, 50, Color.BLACK));

    mvc.perform(multipart("/api/v1/users/me/avatar").file(file))
        .andExpect(status().isUnauthorized());

    mvc.perform(get("/api/v1/users/me/avatar")).andExpect(status().isUnauthorized());

    mvc.perform(delete("/api/v1/users/me/avatar")).andExpect(status().isUnauthorized());
  }

  @Test
  void nonAdminUser_canManageOwnAvatar() throws Exception {
    // 1. Create a VENTAS user and get their token
    User worker = seedUser("VENTAS", "worker@test.example", "88000099", true);
    String workerToken = login("worker@test.example", PASSWORD).get("accessToken").asText();

    // 2. Worker uploads their own avatar
    byte[] jpegBytes = createSampleImage("jpg", 90, 90, Color.MAGENTA);
    MockMultipartFile file =
        new MockMultipartFile("file", "worker.jpg", MediaType.IMAGE_JPEG_VALUE, jpegBytes);

    mvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(file)
                .header("Authorization", "Bearer " + workerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasProfileImage").value(true));

    // 3. Worker downloads their avatar
    mvc.perform(get("/api/v1/users/me/avatar").header("Authorization", "Bearer " + workerToken))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", MediaType.IMAGE_JPEG_VALUE));

    // 4. Worker deletes their avatar
    mvc.perform(delete("/api/v1/users/me/avatar").header("Authorization", "Bearer " + workerToken))
        .andExpect(status().isNoContent());

    // 5. Worker GET avatar now 404
    mvc.perform(get("/api/v1/users/me/avatar").header("Authorization", "Bearer " + workerToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void concurrentUploads_shouldRemainConsistent() throws Exception {
    int threads = 4;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch latch = new CountDownLatch(threads);
    List<Future<Integer>> futures = new ArrayList<>();

    for (int i = 0; i < threads; i++) {
      final int idx = i;
      futures.add(
          executor.submit(
              () -> {
                latch.countDown();
                latch.await();
                byte[] img = createSampleImage("jpg", 60 + idx * 5, 60 + idx * 5, Color.BLUE);
                MockMultipartFile f =
                    new MockMultipartFile("file", "concur_" + idx + ".jpg", MediaType.IMAGE_JPEG_VALUE, img);
                var res =
                    mvc.perform(
                            multipart("/api/v1/users/me/avatar")
                                .file(f)
                                .header("Authorization", "Bearer " + adminToken))
                        .andReturn();
                return res.getResponse().getStatus();
              }));
    }

    for (var f : futures) {
      assertThat(f.get(10, TimeUnit.SECONDS)).isEqualTo(200);
    }
    executor.shutdown();

    // After all finishes, exactly one valid key exists in DB and on disk
    String finalKey =
        jdbc.queryForObject(
            "SELECT profile_image_key FROM auth.users WHERE id = ?", String.class, admin);
    assertThat(finalKey).isNotNull();
    assertThat(Files.exists(tempStoragePath.resolve(finalKey))).isTrue();
  }

  @Test
  void adminCanViewAnyUserAvatar_success() throws Exception {
    User worker = seedUser("VENTAS", "avatar_read_target@test.example", "88000001", true);
    String workerToken = login("avatar_read_target@test.example", PASSWORD).get("accessToken").asText();

    byte[] jpegBytes = createSampleImage("jpg", 100, 100, Color.GREEN);
    MockMultipartFile file =
        new MockMultipartFile("file", "worker.jpg", MediaType.IMAGE_JPEG_VALUE, jpegBytes);

    mvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(file)
                .header("Authorization", "Bearer " + workerToken))
        .andExpect(status().isOk());

    // Admin consulta la foto del usuario
    mvc.perform(
            get("/api/v1/users/" + worker.getId() + "/avatar")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", MediaType.IMAGE_JPEG_VALUE))
        .andExpect(header().string("Cache-Control", "max-age=3600, must-revalidate, private"));
  }

  @Test
  void userCanViewOwnAvatarById_success() throws Exception {
    User worker = seedUser("VENTAS", "avatar_own_view@test.example", "88000002", true);
    String workerToken = login("avatar_own_view@test.example", PASSWORD).get("accessToken").asText();

    byte[] pngBytes = createSampleImage("png", 100, 100, Color.CYAN);
    MockMultipartFile file =
        new MockMultipartFile("file", "worker.png", MediaType.IMAGE_PNG_VALUE, pngBytes);

    mvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(file)
                .header("Authorization", "Bearer " + workerToken))
        .andExpect(status().isOk());

    // El propio usuario consulta su foto vía /api/v1/users/{id}/avatar
    mvc.perform(
            get("/api/v1/users/" + worker.getId() + "/avatar")
                .header("Authorization", "Bearer " + workerToken))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE));
  }

  @Test
  void unauthorizedUserCannotViewOtherUserAvatar_returnsForbidden() throws Exception {
    User worker1 = seedUser("VENTAS", "worker1_view@test.example", "88000003", true);
    User worker2 = seedUser("VENTAS", "worker2_view@test.example", "88000004", true);
    String token1 = login("worker1_view@test.example", PASSWORD).get("accessToken").asText();
    String token2 = login("worker2_view@test.example", PASSWORD).get("accessToken").asText();

    byte[] jpegBytes = createSampleImage("jpg", 100, 100, Color.YELLOW);
    MockMultipartFile file =
        new MockMultipartFile("file", "worker2.jpg", MediaType.IMAGE_JPEG_VALUE, jpegBytes);

    mvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(file)
                .header("Authorization", "Bearer " + token2))
        .andExpect(status().isOk());

    // Worker 1 intenta ver foto de Worker 2 -> 403 Forbidden
    mvc.perform(
            get("/api/v1/users/" + worker2.getId() + "/avatar")
                .header("Authorization", "Bearer " + token1))
        .andExpect(status().isForbidden());
  }

  @Test
  void viewNonExistentUserAvatar_returnsNotFound() throws Exception {
    mvc.perform(
            get("/api/v1/users/" + UUID.randomUUID() + "/avatar")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void viewUserWithoutAvatar_returnsNotFound() throws Exception {
    User worker = seedUser("VENTAS", "worker_no_photo@test.example", "88000005", true);
    mvc.perform(
            get("/api/v1/users/" + worker.getId() + "/avatar")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void adminCanDeleteAnyUserAvatar_success() throws Exception {
    User worker = seedUser("VENTAS", "avatar_del_target@test.example", "88000006", true);
    String workerToken = login("avatar_del_target@test.example", PASSWORD).get("accessToken").asText();

    byte[] jpegBytes = createSampleImage("jpg", 100, 100, Color.ORANGE);
    MockMultipartFile file =
        new MockMultipartFile("file", "worker.jpg", MediaType.IMAGE_JPEG_VALUE, jpegBytes);

    mvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(file)
                .header("Authorization", "Bearer " + workerToken))
        .andExpect(status().isOk());

    String keyBefore =
        jdbc.queryForObject(
            "SELECT profile_image_key FROM auth.users WHERE id = ?", String.class, worker.getId());
    assertThat(keyBefore).isNotNull();
    assertThat(Files.exists(tempStoragePath.resolve(keyBefore))).isTrue();

    // Admin elimina la foto del usuario
    mvc.perform(
            delete("/api/v1/users/" + worker.getId() + "/avatar")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNoContent());

    String keyAfter =
        jdbc.queryForObject(
            "SELECT profile_image_key FROM auth.users WHERE id = ?", String.class, worker.getId());
    assertThat(keyAfter).isNull();
    assertThat(Files.exists(tempStoragePath.resolve(keyBefore))).isFalse();
  }

  @Test
  void userCanDeleteOwnAvatarById_success() throws Exception {
    User worker = seedUser("VENTAS", "avatar_del_own@test.example", "88000007", true);
    String workerToken = login("avatar_del_own@test.example", PASSWORD).get("accessToken").asText();

    byte[] jpegBytes = createSampleImage("jpg", 100, 100, Color.PINK);
    MockMultipartFile file =
        new MockMultipartFile("file", "worker.jpg", MediaType.IMAGE_JPEG_VALUE, jpegBytes);

    mvc.perform(
            multipart("/api/v1/users/me/avatar")
                .file(file)
                .header("Authorization", "Bearer " + workerToken))
        .andExpect(status().isOk());

    mvc.perform(
            delete("/api/v1/users/" + worker.getId() + "/avatar")
                .header("Authorization", "Bearer " + workerToken))
        .andExpect(status().isNoContent());

    String keyAfter =
        jdbc.queryForObject(
            "SELECT profile_image_key FROM auth.users WHERE id = ?", String.class, worker.getId());
    assertThat(keyAfter).isNull();
  }

  @Test
  void unauthorizedUserCannotDeleteOtherUserAvatar_returnsForbidden() throws Exception {
    User worker1 = seedUser("VENTAS", "worker1_del@test.example", "88000008", true);
    User worker2 = seedUser("VENTAS", "worker2_del@test.example", "88000009", true);
    String token1 = login("worker1_del@test.example", PASSWORD).get("accessToken").asText();

    mvc.perform(
            delete("/api/v1/users/" + worker2.getId() + "/avatar")
                .header("Authorization", "Bearer " + token1))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteNonExistentUserAvatar_returnsNotFound() throws Exception {
    mvc.perform(
            delete("/api/v1/users/" + UUID.randomUUID() + "/avatar")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  private UUID createTestBranch(String code, String name) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO auth.branches (id, code, name, branch_type, enabled, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, 'VENTA', true, now(), now(), 0)",
        id,
        code,
        name);
    return id;
  }

  private byte[] createSampleImage(String format, int width, int height, Color color)
      throws IOException {
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(color);
    g.fillRect(0, 0, width, height);
    g.dispose();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, format, baos);
    return baos.toByteArray();
  }
}
