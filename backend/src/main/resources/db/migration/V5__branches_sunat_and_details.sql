-- ============================================================
-- V5: Ampliación de Sucursales con datos SUNAT y operativos
-- ============================================================
-- Incorpora:
--   - Ciudad (city)
--   - Ubigeo peruano de 6 dígitos (ubigeo)
--   - Correo electrónico de contacto (email)
--   - Tipo de sucursal: VENTA, ALMACEN o MIXTO (branch_type)
--   - Código de establecimiento SUNAT de 4 dígitos (sunat_code)
--
-- NOTA ARQUITECTÓNICA:
--   - La relación con la empresa emisora y el RUC quedan reservados
--     para el futuro módulo Empresa / Facturación.
--   - branch_type es NULLABLE en BD para no inventar clasificación
--     en registros existentes (sin DEFAULT arbitrario).
--   - sunat_code es opcional con unicidad condicional WHERE NOT NULL.
-- ============================================================

-- 1. Añadir columnas a auth.branches
ALTER TABLE auth.branches
    ADD COLUMN city        VARCHAR(100),
    ADD COLUMN ubigeo      VARCHAR(6),
    ADD COLUMN email       VARCHAR(150),
    ADD COLUMN branch_type VARCHAR(15),
    ADD COLUMN sunat_code  VARCHAR(4);

-- 2. Restricciones de formato y catálogo
ALTER TABLE auth.branches
    ADD CONSTRAINT ck_branches_ubigeo
        CHECK (ubigeo IS NULL OR ubigeo ~ '^[0-9]{6}$'),
    ADD CONSTRAINT ck_branches_email
        CHECK (email IS NULL OR email ~ '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    ADD CONSTRAINT ck_branches_type
        CHECK (branch_type IS NULL OR branch_type IN ('VENTA', 'ALMACEN', 'MIXTO')),
    ADD CONSTRAINT ck_branches_sunat_code
        CHECK (sunat_code IS NULL OR sunat_code ~ '^[0-9]{4}$');

-- 3. Índices de apoyo para búsquedas y filtros
CREATE INDEX ix_branches_type ON auth.branches (branch_type);
CREATE INDEX ix_branches_ubigeo ON auth.branches (ubigeo);

-- 4. Unicidad de código SUNAT para valores no nulos
-- Dos sucursales no pueden tener el mismo código de anexo SUNAT asignado
CREATE UNIQUE INDEX uq_branches_sunat_code
    ON auth.branches (sunat_code)
    WHERE sunat_code IS NOT NULL;
