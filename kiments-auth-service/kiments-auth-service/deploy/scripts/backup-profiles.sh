#!/usr/bin/env bash
# ==============================================================================
# KIMENTS POS — Respaldo del Volumen de Fotografias de Perfil
# ==============================================================================
set -euo pipefail

BACKUP_DIR="${HOME}/kiments-backups/profiles"
mkdir -p "${BACKUP_DIR}"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="profiles_${TIMESTAMP}.tar.gz"

echo "[INFO] Generando respaldo de volumen kiments_profiles_data..."
docker run --rm \
    -v kiments_profiles_data:/data:ro \
    -v "${BACKUP_DIR}":/backup \
    alpine tar czf "/backup/${BACKUP_FILE}" -C /data .

chmod 600 "${BACKUP_DIR}/${BACKUP_FILE}"
echo "[OK] Respaldo de fotografias generado: ${BACKUP_DIR}/${BACKUP_FILE} ($(stat -c%s "${BACKUP_DIR}/${BACKUP_FILE}") bytes)"
