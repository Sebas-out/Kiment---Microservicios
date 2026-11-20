package com.kiments.cataloginventory.inventory.repository;

import com.kiments.cataloginventory.inventory.domain.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID>, JpaSpecificationExecutor<StockTransfer> {

    @Query("SELECT t FROM StockTransfer t " +
            "JOIN FETCH t.operation op " +
            "LEFT JOIN FETCH t.items item " +
            "LEFT JOIN FETCH item.variant v " +
            "LEFT JOIN FETCH v.product p " +
            "LEFT JOIN FETCH v.color c " +
            "LEFT JOIN FETCH v.size s " +
            "WHERE t.id = :id")
    Optional<StockTransfer> findByIdWithDetails(@Param("id") UUID id);
}
