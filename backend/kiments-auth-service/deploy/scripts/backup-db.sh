#!/usr/bin/env bash
# ==============================================================================
# KIMENTS POS — Respaldo Logico de PostgreSQL en Azure VM
# ==============================================================================
set -euo pipefail

BACKUP_DIR="${HOME}/kiments-backups/postgres"
mkdir -p "${BACKUP_DIR}"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/kiments_db_${TIMESTAMP}.sql"

CONTAINER="kiments-postgres"
DB_NAME="${POSTGRES_DB:-kiments_db}"
DB_USER="${POSTGRES_USER:-kiments_auth}"

echo "[INFO] Iniciando respaldo de ${DB_NAME} desde contenedor ${CONTAINER}..."
docker exec -t "${CONTAINER}" pg_dump -U "${DB_USER}" -F p -b "${DB_NAME}" > "${BACKUP_FILE}"

if [ -s "${BACKUP_FILE}" ]; then
    gzip -f "${BACKUP_FILE}"
    chmod 600 "${BACKUP_FILE}.gz"
    echo "[OK] Respaldo generado con éxito: ${BACKUP_FILE}.gz ($(stat -c%s "${BACKUP_FILE}.gz") bytes)"
else
    echo "[ERROR] El archivo de respaldo está vacío."
    rm -f "${BACKUP_FILE}"
    exit 1
fi
