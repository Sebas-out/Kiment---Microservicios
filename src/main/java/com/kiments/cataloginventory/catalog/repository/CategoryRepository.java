package com.kiments.cataloginventory.catalog.repository;

import com.kiments.cataloginventory.catalog.domain.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("SELECT c FROM Category c WHERE lower(trim(c.name)) = lower(trim(:name))")
    Optional<Category> findByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT c FROM Category c WHERE (:enabled IS NULL OR c.enabled = :enabled) " +
            "AND (:pattern IS NULL OR lower(c.name) LIKE :pattern)")
    Page<Category> findAllFiltered(@Param("pattern") String pattern, @Param("enabled") Boolean enabled, Pageable pageable);
}
