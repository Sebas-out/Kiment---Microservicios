package com.kiments.cataloginventory.catalog.repository;

import com.kiments.cataloginventory.catalog.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    @Query("SELECT i FROM ProductImage i LEFT JOIN FETCH i.color WHERE i.product.id = :productId ORDER BY i.sortOrder ASC")
    List<ProductImage> findByProductIdWithColor(@Param("productId") UUID productId);

    @Query("SELECT i FROM ProductImage i WHERE i.product.id = :productId AND i.color IS NULL")
    Optional<ProductImage> findGlobalImage(@Param("productId") UUID productId);

    @Query("SELECT i FROM ProductImage i WHERE i.product.id = :productId AND i.color.id = :colorId ORDER BY i.sortOrder ASC")
    List<ProductImage> findByProductIdAndColorId(@Param("productId") UUID productId, @Param("colorId") UUID colorId);

    long countByProductIdAndColorId(UUID productId, UUID colorId);
}
