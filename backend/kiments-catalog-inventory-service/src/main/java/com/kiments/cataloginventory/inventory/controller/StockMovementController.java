package com.kiments.cataloginventory.inventory.controller;

import com.kiments.cataloginventory.inventory.domain.StockOperationType;
import com.kiments.cataloginventory.inventory.dto.InventoryDtos;
import com.kiments.cataloginventory.inventory.service.StockMovementService;
import com.kiments.cataloginventory.security.BranchSecurityService;
import com.kiments.cataloginventory.security.CurrentUser;
import com.kiments.cataloginventory.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-movements")
@Validated
@Tag(name = "Movimientos de Stock", description = "Historial inmutable de movimientos y auditoría de inventario")
public class StockMovementController {

    private final StockMovementService service;
    private final BranchSecurityService branchSecurityService;

    public StockMovementController(StockMovementService service, BranchSecurityService branchSecurityService) {
        this.service = service;
        this.branchSecurityService = branchSecurityService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MOVEMENT_READ')")
    @Operation(summary = "Listar movimientos de stock con filtros avanzados y paginación")
    public ResponseEntity<PageResponse<InventoryDtos.StockMovementResponse>> list(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) StockOperationType type,
            @RequestParam(required = false) UUID variantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser user = branchSecurityService.getCurrentUser();
        UUID branchScope = branchSecurityService.resolveBranchScope(branchId);
        return ResponseEntity.ok(service.listMovements(branchScope, type, variantId, from, to, query, page, size, user));
    }
}
