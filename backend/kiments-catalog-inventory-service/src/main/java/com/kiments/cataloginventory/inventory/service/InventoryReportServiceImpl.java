package com.kiments.cataloginventory.inventory.service;

import com.kiments.cataloginventory.catalog.domain.ProductVariant;
import com.kiments.cataloginventory.catalog.repository.ProductVariantRepository;
import com.kiments.cataloginventory.inventory.domain.StockBalance;
import com.kiments.cataloginventory.inventory.dto.InventoryDtos;
import com.kiments.cataloginventory.inventory.repository.StockBalanceRepository;
import com.kiments.cataloginventory.security.CurrentUser;
import com.kiments.cataloginventory.shared.ExcelExportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InventoryReportServiceImpl implements InventoryReportService {

    private final StockBalanceRepository balanceRepository;
    private final ProductVariantRepository variantRepository;
    private final ExcelExportService excelExportService;

    public InventoryReportServiceImpl(
            StockBalanceRepository balanceRepository,
            ProductVariantRepository variantRepository,
            ExcelExportService excelExportService) {
        this.balanceRepository = balanceRepository;
        this.variantRepository = variantRepository;
        this.excelExportService = excelExportService;
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryDtos.InventorySummaryResponse getSummary(UUID requestedBranchId, CurrentUser user) {
        UUID branchScope = user.isAdmin() ? requestedBranchId : user.branchId();

        // 1. Contar variantes activas únicas cuyos productos también estén activos
        long totalVariants = variantRepository.countActiveVariants();

        // 2. Obtener saldos según el alcance de sucursal
        var balances = balanceRepository.searchStockBalances(branchScope, null, false, PageRequest.of(0, 10000)).getContent();

        // 3. Cantidad total de unidades físicas
        long totalQty = balances.stream().mapToLong(StockBalance::getQuantity).sum();

        // 4. Variantes únicas con stock positivo (agrupadas por variantId para evitar duplicar en vista multi-sucursal)
        Set<UUID> withStockVariantIds = balances.stream()
                .filter(b -> b.getQuantity() > 0 && b.getVariant().isEnabled() && b.getVariant().getProduct().isEnabled())
                .map(b -> b.getVariant().getId())
                .collect(Collectors.toSet());

        long withStock = withStockVariantIds.size();
        long outOfStock = Math.max(0, totalVariants - withStock);

        return new InventoryDtos.InventorySummaryResponse(
                totalVariants,
                withStock,
                outOfStock,
                totalQty
        );
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportStockExcel(UUID requestedBranchId, String query, boolean availableOnly, CurrentUser user) {
        UUID branchScope = user.isAdmin() ? requestedBranchId : user.branchId();

        Page<StockBalance> pageResult = balanceRepository.searchStockBalances(
                branchScope, query, availableOnly,
                PageRequest.of(0, ExcelExportService.MAX_EXPORT_ROWS, Sort.by("variant.product.name").ascending())
        );

        List<String> headers = List.of(
                "Producto",
                "Categoría",
                "Color",
                "Talla",
                "SKU",
                "Código de Barras",
                "Precio",
                "Precio Mayorista",
                "Stock Actual",
                "Sucursal"
        );

        List<List<Object>> rows = new ArrayList<>();
        for (StockBalance b : pageResult.getContent()) {
            ProductVariant v = b.getVariant();
            rows.add(List.of(
                    v.getProduct().getName(),
                    v.getProduct().getCategory().getName(),
                    v.getColor().getName(),
                    v.getSize().getName(),
                    v.getSku(),
                    v.getBarcode() != null ? v.getBarcode() : "",
                    v.getPrice().doubleValue(),
                    v.getWholesalePrice() != null ? v.getWholesalePrice().doubleValue() : "",
                    b.getQuantity(),
                    b.getBranchId().toString()
            ));
        }

        return excelExportService.exportToExcel("Reporte de Inventario", headers, rows);
    }
}
