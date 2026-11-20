# KIMENTS POS — Dependencias de Autenticación, Seguridad y Matriz de Permisos
## Integración entre MS-01 (Auth) y MS-02 (Catálogo e Inventario)

---

## 1. Integración Criptográfica y Validación de Tokens

### 1.1. Especificación del JWT Emitido por Auth
El servicio `kiments-auth-service` emite tokens firmados con algoritmo **RS256** utilizando una clave RSA de 2048 bits.

MS-02 actúa como un **Spring Security OAuth2 Resource Server**, configurado para validar la firma y los claims del token consumiendo el conjunto de claves públicas del endpoint JWKS:
* **Endpoint de Claves:** `GET /api/v1/auth/jwks`
* **Variable de configuración:** `AUTH_JWKS_URI=http://localhost:8080/api/v1/auth/jwks`
* **Emisor esperado:** `AUTH_ISSUER=https://kiments-auth-service`
* **Audiencia esperada:** `AUTH_AUDIENCE=kiments-auth`

### 1.2. Claims del Token Verificados en MS-02
* `sub`: UUID del usuario. Obligatorio.
* `iss`: Debe coincidir exactamente con el issuer configurado.
* `aud`: Debe contener la audiencia del servicio.
* `exp`, `nbf`, `iat`: Validados por NimbusJwtDecoder con tolerancia de 60 segundos de reloj.
* `role`: Código del rol asignado (`ADMIN`, `VENTAS`, `ALMACEN`, `VENTAS_ALMACEN`).
* `permissions`: Lista de cadenas de permisos RBAC.
* `branch_id`: UUID de la sucursal asignada. Obligatorio para roles operativos (`ALMACEN`, `VENTAS`, `VENTAS_ALMACEN`); debe ser `null` para el rol `ADMIN`.
* `family_id`: Identificador de la familia de sesión activa.

---

## 2. Matriz de Autorización RBAC y Alcance por Sucursal

| Rol | Alcance de Sucursal | Permisos Clave en MS-02 | Operaciones Permitidas |
|---|---|---|---|
| **`ADMIN`** | **Global** (Todas las sucursales) | `PRODUCT_READ`, `PRODUCT_WRITE`, `CATEGORY_WRITE`, `VARIANT_WRITE`, `STOCK_READ`, `STOCK_ADJUST`, `MOVEMENT_READ`, `STOCK_REPORT_READ`, `REPORT_READ` | Control total del catálogo, fotografías, existencias de cualquier sucursal, ajustes manuales y traspasos entre cualquier par de sucursales. |
| **`ALMACEN`** | **Local** (Exclusivamente su `branch_id`) | `PRODUCT_READ`, `PRODUCT_WRITE`, `CATEGORY_WRITE`, `VARIANT_WRITE`, `STOCK_READ`, `STOCK_ADJUST`, `MOVEMENT_READ`, `STOCK_REPORT_READ` | Crear y editar productos, variantes, categorías y colores. Consultar stock y movimientos **únicamente** de su sucursal. Realizar ajustes manuales en su sucursal. Traspasos: únicamente donde `originBranchId == user.branchId`. |
| **`VENTAS`** | **Local** (Exclusivamente su `branch_id`) | `PRODUCT_READ`, `STOCK_READ` | Lectura del catálogo de productos y consulta de existencias disponibles **únicamente** de su sucursal. Sin acceso de escritura, ajustes ni traspasos. |
| **`VENTAS_ALMACEN`**| **Local** (Exclusivamente su `branch_id`) | Unión de permisos de Ventas y Almacén | Operaciones de venta y administración de inventario dentro de su sucursal asignada. |

### 2.1. Reglas Estrictas de Alcance de Sucursal
1. **No confiar en `branchId` del cliente:** Si un usuario con rol `ALMACEN` o `VENTAS` envía una petición con un parámetro `branchId` diferente al `branch_id` de su token/sesión, el servicio responderá inmediatamente con `403 Forbidden` (`BRANCH_ACCESS_DENIED`).
2. **Traspasos:** Un usuario operativo solo puede transferir inventario si la sucursal origen es la que tiene asignada. No puede debitar inventario de otras sucursales.

---

## 3. Validación de Sesión Activa y Revocación Inmediata

En Auth Service, el mecanismo de cierre de sesión o cambio de contraseña invalida el `active_family_id` del usuario en la base de datos.
Para que un token criptográficamente válido pero revocado en Auth no pueda seguir ejecutando mutaciones de stock:

* MS-02 incluye el puerto `AuthClientPort` y el adaptador `AuthClientAdapter`, que puede consultar `GET /api/v1/auth/me` enviando el Bearer token del usuario.
* **Comportamiento ante caídas de Auth:**
  - Si Auth responde `401 Unauthorized`: MS-02 rechaza la petición con `401 Unauthorized` (`SESSION_INVALID`).
  - Si Auth experimenta timeout o no está disponible (Connection Refused, 5xx): MS-02 **NO** concede la operación y **NO** desloguea erróneamente al usuario; responde con `503 Service Unavailable` (`AUTH_SERVICE_UNAVAILABLE`).

---

## 4. Dependencias Pendientes y Propuesta de Contrato Interno para Auth

### 4.1. Limitación Actual en Auth
En `kiments-auth-service`, los endpoints de sucursales:
* `GET /api/v1/branches`
* `GET /api/v1/branches/{id}`

Tienen la restricción de seguridad:
```java
@PreAuthorize("hasRole('ADMIN') and hasAuthority('BRANCH_READ')")
```
Esto impide que los usuarios con rol `ALMACEN` o `VENTAS` puedan consultar los nombres y códigos de las sucursales existentes en el sistema para seleccionar el destino de un traspaso o visualizar su propia sucursal.

### 4.2. Especificación del Contrato Interno Propuesto

Para solventar esta limitación de manera limpia y sin usar credenciales maestras fijas:

1. **Endpoint propuesto en Auth:**
   * `GET /api/v1/branches/directory` (o permitir `GET /api/v1/branches` a usuarios autenticados con un DTO reducido).
2. **Propósito:** Proporcionar la lista básica de sucursales activas a cualquier trabajador autenticado.
3. **Autenticación:** Cabecera `Authorization: Bearer <usuario>`.
4. **Respuesta Mínima:**
   ```json
   [
     {
       "id": "521c9f1d-624d-4fe5-8c70-9b43396059b2",
       "code": "LOCAL-01",
       "name": "Sucursal Centro",
       "city": "Lima",
       "branchType": "MIXTO",
       "enabled": true
     }
   ]
   ```
5. **Cambio Requerido en Auth Service:**
   En `BranchController.java`, relajar o añadir un endpoint público interno:
   ```java
   @GetMapping("/directory")
   @PreAuthorize("isAuthenticated()")
   public List<BranchDtos.SimpleResponse> directory() {
       return service.listActiveSimple();
   }
   ```
