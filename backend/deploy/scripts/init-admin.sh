#!/usr/bin/env bash
# ==============================================================================
# KIMENTS POS — Inicializacion Manual Segura del Primer Administrador
# Se ejecuta de forma interactiva una unica vez sobre una base vacia.
# No utiliza servidor HTTP ni almacena contraseñas en archivos.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [ -f "${DEPLOY_DIR}/.env" ]; then
    # Cargar variables de base de datos
    set -a
    source "${DEPLOY_DIR}/.env"
    set +a
fi

echo "==================================================================="
echo "   KIMENTS POS — Aprovisionamiento de Administrador Inicial       "
echo "==================================================================="
echo "Este procedimiento registra al primer administrador de forma segura."
echo ""

read -p "Nombres del Administrador: " ADMIN_FIRST_NAME
read -p "Apellidos del Administrador: " ADMIN_LAST_NAME
read -p "DNI (8 dígitos exactos): " ADMIN_DNI
read -p "Correo electrónico: " ADMIN_EMAIL

read -s -p "Contraseña (mínimo 12 caracteres): " ADMIN_PASS1
echo ""
read -s -p "Confirmar contraseña: " ADMIN_PASS2
echo ""

if [ "${ADMIN_PASS1}" != "${ADMIN_PASS2}" ]; then
    echo "[ERROR] Las contraseñas no coinciden."
    exit 1
fi

if [ ${#ADMIN_PASS1} -lt 12 ]; then
    echo "[ERROR] La contraseña debe tener al menos 12 caracteres."
    exit 1
fi

echo "[INFO] Ejecutando bootstrap seguro en contenedor efimero sin servidor HTTP..."

docker run --rm -i \
    --network kiments-internal \
    -e DB_URL="jdbc:postgresql://postgres:5432/${POSTGRES_DB:-kiments_db}" \
    -e DB_USERNAME="${POSTGRES_USER:-kiments_auth}" \
    -e DB_PASSWORD="${POSTGRES_PASSWORD}" \
    -e SPRING_PROFILES_ACTIVE="prod" \
    -e APP_BOOTSTRAP_ADMIN_ENABLED="false" \
    -e APP_BOOTSTRAP_ADMIN_MANUAL_ENABLED="true" \
    -e APP_BOOTSTRAP_ADMIN_FIRST_NAME="${ADMIN_FIRST_NAME}" \
    -e APP_BOOTSTRAP_ADMIN_LAST_NAME="${ADMIN_LAST_NAME}" \
    -e APP_BOOTSTRAP_ADMIN_DNI="${ADMIN_DNI}" \
    -e APP_BOOTSTRAP_ADMIN_EMAIL="${ADMIN_EMAIL}" \
    -e APP_BOOTSTRAP_ADMIN_PASSWORD="${ADMIN_PASS1}" \
    -v "${DEPLOY_DIR}/secrets/private.pem:/app/keys/private.pem:ro" \
    -v "${DEPLOY_DIR}/secrets/public.pem:/app/keys/public.pem:ro" \
    kiments-auth-service:0.1.0 \
    --spring.main.web-application-type=none

echo "[OK] Administrador inicial creado exitosamente. La cuenta ya puede iniciar sesion."
