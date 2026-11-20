package com.kiments.cataloginventory.catalog.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products", schema = "catalog_inventory")
public class Product {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "auto_sku", nullable = false)
    private boolean autoSku = false;

    @Column(name = "auto_barcode", nullable = false)
    private boolean autoBarcode = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    protected Product() {}

    public Product(UUID id, Category category, String name, String description, boolean autoSku, boolean autoBarcode, boolean enabled) {
        this.id = id;
        this.category = category;
        this.name = name != null ? name.trim() : null;
        this.description = description != null ? description.trim() : null;
        this.autoSku = autoSku;
        this.autoBarcode = autoBarcode;
        this.enabled = enabled;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(Category category, String name, String description) {
        if (category != null) this.category = category;
        if (name != null) this.name = name.trim();
        if (description != null) this.description = description.trim();
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void addVariant(ProductVariant variant) {
        variants.add(variant);
        variant.setProduct(this);
    }

    public void addImage(ProductImage image) {
        images.add(image);
        image.setProduct(this);
    }

    public UUID getId() { return id; }
    public Category getCategory() { return category; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public boolean isAutoSku() { return autoSku; }
    public boolean isAutoBarcode() { return autoBarcode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
    public List<ProductVariant> getVariants() { return variants; }
    public List<ProductImage> getImages() { return images; }
}
