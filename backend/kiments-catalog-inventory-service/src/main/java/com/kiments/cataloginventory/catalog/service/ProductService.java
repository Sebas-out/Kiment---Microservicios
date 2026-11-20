package com.kiments.cataloginventory.catalog.service;

import com.kiments.cataloginventory.catalog.dto.ProductDtos;
import com.kiments.cataloginventory.security.CurrentUser;
import com.kiments.cataloginventory.shared.PageResponse;

import java.util.UUID;

public interface ProductService {
    ProductDtos.ProductResponse createProduct(ProductDtos.CreateProduct request, String idempotencyKey, CurrentUser user);
    ProductDtos.ProductResponse updateProduct(UUID id, ProductDtos.UpdateProduct request);
    ProductDtos.ProductResponse setProductStatus(UUID id, boolean enabled);

    ProductDtos.VariantResponse addVariant(UUID productId, ProductDtos.AddVariant request, CurrentUser user);
    ProductDtos.VariantResponse updateVariant(UUID variantId, ProductDtos.UpdateVariant request);
    ProductDtos.VariantResponse setVariantStatus(UUID variantId, boolean enabled);

    ProductDtos.ProductResponse getProductById(UUID id, UUID requestedBranchId, CurrentUser user);
    ProductDtos.VariantResponse getVariantById(UUID variantId, UUID requestedBranchId, CurrentUser user);

    PageResponse<ProductDtos.ProductResponse> listProductsGlobal(
            String query, UUID categoryId, UUID colorId, UUID sizeId, UUID requestedBranchId,
            Boolean enabled, Boolean availableOnly, int page, int size, CurrentUser user);

    PageResponse<ProductDtos.VariantResponse> listVariants(
            String query, UUID productId, UUID categoryId, UUID colorId, UUID sizeId, UUID requestedBranchId,
            Boolean enabled, Boolean availableOnly, int page, int size, CurrentUser user);

    PageResponse<ProductDtos.BarcodeData> listBarcodeData(
            String query, UUID categoryId, int page, int size);
}
