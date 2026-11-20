package com.kiments.cataloginventory.inventory.domain;

import com.kiments.cataloginventory.catalog.domain.ProductVariant;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "stock_transfer_items", schema = "catalog_inventory",
        uniqueConstraints = @UniqueConstraint(name = "uq_transfer_item", columnNames = {"transfer_id", "variant_id"}))
public class StockTransferItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private StockTransfer transfer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(nullable = false)
    private int quantity;

    protected StockTransferItem() {}

    public StockTransferItem(UUID id, StockTransfer transfer, ProductVariant variant, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad a traspasar debe ser mayor a cero");
        }
        this.id = id;
        this.transfer = transfer;
        this.variant = variant;
        this.quantity = quantity;
    }

    public void setTransfer(StockTransfer transfer) {
        this.transfer = transfer;
    }

    public UUID getId() { return id; }
    public StockTransfer getTransfer() { return transfer; }
    public ProductVariant getVariant() { return variant; }
    public int getQuantity() { return quantity; }
}
