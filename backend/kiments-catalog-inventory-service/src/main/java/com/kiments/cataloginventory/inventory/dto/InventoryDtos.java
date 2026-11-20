package com.kiments.cataloginventory.inventory.dto;

import com.kiments.cataloginventory.inventory.domain.StockOperationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InventoryDtos {
    private InventoryDtos() {}

    public record ManualAdjustment(
            @NotNull(message = "La variante es obligatoria")
            UUID variantId,

            @NotNull(message = "La sucursal es obligatoria")
            UUID branchId,

            @NotNull(message = "La cantidad debe ser especificada")
            @Min(value = 1, message = "La cantidad debe ser un entero positivo mayor a 0")
            Integer quantity,

            @NotNull(message = "El tipo de ajuste es obligatorio")
            StockOperationType type,

            @Size(max = 255, message = "El motivo no puede exceder 255 caracteres")
            String reason
    ) {}

    public record TransferRequest(
            @NotNull(message = "La sucursal de origen es obligatoria")
            UUID originBranchId,

            @NotNull(message = "La sucursal de destino es obligatoria")
            UUID destinationBranchId,

            @Size(max = 255, message = "El motivo no puede exceder 255 caracteres")
            String reason,

            @NotEmpty(message = "El traspaso debe contener al menos un ítem")
            @Size(max = 100, message = "No se permiten más de 100 ítems por traspaso")
            List<@Valid TransferItemRequest> items
    ) {}

    public record TransferItemRequest(
            @NotNull(message = "La variante es obligatoria")
            UUID variantId,

            @NotNull(message = "La cantidad es obligatoria")
            @Min(value = 1, message = "La cantidad a traspasar debe ser mayor a 0")
            Integer quantity
    ) {}

    public record StockBalanceResponse(
            UUID id,
            UUID variantId,
            UUID productId,
            String productName,
            String colorName,
            String colorHex,
            String sizeName,
            String sku,
            String barcode,
            UUID branchId,
            int quantity,
            Long version,
            Instant updatedAt
    ) {}

    public record StockMovementResponse(
            UUID id,
            UUID operationId,
            StockOperationType type,
            UUID variantId,
            UUID branchId,
            String branchName,
            String destinationBranchName,
            String productName,
            String variantDetail,
            int beforeQuantity,
            int delta,
            int afterQuantity,
            String reason,
            String userName,
            UUID actorUserId,
            Instant occurredAt,
            String correlationId
    ) {}

    public record TransferResponse(
            UUID id,
            UUID originBranchId,
            UUID destinationBranchId,
            String reason,
            UUID actorUserId,
            UUID operationId,
            Instant createdAt,
            List<TransferItemResponse> items
    ) {}

    public record TransferItemResponse(
            UUID id,
            UUID variantId,
            String sku,
            String productName,
            String variantDetail,
            int quantity
    ) {}

    public record InventorySummaryResponse(
            long totalVariants,
            long variantsWithStock,
            long variantsOutOfStock,
            long totalQuantity
    ) {}
}
