package com.kiments.cataloginventory.inventory.service;

import com.kiments.cataloginventory.inventory.dto.InventoryDtos;
import com.kiments.cataloginventory.security.CurrentUser;
import com.kiments.cataloginventory.shared.PageResponse;

import java.util.List;
import java.util.UUID;

public interface StockService {
    InventoryDtos.StockMovementResponse manualAdjustment(
            InventoryDtos.ManualAdjustment request, String idempotencyKey, CurrentUser user);

    List<InventoryDtos.StockBalanceResponse> getStockByVariant(
            UUID variantId, UUID requestedBranchId, CurrentUser user);

    PageResponse<InventoryDtos.StockBalanceResponse> listStockBalances(
            UUID requestedBranchId, String query, boolean availableOnly, int page, int size, CurrentUser user);
}
