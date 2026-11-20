package com.kiments.cataloginventory.catalog.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_images", schema = "catalog_inventory")
public class ProductImage {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "color_id")
    private Color color;

    @Column(name = "storage_key", nullable = false, unique = true, length = 255)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductImage() {}

    public ProductImage(UUID id, Product product, Color color, String storageKey, String mimeType,
                        long byteSize, int width, int height, int sortOrder) {
        this.id = id;
        this.product = product;
        this.color = color;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.byteSize = byteSize;
        this.width = width;
        this.height = height;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public Color getColor() { return color; }
    public String getStorageKey() { return storageKey; }
    public String getMimeType() { return mimeType; }
    public long getByteSize() { return byteSize; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isGlobal() { return color == null; }
}
