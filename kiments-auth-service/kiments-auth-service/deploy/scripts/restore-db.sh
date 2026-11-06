#!/usr/bin/env bash
# ==============================================================================
# KIMENTS POS — Restauracion Controlada de PostgreSQL en Azure VM
# ==============================================================================
set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo "Uso: $0 <archivo_respaldo.sql.gz | archivo_respaldo.sql>"
    exit 1
fi

BACKUP_INPUT="$1"
if [ ! -f "${BACKUP_INPUT}" ]; then
    echo "[ERROR] No se encuentra el archivo: ${BACKUP_INPUT}"
    exit 1
fi

CONTAINER="kiments-postgres"
DB_NAME="${POSTGRES_DB:-kiments_db}"
DB_USER="${POSTGRES_USER:-kiments_auth}"

echo "==================================================================="
echo "ADVERTENCIA: Esta operacion restaurara la base de datos ${DB_NAME}."
echo "==================================================================="
read -p "¿Desea continuar con la restauracion? (escriba 'si' para confirmar): " CONFIRM
if [ "${CONFIRM}" != "si" ]; then
    echo "[CANCELADO] Operacion abortada por el usuario."
    exit 0
fi

echo "[INFO] Restaurando datos en ${CONTAINER}..."
if [[ "${BACKUP_INPUT}" == *.gz ]]; then
    gunzip -c "${BACKUP_INPUT}" | docker exec -i "${CONTAINER}" psql -U "${DB_USER}" -d "${DB_NAME}"
else
    cat "${BACKUP_INPUT}" | docker exec -i "${CONTAINER}" psql -U "${DB_USER}" -d "${DB_NAME}"
fi

echo "[OK] Restauracion completada exitosamente."
