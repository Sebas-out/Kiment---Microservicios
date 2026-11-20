package com.kiments.cataloginventory.inventory.controller;

import com.kiments.cataloginventory.inventory.dto.InventoryDtos;
import com.kiments.cataloginventory.inventory.service.InventoryReportService;
import com.kiments.cataloginventory.security.BranchSecurityService;
import com.kiments.cataloginventory.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports/inventory")
@Validated
@Tag(name = "Reportes de Inventario", description = "Resumen de métricas y exportación en formato Excel (.xlsx)")
public class InventoryReportController {

    private final InventoryReportService service;
    private final BranchSecurityService branchSecurityService;

    public InventoryReportController(InventoryReportService service, BranchSecurityService branchSecurityService) {
        this.service = service;
        this.branchSecurityService = branchSecurityService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('STOCK_REPORT_READ') or hasAuthority('REPORT_READ')")
    @Operation(summary = "Obtener resumen y métricas de existencias")
    public ResponseEntity<InventoryDtos.InventorySummaryResponse> getSummary(
            @RequestParam(required = false) UUID branchId) {
        CurrentUser user = branchSecurityService.getCurrentUser();
        UUID branchScope = branchSecurityService.resolveBranchScope(branchId);
        return ResponseEntity.ok(service.getSummary(branchScope, user));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('STOCK_REPORT_READ') or hasAuthority('REPORT_READ')")
    @Operation(summary = "Exportar reporte de existencias en formato Microsoft Excel (.xlsx)")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean availableOnly) {
        CurrentUser user = branchSecurityService.getCurrentUser();
        UUID branchScope = branchSecurityService.resolveBranchScope(branchId);
        byte[] bytes = service.exportStockExcel(branchScope, query, availableOnly, user);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventario_kiments.xlsx\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }
}
