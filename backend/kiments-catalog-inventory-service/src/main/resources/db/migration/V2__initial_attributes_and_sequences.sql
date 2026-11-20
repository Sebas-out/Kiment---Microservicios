-- ============================================================
-- V2: Semillas iniciales opcionales de categorías, colores y tallas
-- ============================================================

INSERT INTO catalog_inventory.categories (id, name, description, enabled, created_at, updated_at, version)
VALUES 
    ('11111111-1111-1111-1111-111111111101', 'Pantalones', 'Prendas inferiores y pantalones', true, NOW(), NOW(), 0),
    ('11111111-1111-1111-1111-111111111102', 'Prendas únicas', 'Prendas y vestidos de diseño único', true, NOW(), NOW(), 0),
    ('11111111-1111-1111-1111-111111111103', 'Conjunto', 'Conjuntos completos combinados', true, NOW(), NOW(), 0)
ON CONFLICT DO NOTHING;

INSERT INTO catalog_inventory.colors (id, name, hex, enabled, created_at, updated_at, version)
VALUES 
    ('22222222-2222-2222-2222-222222222201', 'Rosa nude', '#efd8dd', true, NOW(), NOW(), 0),
    ('22222222-2222-2222-2222-222222222202', 'Cocoa', '#895d4e', true, NOW(), NOW(), 0),
    ('22222222-2222-2222-2222-222222222203', 'Negro', '#18181b', true, NOW(), NOW(), 0),
    ('22222222-2222-2222-2222-222222222204', 'Camel claro', '#c19a6b', true, NOW(), NOW(), 0),
    ('22222222-2222-2222-2222-222222222205', 'Azul', '#233b66', true, NOW(), NOW(), 0),
    ('22222222-2222-2222-2222-222222222206', 'Blanco', '#fafafa', true, NOW(), NOW(), 0),
    ('22222222-2222-2222-2222-222222222207', 'Rosado', '#f9b6c7', true, NOW(), NOW(), 0),
    ('22222222-2222-2222-2222-222222222208', 'Mostaza', '#956329', true, NOW(), NOW(), 0)
ON CONFLICT DO NOTHING;

INSERT INTO catalog_inventory.sizes (id, name, sort_order, enabled, created_at, updated_at, version)
VALUES 
    ('33333333-3333-3333-3333-333333333301', 'XS', 10, true, NOW(), NOW(), 0),
    ('33333333-3333-3333-3333-333333333302', 'S', 20, true, NOW(), NOW(), 0),
    ('33333333-3333-3333-3333-333333333303', 'M', 30, true, NOW(), NOW(), 0),
    ('33333333-3333-3333-3333-333333333304', 'L', 40, true, NOW(), NOW(), 0),
    ('33333333-3333-3333-3333-333333333305', 'XL', 50, true, NOW(), NOW(), 0)
ON CONFLICT DO NOTHING;
