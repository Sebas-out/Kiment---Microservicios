# Kiments Catalog & Inventory Service (MS-02)

Servicio Backend de **Catálogo e Inventario** para el sistema **KIMENTS POS**.
Implementado con **Java 21**, **Spring Boot 3.5.16**, **Spring Security OAuth2 Resource Server**, **PostgreSQL** (esquema `catalog_inventory`), **Flyway**, **Spring Data JPA**, **Apache POI** y **SpringDoc OpenAPI**.

---

## 1. Ubicación y Estructura del Proyecto

* **Ruta del Proyecto:** `C:\Users\Bryan\Downloads\kiments-catalog-inventory-service`
* **ArtifactId:** `kiments-catalog-inventory-service`
* **GroupId:** `com.kiments`
* **Paquete Base:** `com.kiments.cataloginventory`
* **Puerto Predeterminado:** `8082`

```
kiments-catalog-inventory-service/
├── .mvn/wrapper/                  # Maven Wrapper 3.9.11
├── docs/                          # Documentación arquitectónica y de integración
│   ├── ARQUITECTURA-Y-MODELO.md
│   ├── INTEGRACION-BFF-NEXTJS.md
│   ├── DEPENDENCIAS-AUTH-Y-PERMISOS.md
│   └── CONTRATO-FUTURA-INTEGRACION-VENTAS-MS03.md
├── src/
│   ├── main/
│   │   ├── java/com/kiments/cataloginventory/
│   │   │   ├── catalog/           # Módulo de Categorías, Colores, Tallas, Productos, Variantes e Imágenes
│   │   │   ├── inventory/         # Módulo de Existencias, Movimientos, Traspasos y Reportes
│   │   │   ├── security/          # OAuth2 Converter, CurrentUser, Seguridad por Sucursal
│   │   │   ├── ports/             # Adaptadores de Storage y Auth
│   │   │   ├── shared/            # Generadores SKU/Barcode, Idempotencia, Exportación Excel
│   │   │   └── config/            # Seguridad, OpenAPI, CORS, Filtros MDC
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/      # Migraciones Flyway V1 y V2
│   └── test/java/com/kiments/cataloginventory/ # Suite completa de pruebas unitarias y de integración
├── .env.example
├── Dockerfile
├── compose.yml
├── pom.xml
├── mvnw
└── mvnw.cmd
```

---

## 2. Requisitos Previos

1. **Java Development Kit (JDK):** Versión 21 (LTS).
2. **PostgreSQL:** Versión 15, 16 o 17 con base de datos `kiments_db`.
3. **Servicio de Autenticación (MS-01):** Ejecutándose en `http://localhost:8080` (opcional para pruebas unitarias).

---

## 3. Comandos de Compilación, Pruebas y Ejecución

### 3.1. Compilar el Proyecto
Desde la raíz del proyecto (`C:\Users\Bryan\Downloads\kiments-catalog-inventory-service`):

```powershell
.\mvnw.cmd compile
```

### 3.2. Ejecutar la Suite de Pruebas
Ejecuta las pruebas unitarias y de integración (seguridad, tokens, aislamiento de sucursales, concurrencia, idempotencia, reglas de traspaso, subida y sanitización de imágenes, y exportación Excel):

```powershell
.\mvnw.cmd test
```

### 3.3. Empaquetar el Artefacto JAR
Genera el archivo ejecutable optimizado en `target/kiments-catalog-inventory-service-0.1.0.jar`:

```powershell
.\mvnw.cmd package -DskipTests
```

### 3.4. Arrancar el Servicio Localmente
Para iniciar el servicio en el puerto `8082`:

```powershell
.\mvnw.cmd spring-boot:run
```

O ejecutando directamente el archivo JAR empaquetado:

```powershell
java -jar target/kiments-catalog-inventory-service-0.1.0.jar
```

---

## 4. Endpoints de Documentación y Monitoreo

Una vez iniciado el servicio:

* **Documentación Interactiva Swagger / OpenAPI:**  
  [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)
* **Especificación OpenAPI (JSON):**  
  [http://localhost:8082/v3/api-docs](http://localhost:8082/v3/api-docs)
* **Health Check (Actuator):**  
  [http://localhost:8082/actuator/health](http://localhost:8082/actuator/health)

---

## 5. Configuración de Variables de Entorno

Copie `.env.example` o configure las siguientes variables según su entorno:

| Variable | Valor Predeterminado | Descripción |
|---|---|---|
| `PORT` | `8082` | Puerto HTTP del servicio |
| `DB_HOST` | `localhost` | Host de PostgreSQL |
| `DB_PORT` | `5432` | Puerto de PostgreSQL |
| `DB_NAME` | `kiments_db` | Base de datos |
| `DB_USER` | `kiments_catalog` | Usuario con permisos sobre esquema `catalog_inventory` |
| `DB_PASSWORD` | `*` | Contraseña de base de datos |
| `AUTH_JWKS_URI` | `http://localhost:8080/api/v1/auth/jwks` | Endpoint de claves públicas de Auth |
| `AUTH_ISSUER` | `https://kiments-auth-service` | Issuer esperado en los tokens JWT |
| `AUTH_AUDIENCE` | `kiments-auth` | Audiencia esperada en los tokens JWT |
| `STORAGE_LOCAL_DIR` | `./storage/catalog-images` | Directorio para imágenes físicas de productos |
| `CORS_ORIGINS` | `http://localhost:3000` | Orígenes web admitidos (BFF Next.js) |
