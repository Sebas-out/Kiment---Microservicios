package com.kiments.cataloginventory.inventory.repository;

import com.kiments.cataloginventory.inventory.domain.StockOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockOperationRepository extends JpaRepository<StockOperation, UUID> {
}
