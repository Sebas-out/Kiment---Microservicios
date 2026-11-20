package com.kiments.cataloginventory.inventory.service;

import com.kiments.cataloginventory.inventory.domain.StockOperationType;
import com.kiments.cataloginventory.inventory.dto.InventoryDtos;
import com.kiments.cataloginventory.security.CurrentUser;
import com.kiments.cataloginventory.shared.PageResponse;

import java.time.Instant;
import java.util.UUID;

public interface StockMovementService {
    PageResponse<InventoryDtos.StockMovementResponse> listMovements(
            UUID requestedBranchId,
            StockOperationType type,
            UUID variantId,
            Instant from,
            Instant to,
            String query,
            int page,
            int size,
            CurrentUser user
    );
}
