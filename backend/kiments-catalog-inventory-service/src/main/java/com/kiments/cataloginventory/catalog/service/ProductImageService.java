package com.kiments.cataloginventory.catalog.service;

import com.kiments.cataloginventory.catalog.dto.ProductDtos;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ProductImageService {
    ProductDtos.ImageResponse uploadImage(UUID productId, UUID colorId, MultipartFile file);
    byte[] getImageBytes(UUID productId, UUID imageId);
    ProductDtos.ImageResponse getImageMetadata(UUID productId, UUID imageId);
    void deleteImage(UUID productId, UUID imageId);
    List<ProductDtos.ImageResponse> reorderImages(UUID productId, UUID colorId, List<UUID> orderedImageIds);
}
