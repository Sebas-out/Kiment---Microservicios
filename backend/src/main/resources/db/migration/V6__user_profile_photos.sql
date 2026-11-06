-- ============================================================
-- V6: Incorporación de referencia a fotografía de perfil de usuarios
-- ============================================================
-- Agrega columna profile_image_key nullable a auth.users.
-- Almacena únicamente la clave interna del archivo (ej. <uuid>.<ext>),
-- sin rutas físicas del sistema ni URLs de localhost.
-- Totalmente compatible y aditiva con usuarios preexistentes.
-- ============================================================

ALTER TABLE auth.users
    ADD COLUMN profile_image_key VARCHAR(255);

ALTER TABLE auth.users
    ADD CONSTRAINT ck_users_profile_image_key
        CHECK (profile_image_key IS NULL OR profile_image_key ~ '^[a-f0-9-]{36}\.(jpg|jpeg|png)$');

CREATE INDEX ix_users_profile_image ON auth.users(profile_image_key)
    WHERE profile_image_key IS NOT NULL;
