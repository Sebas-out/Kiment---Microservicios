package com.kiments.cataloginventory.inventory.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_operations", schema = "catalog_inventory")
public class StockOperation {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockOperationType type;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(nullable = false, length = 30)
    private String source = "MANUAL";

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(length = 255)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    protected StockOperation() {}

    public StockOperation(UUID id, StockOperationType type, UUID actorUserId, String source,
                          String referenceType, String referenceId, String reason, String correlationId) {
        this.id = id;
        this.type = type;
        this.actorUserId = actorUserId;
        this.source = source != null ? source : "MANUAL";
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.reason = reason;
        this.occurredAt = Instant.now();
        this.correlationId = correlationId;
    }

    public UUID getId() { return id; }
    public StockOperationType getType() { return type; }
    public UUID getActorUserId() { return actorUserId; }
    public String getSource() { return source; }
    public String getReferenceType() { return referenceType; }
    public String getReferenceId() { return referenceId; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getCorrelationId() { return correlationId; }
}
