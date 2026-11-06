# KIMENTS POS — Guía de Despliegue de Producción en Azure VM
**Microservicio:** `kiments-auth-service` (SVC-09)  
**Sistema Operativo:** Ubuntu Server 24.04 LTS (x64)  
**Infraestructura:** Azure VM `Standard_B2as_v2` (2 vCPU, 8 GiB RAM, ~64 GiB SSD)  

---

## 1. Requisitos Previos en Ubuntu Server 24.04

Actualizar paquetes del sistema e instalar Docker Engine y Docker Compose:

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl ca-certificates gnupg lsb-release

# Agregar repositorio oficial de Docker
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Permitir al usuario actual ejecutar Docker sin sudo
sudo usermod -aG docker $USER
newgrp docker
```

---

## 2. Estructura de Directorios en la Máquina Virtual

En el directorio de inicio del servidor (`/home/azureuser` o `/opt/kiments`):

```text
/opt/kiments/
├── kiments-auth-service/       # Código fuente del backend
│   ├── deploy/
│   │   ├── Dockerfile
│   │   ├── docker-compose.prod.yml
│   │   ├── .env                # Creado a partir de .env.prod.example (chmod 600)
│   │   ├── secrets/            # Claves RSA (chmod 700)
│   │   │   ├── private.pem     # Clave privada (chmod 600)
│   │   │   └── public.pem      # Clave pública (chmod 644)
│   │   └── scripts/
│   │       ├── generate-keys.sh
│   │       ├── init-admin.sh
│   │       ├── backup-db.sh
│   │       └── restore-db.sh
│   └── src/
└── kiments-frontend/           # Repositorio Next.js (gestionado por Codex)
```

---

## 3. Procedimiento de Despliegue Inicial Paso a Paso

### Paso 1: Configurar Variables de Entorno
```bash
cd /opt/kiments/kiments-auth-service/deploy
cp .env.prod.example .env
chmod 600 .env
nano .env
```
Configurar:
- `POSTGRES_DB=kiments_db`
- `POSTGRES_USER=kiments_auth`
- `POSTGRES_PASSWORD=<generar contraseña segura con openssl rand -base64 24>`
- `JWT_ISSUER=https://pos.kiments.com`
- `JWT_AUDIENCE=kiments-api`
- `CORS_ORIGINS=https://pos.kiments.com`

### Paso 2: Generar Claves RSA para JWT RS256
```bash
chmod +x scripts/*.sh
./scripts/generate-keys.sh
```
Esto creará `secrets/private.pem` (permisos 600) y `secrets/public.pem` (permisos 644) de 3072 bits.

### Paso 3: Iniciar PostgreSQL y Validar Salud
```bash
docker compose -f docker-compose.prod.yml up -d postgres
docker compose -f docker-compose.prod.yml ps postgres
```
Esperar a que el estado sea `healthy`:
```bash
docker compose -f docker-compose.prod.yml logs -f postgres
```

### Paso 4: Construir y Arrancar SVC-09
```bash
docker compose -f docker-compose.prod.yml build auth-service
docker compose -f docker-compose.prod.yml up -d auth-service
```

### Paso 5: Verificar Migraciones Flyway y Salud del Microservicio
```bash
# Revisar logs de arranque y migraciones V1 a V7
docker compose -f docker-compose.prod.yml logs -f auth-service

# Probar Actuator internamente
docker compose -f docker-compose.prod.yml exec auth-service curl -s http://127.0.0.1:8080/actuator/health
# Salida esperada: {"status":"UP"}
```

### Paso 6: Aprovisionar Primer Administrador (Base Nueva Vacía)
Dado que la base de datos de producción es completamente nueva y el bootstrap automático está desactivado por seguridad, ejecutar el aprovisionamiento manual sin servidor HTTP:
```bash
./scripts/init-admin.sh
```
El script solicitará interactivamente el nombre, DNI, correo y contraseña del Administrador y registrará la cuenta mediante Argon2id.

---

## 4. Procedimiento de Actualización y Rollback

### Actualización de SVC-09 (Zero Downtime / Rollout Controlado)
```bash
cd /opt/kiments/kiments-auth-service

# 1. Obtener última versión del código
git pull origin main

# 2. Respaldo preventivo antes de actualizar
./deploy/scripts/backup-db.sh
./deploy/scripts/backup-profiles.sh

# 3. Reconstruir imagen
docker compose -f deploy/docker-compose.prod.yml build auth-service

# 4. Desplegar nueva versión
docker compose -f deploy/docker-compose.prod.yml up -d --no-deps auth-service

# 5. Comprobar salud
docker compose -f deploy/docker-compose.prod.yml ps
docker compose -f deploy/docker-compose.prod.yml exec auth-service curl -s http://127.0.0.1:8080/actuator/health
```

### Rollback en Caso de Incidencia
Si la nueva versión presenta fallos críticos:
```bash
# 1. Volver al commit anterior
git checkout <commit_anterior_estable>

# 2. Reconstruir y reiniciar contenedor
docker compose -f deploy/docker-compose.prod.yml build auth-service
docker compose -f deploy/docker-compose.prod.yml up -d --no-deps auth-service

# 3. Si hubo cambios destructivos en base de datos, restaurar el respaldo:
./deploy/scripts/restore-db.sh ~/kiments-backups/postgres/kiments_db_YYYYMMDD_HHMMSS.sql.gz
```

---

## 5. Integración con Frontend Next.js (Guía para Codex)

El frontend Next.js se ejecutará como contenedor en la misma máquina virtual o en la misma red Docker `kiments-internal`:

| Parámetro | Valor para Next.js | Propósito |
| :--- | :--- | :--- |
| **`AUTH_API_URL`** | `http://auth-service:8080` | URL interna en red Docker para llamadas servidor a servidor (BFF). |
| **`APP_ORIGIN`** | `https://pos.kiments.com` | Origen público del navegador con HTTPS. |
| **DNS Interno** | `auth-service` | Nombre del servicio en Compose. |
| **Puerto Interno** | `8080` | Puerto HTTP interno de Spring Boot. |
| **Healthcheck** | `http://auth-service:8080/actuator/health` | Verificación de disponibilidad del microservicio. |
| **JWKS Endpoint** | `http://auth-service:8080/api/v1/auth/jwks` | Claves públicas para validación de firma JWT si se requiere en el servidor. |

**Importante:** El navegador de los clientes **NUNCA** se conecta a PostgreSQL (`5432`) ni a Spring Boot (`8080`) directamente. Todas las peticiones van por HTTPS (`443`) hacia el Reverse Proxy, que enruta al Frontend Next.js y/o a los endpoints del API.
