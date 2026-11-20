#!/usr/bin/env bash
# ==============================================================================
# KIMENTS POS — Generador de Claves RSA 3072-bit para Produccion en Ubuntu 24.04
# Formato: PKCS#8 (private.pem) y SubjectPublicKeyInfo (public.pem)
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SECRETS_DIR="${SCRIPT_DIR}/../secrets"

mkdir -p "${SECRETS_DIR}"
PRIVATE_KEY="${SECRETS_DIR}/private.pem"
PUBLIC_KEY="${SECRETS_DIR}/public.pem"

if [ -f "${PRIVATE_KEY}" ] || [ -f "${PUBLIC_KEY}" ]; then
    echo "[AVISO] Ya existen claves en ${SECRETS_DIR}. No se sobrescribiran para evitar invalidar tokens."
    exit 0
fi

echo "[INFO] Generando par de claves RSA 3072-bit PKCS#8..."
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "${PRIVATE_KEY}"
openssl rsa -pubout -in "${PRIVATE_KEY}" -out "${PUBLIC_KEY}"

# Permisos seguros: solo el propietario puede leer la clave privada
chmod 600 "${PRIVATE_KEY}"
chmod 644 "${PUBLIC_KEY}"

echo "[OK] Claves generadas exitosamente:"
ls -la "${SECRETS_DIR}"
