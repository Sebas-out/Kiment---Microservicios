-- ============================================================
-- V4: Tabla de sucursales y permisos RBAC de administración
-- ============================================================
-- PRECONDICIÓN: auth.users.branch_id ya existe como UUID nullable
-- (creado en V1). No se crean sucursales automáticas ni se
-- asignan trabajadores en esta migración.
-- Si existieran filas en auth.users con branch_id NOT NULL, la
-- restricción FK de la última sección fallaría; Flyway aborta la
-- migración sin modificar la tabla de usuarios. El operador debe
-- limpiar o mapear esos valores manualmente antes de aplicar V4.
-- ============================================================

-- 1. Tabla de sucursales
CREATE TABLE auth.branches (
    id         UUID        PRIMARY KEY,
    code       VARCHAR(20) NOT NULL
                   UNIQUE
                   CHECK (code ~ '^[A-Z0-9_-]{2,20}$'),
    name       VARCHAR(100) NOT NULL
                   CHECK (length(trim(name)) > 0),
    address    VARCHAR(200),
    phone      VARCHAR(20)
                   CHECK (phone IS NULL OR phone ~ '^\+?[0-9 ()-]{6,20}$'),
    enabled    BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0
);

-- Índice único case-insensitive en code (consultas de unicidad sin distinción)
CREATE UNIQUE INDEX uq_branches_code_ci ON auth.branches (lower(code));

-- Índice de apoyo para filtros frecuentes por estado
CREATE INDEX ix_branches_enabled ON auth.branches (enabled);

-- 2. Nuevos permisos RBAC de sucursales (solo si no existen)
INSERT INTO auth.permissions (code, description)
SELECT 'BRANCH_READ',    'Consultar sucursales'          WHERE NOT EXISTS (SELECT 1 FROM auth.permissions WHERE code = 'BRANCH_READ');
INSERT INTO auth.permissions (code, description)
SELECT 'BRANCH_CREATE',  'Crear sucursales'              WHERE NOT EXISTS (SELECT 1 FROM auth.permissions WHERE code = 'BRANCH_CREATE');
INSERT INTO auth.permissions (code, description)
SELECT 'BRANCH_UPDATE',  'Editar sucursales'             WHERE NOT EXISTS (SELECT 1 FROM auth.permissions WHERE code = 'BRANCH_UPDATE');
INSERT INTO auth.permissions (code, description)
SELECT 'BRANCH_DISABLE', 'Activar o desactivar sucursales' WHERE NOT EXISTS (SELECT 1 FROM auth.permissions WHERE code = 'BRANCH_DISABLE');

-- 3. Asignar los cuatro permisos al rol ADMIN (idempotente)
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM   auth.roles r
CROSS  JOIN auth.permissions p
WHERE  r.code = 'ADMIN'
  AND  p.code IN ('BRANCH_READ', 'BRANCH_CREATE', 'BRANCH_UPDATE', 'BRANCH_DISABLE')
ON CONFLICT DO NOTHING;

-- 4. Clave foránea en auth.users → auth.branches
-- Esta instrucción falla intencionalmente si existen branch_id que no
-- corresponden a ninguna fila en auth.branches, protegiéndonos de
-- datos huérfanos en la base principal. Flyway revertirá la migración.
ALTER TABLE auth.users
    ADD CONSTRAINT fk_users_branch
    FOREIGN KEY (branch_id) REFERENCES auth.branches (id) ON DELETE RESTRICT;

-- Índice de apoyo para JOIN y filtros por sucursal en usuarios
CREATE INDEX ix_users_branch_id ON auth.users (branch_id);
