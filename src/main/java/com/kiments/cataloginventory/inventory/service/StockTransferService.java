package com.kiments.cataloginventory.inventory.service;

import com.kiments.cataloginventory.inventory.dto.InventoryDtos;
import com.kiments.cataloginventory.security.CurrentUser;
import com.kiments.cataloginventory.shared.PageResponse;

import java.time.Instant;
import java.util.UUID;

public interface StockTransferService {
    InventoryDtos.TransferResponse executeTransfer(
            InventoryDtos.TransferRequest request, String idempotencyKey, CurrentUser user);

    InventoryDtos.TransferResponse getTransferById(UUID id, CurrentUser user);

    PageResponse<InventoryDtos.TransferResponse> listTransfers(
            UUID requestedBranchId, Instant from, Instant to, int page, int size, CurrentUser user);
}
