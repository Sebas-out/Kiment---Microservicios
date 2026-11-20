package com.kiments.cataloginventory.inventory.controller;

import com.kiments.cataloginventory.inventory.dto.InventoryDtos;
import com.kiments.cataloginventory.inventory.service.StockService;
import com.kiments.cataloginventory.security.BranchSecurityService;
import com.kiments.cataloginventory.security.CurrentUser;
import com.kiments.cataloginventory.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock")
@Validated
@Tag(name = "Existencias e Inventario", description = "Consulta y ajuste manual de existencias")
public class StockController {

    private final StockService service;
    private final BranchSecurityService branchSecurityService;

    public StockController(StockService service, BranchSecurityService branchSecurityService) {
        this.service = service;
        this.branchSecurityService = branchSecurityService;
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    @Operation(summary = "Realizar ajuste manual de existencias (Entrada o Salida)")
    public ResponseEntity<InventoryDtos.StockMovementResponse> adjust(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InventoryDtos.ManualAdjustment request) {
        CurrentUser user = branchSecurityService.getCurrentUser();
        branchSecurityService.enforceBranchAccess(request.branchId());
        return ResponseEntity.ok(service.manualAdjustment(request, idempotencyKey, user));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STOCK_READ')")
    @Operation(summary = "Consultar existencias por sucursal con filtros y paginación")
    public ResponseEntity<PageResponse<InventoryDtos.StockBalanceResponse>> listStock(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean availableOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser user = branchSecurityService.getCurrentUser();
        UUID branchScope = branchSecurityService.resolveBranchScope(branchId);
        return ResponseEntity.ok(service.listStockBalances(branchScope, query, availableOnly, page, size, user));
    }

    @GetMapping("/variants/{variantId}")
    @PreAuthorize("hasAuthority('STOCK_READ')")
    @Operation(summary = "Consultar existencias de una variante específica por sucursal")
    public ResponseEntity<List<InventoryDtos.StockBalanceResponse>> getStockByVariant(
            @PathVariable UUID variantId,
            @RequestParam(required = false) UUID branchId) {
        CurrentUser user = branchSecurityService.getCurrentUser();
        UUID branchScope = branchSecurityService.resolveBranchScope(branchId);
        return ResponseEntity.ok(service.getStockByVariant(variantId, branchScope, user));
    }
}
