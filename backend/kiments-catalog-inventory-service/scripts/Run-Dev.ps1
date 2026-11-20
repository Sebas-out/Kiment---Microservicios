# ============================================================
# Run-Dev.ps1 — Arranque local de kiments-catalog-inventory-service
# ============================================================
# Uso interactivo:
#   .\scripts\Run-Dev.ps1
#
# Uso con parámetros personalizados:
#   .\scripts\Run-Dev.ps1 -DbUrl "jdbc:postgresql://localhost:5432/kiments_db" -DbUser "kiments_catalog" -Port 8082
# ============================================================

[CmdletBinding()]
param(
    [string]$DbUrl = $env:DB_URL,
    [string]$DbUser = $env:DB_USER,
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$Port = '8082',
    [string]$AuthUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# ── 1. Detectar Java 21 ───────────────────────────────────────────────────────
if (-not $env:JAVA_HOME) {
    if (Test-Path 'C:\Program Files\Java\jdk-21\bin\java.exe') {
        $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
    } else {
        throw 'JAVA_HOME no está definido y no se encontró JDK 21. Configure $env:JAVA_HOME antes de continuar.'
    }
}

$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) {
    throw "No se encontró java.exe en: $java"
}

# ── 2. Localizar artefacto JAR ────────────────────────────────────────────────
$repoRoot = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $repoRoot 'target\kiments-catalog-inventory-service-0.1.0.jar'
$mvnw = Join-Path $repoRoot 'mvnw.cmd'

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

# ── 3. Base de Datos ──────────────────────────────────────────────────────────
if (-not $DbUrl) {
    $DbUrl = 'jdbc:postgresql://localhost:5432/kiments_db?currentSchema=catalog_inventory'
}
if (-not $DbUser) {
    $DbUser = 'kiments_catalog'
}
if (-not $DbPassword) {
    if ($env:DB_PASSWORD) {
        $DbPassword = $env:DB_PASSWORD
    } else {
        $DbPassword = 'cordex2026'
    }
}

$env:PORT = $Port
$env:DB_HOST = 'localhost'
$env:DB_PORT = '5432'
$env:DB_NAME = 'kiments_db'
$env:DB_USER = $DbUser
$env:DB_PASSWORD = $DbPassword
$env:AUTH_JWKS_URI = "$AuthUrl/api/v1/auth/jwks"
$env:AUTH_ME_URI = "$AuthUrl/api/v1/auth/me"

Write-Host ''
Write-Host '===================================================' -ForegroundColor Green
Write-Host '   KIMENTS POS — MS-02 (Catalog & Inventory)       ' -ForegroundColor Green
Write-Host '===================================================' -ForegroundColor Green
Write-Host "  Java Home     : $env:JAVA_HOME"
Write-Host "  Puerto        : $Port"
Write-Host "  Base de datos : $DbUrl"
Write-Host "  Usuario BD    : $DbUser"
Write-Host "  Auth Service  : $AuthUrl"
Write-Host "  Swagger UI    : http://localhost:$Port/swagger-ui.html"
Write-Host '===================================================' -ForegroundColor Green
Write-Host 'Iniciando servicio... Presione Ctrl + C para detener.' -ForegroundColor Yellow
Write-Host ''

Push-Location $repoRoot
try {
    & $java -jar $jar
} finally {
    Pop-Location
}
