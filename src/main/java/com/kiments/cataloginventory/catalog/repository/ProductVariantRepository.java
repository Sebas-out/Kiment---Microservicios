package com.kiments.cataloginventory.catalog.repository;

import com.kiments.cataloginventory.catalog.domain.ProductVariant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    @Query("SELECT count(v) FROM ProductVariant v WHERE v.enabled = true AND v.product.enabled = true")
    long countActiveVariants();

    Optional<ProductVariant> findBySku(String sku);

    Optional<ProductVariant> findByBarcode(String barcode);

    @Query("SELECT v FROM ProductVariant v WHERE v.product.id = :productId AND v.color.id = :colorId AND v.size.id = :sizeId")
    Optional<ProductVariant> findByProductAndColorAndSize(@Param("productId") UUID productId,
                                                         @Param("colorId") UUID colorId,
                                                         @Param("sizeId") UUID sizeId);

    @Query("SELECT v FROM ProductVariant v " +
            "JOIN FETCH v.product p " +
            "JOIN FETCH v.color c " +
            "JOIN FETCH v.size s " +
            "WHERE v.product.id = :productId")
    List<ProductVariant> findByProductIdWithAttributes(@Param("productId") UUID productId);

    @Query(value = "SELECT v FROM ProductVariant v " +
            "JOIN FETCH v.product p " +
            "JOIN FETCH p.category cat " +
            "JOIN FETCH v.color c " +
            "JOIN FETCH v.size s " +
            "WHERE (:enabled IS NULL OR v.enabled = :enabled) " +
            "AND (:productId IS NULL OR v.product.id = :productId) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:colorId IS NULL OR v.color.id = :colorId) " +
            "AND (:sizeId IS NULL OR v.size.id = :sizeId) " +
            "AND (:pattern IS NULL OR lower(p.name) LIKE :pattern " +
            "     OR lower(v.sku) LIKE :pattern " +
            "     OR (v.barcode IS NOT NULL AND lower(v.barcode) LIKE :pattern))",
            countQuery = "SELECT count(v) FROM ProductVariant v " +
                    "WHERE (:enabled IS NULL OR v.enabled = :enabled) " +
                    "AND (:productId IS NULL OR v.product.id = :productId) " +
                    "AND (:categoryId IS NULL OR v.product.category.id = :categoryId) " +
                    "AND (:colorId IS NULL OR v.color.id = :colorId) " +
                    "AND (:sizeId IS NULL OR v.size.id = :sizeId) " +
                    "AND (:pattern IS NULL OR lower(v.product.name) LIKE :pattern " +
                    "     OR lower(v.sku) LIKE :pattern " +
                    "     OR (v.barcode IS NOT NULL AND lower(v.barcode) LIKE :pattern))")
    Page<ProductVariant> searchVariants(@Param("pattern") String pattern,
                                        @Param("productId") UUID productId,
                                        @Param("categoryId") UUID categoryId,
                                        @Param("colorId") UUID colorId,
                                        @Param("sizeId") UUID sizeId,
                                        @Param("enabled") Boolean enabled,
                                        Pageable pageable);
}
