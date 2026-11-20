package com.kiments.cataloginventory.catalog.service;

import com.kiments.cataloginventory.catalog.domain.Color;
import com.kiments.cataloginventory.catalog.domain.Product;
import com.kiments.cataloginventory.catalog.domain.ProductImage;
import com.kiments.cataloginventory.catalog.dto.ProductDtos;
import com.kiments.cataloginventory.catalog.repository.ColorRepository;
import com.kiments.cataloginventory.catalog.repository.ProductImageRepository;
import com.kiments.cataloginventory.catalog.repository.ProductRepository;
import com.kiments.cataloginventory.exception.ApiException;
import com.kiments.cataloginventory.ports.storage.StoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProductImageServiceImpl implements ProductImageService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageServiceImpl.class);
    private static final int MAX_IMAGES_PER_COLOR = 5;

    private final ProductRepository productRepository;
    private final ColorRepository colorRepository;
    private final ProductImageRepository imageRepository;
    private final StoragePort storagePort;

    public ProductImageServiceImpl(ProductRepository productRepository,
                                  ColorRepository colorRepository,
                                  ProductImageRepository imageRepository,
                                  StoragePort storagePort) {
        this.productRepository = productRepository;
        this.colorRepository = colorRepository;
        this.imageRepository = imageRepository;
        this.storagePort = storagePort;
    }

    @Override
    @Transactional
    public ProductDtos.ImageResponse uploadImage(UUID productId, UUID colorId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "El archivo de imagen no puede estar vacío");
        }

        Product product = productRepository.findById(productId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Producto no encontrado"));

        Color color = null;
        if (colorId != null) {
            color = colorRepository.findById(colorId).orElseThrow(() ->
                    new ApiException(HttpStatus.NOT_FOUND, "COLOR_NOT_FOUND", "Color no encontrado"));

            long count = imageRepository.countByProductIdAndColorId(productId, colorId);
            if (count >= MAX_IMAGES_PER_COLOR) {
                throw new ApiException(HttpStatus.CONFLICT, "MAX_IMAGES_EXCEEDED",
                        "Se ha alcanzado el límite máximo de " + MAX_IMAGES_PER_COLOR + " imágenes para este color");
            }
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_READ_ERROR", "Error al leer el archivo cargado");
        }

        // Almacenar físicamente en storage port (con decodificación y sanitización)
        StoragePort.StoredImage stored = storagePort.storeImage(bytes, file.getContentType(), file.getOriginalFilename());

        try {
            // Si es imagen global y ya existía una, se reemplaza de forma segura
            String oldStorageKeyToDelete = null;
            if (color == null) {
                Optional<ProductImage> existingGlobal = imageRepository.findGlobalImage(productId);
                if (existingGlobal.isPresent()) {
                    ProductImage oldImage = existingGlobal.get();
                    oldStorageKeyToDelete = oldImage.getStorageKey();
                    imageRepository.delete(oldImage);
                    imageRepository.flush();
                }
            }

            int sortOrder = 0;
            if (color != null) {
                List<ProductImage> currentImages = imageRepository.findByProductIdAndColorId(productId, colorId);
                sortOrder = currentImages.size();
            }

            ProductImage imageEntity = new ProductImage(
                    UUID.randomUUID(),
                    product,
                    color,
                    stored.storageKey(),
                    stored.mimeType(),
                    stored.byteSize(),
                    stored.width(),
                    stored.height(),
                    sortOrder
            );
            ProductImage saved = imageRepository.save(imageEntity);

            // Si reemplazó una imagen global previa, ahora podemos borrar el archivo anterior con seguridad
            if (oldStorageKeyToDelete != null) {
                storagePort.removeImage(oldStorageKeyToDelete);
            }

            return mapToDto(saved);

        } catch (Exception e) {
            // Si la persistencia en DB falla, limpiamos el archivo recién subido para no dejar huérfanos
            log.error("Fallo al persistir imagen en base de datos. Limpiando archivo {}: {}", stored.storageKey(), e.getMessage());
            storagePort.removeImage(stored.storageKey());
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getImageBytes(UUID productId, UUID imageId) {
        ProductImage image = imageRepository.findById(imageId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "Fotografía no encontrada"));

        if (!image.getProduct().getId().equals(productId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_MISMATCH", "La imagen no corresponde al producto indicado");
        }

        return storagePort.retrieveImage(image.getStorageKey());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDtos.ImageResponse getImageMetadata(UUID productId, UUID imageId) {
        ProductImage image = imageRepository.findById(imageId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "Fotografía no encontrada"));

        if (!image.getProduct().getId().equals(productId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_MISMATCH", "La imagen no corresponde al producto indicado");
        }

        return mapToDto(image);
    }

    @Override
    @Transactional
    public void deleteImage(UUID productId, UUID imageId) {
        ProductImage image = imageRepository.findById(imageId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "Fotografía no encontrada"));

        if (!image.getProduct().getId().equals(productId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_MISMATCH", "La imagen no corresponde al producto indicado");
        }

        String storageKey = image.getStorageKey();
        imageRepository.delete(image);
        imageRepository.flush();

        // Borrado del archivo físico posterior al commit/flush exitoso
        storagePort.removeImage(storageKey);
    }

    @Override
    @Transactional
    public List<ProductDtos.ImageResponse> reorderImages(UUID productId, UUID colorId, List<UUID> orderedImageIds) {
        List<ProductImage> images;
        if (colorId != null) {
            images = imageRepository.findByProductIdAndColorId(productId, colorId);
        } else {
            images = imageRepository.findByProductIdWithColor(productId);
        }

        for (int i = 0; i < orderedImageIds.size(); i++) {
            UUID id = orderedImageIds.get(i);
            int order = i;
            images.stream()
                    .filter(img -> img.getId().equals(id))
                    .findFirst()
                    .ifPresent(img -> img.setSortOrder(order));
        }

        return images.stream().map(this::mapToDto).toList();
    }

    private ProductDtos.ImageResponse mapToDto(ProductImage img) {
        return new ProductDtos.ImageResponse(
                img.getId(),
                img.getProduct().getId(),
                img.getColor() != null ? img.getColor().getId() : null,
                img.getColor() != null ? img.getColor().getName() : null,
                "/api/v1/products/" + img.getProduct().getId() + "/images/" + img.getId(),
                img.getMimeType(),
                img.getByteSize(),
                img.getWidth(),
                img.getHeight(),
                img.getSortOrder(),
                img.getCreatedAt()
        );
    }
}
