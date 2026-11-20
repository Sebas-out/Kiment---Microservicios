package com.kiments.cataloginventory.inventory.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers", schema = "catalog_inventory")
public class StockTransfer {

    @Id
    private UUID id;

    @Column(name = "origin_branch_id", nullable = false)
    private UUID originBranchId;

    @Column(name = "destination_branch_id", nullable = false)
    private UUID destinationBranchId;

    @Column(length = 255)
    private String reason;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operation_id", nullable = false)
    private StockOperation operation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockTransferItem> items = new ArrayList<>();

    protected StockTransfer() {}

    public StockTransfer(UUID id, UUID originBranchId, UUID destinationBranchId, String reason,
                         UUID actorUserId, StockOperation operation) {
        if (originBranchId.equals(destinationBranchId)) {
            throw new IllegalArgumentException("La sucursal de origen y destino deben ser distintas");
        }
        this.id = id;
        this.originBranchId = originBranchId;
        this.destinationBranchId = destinationBranchId;
        this.reason = reason;
        this.actorUserId = actorUserId;
        this.operation = operation;
        this.createdAt = Instant.now();
    }

    public void addItem(StockTransferItem item) {
        items.add(item);
        item.setTransfer(this);
    }

    public UUID getId() { return id; }
    public UUID getOriginBranchId() { return originBranchId; }
    public UUID getDestinationBranchId() { return destinationBranchId; }
    public String getReason() { return reason; }
    public UUID getActorUserId() { return actorUserId; }
    public StockOperation getOperation() { return operation; }
    public Instant getCreatedAt() { return createdAt; }
    public List<StockTransferItem> getItems() { return items; }
}
