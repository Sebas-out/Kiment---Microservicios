# ============================================================
# Run-Dev.ps1 — Arranque local seguro de kiments-auth-service
# ============================================================
# Uso interactivo:
#   .\scripts\Run-Dev.ps1
#
# Uso con parámetros personalizados:
#   .\scripts\Run-Dev.ps1 -DbUrl "jdbc:postgresql://localhost:5432/kiments_db" -DbUser "kiments_auth" -KeyDirectory "C:\ruta\segura\keys"
#
# Seguridad:
#   - Solicita contraseña oculta mediante Read-Host -MaskInput si no está definida en la sesión.
#   - Nunca imprime contraseñas, secretos, tokens ni contenido de claves privadas.
#   - No guarda secretos en archivos ni en el repositorio.
#   - No regenera claves RSA existentes.
#   - Mantiene estrictamente desactivado el bootstrap del primer ADMIN.
#   - Admite detención limpia con Ctrl + C.
# ============================================================

[CmdletBinding()]
param(
    [string]$DbUrl = $env:DB_URL,
    [string]$DbUser = $env:DB_USERNAME,
    [string]$KeyDirectory = (Join-Path $env:LOCALAPPDATA 'Kiments\auth-keys'),
    [string]$Profile = 'dev'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# ── 1. Detectar Java 21 ───────────────────────────────────────────────────────
if (-not $env:JAVA_HOME) {
    if (Test-Path 'C:\Program Files\Java\jdk-21\bin\java.exe') {
        $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
    } else {
        throw 'JAVA_HOME no está definido y no se encontró JDK 21 en la ruta predeterminada. Configure $env:JAVA_HOME antes de continuar.'
    }
}

$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) {
    throw "No se encontró java.exe en: $java"
}

# ── 2. Detectar Maven Wrapper y JAR ───────────────────────────────────────────
$repoRoot = Split-Path -Parent $PSScriptRoot
$mvnw = Join-Path $repoRoot 'mvnw.cmd'
if (-not (Test-Path -LiteralPath $mvnw)) {
    Write-Warning "No se encontró el wrapper de Maven en: $mvnw"
}

$jar = Join-Path $repoRoot 'target\kiments-auth-service-0.1.0.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    Write-Host '[INFO] El artefacto JAR no existe. Compilando con Maven Wrapper...' -ForegroundColor Cyan
    Push-Location $repoRoot
    try {
        & $mvnw package -DskipTests --no-transfer-progress
        if ($LASTEXITCODE -ne 0) {
            throw 'Falló la compilación del paquete JAR. Revise los errores antes de continuar.'
        }
    } finally {
        Pop-Location
    }
}

# ── 3. Configuración de Base de Datos y Credenciales Seguras ──────────────────
if (-not $DbUrl) {
    $DbUrl = 'jdbc:postgresql://localhost:5432/kiments_db'
}
$env:DB_URL = $DbUrl

if (-not $DbUser) {
    $DbUser = 'kiments_auth'
}
$env:DB_USERNAME = $DbUser

if (-not $env:DB_PASSWORD) {
    $maskedPass = Read-Host "Ingrese la contraseña para PostgreSQL ($DbUser)" -MaskInput
    if ([string]::IsNullOrWhiteSpace($maskedPass)) {
        throw 'La contraseña de la base de datos no puede estar vacía.'
    }
    $env:DB_PASSWORD = $maskedPass
}

# ── 4. Validación de Claves RSA Externas ───────────────────────────────────────
if ($env:JWT_PRIVATE_KEY -and $env:JWT_PUBLIC_KEY) {
    $privateUri = $env:JWT_PRIVATE_KEY
    $publicUri  = $env:JWT_PUBLIC_KEY
} else {
    $privatePath = Join-Path $KeyDirectory 'private.pem'
    $publicPath  = Join-Path $KeyDirectory 'public.pem'

    if (-not (Test-Path -LiteralPath $privatePath) -or -not (Test-Path -LiteralPath $publicPath)) {
        throw "No se encontraron las claves RSA en: $KeyDirectory`nSi es una instalación nueva de desarrollo, ejecute primero: .\scripts\New-DevKeys.ps1"
    }

    $privateUri = ([Uri]::new($privatePath)).AbsoluteUri
    $publicUri  = ([Uri]::new($publicPath)).AbsoluteUri
    $env:JWT_PRIVATE_KEY = $privateUri
    $env:JWT_PUBLIC_KEY  = $publicUri
}

# ── 5. Configuración de Seguridad y Bootstrap ─────────────────────────────────
# Mantener estrictamente desactivado el bootstrap del administrador
$env:APP_BOOTSTRAP_ADMIN_ENABLED = 'false'
$env:APP_BOOTSTRAP_ADMIN_MANUAL_ENABLED = 'false'

# Perfiles y configuración JWT
$env:SPRING_PROFILES_ACTIVE = $Profile
if (-not $env:JWT_ISSUER)   { $env:JWT_ISSUER = 'http://localhost:8080' }
if (-not $env:JWT_AUDIENCE) { $env:JWT_AUDIENCE = 'kiments-api' }
if (-not $env:JWT_KEY_ID)   { $env:JWT_KEY_ID = 'kiments-rsa-1' }

# ── 6. Resumen de Ejecución Seguro ───────────────────────────────────────────
Write-Host ''
Write-Host '===================================================' -ForegroundColor Green
Write-Host '   KIMENTS POS — SVC-09 (kiments-auth-service)    ' -ForegroundColor Green
Write-Host '===================================================' -ForegroundColor Green
Write-Host "  Java Home     : $env:JAVA_HOME"
Write-Host "  Perfil Spring : $env:SPRING_PROFILES_ACTIVE"
Write-Host "  Base de datos : $env:DB_URL"
Write-Host "  Usuario BD    : $env:DB_USERNAME"
Write-Host "  Contraseña BD : [OCULTA / CONFIGURADA]"
Write-Host "  JWT Issuer    : $env:JWT_ISSUER"
Write-Host "  Audience      : $env:JWT_AUDIENCE"
Write-Host "  Clave RSA     : [ARCHIVOS EXTERNOS VERIFICADOS]"
Write-Host "  Bootstrap     : DESACTIVADO (Seguro)"
Write-Host '===================================================' -ForegroundColor Green
Write-Host 'Iniciando servicio... Presione Ctrl + C para detener.' -ForegroundColor Yellow
Write-Host ''

# ── 7. Ejecutar Spring Boot ───────────────────────────────────────────────────
Push-Location $repoRoot
try {
    & $java -jar $jar
} finally {
    Pop-Location
}
