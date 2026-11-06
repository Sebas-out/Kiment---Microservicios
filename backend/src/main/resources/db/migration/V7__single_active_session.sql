-- ============================================================
-- V7: Sesión única activa por usuario
-- ============================================================
-- Agrega active_family_id para vincular la sesión activa
-- del usuario y permitir la revocación inmediata de access tokens
-- de sesiones anteriores.
-- ============================================================

ALTER TABLE auth.users
    ADD COLUMN active_family_id UUID;

CREATE INDEX ix_users_active_family ON auth.users(active_family_id)
    WHERE active_family_id IS NOT NULL;
