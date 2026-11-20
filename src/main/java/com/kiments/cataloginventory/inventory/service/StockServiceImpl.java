package com.kiments.cataloginventory.inventory.service;

import com.kiments.cataloginventory.catalog.domain.ProductVariant;
import com.kiments.cataloginventory.catalog.repository.ProductVariantRepository;
import com.kiments.cataloginventory.exception.ApiException;
import com.kiments.cataloginventory.inventory.domain.*;
import com.kiments.cataloginventory.inventory.dto.InventoryDtos;
import com.kiments.cataloginventory.inventory.repository.StockBalanceRepository;
import com.kiments.cataloginventory.inventory.repository.StockMovementRepository;
import com.kiments.cataloginventory.inventory.repository.StockOperationRepository;
import com.kiments.cataloginventory.security.CurrentUser;
import com.kiments.cataloginventory.shared.PageResponse;
import com.kiments.cataloginventory.shared.idempotency.IdempotencyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StockServiceImpl implements StockService {

    private final StockBalanceRepository balanceRepository;
    private final StockOperationRepository operationRepository;
    private final StockMovementRepository movementRepository;
    private final ProductVariantRepository variantRepository;
    private final IdempotencyService idempotencyService;

    public StockServiceImpl(
            StockBalanceRepository balanceRepository,
            StockOperationRepository operationRepository,
            StockMovementRepository movementRepository,
            ProductVariantRepository variantRepository,
            IdempotencyService idempotencyService) {
        this.balanceRepository = balanceRepository;
        this.operationRepository = operationRepository;
        this.movementRepository = movementRepository;
        this.variantRepository = variantRepository;
        this.idempotencyService = idempotencyService;
    }

    @Override
    @Transactional
    public InventoryDtos.StockMovementResponse manualAdjustment(
            InventoryDtos.ManualAdjustment request, String idempotencyKey, CurrentUser user) {

        return idempotencyService.executeIdempotent(
                idempotencyKey,
                user.userId(),
                "/api/v1/stock/adjust",
                request,
                InventoryDtos.StockMovementResponse.class,
                () -> doManualAdjustment(request, user)
        );
    }

    private InventoryDtos.StockMovementResponse doManualAdjustment(
            InventoryDtos.ManualAdjustment request, CurrentUser user) {

        // Validar tipo permitido en endpoint manual
        if (request.type() != StockOperationType.MANUAL_IN && request.type() != StockOperationType.MANUAL_OUT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ADJUSTMENT_TYPE",
                    "El endpoint manual únicamente admite tipos MANUAL_IN o MANUAL_OUT");
        }

        // Scope por sucursal
        if (!user.isAdmin() && !request.branchId().equals(user.branchId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_SCOPE_VIOLATION",
                    "No tiene permisos para modificar existencias de una sucursal distinta a la suya asignada");
        }

        ProductVariant variant = variantRepository.findById(request.variantId()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "Variante no encontrada con id: " + request.variantId()));

        if (!variant.isEnabled() || !variant.getProduct().isEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "INACTIVE_PRODUCT_OR_VARIANT",
                    "No se pueden realizar ajustes de stock sobre productos o variantes desactivadas");
        }

        int delta = request.type() == StockOperationType.MANUAL_IN ? request.quantity() : -request.quantity();

        // Bloqueo pesimista para evitar carreras concurrentes en el saldo
        StockBalance balance = balanceRepository.findByVariantIdAndBranchIdForUpdate(variant.getId(), request.branchId())
                .orElseGet(() -> {
                    if (delta < 0) {
                        throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                                "La sucursal no cuenta con registro previo de existencias para realizar una salida");
                    }
                    StockBalance newBalance = new StockBalance(UUID.randomUUID(), variant, request.branchId(), 0);
                    return balanceRepository.save(newBalance);
                });

        int before = balance.getQuantity();
        if (before + delta < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                    "Stock insuficiente en la sucursal. Stock disponible: " + before + ", salida requerida: " + request.quantity());
        }

        balance.adjustQuantity(delta);
        balanceRepository.save(balance);

        StockOperation operation = new StockOperation(
                UUID.randomUUID(),
                request.type(),
                user.userId(),
                "MANUAL",
                "MANUAL_ADJUSTMENT",
                variant.getSku(),
                request.reason(),
                null
        );
        operationRepository.save(operation);

        StockMovement movement = new StockMovement(
                UUID.randomUUID(),
                operation,
                variant,
                request.branchId(),
                before,
                delta,
                before + delta,
                variant.getProduct().getName(),
                variant.getColor().getName() + " / " + variant.getSize().getName() + " — " + variant.getSku(),
                "Usuario " + user.userId(),
                "Sucursal " + request.branchId(),
                null
        );
        StockMovement savedMovement = movementRepository.save(movement);

        return mapToMovementResponse(savedMovement);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryDtos.StockBalanceResponse> getStockByVariant(
            UUID variantId, UUID requestedBranchId, CurrentUser user) {

        ProductVariant variant = variantRepository.findById(variantId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "Variante no encontrada con id: " + variantId));

        UUID branchScope = user.isAdmin() ? requestedBranchId : user.branchId();

        List<StockBalance> balances = balanceRepository.findByVariantIdWithDetails(variant.getId());
        return balances.stream()
                .filter(b -> branchScope == null || b.getBranchId().equals(branchScope))
                .map(this::mapToBalanceResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryDtos.StockBalanceResponse> listStockBalances(
            UUID requestedBranchId, String query, boolean availableOnly, int page, int size, CurrentUser user) {

        UUID branchScope = user.isAdmin() ? requestedBranchId : user.branchId();
        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.min(100, Math.max(1, size));
        var pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by("updatedAt").descending().and(Sort.by("id").ascending()));

        String pattern = (query != null && !query.isBlank()) ? "%" + query.trim().toLowerCase() + "%" : null;
        Page<StockBalance> result = balanceRepository.searchStockBalances(branchScope, pattern, availableOnly, pageable);

        return PageResponse.from(result.map(this::mapToBalanceResponse));
    }

    private InventoryDtos.StockBalanceResponse mapToBalanceResponse(StockBalance b) {
        return new InventoryDtos.StockBalanceResponse(
                b.getId(),
                b.getVariant().getId(),
                b.getVariant().getProduct().getId(),
                b.getVariant().getProduct().getName(),
                b.getVariant().getColor().getName(),
                b.getVariant().getColor().getHex(),
                b.getVariant().getSize().getName(),
                b.getVariant().getSku(),
                b.getVariant().getBarcode(),
                b.getBranchId(),
                b.getQuantity(),
                b.getVersion(),
                b.getUpdatedAt()
        );
    }

    private InventoryDtos.StockMovementResponse mapToMovementResponse(StockMovement m) {
        return new InventoryDtos.StockMovementResponse(
                m.getId(),
                m.getOperation().getId(),
                m.getOperation().getType(),
                m.getVariant().getId(),
                m.getBranchId(),
                m.getBranchName(),
                m.getDestinationBranchName(),
                m.getProductName(),
                m.getVariantDetail(),
                m.getBeforeQuantity(),
                m.getDelta(),
                m.getAfterQuantity(),
                m.getOperation().getReason(),
                m.getUserName(),
                m.getOperation().getActorUserId(),
                m.getOperation().getOccurredAt(),
                m.getOperation().getCorrelationId()
        );
    }
}
