package com.kiments.cataloginventory.inventory.domain;

import com.kiments.cataloginventory.catalog.domain.ProductVariant;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "stock_movements", schema = "catalog_inventory")
public class StockMovement {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operation_id", nullable = false)
    private StockOperation operation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "before_quantity", nullable = false)
    private int beforeQuantity;

    @Column(nullable = false)
    private int delta;

    @Column(name = "after_quantity", nullable = false)
    private int afterQuantity;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(name = "variant_detail", nullable = false, length = 150)
    private String variantDetail;

    @Column(name = "user_name", nullable = false, length = 160)
    private String userName;

    @Column(name = "branch_name", nullable = false, length = 100)
    private String branchName;

    @Column(name = "destination_branch_name", length = 100)
    private String destinationBranchName;

    protected StockMovement() {}

    public StockMovement(UUID id, StockOperation operation, ProductVariant variant, UUID branchId,
                         int beforeQuantity, int delta, int afterQuantity,
                         String productName, String variantDetail, String userName,
                         String branchName, String destinationBranchName) {
        this.id = id;
        this.operation = operation;
        this.variant = variant;
        this.branchId = branchId;
        this.beforeQuantity = beforeQuantity;
        this.delta = delta;
        this.afterQuantity = afterQuantity;
        this.productName = productName != null ? productName : "";
        this.variantDetail = variantDetail != null ? variantDetail : "";
        this.userName = userName != null ? userName : "";
        this.branchName = branchName != null ? branchName : "";
        this.destinationBranchName = destinationBranchName;
    }

    public UUID getId() { return id; }
    public StockOperation getOperation() { return operation; }
    public ProductVariant getVariant() { return variant; }
    public UUID getBranchId() { return branchId; }
    public int getBeforeQuantity() { return beforeQuantity; }
    public int getDelta() { return delta; }
    public int getAfterQuantity() { return afterQuantity; }
    public String getProductName() { return productName; }
    public String getVariantDetail() { return variantDetail; }
    public String getUserName() { return userName; }
    public String getBranchName() { return branchName; }
    public String getDestinationBranchName() { return destinationBranchName; }
}
