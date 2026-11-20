package com.kiments.cataloginventory.inventory.domain;

import com.kiments.cataloginventory.catalog.domain.ProductVariant;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_balances", schema = "catalog_inventory",
        uniqueConstraints = @UniqueConstraint(name = "uq_stock_variant_branch", columnNames = {"variant_id", "branch_id"}))
public class StockBalance {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private int quantity;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockBalance() {}

    public StockBalance(UUID id, ProductVariant variant, UUID branchId, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("La cantidad de existencias no puede ser negativa");
        }
        this.id = id;
        this.variant = variant;
        this.branchId = branchId;
        this.quantity = quantity;
        this.updatedAt = Instant.now();
    }

    public void adjustQuantity(int delta) {
        int next = this.quantity + delta;
        if (next < 0) {
            throw new IllegalStateException("Saldo insuficiente: cantidad actual " + this.quantity + ", ajuste solicitado " + delta);
        }
        this.quantity = next;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ProductVariant getVariant() { return variant; }
    public UUID getBranchId() { return branchId; }
    public int getQuantity() { return quantity; }
    public Long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
