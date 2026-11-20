package com.kiments.cataloginventory.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiments.cataloginventory.config.StorageProperties;
import com.kiments.cataloginventory.exception.ApiException;
import com.kiments.cataloginventory.ports.storage.LocalStorageAdapter;
import com.kiments.cataloginventory.ports.storage.StoragePort;
import com.kiments.cataloginventory.shared.idempotency.IdempotencyRecord;
import com.kiments.cataloginventory.shared.idempotency.IdempotencyRepository;
import com.kiments.cataloginventory.shared.idempotency.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ImageAndIdempotencyTests {

    @TempDir
    Path tempStorageDir;

    private LocalStorageAdapter storageAdapter;
    private IdempotencyRepository idempotencyRepository;
    private IdempotencyService idempotencyService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.getLocal().setBaseDir(tempStorageDir.toString());
        properties.setMaxFileSizeBytes(5 * 1024 * 1024); // 5 MB
        storageAdapter = new LocalStorageAdapter(properties);

        idempotencyRepository = Mockito.mock(IdempotencyRepository.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        idempotencyService = new IdempotencyService(idempotencyRepository, objectMapper);
    }

    private byte[] createTestImage(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Debe procesar y almacenar una imagen JPEG válida")
    void storesValidJpegImage() throws IOException {
        byte[] validJpeg = createTestImage("jpg", 200, 200);
        StoragePort.StoredImage stored = storageAdapter.storeImage(validJpeg, "image/jpeg", "test.jpg");

        assertThat(stored).isNotNull();
        assertThat(stored.storageKey()).endsWith(".jpg");
        assertThat(stored.width()).isEqualTo(200);
        assertThat(stored.height()).isEqualTo(200);

        byte[] retrieved = storageAdapter.retrieveImage(stored.storageKey());
        assertThat(retrieved).isNotEmpty();

        storageAdapter.removeImage(stored.storageKey());
        assertThatThrownBy(() -> storageAdapter.retrieveImage(stored.storageKey()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Debe rechazar archivos falsificados con formato no admitido o corruptos")
    void rejectsCorruptOrFakeImage() {
        byte[] fakeBytes = "ESTO_NO_ES_UNA_IMAGEN_REAL".getBytes();

        assertThatThrownBy(() -> storageAdapter.storeImage(fakeBytes, "image/jpeg", "fake.jpg"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Debe rechazar archivos que exceden el tamaño máximo de 5MB")
    void rejectsExceededFileSize() {
        byte[] hugeBytes = new byte[6 * 1024 * 1024]; // 6 MB

        assertThatThrownBy(() -> storageAdapter.storeImage(hugeBytes, "image/jpeg", "huge.jpg"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    @DisplayName("Debe retornar respuesta cacheada si se envía la misma Idempotency-Key y mismo payload")
    void returnsCachedResponseOnIdenticalIdempotencyKey() {
        UUID actorId = UUID.randomUUID();
        String key = "IDEMP-KEY-12345";
        String endpoint = "/api/v1/stock/adjust";
        record DummyRequest(String sku, int qty) {}
        record DummyResponse(String status, int newStock) {}

        DummyRequest req = new DummyRequest("SKU-TEST", 10);
        String requestHash = IdempotencyService.computeHash("{\"sku\":\"SKU-TEST\",\"qty\":10}");

        IdempotencyRecord cachedRecord = new IdempotencyRecord(
                UUID.randomUUID(), key, actorId, endpoint, requestHash, 200, "{\"status\":\"SUCCESS\",\"newStock\":50}", Instant.now()
        );

        when(idempotencyRepository.findByIdempotencyKeyAndActorUserIdAndEndpoint(key, actorId, endpoint))
                .thenReturn(Optional.of(cachedRecord));

        AtomicInteger executions = new AtomicInteger(0);
        DummyResponse response = idempotencyService.executeIdempotent(
                key, actorId, endpoint, req, DummyResponse.class,
                () -> {
                    executions.incrementAndGet();
                    return new DummyResponse("NEW", 999);
                }
        );

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.newStock()).isEqualTo(50);
        assertThat(executions.get()).isZero(); // El bloque original NO se ejecutó
    }

    @Test
    @DisplayName("Debe lanzar 409 Conflict si se envía la misma Idempotency-Key con payload distinto")
    void throwsConflictOnSameKeyWithDifferingPayload() {
        UUID actorId = UUID.randomUUID();
        String key = "IDEMP-KEY-12345";
        String endpoint = "/api/v1/stock/adjust";

        // Registro existente con hash de "peticion original"
        String originalHash = IdempotencyService.computeHash("original");
        IdempotencyRecord cachedRecord = new IdempotencyRecord(
                UUID.randomUUID(), key, actorId, endpoint, originalHash, 200, "{}", Instant.now()
        );

        when(idempotencyRepository.findByIdempotencyKeyAndActorUserIdAndEndpoint(key, actorId, endpoint))
                .thenReturn(Optional.of(cachedRecord));

        assertThatThrownBy(() -> idempotencyService.executeIdempotent(
                key, actorId, endpoint, "peticion modificada", String.class, () -> "OK"
        ))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }
}
