package com.kiments.cataloginventory.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiments.cataloginventory.catalog.domain.*;
import com.kiments.cataloginventory.catalog.dto.CategoryDtos;
import com.kiments.cataloginventory.catalog.dto.ProductDtos;
import com.kiments.cataloginventory.catalog.repository.*;
import com.kiments.cataloginventory.catalog.service.ProductServiceImpl;
import com.kiments.cataloginventory.exception.ApiException;
import com.kiments.cataloginventory.inventory.domain.*;
import com.kiments.cataloginventory.inventory.dto.InventoryDtos;
import com.kiments.cataloginventory.inventory.repository.StockBalanceRepository;
import com.kiments.cataloginventory.inventory.repository.StockMovementRepository;
import com.kiments.cataloginventory.inventory.repository.StockOperationRepository;
import com.kiments.cataloginventory.inventory.repository.StockTransferRepository;
import com.kiments.cataloginventory.inventory.service.StockServiceImpl;
import com.kiments.cataloginventory.inventory.service.StockTransferServiceImpl;
import com.kiments.cataloginventory.security.BranchSecurityService;
import com.kiments.cataloginventory.security.CurrentUser;
import com.kiments.cataloginventory.shared.BarcodeGenerator;
import com.kiments.cataloginventory.shared.SkuGenerator;
import com.kiments.cataloginventory.shared.idempotency.IdempotencyRepository;
import com.kiments.cataloginventory.shared.idempotency.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductAndStockBusinessRulesTests {

    private ProductRepository productRepository;
    private ProductVariantRepository variantRepository;
    private ProductImageRepository imageRepository;
    private CategoryRepository categoryRepository;
    private ColorRepository colorRepository;
    private SizeRepository sizeRepository;
    private StockBalanceRepository balanceRepository;
    private StockOperationRepository operationRepository;
    private StockMovementRepository movementRepository;
    private StockTransferRepository transferRepository;
    private IdempotencyRepository idempotencyRepository;
    private IdempotencyService idempotencyService;
    private BranchSecurityService branchSecurityService;
    private SkuGenerator skuGenerator;
    private BarcodeGenerator barcodeGenerator;

    private ProductServiceImpl productService;
    private StockServiceImpl stockService;
    private StockTransferServiceImpl transferService;

    private CurrentUser adminUser;
    private CurrentUser operativeUser;
    private UUID mainBranchId;
    private UUID secondaryBranchId;
    private Category testCategory;
    private Color testColor1;
    private Color testColor2;
    private Size testSizeM;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        variantRepository = mock(ProductVariantRepository.class);
        imageRepository = mock(ProductImageRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        colorRepository = mock(ColorRepository.class);
        sizeRepository = mock(SizeRepository.class);
        balanceRepository = mock(StockBalanceRepository.class);
        operationRepository = mock(StockOperationRepository.class);
        movementRepository = mock(StockMovementRepository.class);
        transferRepository = mock(StockTransferRepository.class);
        idempotencyRepository = mock(IdempotencyRepository.class);
        branchSecurityService = mock(BranchSecurityService.class);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        idempotencyService = new IdempotencyService(idempotencyRepository, objectMapper);
        barcodeGenerator = new BarcodeGenerator();
        skuGenerator = new SkuGenerator(mock(JdbcTemplate.class), barcodeGenerator);

        productService = new ProductServiceImpl(
                productRepository, variantRepository, imageRepository, categoryRepository,
                colorRepository, sizeRepository, balanceRepository, operationRepository,
                movementRepository, skuGenerator, barcodeGenerator, branchSecurityService, idempotencyService
        );

        stockService = new StockServiceImpl(
                balanceRepository, operationRepository, movementRepository, variantRepository, idempotencyService
        );

        transferService = new StockTransferServiceImpl(
                transferRepository, balanceRepository, operationRepository, movementRepository,
                variantRepository, idempotencyService
        );

        mainBranchId = UUID.randomUUID();
        secondaryBranchId = UUID.randomUUID();

        adminUser = new CurrentUser(UUID.randomUUID(), "ADMIN", Set.of("PRODUCT_WRITE", "STOCK_ADJUST"), null, UUID.randomUUID());
        operativeUser = new CurrentUser(UUID.randomUUID(), "ALMACEN", Set.of("PRODUCT_WRITE", "STOCK_ADJUST"), mainBranchId, UUID.randomUUID());

        testCategory = new Category(UUID.randomUUID(), "Pantalones", "Prendas inferiores", true);
        testColor1 = new Color(UUID.randomUUID(), "Azul", "#0000FF", true);
        testColor2 = new Color(UUID.randomUUID(), "Negro", "#000000", true);
        testSizeM = new Size(UUID.randomUUID(), "M", 30, true);

        when(categoryRepository.findById(testCategory.getId())).thenReturn(Optional.of(testCategory));
        when(colorRepository.findById(testColor1.getId())).thenReturn(Optional.of(testColor1));
        when(colorRepository.findById(testColor2.getId())).thenReturn(Optional.of(testColor2));
        when(sizeRepository.findById(testSizeM.getId())).thenReturn(Optional.of(testSizeM));
    }

    @Test
    @DisplayName("Debe crear producto, variantes y stock inicial de forma atómica")
    void createsProductWithVariantsAndInitialStockAtomics() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        var variant1 = new ProductDtos.CreateVariantItem(
                testColor1.getId(),
                testSizeM.getId(),
                null,
                null,
                new BigDecimal("89.90"),
                new BigDecimal("69.90"),
                List.of(new ProductDtos.InitialStockItem(mainBranchId, 15))
        );

        var variant2 = new ProductDtos.CreateVariantItem(
                testColor2.getId(),
                testSizeM.getId(),
                null,
                null,
                new BigDecimal("89.90"),
                new BigDecimal("69.90"),
                List.of(new ProductDtos.InitialStockItem(mainBranchId, 20))
        );

        var request = new ProductDtos.CreateProduct(
                testCategory.getId(),
                "Pantalón Jean Clásico",
                "Jean denim stretch",
                true,
                true,
                true,
                List.of(variant1, variant2)
        );

        ProductDtos.ProductResponse response = productService.createProduct(request, null, adminUser);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Pantalón Jean Clásico");
        verify(productRepository).save(any(Product.class));
        verify(operationRepository).save(any(StockOperation.class));
        verify(balanceRepository, times(2)).save(any(StockBalance.class));
        verify(movementRepository, times(2)).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("Debe rechazar creación si existen combinaciones duplicadas de color y talla")
    void rejectsDuplicateVariantCombinations() {
        var variant1 = new ProductDtos.CreateVariantItem(
                testColor1.getId(), testSizeM.getId(), null, null, new BigDecimal("50.00"), null, null
        );
        var variant2 = new ProductDtos.CreateVariantItem(
                testColor1.getId(), testSizeM.getId(), null, null, new BigDecimal("50.00"), null, null
        );

        var request = new ProductDtos.CreateProduct(
                testCategory.getId(), "Producto Duplicado", null, true, true, true, List.of(variant1, variant2)
        );

        assertThatThrownBy(() -> productService.createProduct(request, null, adminUser))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Actualizar producto modifica atributos sin tocar saldos ni movimientos de inventario")
    void updateProductModifiesAttributesWithoutAlteringStock() {
        Product existingProduct = new Product(UUID.randomUUID(), testCategory, "Nombre Original", "Desc", true, true, true);
        when(productRepository.findById(existingProduct.getId())).thenReturn(Optional.of(existingProduct));

        var updateReq = new ProductDtos.UpdateProduct(testCategory.getId(), "Nombre Editado", "Nueva Desc");
        ProductDtos.ProductResponse response = productService.updateProduct(existingProduct.getId(), updateReq);

        assertThat(response.name()).isEqualTo("Nombre Editado");
        assertThat(existingProduct.getName()).isEqualTo("Nombre Editado");

        // Stock y movimientos NUNCA deben invocarse en un update de producto
        verifyNoInteractions(operationRepository);
        verifyNoInteractions(movementRepository);
    }

    @Test
    @DisplayName("Ajuste manual de salida debe fallar si el stock disponible es insuficiente")
    void manualOutRejectsInsufficientStock() {
        ProductVariant variant = new ProductVariant(UUID.randomUUID(), null, testColor1, testSizeM, "SKU-1", null, new BigDecimal("10.00"), null, true);
        Product product = new Product(UUID.randomUUID(), testCategory, "Pantalón", "", false, false, true);
        variant.setProduct(product);

        when(variantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));

        StockBalance currentBalance = new StockBalance(UUID.randomUUID(), variant, mainBranchId, 5);
        when(balanceRepository.findByVariantIdAndBranchIdForUpdate(variant.getId(), mainBranchId))
                .thenReturn(Optional.of(currentBalance));

        var adjustment = new InventoryDtos.ManualAdjustment(
                variant.getId(), mainBranchId, 10, StockOperationType.MANUAL_OUT, "Salida por merma"
        );

        assertThatThrownBy(() -> stockService.manualAdjustment(adjustment, null, operativeUser))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("Traspaso atómico debita origen, acredita destino y genera 2 movimientos por cada ítem")
    void executeTransferDebitsOriginAndCreditsDestination() {
        ProductVariant variant = new ProductVariant(UUID.randomUUID(), null, testColor1, testSizeM, "SKU-TR-1", null, new BigDecimal("10.00"), null, true);
        Product product = new Product(UUID.randomUUID(), testCategory, "Pantalón", "", false, false, true);
        variant.setProduct(product);

        when(variantRepository.findById(variant.getId())).thenReturn(Optional.of(variant));

        StockBalance originBalance = new StockBalance(UUID.randomUUID(), variant, mainBranchId, 30);
        StockBalance destBalance = new StockBalance(UUID.randomUUID(), variant, secondaryBranchId, 5);

        when(balanceRepository.findByVariantIdAndBranchIdForUpdate(variant.getId(), mainBranchId))
                .thenReturn(Optional.of(originBalance));
        when(balanceRepository.findByVariantIdAndBranchIdForUpdate(variant.getId(), secondaryBranchId))
                .thenReturn(Optional.of(destBalance));
        when(transferRepository.save(any(StockTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        var transferReq = new InventoryDtos.TransferRequest(
                mainBranchId, secondaryBranchId, "Reabastecimiento",
                List.of(new InventoryDtos.TransferItemRequest(variant.getId(), 12))
        );

        InventoryDtos.TransferResponse response = transferService.executeTransfer(transferReq, null, operativeUser);

        assertThat(response).isNotNull();
        assertThat(originBalance.getQuantity()).isEqualTo(18); // 30 - 12
        assertThat(destBalance.getQuantity()).isEqualTo(17);   // 5 + 12

        // Verificar 2 movimientos guardados (1 para salida en origen, 1 para entrada en destino)
        verify(movementRepository, times(2)).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("Traspaso falla si origen y destino son la misma sucursal")
    void rejectsTransferWhenOriginEqualsDestination() {
        var transferReq = new InventoryDtos.TransferRequest(
                mainBranchId, mainBranchId, "Mismo destino",
                List.of(new InventoryDtos.TransferItemRequest(UUID.randomUUID(), 5))
        );

        assertThatThrownBy(() -> transferService.executeTransfer(transferReq, null, operativeUser))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Traspaso falla si el usuario operativo intenta traspasar desde otra sucursal no asignada")
    void rejectsTransferFromUnauthorizedOriginBranch() {
        var transferReq = new InventoryDtos.TransferRequest(
                secondaryBranchId, mainBranchId, "Intento no autorizado",
                List.of(new InventoryDtos.TransferItemRequest(UUID.randomUUID(), 5))
        );

        assertThatThrownBy(() -> transferService.executeTransfer(transferReq, null, operativeUser))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
