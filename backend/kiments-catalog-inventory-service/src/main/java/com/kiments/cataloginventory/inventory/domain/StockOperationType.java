package com.kiments.cataloginventory.inventory.domain;

public enum StockOperationType {
    INITIAL_STOCK,
    MANUAL_IN,
    MANUAL_OUT,
    TRANSFER_IN,
    TRANSFER_OUT,
    // Tipos reservados para futura integración con MS-03 Ventas
    SALE_OUT,
    SALE_RETURN
}
