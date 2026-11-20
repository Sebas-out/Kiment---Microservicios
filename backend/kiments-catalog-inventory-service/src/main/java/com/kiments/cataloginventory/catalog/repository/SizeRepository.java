package com.kiments.cataloginventory.catalog.repository;

import com.kiments.cataloginventory.catalog.domain.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SizeRepository extends JpaRepository<Size, UUID> {

    @Query("SELECT s FROM Size s WHERE lower(trim(s.name)) = lower(trim(:name))")
    Optional<Size> findByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT s FROM Size s WHERE (:enabled IS NULL OR s.enabled = :enabled) " +
            "AND (:pattern IS NULL OR lower(s.name) LIKE :pattern)")
    Page<Size> findAllFiltered(@Param("pattern") String pattern, @Param("enabled") Boolean enabled, Pageable pageable);
}
