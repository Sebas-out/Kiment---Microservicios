# KIMENTS Auth Service — SVC-09

Servicio independiente de autenticación y usuarios. Java 21, Spring Boot 3.5.16, Maven Wrapper 3.9.11, PostgreSQL, Flyway, Spring Security y springdoc 2.10.0. No modifica ni necesita el frontend.

## Requisitos e instalación

Java **JDK 21**, PowerShell 7 y PostgreSQL 17 (o Docker para la base de desarrollo). Maven no necesita instalación. Se necesita internet en la primera compilación.

```powershell
Set-Location 'C:\Users\Bryan\Downloads\kiments-auth-service'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
```

La ruta de Java anterior fue comprobada en este equipo. En otros equipos, ajustar `JAVA_HOME`.

## Base de datos y variables

`.env.example` enumera las variables. **Spring no carga `.env` automáticamente**: exportarlas en la misma terminal que ejecuta Java. Compose sí puede leer `.env`.

Con PostgreSQL instalado, un administrador debe crear una base nueva y su propietario; no ejecutar si ya existen:

```sql
CREATE ROLE kiments_auth LOGIN;
-- En psql: \password kiments_auth   (solicita una contraseña sin guardarla en SQL)
CREATE DATABASE kiments_db OWNER kiments_auth;
```

Flyway crea el schema `auth` y aplica V1/V2; Hibernate solo valida. Producción debe usar un rol dedicado sin privilegios de superusuario y conexión TLS según la infraestructura.

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/kiments_db'
$env:DB_USERNAME = 'kiments_auth'
$env:DB_PASSWORD = Read-Host 'Contraseña PostgreSQL' -MaskInput
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:JWT_ISSUER = 'http://localhost:8080'
$env:JWT_AUDIENCE = 'kiments-api'
$env:JWT_KEY_ID = 'kiments-rsa-1'
```

Alternativa de desarrollo **con Docker instalado**, después de definir `DB_USERNAME` y `DB_PASSWORD`: `docker compose up -d postgres`. No se ejecutó Docker en el equipo de implementación. No usar `down -v` sobre datos que se deban conservar.

## Claves RSA externas

```powershell
.\scripts\New-DevKeys.ps1
$keyDir = Join-Path $env:LOCALAPPDATA 'Kiments/auth-keys'
$env:JWT_PRIVATE_KEY = ([Uri]::new((Join-Path $keyDir 'private.pem'))).AbsoluteUri
$env:JWT_PUBLIC_KEY = ([Uri]::new((Join-Path $keyDir 'public.pem'))).AbsoluteUri
```

El script genera RSA 3072 con APIs estándar de .NET, restringe permisos en Windows y rechaza sobrescrituras o rutas dentro del proyecto. Si ya se generaron las claves, ejecutar solo las asignaciones de variables. En producción, suministrar claves mediante archivos secretos con permisos mínimos. Nunca compartir la privada.

## Compilar e inicializar el primer administrador

`mvnw.cmd verify` compila y prueba el servicio. Las pruebas de integración deben usar bases aisladas, nunca `kiments_db`.

La migración V3 añade `auth.admin_initialization`. Un administrador creado por este procedimiento deja un marcador persistente que no tiene FK hacia users: borrar o desactivar usuarios no vuelve a habilitar la inicialización. Las bases que ya contienen usuarios o sesiones quedan bloqueadas al aplicar V3. Los respaldos/importaciones deben conservar también esta tabla; una base legacy vacía sin evidencia histórica no permite reconstruir su historia de inicialización.

Para una primera instalación **dev**, exportar temporalmente los datos obligatorios. No guardar contraseñas en `.env`, SQL, README ni historial:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:APP_BOOTSTRAP_ADMIN_ENABLED = 'true'
$env:APP_BOOTSTRAP_ADMIN_EMAIL = Read-Host 'Correo del primer administrador'
$env:APP_BOOTSTRAP_ADMIN_FIRST_NAME = Read-Host 'Nombre'
$env:APP_BOOTSTRAP_ADMIN_LAST_NAME = Read-Host 'Apellido'
$env:APP_BOOTSTRAP_ADMIN_DNI = Read-Host 'DNI de 8 dígitos'
$env:APP_BOOTSTRAP_ADMIN_PASSWORD = Read-Host 'Contraseña nueva (12 a 128 caracteres)' -MaskInput
try {
    & "$env:JAVA_HOME/bin/java.exe" -jar target/kiments-auth-service-0.1.0.jar --spring.main.web-application-type=none
    if ($LASTEXITCODE -ne 0) { throw 'Inicialización no completada; revisar el error.' }
} finally {
    $env:APP_BOOTSTRAP_ADMIN_ENABLED = 'false'
    Remove-Item Env:APP_BOOTSTRAP_ADMIN_EMAIL,Env:APP_BOOTSTRAP_ADMIN_FIRST_NAME,Env:APP_BOOTSTRAP_ADMIN_LAST_NAME,Env:APP_BOOTSTRAP_ADMIN_DNI,Env:APP_BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
}
```

Requiere además las variables de conexión y las claves externas descritas antes. No hay correo ni contraseña predeterminados. Flyway termina antes del runner. La creación y el registro del marcador se confirman en una única transacción protegida por el bloqueo PostgreSQL existente.

La misma inicialización automática admite arranque web en dev; el comando anterior permite ejecutar migraciones e inicialización y terminar sin abrir HTTP. Sin perfil dev, con prod activo, sin variable explícita `APP_BOOTSTRAP_ADMIN_ENABLED=true`, con datos inválidos, con usuarios/sesiones previos o con marcador cerrado, no crea ni modifica usuarios. Repetir la inicialización ya registrada es una operación sin cambios y no necesita volver a proporcionar los datos personales. Nunca reactiva cuentas ni cambia contraseñas.

**Producción:** el modo automático no se permite, aunque se fuerce su flag. El operador puede usar el mismo inicializador en modo administrativo explícito sin servidor HTTP: configurar los datos propios de esa instalación (sin credenciales predeterminadas), `APP_BOOTSTRAP_ADMIN_ENABLED=false`, `APP_BOOTSTRAP_ADMIN_MANUAL_ENABLED=true` y ejecutar el JAR con `--spring.main.web-application-type=none`. El marcador, la exclusión concurrente y la exigencia de base inicial vacía se aplican igual. Limpiar después datos/contraseña y desactivar ambos flags. No hay endpoint público de bootstrap ni procedimiento automático de recuperación. La antigua opción `app.bootstrap.enabled=true` se rechaza para evitar omitir los controles nuevos.

Arranque normal: ambos flags en false; omitir todas las variables personales y la contraseña bootstrap. La contraseña de conexión PostgreSQL es independiente de la contraseña del usuario POS.

## Ejecutar

En la misma terminal con las variables anteriores:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -jar target/kiments-auth-service-0.1.0.jar
```

También: `.\mvnw.cmd spring-boot:run`. Health: `http://localhost:8080/actuator/health`. Swagger en perfil dev: `http://localhost:8080/swagger-ui/index.html`; contrato: `/v3/api-docs`. En prod están desactivados por defecto. Para producción definir explícitamente `SPRING_PROFILES_ACTIVE=prod`, issuer HTTPS, audience, secretos y configuración operativa. No se declara preparado para producción sin esa verificación.

## Contratos REST

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/v1/auth/login` | Correo y contraseña; público con limitación |
| GET | `/api/v1/auth/me` | Bearer; perfil y permisos actuales |
| POST | `/api/v1/auth/change-password` | Bearer; `currentPassword`, `newPassword` |
| POST | `/api/v1/auth/refresh` | `refreshToken` opaco |
| POST | `/api/v1/auth/logout` | `refreshToken`; revoca su familia, respuesta 204 |
| GET | `/api/v1/auth/jwks` | Clave pública RSA para otros servicios |
| GET, POST | `/api/v1/users` | ADMIN con permiso específico |
| GET, PATCH | `/api/v1/users/{id}` | ADMIN con permiso específico |
| PATCH | `/api/v1/users/{id}/role` | ADMIN; cuerpo `{"role":"VENTAS"}` |
| PATCH | `/api/v1/users/{id}/status` | ADMIN; cuerpo `{"enabled":false}` |
| GET | `/api/v1/roles` | ADMIN y USER_READ |
| GET | `/api/v1/roles/{id}/permissions` | ADMIN y USER_READ |

Creación de usuario: `firstName`, `lastName`, `dni`, `email`, `password`, `role`, `enabled` y `phone` opcional. PATCH personal admite exclusivamente nombres, DNI, teléfono y correo; teléfono vacío lo borra. Rol, estado y contraseña usan operaciones separadas. `branchId` se reserva para una integración futura y no es editable por asignación masiva.

Filtros de listado: `name` (nombre y apellido, parcial, sin distinguir mayúsculas), `email` y `dni` exactos, `role`, `enabled`, `page` desde 0 y `size` entre 1 y 100. Respuesta: `content`, `page`, `size`, `totalElements`, `totalPages`. Fechas UTC ISO-8601. Errores `application/problem+json` sin valores sensibles ni detalles SQL.

Login recibe `{"email":"correo real","password":"contraseña real"}` y responde `accessToken`, `tokenType`, `expiresIn` (900 segundos), `refreshToken`. No registrar esos cuerpos. No hay registro público ni eliminación física de usuarios.

## Seguridad y decisiones de alcance

- Argon2id: 19 MiB, 2 iteraciones, paralelismo 1, sal de 16 bytes y hash de 32 bytes; Bouncy Castle 1.84. Credenciales incorrectas, inexistentes e inactivas comparten respuesta.
- JWT RS256 con issuer, audience, exp, nbf, iat, jti y UUID de usuario; sin nombres, DNI ni correo. JWKS publica únicamente la clave pública. Otros servicios deben verificar RS256, issuer, audience y tiempos usando JWKS; no consultar tablas de autenticación. Cambios de claves requieren coordinar la retención de la clave pública anterior; esta versión publica una clave activa.
- Este servicio consulta estado y permisos actuales en cada petición autenticada. Las mutaciones administrativas vuelven a comprobar permisos dentro de su transacción después del bloqueo. No se permite cambiar el propio rol ni desactivar/degradar al último ADMIN activo.
- Un bloqueo de fila común serializa las mutaciones sensibles y el bootstrap entre instancias. Es una elección sencilla para este primer servicio; medir contención antes de escalar el volumen de accesos. Las entidades también tienen versión optimista.
- Refresh de 256 bits, solo SHA-256 en base de datos, familia con vencimiento absoluto de 7 días, rotación de un solo uso y revocación de familia ante reutilización. El cliente debe serializar renovaciones: dos renovaciones simultáneas del mismo token provocan revocación por replay. Logout, cambio de contraseña, desactivación y cambio de rol revocan sesiones renovables según su alcance.
- Revocar refresh o cerrar sesión **no invalida automáticamente un access JWT emitido**. Puede seguir vigente hasta 15 minutos; este servicio además aplica el estado/rol actual. No hay lista global de revocación de access tokens.
- Login limita en PostgreSQL a 10 solicitudes por cuenta y 50 por IP en ventanas de 15 minutos, incluyendo accesos correctos. No confía en cabeceras de IP reenviadas. Detrás de un BFF, la cuota IP puede compartirse: ajustar la estrategia y el proxy de confianza antes de desplegar. El límite de cuenta puede causar bloqueo temporal ante abuso dirigido.
- API sin cookies, con Bearer y CSRF desactivado para este contrato. CORS cerrado por defecto; `CORS_ORIGINS` acepta una lista separada por comas de orígenes explícitos. El futuro BFF debe custodiar los tokens en el servidor y usar cookies HttpOnly, Secure, SameSite y protección CSRF en sus endpoints. No usar localStorage.
- Permisos futuros están preparados como catálogo, sin implementar ventas, inventario ni facturación. No hay integración con el frontend en esta entrega.

## Pruebas

```powershell
.\mvnw.cmd verify
```

Ejecuta pruebas unitarias JUnit/Mockito y, si Docker está disponible, integración con Testcontainers y PostgreSQL. Mockito se carga como agente al iniciar el proceso de pruebas, evitando attach dinámico en Windows. Sin Docker, sus escenarios se omiten explícitamente; un build verde con pruebas omitidas no acredita integración.

Alternativa con una base **exclusiva y desechable de pruebas** cuyo nombre termine en `_test`:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/kiments_auth_test'
$env:TEST_DB_USERNAME = 'usuario de pruebas'
$env:TEST_DB_PASSWORD = Read-Host 'Contraseña de la base de pruebas' -MaskInput
.\mvnw.cmd verify
```

Estos escenarios vacían `auth.users`, sesiones e intentos antes de cada prueba: nunca apuntarlos a una base real. Generan claves efímeras para pruebas; no requieren claves reales. Cubren autenticación, firma y claims JWT, roles, restricciones, CRUD, concurrencia del último ADMIN, permisos actuales, contraseña, rate limiting, renovación, replay, logout, Swagger y health.

## Organización y patrones

```text
src/main/java/com/kiments/auth/
  authentication/   AuthController, AuthenticationService, AuthenticationServiceImpl, DTO
  users/            UserController, UserService, UserServiceImpl, UserRepository, User, DTO, Mapper
  roles/            RoleController, RoleService, RoleServiceImpl, RoleRepository, Role
  permissions/      Permission, PermissionRepository
  sessions/         RefreshSession, RefreshSessionRepository, RefreshTokens
  security/         TokenService, CurrentUser, conversión JWT, rate limiting y errores
  config/           Security, JWT, OpenAPI, bootstrap
  exception/        Problem Details centralizado
  shared/           Normalización y bloqueo de mutaciones
src/main/resources/db/migration/  V1 esquema, V2 catálogo RBAC
src/test/java/com/kiments/auth/   Unitarias y escenarios PostgreSQL
scripts/                         Generación de claves de desarrollo
```

Patrones utilizados: MVC REST, Service Layer con interfaces e implementaciones, Repository, DTO/Mapper, inyección por constructor y cadena de filtros de Spring Security. Los servicios son beans singleton administrados por Spring; no existe un singleton manual de conexiones. El DataSource/pool lo administra Spring Boot.

No incluye documentación académica extensa. Pendientes operativos: HTTPS, secretos reales, copias de seguridad, observabilidad/auditoría de seguridad, política de rotación de claves, mantenimiento de sesiones vencidas y validación del despliegue Docker en el entorno destino.

Versiones contrastadas con las fuentes oficiales: [Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [springdoc v2](https://springdoc.org/v2/).

Pruebas específicas de inicialización: `BootstrapIntegrationTest` requiere `STAGE2_TEST_DB_URL` apuntando exclusivamente a una base aislada llamada `kiments_bootstrap_*_test`. Comprueban dev/producción, flags, datos inválidos, estado persistente, usuarios previos, concurrencia, login y perfil. No ejecutar suites que limpian tablas contra la base real.

## Gestión de usuarios verificada (etapa 3)

ADMIN necesita además el permiso específico USER_READ, USER_CREATE, USER_UPDATE, USER_DISABLE o USER_ROLE_CHANGE. VENTAS, ALMACEN y VENTAS_ALMACEN reciben 403 en los endpoints administrativos. Los roles y sus permisos se consultan; no se crean roles personalizados mediante esta API.

- Crear usuario: POST /api/v1/users, respuesta 201 con Location. Consultar/listar y editar por PATCH: 200.
- PATCH /api/v1/users/{id} solo admite firstName, lastName, dni, phone y email. phone vacío lo borra; campos de rol, estado o contraseña en este cuerpo se rechazan con 400.
- Cambiar rol o estado: PATCH /api/v1/users/{id}/role y /status. Cambiar el propio rol o desactivar el último ADMIN activo devuelve 409.
- Filtros de listado: name parcial (nombre/apellido), email y dni exactos, role y enabled; se pueden combinar. El correo se normaliza sin distinguir mayúsculas.
- page empieza en 0, size entre 1 y 100. Un desplazamiento page*size superior al límite entero de JPA se rechaza con 400. Las páginas fuera del conjunto devuelven content vacío.
- Correo o DNI duplicados devuelven 409 y revierten toda la actualización. Datos inválidos: 400; UUID inexistente: 404; tipo de contenido no admitido: 415; método no implementado: 405 con Allow.
- No hay eliminación física vía API. POST /api/v1/auth/change-password exige currentPassword y newPassword; respuesta 204. Revoca refresh previos; los access JWT ya emitidos conservan su expiración, con comprobación del estado y permisos actuales en este servicio.

StageThreeIntegrationTest reutiliza los escenarios existentes y añade matriz exacta de permisos, todas las rutas por rol, validaciones, restricciones PostgreSQL y concurrencia. Requiere STAGE3_TEST_DB_URL y solo acepta una base de fixtures llamada kiments_users_stage3_*_test en 127.0.0.1:55439, con el rol aislado kiments_test. Nunca acepta kiments_db. Las contraseñas de esta suite se generan en memoria. Las pruebas unitarias adicionales están en UserServiceRulesTest.

Para integrar Next.js, usar los contratos REST y OpenAPI y el patrón BFF descrito antes: tokens en el servidor, sin localStorage. CORS permanece cerrado por defecto; no se modificó el frontend ni se implementaron cookies/BFF en esta etapa.
