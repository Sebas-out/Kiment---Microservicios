package com.kiments.cataloginventory.catalog.repository;

import com.kiments.cataloginventory.catalog.domain.Color;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ColorRepository extends JpaRepository<Color, UUID> {

    @Query("SELECT c FROM Color c WHERE lower(trim(c.name)) = lower(trim(:name))")
    Optional<Color> findByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT c FROM Color c WHERE (:enabled IS NULL OR c.enabled = :enabled) " +
            "AND (:pattern IS NULL OR lower(c.name) LIKE :pattern)")
    Page<Color> findAllFiltered(@Param("pattern") String pattern, @Param("enabled") Boolean enabled, Pageable pageable);
}
