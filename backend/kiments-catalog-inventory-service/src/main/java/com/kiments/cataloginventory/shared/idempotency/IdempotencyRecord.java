package com.kiments.cataloginventory.shared.idempotency;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", schema = "catalog_inventory")
public class IdempotencyRecord {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "endpoint", nullable = false, length = 120)
    private String endpoint;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(UUID id, String idempotencyKey, UUID actorUserId, String endpoint,
                             String requestHash, int responseStatus, String responseBody, Instant createdAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.actorUserId = actorUserId;
        this.endpoint = endpoint;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
