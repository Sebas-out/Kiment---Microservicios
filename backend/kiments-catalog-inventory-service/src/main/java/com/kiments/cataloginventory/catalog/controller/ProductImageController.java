package com.kiments.cataloginventory.catalog.controller;

import com.kiments.cataloginventory.catalog.dto.ProductDtos;
import com.kiments.cataloginventory.catalog.service.ProductImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/products/{productId}/images")
@Validated
@Tag(name = "Fotografías de Productos", description = "Carga, descarga, ordenamiento y eliminación de imágenes de catálogo")
public class ProductImageController {

    private final ProductImageService service;

    public ProductImageController(ProductImageService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Subir fotografía (global o por color)")
    public ResponseEntity<ProductDtos.ImageResponse> uploadImage(
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID colorId,
            @RequestParam("file") MultipartFile file) {
        ProductDtos.ImageResponse response = service.uploadImage(productId, colorId, file);
        return ResponseEntity.created(URI.create(response.url())).body(response);
    }

    @GetMapping("/{imageId}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    @Operation(summary = "Descargar bytes de fotografía de forma autorizada")
    public ResponseEntity<byte[]> getImage(
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        ProductDtos.ImageResponse metadata = service.getImageMetadata(productId, imageId);
        byte[] bytes = service.getImageBytes(productId, imageId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.mimeType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate().mustRevalidate())
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }

    @GetMapping("/{imageId}/metadata")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    @Operation(summary = "Obtener metadatos de una imagen")
    public ResponseEntity<ProductDtos.ImageResponse> getImageMetadata(
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        return ResponseEntity.ok(service.getImageMetadata(productId, imageId));
    }

    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Eliminar fotografía de producto")
    public ResponseEntity<Void> deleteImage(
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        service.deleteImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/order")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    @Operation(summary = "Reordenar fotografías por color o globales")
    public ResponseEntity<List<ProductDtos.ImageResponse>> reorder(
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID colorId,
            @Valid @RequestBody ProductDtos.OrderImages request) {
        return ResponseEntity.ok(service.reorderImages(productId, colorId, request.imageIds()));
    }
}
