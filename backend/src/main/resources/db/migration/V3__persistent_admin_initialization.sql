CREATE TABLE auth.admin_initialization (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','INITIALIZED','BLOCKED')),
    recorded_at TIMESTAMPTZ,
    initial_user_id UUID,
    source VARCHAR(40),
    CHECK ((status = 'PENDING' AND recorded_at IS NULL AND initial_user_id IS NULL AND source IS NULL)
        OR (status = 'INITIALIZED' AND recorded_at IS NOT NULL AND initial_user_id IS NOT NULL AND source IS NOT NULL)
        OR (status = 'BLOCKED' AND recorded_at IS NOT NULL AND source IS NOT NULL))
);
-- Sin FK hacia users: eliminar/desactivar usuarios nunca borra la evidencia de inicialización.
-- Una importación con usuarios o sesiones existentes queda cerrada al bootstrap.
INSERT INTO auth.admin_initialization(id,status,recorded_at,source)
SELECT 1,
    CASE WHEN EXISTS(SELECT 1 FROM auth.users) OR EXISTS(SELECT 1 FROM auth.refresh_sessions)
        THEN 'BLOCKED' ELSE 'PENDING' END,
    CASE WHEN EXISTS(SELECT 1 FROM auth.users) OR EXISTS(SELECT 1 FROM auth.refresh_sessions)
        THEN now() ELSE NULL END,
    CASE WHEN EXISTS(SELECT 1 FROM auth.users) OR EXISTS(SELECT 1 FROM auth.refresh_sessions)
        THEN 'PREEXISTING_DATA' ELSE NULL END;
