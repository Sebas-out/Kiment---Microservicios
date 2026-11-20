package com.kiments.cataloginventory.inventory.controller;

import com.kiments.cataloginventory.inventory.dto.InventoryDtos;
import com.kiments.cataloginventory.inventory.service.StockTransferService;
import com.kiments.cataloginventory.security.BranchSecurityService;
import com.kiments.cataloginventory.security.CurrentUser;
import com.kiments.cataloginventory.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
@Validated
@Tag(name = "Traspasos de Stock", description = "Traspasos atómicos de mercadería entre sucursales")
public class StockTransferController {

    private final StockTransferService service;
    private final BranchSecurityService branchSecurityService;

    public StockTransferController(StockTransferService service, BranchSecurityService branchSecurityService) {
        this.service = service;
        this.branchSecurityService = branchSecurityService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    @Operation(summary = "Ejecutar traspaso grupal atómico de variantes entre sucursales")
    public ResponseEntity<InventoryDtos.TransferResponse> executeTransfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InventoryDtos.TransferRequest request) {
        CurrentUser user = branchSecurityService.getCurrentUser();
        branchSecurityService.enforceBranchAccess(request.originBranchId());
        InventoryDtos.TransferResponse response = service.executeTransfer(request, idempotencyKey, user);
        return ResponseEntity.created(URI.create("/api/v1/stock-transfers/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STOCK_READ') or hasAuthority('MOVEMENT_READ')")
    @Operation(summary = "Consultar detalle de un traspaso específico")
    public ResponseEntity<InventoryDtos.TransferResponse> getById(@PathVariable UUID id) {
        CurrentUser user = branchSecurityService.getCurrentUser();
        return ResponseEntity.ok(service.getTransferById(id, user));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STOCK_READ') or hasAuthority('MOVEMENT_READ')")
    @Operation(summary = "Listar traspasos de sucursal con filtros y paginación")
    public ResponseEntity<PageResponse<InventoryDtos.TransferResponse>> listTransfers(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser user = branchSecurityService.getCurrentUser();
        UUID branchScope = branchSecurityService.resolveBranchScope(branchId);
        return ResponseEntity.ok(service.listTransfers(branchScope, from, to, page, size, user));
    }
}
