package com.kiments.cataloginventory.catalog.controller;

import com.kiments.cataloginventory.catalog.dto.ProductDtos;
import com.kiments.cataloginventory.catalog.service.ProductService;
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

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Productos y Variantes", description = "Gestión del catálogo completo de productos y variantes")
public class ProductController {

    private final ProductService service;
    private final BranchSecurityService branchSecurityService;

    public ProductController(ProductService service, BranchSecurityService branchSecurityService) {
        this.service = service;
        this.branchSecurityService = branchSecurityService;
    }

    @PostMapping("/products")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Crear producto con variantes y stock inicial atómico")
    public ResponseEntity<ProductDtos.ProductResponse> createProduct(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProductDtos.CreateProduct request) {
        CurrentUser user = branchSecurityService.getCurrentUser();
        ProductDtos.ProductResponse created = service.createProduct(request, idempotencyKey, user);
        return ResponseEntity.created(URI.create("/api/v1/products/" + created.id())).body(created);
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Actualizar datos generales del producto (nombre, descripción, categoría)")
    public ResponseEntity<ProductDtos.ProductResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductDtos.UpdateProduct request) {
        return ResponseEntity.ok(service.updateProduct(id, request));
    }

    @PatchMapping("/products/{id}/status")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Activar o desactivar producto")
    public ResponseEntity<ProductDtos.ProductResponse> setProductStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ProductDtos.Status request) {
        return ResponseEntity.ok(service.setProductStatus(id, request.enabled()));
    }

    @GetMapping("/products/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    @Operation(summary = "Obtener detalle de producto con variantes y fotografías")
    public ResponseEntity<ProductDtos.ProductResponse> getProductById(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID branchId) {
        CurrentUser user = branchSecurityService.getCurrentUser();
        UUID branchScope = branchSecurityService.resolveBranchScope(branchId);
        return ResponseEntity.ok(service.getProductById(id, branchScope, user));
    }

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    @Operation(summary = "Listar catálogo de productos (Vista Global)")
    public ResponseEntity<PageResponse<ProductDtos.ProductResponse>> listProductsGlobal(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID colorId,
            @RequestParam(required = false) UUID sizeId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean available,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        CurrentUser user = branchSecurityService.getCurrentUser();
        UUID branchScope = branchSecurityService.resolveBranchScope(branchId);
        return ResponseEntity.ok(service.listProductsGlobal(
                query, categoryId, colorId, sizeId, branchScope, enabled, available, page, size, user));
    }

    @GetMapping("/products/variants")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    @Operation(summary = "Listar variantes paginadas (Vista Variantes)")
    public ResponseEntity<PageResponse<ProductDtos.VariantResponse>> listVariants(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID colorId,
            @RequestParam(required = false) UUID sizeId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean available,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        CurrentUser user = branchSecurityService.getCurrentUser();
        UUID branchScope = branchSecurityService.resolveBranchScope(branchId);
        return ResponseEntity.ok(service.listVariants(
                query, productId, categoryId, colorId, sizeId, branchScope, enabled, available, page, size, user));
    }

    @PostMapping("/products/{id}/variants")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE') or hasAuthority('VARIANT_WRITE')")
    @Operation(summary = "Agregar nueva variante a un producto existente")
    public ResponseEntity<ProductDtos.VariantResponse> addVariant(
            @PathVariable UUID id,
            @Valid @RequestBody ProductDtos.AddVariant request) {
        CurrentUser user = branchSecurityService.getCurrentUser();
        ProductDtos.VariantResponse created = service.addVariant(id, request, user);
        return ResponseEntity.created(URI.create("/api/v1/variants/" + created.id())).body(created);
    }

    @PutMapping("/variants/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE') or hasAuthority('VARIANT_WRITE')")
    @Operation(summary = "Actualizar precio y datos de variante (no altera existencias)")
    public ResponseEntity<ProductDtos.VariantResponse> updateVariant(
            @PathVariable UUID id,
            @Valid @RequestBody ProductDtos.UpdateVariant request) {
        return ResponseEntity.ok(service.updateVariant(id, request));
    }

    @PatchMapping("/variants/{id}/status")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE') or hasAuthority('VARIANT_WRITE')")
    @Operation(summary = "Activar o desactivar variante")
    public ResponseEntity<ProductDtos.VariantResponse> setVariantStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ProductDtos.Status request) {
        return ResponseEntity.ok(service.setVariantStatus(id, request.enabled()));
    }

    @GetMapping("/variants/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    @Operation(summary = "Obtener detalle de variante por ID")
    public ResponseEntity<ProductDtos.VariantResponse> getVariantById(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID branchId) {
        CurrentUser user = branchSecurityService.getCurrentUser();
        UUID branchScope = branchSecurityService.resolveBranchScope(branchId);
        return ResponseEntity.ok(service.getVariantById(id, branchScope, user));
    }

    @GetMapping("/products/barcode-data")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    @Operation(summary = "Obtener datos de productos y variantes para impresión de códigos de barras")
    public ResponseEntity<PageResponse<ProductDtos.BarcodeData>> listBarcodeData(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(service.listBarcodeData(query, categoryId, page, size));
    }
}
