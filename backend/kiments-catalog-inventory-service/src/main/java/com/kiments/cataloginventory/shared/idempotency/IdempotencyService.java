package com.kiments.cataloginventory.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiments.cataloginventory.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public static String computeHash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((payload != null ? payload : "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }

    public <T> T executeIdempotent(
            String idempotencyKey,
            UUID actorUserId,
            String endpoint,
            Object requestObject,
            Class<T> responseClass,
            Supplier<T> executionBlock) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return executionBlock.get();
        }

        String payloadStr;
        try {
            payloadStr = objectMapper.writeValueAsString(requestObject);
        } catch (Exception e) {
            payloadStr = String.valueOf(requestObject);
        }
        String currentHash = computeHash(payloadStr);

        Optional<IdempotencyRecord> existing = repository.findByIdempotencyKeyAndActorUserIdAndEndpoint(
                idempotencyKey, actorUserId, endpoint);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(currentHash)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                        "Se envió la misma Idempotency-Key con un contenido de petición diferente");
            }
            try {
                return objectMapper.readValue(record.getResponseBody(), responseClass);
            } catch (Exception e) {
                throw new RuntimeException("Error al deserializar respuesta idempotente almacenada", e);
            }
        }

        T result = executionBlock.get();

        try {
            String resultJson = objectMapper.writeValueAsString(result);
            IdempotencyRecord newRecord = new IdempotencyRecord(
                    UUID.randomUUID(),
                    idempotencyKey,
                    actorUserId,
                    endpoint,
                    currentHash,
                    HttpStatus.OK.value(),
                    resultJson,
                    Instant.now()
            );
            saveRecordInNewTransaction(newRecord);
        } catch (Exception ignored) {
            // Si ocurre colisión en la inserción de idempotencia, se ignora pues la operación ya ocurrió
        }

        return result;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRecordInNewTransaction(IdempotencyRecord record) {
        repository.save(record);
    }
}
