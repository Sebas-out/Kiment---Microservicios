package com.kiments.cataloginventory.inventory.service;

import com.kiments.cataloginventory.inventory.dto.InventoryDtos;
import com.kiments.cataloginventory.security.CurrentUser;

import java.util.UUID;

public interface InventoryReportService {
    InventoryDtos.InventorySummaryResponse getSummary(UUID requestedBranchId, CurrentUser user);
    byte[] exportStockExcel(UUID requestedBranchId, String query, boolean availableOnly, CurrentUser user);
}
