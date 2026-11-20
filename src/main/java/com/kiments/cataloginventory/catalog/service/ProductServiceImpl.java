package com.kiments.cataloginventory.catalog.service;

import com.kiments.cataloginventory.catalog.domain.*;
import com.kiments.cataloginventory.catalog.dto.ProductDtos;
import com.kiments.cataloginventory.catalog.repository.*;
import com.kiments.cataloginventory.exception.ApiException;
import com.kiments.cataloginventory.inventory.domain.*;
import com.kiments.cataloginventory.inventory.repository.StockBalanceRepository;
import com.kiments.cataloginventory.inventory.repository.StockMovementRepository;
import com.kiments.cataloginventory.inventory.repository.StockOperationRepository;
import com.kiments.cataloginventory.security.BranchSecurityService;
import com.kiments.cataloginventory.security.CurrentUser;
import com.kiments.cataloginventory.shared.BarcodeGenerator;
import com.kiments.cataloginventory.shared.PageResponse;
import com.kiments.cataloginventory.shared.SkuGenerator;
import com.kiments.cataloginventory.shared.idempotency.IdempotencyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final CategoryRepository categoryRepository;
    private final ColorRepository colorRepository;
    private final SizeRepository sizeRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockOperationRepository operationRepository;
    private final StockMovementRepository movementRepository;
    private final SkuGenerator skuGenerator;
    private final BarcodeGenerator barcodeGenerator;
    private final BranchSecurityService branchSecurityService;
    private final IdempotencyService idempotencyService;

    public ProductServiceImpl(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            ProductImageRepository imageRepository,
            CategoryRepository categoryRepository,
            ColorRepository colorRepository,
            SizeRepository sizeRepository,
            StockBalanceRepository balanceRepository,
            StockOperationRepository operationRepository,
            StockMovementRepository movementRepository,
            SkuGenerator skuGenerator,
            BarcodeGenerator barcodeGenerator,
            BranchSecurityService branchSecurityService,
            IdempotencyService idempotencyService) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.imageRepository = imageRepository;
        this.categoryRepository = categoryRepository;
        this.colorRepository = colorRepository;
        this.sizeRepository = sizeRepository;
        this.balanceRepository = balanceRepository;
        this.operationRepository = operationRepository;
        this.movementRepository = movementRepository;
        this.skuGenerator = skuGenerator;
        this.barcodeGenerator = barcodeGenerator;
        this.branchSecurityService = branchSecurityService;
        this.idempotencyService = idempotencyService;
    }

    @Override
    @Transactional
    public ProductDtos.ProductResponse createProduct(ProductDtos.CreateProduct request, String idempotencyKey, CurrentUser user) {
        return idempotencyService.executeIdempotent(
                idempotencyKey,
                user.userId(),
                "/api/v1/products",
                request,
                ProductDtos.ProductResponse.class,
                () -> doCreateProduct(request, user)
        );
    }

    private ProductDtos.ProductResponse doCreateProduct(ProductDtos.CreateProduct request, CurrentUser user) {
        Category category = categoryRepository.findById(request.categoryId())
                .filter(Category::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND",
                        "Categoría no encontrada o inactiva: " + request.categoryId()));

        boolean autoSku = Boolean.TRUE.equals(request.autoSku());
        boolean autoBarcode = Boolean.TRUE.equals(request.autoBarcode());

        Product product = new Product(
                UUID.randomUUID(),
                category,
                request.name(),
                request.description(),
                autoSku,
                autoBarcode,
                request.enabled() != null ? request.enabled() : true
        );

        // Validar unicidad de combinaciones (color, talla) en la misma solicitud
        Set<String> combinationKeys = new HashSet<>();
        for (var vReq : request.variants()) {
            String key = vReq.colorId() + "_" + vReq.sizeId();
            if (!combinationKeys.add(key)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_VARIANT_COMBINATION",
                        "La solicitud contiene combinaciones duplicadas de color y talla");
            }
        }

        // Crear variantes
        List<ProductVariant> variants = new ArrayList<>();
        List<InitialStockTuple> initialStockTuples = new ArrayList<>();

        for (var vReq : request.variants()) {
            Color color = colorRepository.findById(vReq.colorId())
                    .filter(Color::isEnabled)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COLOR_NOT_FOUND",
                            "Color no encontrado o inactivo: " + vReq.colorId()));

            Size size = sizeRepository.findById(vReq.sizeId())
                    .filter(Size::isEnabled)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SIZE_NOT_FOUND",
                            "Talla no encontrada o inactiva: " + vReq.sizeId()));

            // Resolución SKU
            String finalSku;
            if (autoSku) {
                finalSku = skuGenerator.generateSku(product.getName(), color.getName(), size.getName());
            } else {
                if (vReq.sku() == null || vReq.sku().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "SKU_REQUIRED",
                            "El SKU es obligatorio cuando autoSku está desactivado");
                }
                finalSku = vReq.sku().trim().toUpperCase();
                if (variantRepository.findBySku(finalSku).isPresent()) {
                    throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_SKU",
                            "Ya existe una variante registrada con el SKU '" + finalSku + "'");
                }
            }

            // Resolución Barcode
            String finalBarcode = null;
            if (autoBarcode) {
                finalBarcode = skuGenerator.generateBarcode();
            } else if (vReq.barcode() != null && !vReq.barcode().isBlank()) {
                finalBarcode = vReq.barcode().trim();
                if (!barcodeGenerator.isValidManualBarcode(finalBarcode)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BARCODE_FORMAT",
                            "El código de barras '" + finalBarcode + "' no tiene un formato válido");
                }
                if (variantRepository.findByBarcode(finalBarcode).isPresent()) {
                    throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_BARCODE",
                            "Ya existe una variante registrada con el código de barras '" + finalBarcode + "'");
                }
            }

            ProductVariant variant = new ProductVariant(
                    UUID.randomUUID(),
                    product,
                    color,
                    size,
                    finalSku,
                    finalBarcode,
                    vReq.price(),
                    vReq.wholesalePrice(),
                    true
            );
            product.addVariant(variant);
            variants.add(variant);

            if (vReq.initialStock() != null) {
                for (var initStock : vReq.initialStock()) {
                    if (initStock.quantity() > 0) {
                        // Enforce branch scope: if operative, can only initialize for own branch
                        if (!user.isAdmin() && !initStock.branchId().equals(user.branchId())) {
                            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_SCOPE_VIOLATION",
                                    "No puede registrar existencias iniciales para una sucursal distinta a la suya asignada");
                        }
                        initialStockTuples.add(new InitialStockTuple(variant, initStock.branchId(), initStock.quantity()));
                    }
                }
            }
        }

        Product savedProduct = productRepository.save(product);

        // Registrar existencias iniciales de forma atómica y auditable
        if (!initialStockTuples.isEmpty()) {
            StockOperation operation = new StockOperation(
                    UUID.randomUUID(),
                    StockOperationType.INITIAL_STOCK,
                    user.userId(),
                    "INITIAL_PRODUCT_CREATION",
                    "PRODUCT",
                    savedProduct.getId().toString(),
                    "Stock inicial registrado en la creación del producto " + savedProduct.getName(),
                    null
            );
            operationRepository.save(operation);

            for (InitialStockTuple tuple : initialStockTuples) {
                StockBalance balance = new StockBalance(
                        UUID.randomUUID(),
                        tuple.variant(),
                        tuple.branchId(),
                        tuple.quantity()
                );
                balanceRepository.save(balance);

                StockMovement movement = new StockMovement(
                        UUID.randomUUID(),
                        operation,
                        tuple.variant(),
                        tuple.branchId(),
                        0,
                        tuple.quantity(),
                        tuple.quantity(),
                        savedProduct.getName(),
                        tuple.variant().getColor().getName() + " / " + tuple.variant().getSize().getName() + " — " + tuple.variant().getSku(),
                        "Usuario " + user.userId(),
                        "Sucursal " + tuple.branchId(),
                        null
                );
                movementRepository.save(movement);
            }
        }

        return mapToProductResponse(savedProduct, user.branchId());
    }

    private record InitialStockTuple(ProductVariant variant, UUID branchId, int quantity) {}

    @Override
    @Transactional
    public ProductDtos.ProductResponse updateProduct(UUID id, ProductDtos.UpdateProduct request) {
        Product product = productRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Producto no encontrado con id: " + id));

        Category category = categoryRepository.findById(request.categoryId())
                .filter(Category::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND",
                        "Categoría no encontrada o inactiva: " + request.categoryId()));

        product.update(category, request.name(), request.description());
        return mapToProductResponse(product, null);
    }

    @Override
    @Transactional
    public ProductDtos.ProductResponse setProductStatus(UUID id, boolean enabled) {
        Product product = productRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Producto no encontrado con id: " + id));
        product.setEnabled(enabled);
        return mapToProductResponse(product, null);
    }

    @Override
    @Transactional
    public ProductDtos.VariantResponse addVariant(UUID productId, ProductDtos.AddVariant request, CurrentUser user) {
        Product product = productRepository.findById(productId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Producto no encontrado con id: " + productId));

        Color color = colorRepository.findById(request.colorId())
                .filter(Color::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COLOR_NOT_FOUND", "Color no encontrado o inactivo"));

        Size size = sizeRepository.findById(request.sizeId())
                .filter(Size::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SIZE_NOT_FOUND", "Talla no encontrada o inactiva"));

        if (variantRepository.findByProductAndColorAndSize(productId, request.colorId(), request.sizeId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_VARIANT_COMBINATION",
                    "El producto ya cuenta con una variante para la combinación color/talla seleccionada");
        }

        String sku = request.sku();
        if (sku == null || sku.isBlank()) {
            sku = skuGenerator.generateSku(product.getName(), color.getName(), size.getName());
        } else {
            sku = sku.trim().toUpperCase();
            if (variantRepository.findBySku(sku).isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_SKU", "El SKU '" + sku + "' ya está registrado");
            }
        }

        String barcode = request.barcode();
        if (barcode != null && !barcode.isBlank()) {
            barcode = barcode.trim();
            if (variantRepository.findByBarcode(barcode).isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_BARCODE", "El código de barras ya está registrado");
            }
        } else {
            barcode = null;
        }

        ProductVariant variant = new ProductVariant(
                UUID.randomUUID(),
                product,
                color,
                size,
                sku,
                barcode,
                request.price(),
                request.wholesalePrice(),
                true
        );
        product.addVariant(variant);
        ProductVariant savedVariant = variantRepository.save(variant);

        // Si se envió stock inicial para la nueva variante
        if (request.initialStock() != null && !request.initialStock().isEmpty()) {
            StockOperation operation = new StockOperation(
                    UUID.randomUUID(),
                    StockOperationType.INITIAL_STOCK,
                    user.userId(),
                    "VARIANT_ADDITION",
                    "PRODUCT_VARIANT",
                    savedVariant.getId().toString(),
                    "Stock inicial para nueva variante " + savedVariant.getSku(),
                    null
            );
            operationRepository.save(operation);

            for (var initStock : request.initialStock()) {
                if (initStock.quantity() > 0) {
                    if (!user.isAdmin() && !initStock.branchId().equals(user.branchId())) {
                        throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_SCOPE_VIOLATION",
                                "No puede registrar existencias para una sucursal distinta a la suya asignada");
                    }
                    StockBalance balance = new StockBalance(UUID.randomUUID(), savedVariant, initStock.branchId(), initStock.quantity());
                    balanceRepository.save(balance);

                    StockMovement movement = new StockMovement(
                            UUID.randomUUID(),
                            operation,
                            savedVariant,
                            initStock.branchId(),
                            0,
                            initStock.quantity(),
                            initStock.quantity(),
                            product.getName(),
                            color.getName() + " / " + size.getName() + " — " + savedVariant.getSku(),
                            "Usuario " + user.userId(),
                            "Sucursal " + initStock.branchId(),
                            null
                    );
                    movementRepository.save(movement);
                }
            }
        }

        return mapToVariantResponse(savedVariant, user.branchId());
    }

    @Override
    @Transactional
    public ProductDtos.VariantResponse updateVariant(UUID variantId, ProductDtos.UpdateVariant request) {
        ProductVariant variant = variantRepository.findById(variantId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "Variante no encontrada con id: " + variantId));

        if (request.barcode() != null && !request.barcode().isBlank()) {
            String cleanBarcode = request.barcode().trim();
            variantRepository.findByBarcode(cleanBarcode).ifPresent(v -> {
                if (!v.getId().equals(variantId)) {
                    throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_BARCODE", "El código de barras ya está asignado a otra variante");
                }
            });
        }

        variant.updateDetails(request.price(), request.wholesalePrice(), request.barcode());
        if (request.enabled() != null) {
            variant.setEnabled(request.enabled());
        }

        return mapToVariantResponse(variant, null);
    }

    @Override
    @Transactional
    public ProductDtos.VariantResponse setVariantStatus(UUID variantId, boolean enabled) {
        ProductVariant variant = variantRepository.findById(variantId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "Variante no encontrada con id: " + variantId));
        variant.setEnabled(enabled);
        return mapToVariantResponse(variant, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDtos.ProductResponse getProductById(UUID id, UUID requestedBranchId, CurrentUser user) {
        Product product = productRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Producto no encontrado con id: " + id));

        UUID branchScope = user.isAdmin() ? requestedBranchId : user.branchId();
        return mapToProductResponse(product, branchScope);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDtos.VariantResponse getVariantById(UUID variantId, UUID requestedBranchId, CurrentUser user) {
        ProductVariant variant = variantRepository.findById(variantId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "Variante no encontrada con id: " + variantId));

        UUID branchScope = user.isAdmin() ? requestedBranchId : user.branchId();
        return mapToVariantResponse(variant, branchScope);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductDtos.ProductResponse> listProductsGlobal(
            String query, UUID categoryId, UUID colorId, UUID sizeId, UUID requestedBranchId,
            Boolean enabled, Boolean availableOnly, int page, int size, CurrentUser user) {

        UUID branchScope = user.isAdmin() ? requestedBranchId : user.branchId();
        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.min(100, Math.max(1, size));
        var pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by("name").ascending().and(Sort.by("id").ascending()));

        String pattern = (query != null && !query.isBlank()) ? "%" + query.trim().toLowerCase() + "%" : null;
        Page<Product> productPage = productRepository.searchProducts(pattern, categoryId, enabled, pageable);

        List<ProductDtos.ProductResponse> dtos = productPage.getContent().stream()
                .map(p -> mapToProductResponse(p, branchScope))
                .filter(resp -> {
                    if (colorId != null && resp.variants().stream().noneMatch(v -> v.colorId().equals(colorId))) {
                        return false;
                    }
                    if (sizeId != null && resp.variants().stream().noneMatch(v -> v.sizeId().equals(sizeId))) {
                        return false;
                    }
                    if (Boolean.TRUE.equals(availableOnly)) {
                        return resp.variants().stream().anyMatch(v -> v.totalStock() > 0);
                    }
                    return true;
                })
                .toList();

        return new PageResponse<>(dtos, productPage.getNumber(), productPage.getSize(), productPage.getTotalElements(), productPage.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductDtos.VariantResponse> listVariants(
            String query, UUID productId, UUID categoryId, UUID colorId, UUID sizeId, UUID requestedBranchId,
            Boolean enabled, Boolean availableOnly, int page, int size, CurrentUser user) {

        UUID branchScope = user.isAdmin() ? requestedBranchId : user.branchId();
        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.min(100, Math.max(1, size));
        var pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by("product.name").ascending().and(Sort.by("sku").ascending()));

        String pattern = (query != null && !query.isBlank()) ? "%" + query.trim().toLowerCase() + "%" : null;
        Page<ProductVariant> variantsPage = variantRepository.searchVariants(pattern, productId, categoryId, colorId, sizeId, enabled, pageable);

        List<ProductDtos.VariantResponse> responses = variantsPage.getContent().stream()
                .map(v -> mapToVariantResponse(v, branchScope))
                .filter(v -> {
                    if (Boolean.TRUE.equals(availableOnly)) {
                        return v.totalStock() > 0;
                    }
                    return true;
                })
                .toList();

        return new PageResponse<>(responses, variantsPage.getNumber(), variantsPage.getSize(), variantsPage.getTotalElements(), variantsPage.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductDtos.BarcodeData> listBarcodeData(String query, UUID categoryId, int page, int size) {
        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.min(100, Math.max(1, size));
        var pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by("product.name").ascending().and(Sort.by("sku").ascending()));

        String pattern = (query != null && !query.isBlank()) ? "%" + query.trim().toLowerCase() + "%" : null;
        Page<ProductVariant> variantsPage = variantRepository.searchVariants(pattern, null, categoryId, null, null, true, pageable);

        List<ProductDtos.BarcodeData> content = variantsPage.getContent().stream()
                .map(v -> new ProductDtos.BarcodeData(
                        v.getProduct().getId(),
                        v.getProduct().getName(),
                        v.getId(),
                        v.getColor().getName(),
                        v.getSize().getName(),
                        v.getSku(),
                        v.getBarcode(),
                        v.getPrice()
                ))
                .toList();

        return new PageResponse<>(content, variantsPage.getNumber(), variantsPage.getSize(), variantsPage.getTotalElements(), variantsPage.getTotalPages());
    }

    private ProductDtos.ProductResponse mapToProductResponse(Product product, UUID branchScope) {
        List<ProductVariant> variants = variantRepository.findByProductIdWithAttributes(product.getId());
        List<ProductImage> images = imageRepository.findByProductIdWithColor(product.getId());

        String globalImageUrl = images.stream()
                .filter(ProductImage::isGlobal)
                .findFirst()
                .map(img -> "/api/v1/products/" + product.getId() + "/images/" + img.getId())
                .orElse(null);

        List<ProductDtos.ImageResponse> imageDtos = images.stream()
                .map(img -> new ProductDtos.ImageResponse(
                        img.getId(),
                        product.getId(),
                        img.getColor() != null ? img.getColor().getId() : null,
                        img.getColor() != null ? img.getColor().getName() : null,
                        "/api/v1/products/" + product.getId() + "/images/" + img.getId(),
                        img.getMimeType(),
                        img.getByteSize(),
                        img.getWidth(),
                        img.getHeight(),
                        img.getSortOrder(),
                        img.getCreatedAt()
                ))
                .toList();

        List<ProductDtos.VariantResponse> variantDtos = variants.stream()
                .map(v -> mapToVariantResponse(v, branchScope))
                .toList();

        return new ProductDtos.ProductResponse(
                product.getId(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getName(),
                product.getDescription(),
                product.isEnabled(),
                product.isAutoSku(),
                product.isAutoBarcode(),
                globalImageUrl,
                variantDtos,
                imageDtos,
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getVersion()
        );
    }

    private ProductDtos.VariantResponse mapToVariantResponse(ProductVariant variant, UUID branchScope) {
        List<StockBalance> balances = balanceRepository.findByVariantIdWithDetails(variant.getId());

        Map<UUID, Integer> stockByBranch = new HashMap<>();
        int totalStock = 0;

        for (StockBalance b : balances) {
            stockByBranch.put(b.getBranchId(), b.getQuantity());
            if (branchScope == null || branchScope.equals(b.getBranchId())) {
                totalStock += b.getQuantity();
            }
        }

        return new ProductDtos.VariantResponse(
                variant.getId(),
                variant.getProduct().getId(),
                variant.getProduct().getName(),
                variant.getColor().getId(),
                variant.getColor().getName(),
                variant.getColor().getHex(),
                variant.getSize().getId(),
                variant.getSize().getName(),
                variant.getSku(),
                variant.getBarcode(),
                variant.getPrice(),
                variant.getWholesalePrice(),
                variant.isEnabled(),
                stockByBranch,
                totalStock,
                variant.getCreatedAt(),
                variant.getUpdatedAt(),
                variant.getVersion()
        );
    }
}
