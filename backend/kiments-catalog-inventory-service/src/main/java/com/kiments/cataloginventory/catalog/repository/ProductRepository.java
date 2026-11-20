package com.kiments.cataloginventory.catalog.repository;

import com.kiments.cataloginventory.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("SELECT p FROM Product p WHERE lower(trim(p.name)) = lower(trim(:name))")
    Optional<Product> findByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT p FROM Product p " +
            "JOIN FETCH p.category c " +
            "WHERE p.id = :id")
    Optional<Product> findByIdWithCategory(@Param("id") UUID id);

    @Query(value = "SELECT DISTINCT p FROM Product p " +
            "JOIN FETCH p.category c " +
            "WHERE (:enabled IS NULL OR p.enabled = :enabled) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:pattern IS NULL OR lower(p.name) LIKE :pattern " +
            "     OR EXISTS (SELECT 1 FROM ProductVariant v WHERE v.product = p AND " +
            "                (lower(v.sku) LIKE :pattern " +
            "                 OR (v.barcode IS NOT NULL AND lower(v.barcode) LIKE :pattern))))",
            countQuery = "SELECT count(DISTINCT p) FROM Product p " +
                    "WHERE (:enabled IS NULL OR p.enabled = :enabled) " +
                    "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
                    "AND (:pattern IS NULL OR lower(p.name) LIKE :pattern " +
                    "     OR EXISTS (SELECT 1 FROM ProductVariant v WHERE v.product = p AND " +
                    "                (lower(v.sku) LIKE :pattern " +
                    "                 OR (v.barcode IS NOT NULL AND lower(v.barcode) LIKE :pattern))))")
    Page<Product> searchProducts(@Param("pattern") String pattern,
                                @Param("categoryId") UUID categoryId,
                                @Param("enabled") Boolean enabled,
                                Pageable pageable);
}
