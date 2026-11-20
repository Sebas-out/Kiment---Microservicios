package com.kiments.cataloginventory.inventory.service;

import com.kiments.cataloginventory.catalog.domain.ProductVariant;
import com.kiments.cataloginventory.catalog.repository.ProductVariantRepository;
import com.kiments.cataloginventory.exception.ApiException;
import com.kiments.cataloginventory.inventory.domain.*;
import com.kiments.cataloginventory.inventory.dto.InventoryDtos;
import com.kiments.cataloginventory.inventory.repository.StockBalanceRepository;
import com.kiments.cataloginventory.inventory.repository.StockMovementRepository;
import com.kiments.cataloginventory.inventory.repository.StockOperationRepository;
import com.kiments.cataloginventory.inventory.repository.StockTransferRepository;
import com.kiments.cataloginventory.inventory.repository.StockTransferSpecifications;
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
import java.util.*;

@Service
public class StockTransferServiceImpl implements StockTransferService {

    private final StockTransferRepository transferRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockOperationRepository operationRepository;
    private final StockMovementRepository movementRepository;
    private final ProductVariantRepository variantRepository;
    private final IdempotencyService idempotencyService;

    public StockTransferServiceImpl(
            StockTransferRepository transferRepository,
            StockBalanceRepository balanceRepository,
            StockOperationRepository operationRepository,
            StockMovementRepository movementRepository,
            ProductVariantRepository variantRepository,
            IdempotencyService idempotencyService) {
        this.transferRepository = transferRepository;
        this.balanceRepository = balanceRepository;
        this.operationRepository = operationRepository;
        this.movementRepository = movementRepository;
        this.variantRepository = variantRepository;
        this.idempotencyService = idempotencyService;
    }

    @Override
    @Transactional
    public InventoryDtos.TransferResponse executeTransfer(
            InventoryDtos.TransferRequest request, String idempotencyKey, CurrentUser user) {

        return idempotencyService.executeIdempotent(
                idempotencyKey,
                user.userId(),
                "/api/v1/stock-transfers",
                request,
                InventoryDtos.TransferResponse.class,
                () -> doExecuteTransfer(request, user)
        );
    }

    private InventoryDtos.TransferResponse doExecuteTransfer(
            InventoryDtos.TransferRequest request, CurrentUser user) {

        if (request.originBranchId().equals(request.destinationBranchId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_BRANCHES",
                    "La sucursal de origen y destino del traspaso deben ser distintas");
        }

        // Validación de permisos de sucursal de origen
        if (!user.isAdmin() && !request.originBranchId().equals(user.branchId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_SCOPE_VIOLATION",
                    "No tiene autorización para transferir mercadería desde una sucursal distinta a la asignada");
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_TRANSFER_ITEMS",
                    "El traspaso debe contener al menos un ítem");
        }

        // Consolidación de ítems duplicados por variante
        Map<UUID, Integer> consolidated = new LinkedHashMap<>();
        for (var item : request.items()) {
            if (item.quantity() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY",
                        "La cantidad de cada ítem a transferir debe ser mayor a 0");
            }
            consolidated.merge(item.variantId(), item.quantity(), Integer::sum);
        }

        // Ordenamiento determinista de los IDs de variante para prevenir deadlocks en PostgreSQL
        List<UUID> sortedVariantIds = consolidated.keySet().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();

        // 1. Obtener y validar todas las variantes
        Map<UUID, ProductVariant> variantMap = new HashMap<>();
        for (UUID varId : sortedVariantIds) {
            ProductVariant variant = variantRepository.findById(varId).orElseThrow(() ->
                    new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "Variante no encontrada: " + varId));

            if (!variant.isEnabled() || !variant.getProduct().isEnabled()) {
                throw new ApiException(HttpStatus.CONFLICT, "INACTIVE_VARIANT",
                        "La variante " + variant.getSku() + " o su producto se encuentran desactivados");
            }
            variantMap.put(varId, variant);
        }

        // 2. Bloqueo pesimista determinista de saldos de origen y destino
        Map<UUID, StockBalance> originBalances = new HashMap<>();
        Map<UUID, StockBalance> destinationBalances = new HashMap<>();

        for (UUID varId : sortedVariantIds) {
            ProductVariant variant = variantMap.get(varId);
            int qtyToTransfer = consolidated.get(varId);

            // Bloqueo pesimista en origen
            StockBalance originBalance = balanceRepository.findByVariantIdAndBranchIdForUpdate(varId, request.originBranchId())
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                            "No existen existencias en la sucursal de origen para la variante " + variant.getSku()));

            if (originBalance.getQuantity() < qtyToTransfer) {
                throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                        "Stock insuficiente en origen para la variante " + variant.getSku() +
                                ". Disponible: " + originBalance.getQuantity() + ", Solicitado: " + qtyToTransfer);
            }
            originBalances.put(varId, originBalance);

            // Bloqueo pesimista en destino (se crea seguro con saldo 0 si aún no existe)
            StockBalance destBalance = balanceRepository.findByVariantIdAndBranchIdForUpdate(varId, request.destinationBranchId())
                    .orElseGet(() -> {
                        StockBalance newDest = new StockBalance(UUID.randomUUID(), variant, request.destinationBranchId(), 0);
                        return balanceRepository.save(newDest);
                    });
            destinationBalances.put(varId, destBalance);
        }

        // 3. Crear Operación de Traspaso
        StockOperation operation = new StockOperation(
                UUID.randomUUID(),
                StockOperationType.TRANSFER_OUT,
                user.userId(),
                "TRANSFER",
                "TRANSFER_OPERATION",
                request.originBranchId() + "->" + request.destinationBranchId(),
                request.reason(),
                null
        );
        operationRepository.save(operation);

        StockTransfer transfer = new StockTransfer(
                UUID.randomUUID(),
                request.originBranchId(),
                request.destinationBranchId(),
                request.reason(),
                user.userId(),
                operation
        );

        // 4. Modificar saldos y generar movimientos bidireccionales correlacionados
        List<InventoryDtos.TransferItemResponse> itemResponses = new ArrayList<>();

        for (UUID varId : sortedVariantIds) {
            ProductVariant variant = variantMap.get(varId);
            int qty = consolidated.get(varId);

            StockBalance originBal = originBalances.get(varId);
            StockBalance destBal = destinationBalances.get(varId);

            int originBefore = originBal.getQuantity();
            originBal.adjustQuantity(-qty);
            balanceRepository.save(originBal);

            int destBefore = destBal.getQuantity();
            destBal.adjustQuantity(qty);
            balanceRepository.save(destBal);

            StockTransferItem transferItem = new StockTransferItem(UUID.randomUUID(), transfer, variant, qty);
            transfer.addItem(transferItem);

            String variantDetail = variant.getColor().getName() + " / " + variant.getSize().getName() + " — " + variant.getSku();

            // Movimiento 1: Salida de Origen (TRANSFER_OUT)
            StockMovement outMovement = new StockMovement(
                    UUID.randomUUID(),
                    operation,
                    variant,
                    request.originBranchId(),
                    originBefore,
                    -qty,
                    originBefore - qty,
                    variant.getProduct().getName(),
                    variantDetail,
                    "Usuario " + user.userId(),
                    "Sucursal Origen " + request.originBranchId(),
                    "Sucursal Destino " + request.destinationBranchId()
            );
            movementRepository.save(outMovement);

            // Movimiento 2: Entrada en Destino (TRANSFER_IN)
            StockMovement inMovement = new StockMovement(
                    UUID.randomUUID(),
                    operation,
                    variant,
                    request.destinationBranchId(),
                    destBefore,
                    qty,
                    destBefore + qty,
                    variant.getProduct().getName(),
                    variantDetail,
                    "Usuario " + user.userId(),
                    "Sucursal Destino " + request.destinationBranchId(),
                    "Sucursal Origen " + request.originBranchId()
            );
            movementRepository.save(inMovement);

            itemResponses.add(new InventoryDtos.TransferItemResponse(
                    transferItem.getId(),
                    variant.getId(),
                    variant.getSku(),
                    variant.getProduct().getName(),
                    variantDetail,
                    qty
            ));
        }

        StockTransfer savedTransfer = transferRepository.save(transfer);

        return new InventoryDtos.TransferResponse(
                savedTransfer.getId(),
                savedTransfer.getOriginBranchId(),
                savedTransfer.getDestinationBranchId(),
                savedTransfer.getReason(),
                savedTransfer.getActorUserId(),
                operation.getId(),
                savedTransfer.getCreatedAt(),
                itemResponses
        );
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryDtos.TransferResponse getTransferById(UUID id, CurrentUser user) {
        StockTransfer transfer = transferRepository.findByIdWithDetails(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", "Traspaso no encontrado"));

        if (!user.isAdmin()) {
            boolean involved = transfer.getOriginBranchId().equals(user.branchId()) ||
                    transfer.getDestinationBranchId().equals(user.branchId());
            if (!involved) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "No tiene acceso a consultar este traspaso");
            }
        }

        List<InventoryDtos.TransferItemResponse> items = transfer.getItems().stream()
                .map(item -> new InventoryDtos.TransferItemResponse(
                        item.getId(),
                        item.getVariant().getId(),
                        item.getVariant().getSku(),
                        item.getVariant().getProduct().getName(),
                        item.getVariant().getColor().getName() + " / " + item.getVariant().getSize().getName() + " — " + item.getVariant().getSku(),
                        item.getQuantity()
                ))
                .toList();

        return new InventoryDtos.TransferResponse(
                transfer.getId(),
                transfer.getOriginBranchId(),
                transfer.getDestinationBranchId(),
                transfer.getReason(),
                transfer.getActorUserId(),
                transfer.getOperation().getId(),
                transfer.getCreatedAt(),
                items
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryDtos.TransferResponse> listTransfers(
            UUID requestedBranchId, Instant from, Instant to, int page, int size, CurrentUser user) {

        UUID branchScope = user.isAdmin() ? requestedBranchId : user.branchId();
        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.min(100, Math.max(1, size));
        var pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by("createdAt").descending().and(Sort.by("id").ascending()));

        var spec = StockTransferSpecifications.filter(branchScope, from, to);
        Page<StockTransfer> transfersPage = transferRepository.findAll(spec, pageable);

        List<InventoryDtos.TransferResponse> dtos = transfersPage.getContent().stream()
                .map(t -> {
                    List<InventoryDtos.TransferItemResponse> items = t.getItems().stream()
                            .map(item -> new InventoryDtos.TransferItemResponse(
                                    item.getId(),
                                    item.getVariant().getId(),
                                    item.getVariant().getSku(),
                                    item.getVariant().getProduct().getName(),
                                    item.getVariant().getColor().getName() + " / " + item.getVariant().getSize().getName(),
                                    item.getQuantity()
                            ))
                            .toList();
                    return new InventoryDtos.TransferResponse(
                            t.getId(),
                            t.getOriginBranchId(),
                            t.getDestinationBranchId(),
                            t.getReason(),
                            t.getActorUserId(),
                            t.getOperation().getId(),
                            t.getCreatedAt(),
                            items
                    );
                })
                .toList();

        return new PageResponse<>(dtos, transfersPage.getNumber(), transfersPage.getSize(), transfersPage.getTotalElements(), transfersPage.getTotalPages());
    }
}
