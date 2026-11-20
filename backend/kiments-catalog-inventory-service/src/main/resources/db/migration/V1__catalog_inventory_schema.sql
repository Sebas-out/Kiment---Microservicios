-- ============================================================
-- V1: Esquema catalog_inventory y tablas del Catálogo e Inventario
-- ============================================================

CREATE SCHEMA IF NOT EXISTS catalog_inventory;

-- 1. Secuencias seguras para SKU y Códigos de Barras automáticos
CREATE SEQUENCE IF NOT EXISTS catalog_inventory.sku_seq START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS catalog_inventory.barcode_seq START WITH 100000000 INCREMENT BY 1;

-- 2. Categorías
CREATE TABLE catalog_inventory.categories (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(500),
    enabled     BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_categories_name_ci ON catalog_inventory.categories (lower(trim(name)));
CREATE INDEX ix_categories_enabled ON catalog_inventory.categories (enabled);

-- 3. Colores
CREATE TABLE catalog_inventory.colors (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    hex         VARCHAR(7)   NOT NULL CHECK (hex ~* '^#[0-9A-Fa-f]{6}$'),
    enabled     BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_colors_name_ci ON catalog_inventory.colors (lower(trim(name)));
CREATE INDEX ix_colors_enabled ON catalog_inventory.colors (enabled);

-- 4. Tallas
CREATE TABLE catalog_inventory.sizes (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(30)  NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    enabled     BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_sizes_name_ci ON catalog_inventory.sizes (lower(trim(name)));
CREATE INDEX ix_sizes_enabled ON catalog_inventory.sizes (enabled);

-- 5. Productos
CREATE TABLE catalog_inventory.products (
    id           UUID          PRIMARY KEY,
    category_id  UUID          NOT NULL REFERENCES catalog_inventory.categories(id) ON DELETE RESTRICT,
    name         VARCHAR(120)  NOT NULL CHECK (length(trim(name)) > 0),
    description  VARCHAR(1000),
    enabled      BOOLEAN       NOT NULL DEFAULT true,
    auto_sku     BOOLEAN       NOT NULL DEFAULT false,
    auto_barcode BOOLEAN       NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_products_category_id ON catalog_inventory.products (category_id);
CREATE INDEX ix_products_enabled ON catalog_inventory.products (enabled);
CREATE INDEX ix_products_name_ci ON catalog_inventory.products (lower(name));

-- 6. Variantes del Producto
CREATE TABLE catalog_inventory.product_variants (
    id              UUID          PRIMARY KEY,
    product_id      UUID          NOT NULL REFERENCES catalog_inventory.products(id) ON DELETE RESTRICT,
    color_id        UUID          NOT NULL REFERENCES catalog_inventory.colors(id) ON DELETE RESTRICT,
    size_id         UUID          NOT NULL REFERENCES catalog_inventory.sizes(id) ON DELETE RESTRICT,
    sku             VARCHAR(60)   NOT NULL,
    barcode         VARCHAR(60),
    price           NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    wholesale_price NUMERIC(12,2) CHECK (wholesale_price IS NULL OR wholesale_price >= 0),
    enabled         BOOLEAN       NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_product_color_size UNIQUE (product_id, color_id, size_id)
);
CREATE UNIQUE INDEX uq_variants_sku ON catalog_inventory.product_variants (sku);
CREATE UNIQUE INDEX uq_variants_barcode ON catalog_inventory.product_variants (barcode) WHERE barcode IS NOT NULL;
CREATE INDEX ix_variants_product_id ON catalog_inventory.product_variants (product_id);
CREATE INDEX ix_variants_color_id ON catalog_inventory.product_variants (color_id);
CREATE INDEX ix_variants_size_id ON catalog_inventory.product_variants (size_id);
CREATE INDEX ix_variants_enabled ON catalog_inventory.product_variants (enabled);

-- 7. Fotografías del Producto (Globales o por Color)
CREATE TABLE catalog_inventory.product_images (
    id          UUID          PRIMARY KEY,
    product_id  UUID          NOT NULL REFERENCES catalog_inventory.products(id) ON DELETE CASCADE,
    color_id    UUID          REFERENCES catalog_inventory.colors(id) ON DELETE SET NULL,
    storage_key VARCHAR(255)  NOT NULL UNIQUE,
    mime_type   VARCHAR(50)   NOT NULL,
    byte_size   BIGINT        NOT NULL CHECK (byte_size > 0),
    width       INT           NOT NULL CHECK (width > 0),
    height      INT           NOT NULL CHECK (height > 0),
    sort_order  INT           NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_images_product_color ON catalog_inventory.product_images (product_id, color_id, sort_order);
CREATE UNIQUE INDEX uq_product_global_image ON catalog_inventory.product_images (product_id) WHERE color_id IS NULL;

-- 8. Saldos de Existencias (Stock por Sucursal)
CREATE TABLE catalog_inventory.stock_balances (
    id          UUID         PRIMARY KEY,
    variant_id  UUID         NOT NULL REFERENCES catalog_inventory.product_variants(id) ON DELETE RESTRICT,
    branch_id   UUID         NOT NULL,
    quantity    INT          NOT NULL CHECK (quantity >= 0),
    version     BIGINT       NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_stock_variant_branch UNIQUE (variant_id, branch_id)
);
CREATE INDEX ix_stock_balances_branch_id ON catalog_inventory.stock_balances (branch_id);
CREATE INDEX ix_stock_balances_variant_id ON catalog_inventory.stock_balances (variant_id);

-- 9. Operaciones de Inventario (Cabecera auditable)
CREATE TABLE catalog_inventory.stock_operations (
    id             UUID         PRIMARY KEY,
    type           VARCHAR(30)  NOT NULL,
    actor_user_id  UUID         NOT NULL,
    source         VARCHAR(30)  NOT NULL DEFAULT 'MANUAL',
    reference_type VARCHAR(50),
    reference_id   VARCHAR(100),
    reason         VARCHAR(255),
    occurred_at    TIMESTAMPTZ  NOT NULL,
    correlation_id VARCHAR(64)
);
CREATE INDEX ix_stock_operations_occurred_at ON catalog_inventory.stock_operations (occurred_at);
CREATE INDEX ix_stock_operations_type ON catalog_inventory.stock_operations (type);
CREATE INDEX ix_stock_operations_actor ON catalog_inventory.stock_operations (actor_user_id);

-- 10. Movimientos de Stock (Líneas inmutables)
CREATE TABLE catalog_inventory.stock_movements (
    id                      UUID          PRIMARY KEY,
    operation_id            UUID          NOT NULL REFERENCES catalog_inventory.stock_operations(id) ON DELETE RESTRICT,
    variant_id              UUID          NOT NULL REFERENCES catalog_inventory.product_variants(id) ON DELETE RESTRICT,
    branch_id               UUID          NOT NULL,
    before_quantity         INT           NOT NULL CHECK (before_quantity >= 0),
    delta                   INT           NOT NULL,
    after_quantity          INT           NOT NULL CHECK (after_quantity >= 0),
    product_name            VARCHAR(120)  NOT NULL,
    variant_detail          VARCHAR(150)  NOT NULL,
    user_name               VARCHAR(160)  NOT NULL,
    branch_name             VARCHAR(100)  NOT NULL,
    destination_branch_name VARCHAR(100)
);
CREATE INDEX ix_movements_operation_id ON catalog_inventory.stock_movements (operation_id);
CREATE INDEX ix_movements_branch_id ON catalog_inventory.stock_movements (branch_id);
CREATE INDEX ix_movements_variant_id ON catalog_inventory.stock_movements (variant_id);

-- 11. Traspasos entre Sucursales
CREATE TABLE catalog_inventory.stock_transfers (
    id                    UUID         PRIMARY KEY,
    origin_branch_id      UUID         NOT NULL,
    destination_branch_id UUID         NOT NULL CHECK (origin_branch_id <> destination_branch_id),
    reason                VARCHAR(255),
    actor_user_id         UUID         NOT NULL,
    operation_id          UUID         NOT NULL REFERENCES catalog_inventory.stock_operations(id) ON DELETE RESTRICT,
    created_at            TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_transfers_origin ON catalog_inventory.stock_transfers (origin_branch_id);
CREATE INDEX ix_transfers_destination ON catalog_inventory.stock_transfers (destination_branch_id);
CREATE INDEX ix_transfers_created_at ON catalog_inventory.stock_transfers (created_at);

-- 12. Ítems del Traspaso
CREATE TABLE catalog_inventory.stock_transfer_items (
    id          UUID  PRIMARY KEY,
    transfer_id UUID  NOT NULL REFERENCES catalog_inventory.stock_transfers(id) ON DELETE CASCADE,
    variant_id  UUID  NOT NULL REFERENCES catalog_inventory.product_variants(id) ON DELETE RESTRICT,
    quantity    INT   NOT NULL CHECK (quantity > 0),
    CONSTRAINT uq_transfer_item UNIQUE (transfer_id, variant_id)
);
CREATE INDEX ix_transfer_items_transfer_id ON catalog_inventory.stock_transfer_items (transfer_id);

-- 13. Registro de Idempotencia
CREATE TABLE catalog_inventory.idempotency_records (
    id              UUID          PRIMARY KEY,
    idempotency_key VARCHAR(120)  NOT NULL,
    actor_user_id   UUID          NOT NULL,
    endpoint        VARCHAR(120)  NOT NULL,
    request_hash    VARCHAR(64)   NOT NULL,
    response_status INT           NOT NULL,
    response_body   TEXT          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_idempotency_key_actor_endpoint UNIQUE (idempotency_key, actor_user_id, endpoint)
);
CREATE INDEX ix_idempotency_created_at ON catalog_inventory.idempotency_records (created_at);
