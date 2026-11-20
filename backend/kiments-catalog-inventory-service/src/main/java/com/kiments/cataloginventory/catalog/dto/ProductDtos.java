package com.kiments.cataloginventory.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProductDtos {
    private ProductDtos() {}

    public record CreateProduct(
            @NotNull(message = "La categoría es obligatoria")
            UUID categoryId,

            @NotBlank(message = "El nombre del producto es obligatorio")
            @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
            String name,

            @Size(max = 1000, message = "La descripción no puede exceder 1000 caracteres")
            String description,

            Boolean autoSku,
            Boolean autoBarcode,
            Boolean enabled,

            @NotEmpty(message = "El producto debe tener al menos una variante (combinación color/talla)")
            @Size(max = 100, message = "No se permiten más de 100 combinaciones de variantes por creación")
            List<@Valid CreateVariantItem> variants
    ) {}

    public record CreateVariantItem(
            @NotNull(message = "El color es obligatorio")
            UUID colorId,

            @NotNull(message = "La talla es obligatoria")
            UUID sizeId,

            @Size(max = 60, message = "El SKU no puede exceder 60 caracteres")
            String sku,

            @Size(max = 60, message = "El código de barras no puede exceder 60 caracteres")
            String barcode,

            @NotNull(message = "El precio es obligatorio")
            @DecimalMin(value = "0.00", message = "El precio debe ser mayor o igual a 0")
            BigDecimal price,

            @DecimalMin(value = "0.00", message = "El precio mayorista debe ser mayor o igual a 0")
            BigDecimal wholesalePrice,

            List<@Valid InitialStockItem> initialStock
    ) {}

    public record InitialStockItem(
            @NotNull(message = "La sucursal de stock inicial es obligatoria")
            UUID branchId,

            @NotNull(message = "La cantidad inicial es obligatoria")
            @Min(value = 0, message = "La cantidad inicial no puede ser negativa")
            Integer quantity
    ) {}

    public record UpdateProduct(
            @NotNull(message = "La categoría es obligatoria")
            UUID categoryId,

            @NotBlank(message = "El nombre del producto es obligatorio")
            @Size(max = 120, message = "El nombre no puede exceder 120 caracteres")
            String name,

            @Size(max = 1000, message = "La descripción no puede exceder 1000 caracteres")
            String description
    ) {}

    public record AddVariant(
            @NotNull(message = "El color es obligatorio")
            UUID colorId,

            @NotNull(message = "La talla es obligatoria")
            UUID sizeId,

            @Size(max = 60, message = "El SKU no puede exceder 60 caracteres")
            String sku,

            @Size(max = 60, message = "El código de barras no puede exceder 60 caracteres")
            String barcode,

            @NotNull(message = "El precio es obligatorio")
            @DecimalMin(value = "0.00", message = "El precio debe ser mayor o igual a 0")
            BigDecimal price,

            @DecimalMin(value = "0.00", message = "El precio mayorista debe ser mayor o igual a 0")
            BigDecimal wholesalePrice,

            List<@Valid InitialStockItem> initialStock
    ) {}

    public record UpdateVariant(
            @NotNull(message = "El precio es obligatorio")
            @DecimalMin(value = "0.00", message = "El precio debe ser mayor o igual a 0")
            BigDecimal price,

            @DecimalMin(value = "0.00", message = "El precio mayorista debe ser mayor o igual a 0")
            BigDecimal wholesalePrice,

            @Size(max = 60, message = "El código de barras no puede exceder 60 caracteres")
            String barcode,

            Boolean enabled
    ) {}

    public record Status(boolean enabled) {}

    public record OrderImages(
            @NotEmpty(message = "La lista de identificadores no puede estar vacía")
            List<UUID> imageIds
    ) {}

    public record ProductResponse(
            UUID id,
            UUID categoryId,
            String categoryName,
            String name,
            String description,
            boolean enabled,
            boolean autoSku,
            boolean autoBarcode,
            String globalImageUrl,
            List<VariantResponse> variants,
            List<ImageResponse> images,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {}

    public record VariantResponse(
            UUID id,
            UUID productId,
            String productName,
            UUID colorId,
            String colorName,
            String colorHex,
            UUID sizeId,
            String sizeName,
            String sku,
            String barcode,
            BigDecimal price,
            BigDecimal wholesalePrice,
            boolean enabled,
            Map<UUID, Integer> stockByBranch,
            int totalStock,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {}

    public record ImageResponse(
            UUID id,
            UUID productId,
            UUID colorId,
            String colorName,
            String url,
            String mimeType,
            long byteSize,
            int width,
            int height,
            int sortOrder,
            Instant createdAt
    ) {}

    public record BarcodeData(
            UUID productId,
            String productName,
            UUID variantId,
            String colorName,
            String sizeName,
            String sku,
            String barcode,
            BigDecimal price
    ) {}
}
