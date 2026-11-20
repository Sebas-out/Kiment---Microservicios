package com.kiments.cataloginventory.catalog.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_variants", schema = "catalog_inventory")
public class ProductVariant {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "color_id", nullable = false)
    private Color color;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "size_id", nullable = false)
    private Size size;

    @Column(nullable = false, unique = true, length = 60)
    private String sku;

    @Column(unique = true, length = 60)
    private String barcode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "wholesale_price", precision = 12, scale = 2)
    private BigDecimal wholesalePrice;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    protected ProductVariant() {}

    public ProductVariant(UUID id, Product product, Color color, Size size, String sku, String barcode,
                          BigDecimal price, BigDecimal wholesalePrice, boolean enabled) {
        this.id = id;
        this.product = product;
        this.color = color;
        this.size = size;
        this.sku = sku != null ? sku.trim().toUpperCase() : null;
        this.barcode = barcode != null && !barcode.isBlank() ? barcode.trim() : null;
        this.price = price;
        this.wholesalePrice = wholesalePrice;
        this.enabled = enabled;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDetails(BigDecimal price, BigDecimal wholesalePrice, String barcode) {
        if (price != null) this.price = price;
        this.wholesalePrice = wholesalePrice;
        if (barcode != null) {
            this.barcode = barcode.isBlank() ? null : barcode.trim();
        }
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public Color getColor() { return color; }
    public Size getSize() { return size; }
    public String getSku() { return sku; }
    public String getBarcode() { return barcode; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getWholesalePrice() { return wholesalePrice; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
