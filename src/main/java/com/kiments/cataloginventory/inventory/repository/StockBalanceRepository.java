package com.kiments.cataloginventory.inventory.repository;

import com.kiments.cataloginventory.inventory.domain.StockBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockBalanceRepository extends JpaRepository<StockBalance, UUID> {

    Optional<StockBalance> findByVariantIdAndBranchId(UUID variantId, UUID branchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM StockBalance b WHERE b.variant.id = :variantId AND b.branchId = :branchId")
    Optional<StockBalance> findByVariantIdAndBranchIdForUpdate(@Param("variantId") UUID variantId, @Param("branchId") UUID branchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM StockBalance b WHERE b.branchId = :branchId AND b.variant.id IN :variantIds ORDER BY b.variant.id ASC")
    List<StockBalance> findByBranchIdAndVariantIdInForUpdate(@Param("branchId") UUID branchId, @Param("variantIds") Collection<UUID> variantIds);

    @Query("SELECT b FROM StockBalance b JOIN FETCH b.variant v JOIN FETCH v.color c JOIN FETCH v.size s WHERE b.variant.id = :variantId")
    List<StockBalance> findByVariantIdWithDetails(@Param("variantId") UUID variantId);

    @Query("SELECT b FROM StockBalance b WHERE b.variant.id IN :variantIds")
    List<StockBalance> findByVariantIdIn(@Param("variantIds") Collection<UUID> variantIds);

    @Query("SELECT b FROM StockBalance b WHERE b.branchId = :branchId AND b.variant.id IN :variantIds")
    List<StockBalance> findByBranchIdAndVariantIdIn(@Param("branchId") UUID branchId, @Param("variantIds") Collection<UUID> variantIds);

    @Query(value = "SELECT b FROM StockBalance b " +
            "JOIN FETCH b.variant v " +
            "JOIN FETCH v.product p " +
            "JOIN FETCH v.color c " +
            "JOIN FETCH v.size s " +
            "WHERE (:branchId IS NULL OR b.branchId = :branchId) " +
            "AND (:availableOnly = false OR b.quantity > 0) " +
            "AND (:pattern IS NULL OR lower(p.name) LIKE :pattern " +
            "     OR lower(v.sku) LIKE :pattern " +
            "     OR (v.barcode IS NOT NULL AND lower(v.barcode) LIKE :pattern))",
            countQuery = "SELECT count(b) FROM StockBalance b " +
                    "WHERE (:branchId IS NULL OR b.branchId = :branchId) " +
                    "AND (:availableOnly = false OR b.quantity > 0) " +
                    "AND (:pattern IS NULL OR lower(b.variant.product.name) LIKE :pattern " +
                    "     OR lower(b.variant.sku) LIKE :pattern " +
                    "     OR (b.variant.barcode IS NOT NULL AND lower(b.variant.barcode) LIKE :pattern))")
    Page<StockBalance> searchStockBalances(@Param("branchId") UUID branchId,
                                          @Param("pattern") String pattern,
                                          @Param("availableOnly") boolean availableOnly,
                                          Pageable pageable);
}
