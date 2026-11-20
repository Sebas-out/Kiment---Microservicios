# KIMENTS POS — Guía de Integración para el BFF Next.js
## Conexión del Frontend con MS-02 Catálogo e Inventario

---

## 1. Contexto de Arquitectura y Autenticación

El frontend Next.js se comunica con el backend mediante su BFF (Backend For Frontend). Las cookies de sesión `__Host-kiments-access` y `__Host-kiments-refresh` residen exclusivamente entre el navegador y el BFF Next.js.
El BFF extrae el JWT de acceso y lo envía en la cabecera `Authorization: Bearer <accessToken>` hacia el microservicio MS-02.

```
Navegador (React 19)
       │  (Cookie HttpOnly, SameSite=Strict)
       ▼
BFF Next.js (/api/management/...)
       │  Authorization: Bearer <JWT>
       │  Idempotency-Key: <UUID> (opcional en mutaciones)
       │  X-Correlation-Id: <UUID>
       ▼
MS-02 Catálogo e Inventario (Puerto 8082)
```

---

## 2. Convenciones de la API

* **Formato de datos:** JSON (`application/json;charset=UTF-8`).
* **Formato de errores:** RFC 7807 Problem Details (`application/problem+json`).
* **Paginación uniforme:**
  - Petición: `page` (base 0), `size` (máximo 100).
  - Respuesta `PageResponse<T>`:
    ```json
    {
      "content": [ ... ],
      "page": 0,
      "size": 20,
      "totalElements": 48,
      "totalPages": 3
    }
    ```
* **Manejo de idempotencia:** En peticiones de creación o modificación crítica (`POST /api/v1/products`, `/api/v1/stock/adjust`, `/api/v1/stock-transfers`), enviar la cabecera `Idempotency-Key: <UUID>`. Si la petición se reintenta por desconexión con la misma clave y payload, MS-02 responderá con el resultado previamente generado sin duplicar registros.

---

## 3. Endpoints Principales y Mapeo al Frontend

### 3.1. Atributos (Categorías, Colores, Tallas)

| Endpoint | Método | Permiso requerido | Propósito en el Frontend |
|---|---|---|---|
| `/api/v1/categories` | GET | `PRODUCT_READ` | Lista paginada para selectores y gestión de categorías |
| `/api/v1/categories` | POST | `CATEGORY_WRITE` | Crear categoría (drawer rápido o pantalla propia) |
| `/api/v1/categories/{id}` | PUT | `CATEGORY_WRITE` | Actualizar nombre/descripción |
| `/api/v1/categories/{id}/status` | PATCH | `CATEGORY_WRITE` | Activar o desactivar categoría |
| `/api/v1/colors` | GET | `PRODUCT_READ` | Lista de colores con hex para paleta y swatches |
| `/api/v1/colors` | POST | `VARIANT_WRITE` | Crear color con nombre y código `#RRGGBB` |
| `/api/v1/colors/{id}/status` | PATCH | `VARIANT_WRITE` | Activar o desactivar color |
| `/api/v1/sizes` | GET | `PRODUCT_READ` | Lista de tallas ordenada por `sortOrder` |
| `/api/v1/sizes` | POST | `VARIANT_WRITE` | Crear talla |

### 3.2. Catálogo de Productos y Variantes

#### Crear Producto con Matriz de Variantes y Stock Inicial
* **Endpoint:** `POST /api/v1/products`
* **Permiso:** `PRODUCT_WRITE`
* **Cabecera recomendada:** `Idempotency-Key: <uuid>`
* **Ejemplo de Payload:**
  ```json
  {
    "categoryId": "11111111-1111-1111-1111-111111111101",
    "name": "Pantalón Denim Flare",
    "description": "Jean tiro alto confeccionado en tela stretch",
    "autoSku": true,
    "autoBarcode": true,
    "enabled": true,
    "variants": [
      {
        "colorId": "22222222-2222-2222-2222-222222222205",
        "sizeId": "33333333-3333-3333-3333-333333333302",
        "sku": null,
        "barcode": null,
        "price": 89.90,
        "wholesalePrice": 69.90,
        "initialStock": [
          {
            "branchId": "521c9f1d-624d-4fe5-8c70-9b43396059b2",
            "quantity": 10
          }
        ]
      },
      {
        "colorId": "22222222-2222-2222-2222-222222222205",
        "sizeId": "33333333-3333-3333-3333-333333333303",
        "sku": null,
        "barcode": null,
        "price": 89.90,
        "wholesalePrice": 69.90,
        "initialStock": [
          {
            "branchId": "521c9f1d-624d-4fe5-8c70-9b43396059b2",
            "quantity": 15
          }
        ]
      }
    ]
  }
  ```

#### Listado Global de Productos (Pestaña "Global")
* **Endpoint:** `GET /api/v1/products`
* **Parámetros query:** `query`, `categoryId`, `colorId`, `sizeId`, `branchId`, `enabled`, `available`, `page`, `size`.
* **Comportamiento:** Devuelve los productos agrupados con su lista de variantes, fotografía global y existencias consolidadas.

#### Listado Detallado de Variantes (Pestaña "Variantes")
* **Endpoint:** `GET /api/v1/products/variants`
* **Parámetros query:** `query`, `productId`, `categoryId`, `colorId`, `sizeId`, `branchId`, `enabled`, `available`, `page`, `size`.
* **Comportamiento:** Paginación fila por fila de cada variante individual con su SKU, código de barras, precio y existencias.

#### Edición de Producto
* **Endpoint:** `PUT /api/v1/products/{id}`
* **Comportamiento:** Modifica únicamente nombre, categoría y descripción. **No altera existencias ni variantes.**
* **Edición de variante:** `PUT /api/v1/variants/{id}` (modifica precio, precio mayorista o código de barras sin tocar inventario).

### 3.3. Fotografías de Producto

| Endpoint | Método | Permiso | Descripción |
|---|---|---|---|
| `/api/v1/products/{id}/images` | POST | `PRODUCT_WRITE` | Carga multipart (`file`, `colorId` opcional). Si `colorId` se omite, se guarda como imagen global |
| `/api/v1/products/{id}/images/{imageId}` | GET | `PRODUCT_READ` | Descarga de la imagen autenticada con caché privada (`max-age=3600`) |
| `/api/v1/products/{id}/images/{imageId}` | DELETE | `PRODUCT_WRITE` | Elimina la imagen física y su metadato |
| `/api/v1/products/{id}/images/order` | PUT | `PRODUCT_WRITE` | Reordena las imágenes enviando `imageIds` ordenados |

* **Resolución en el Frontend:** En `<img src="...">`, el BFF puede exponer un proxy `/api/media/products/[productId]/images/[imageId]` que inyecte el Bearer token, evitando exponer cabeceras en etiquetas `<img>` del navegador.

### 3.4. Existencias e Inventario

#### Consulta de Stock
* **Endpoint:** `GET /api/v1/stock`
* **Filtros:** `branchId`, `query`, `availableOnly`, `page`, `size`.
* **Regla de autorización:** Si el usuario no es `ADMIN`, el parámetro `branchId` es forzado automáticamente a su sucursal autorizada.

#### Ajuste Manual de Stock (Entradas y Salidas)
* **Endpoint:** `POST /api/v1/stock/adjust`
* **Permiso:** `STOCK_ADJUST`
* **Payload:**
  ```json
  {
    "variantId": "44444444-4444-4444-4444-444444444401",
    "branchId": "521c9f1d-624d-4fe5-8c70-9b43396059b2",
    "quantity": 5,
    "type": "MANUAL_IN",
    "reason": "Ingreso por reposición de proveedor"
  }
  ```

#### Traspaso Grupal entre Sucursales
* **Endpoint:** `POST /api/v1/stock-transfers`
* **Permiso:** `STOCK_ADJUST`
* **Payload:**
  ```json
  {
    "originBranchId": "521c9f1d-624d-4fe5-8c70-9b43396059b2",
    "destinationBranchId": "621c9f1d-624d-4fe5-8c70-9b43396059b2",
    "reason": "Traspaso de mercadería para campaña fin de semana",
    "items": [
      {
        "variantId": "44444444-4444-4444-4444-444444444401",
        "quantity": 4
      },
      {
        "variantId": "44444444-4444-4444-4444-444444444402",
        "quantity": 2
      }
    ]
  }
  ```

#### Historial de Movimientos
* **Endpoint:** `GET /api/v1/stock-movements`
* **Permiso:** `MOVEMENT_READ`
* **Filtros:** `branchId`, `type`, `variantId`, `from`, `to`, `query`, `page`, `size`.

### 3.5. Reportes y Exportación

#### Resumen de Métricas
* **Endpoint:** `GET /api/v1/reports/inventory/summary?branchId=...`
* **Permiso:** `STOCK_REPORT_READ`
* **Respuesta:**
  ```json
  {
    "totalVariants": 120,
    "variantsWithStock": 95,
    "variantsOutOfStock": 25,
    "totalQuantity": 840
  }
  ```

#### Descarga Excel (.xlsx)
* **Endpoint:** `GET /api/v1/reports/inventory/export`
* **Permiso:** `STOCK_REPORT_READ`
* **Respuesta:** Archivo binario con cabecera `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` y `Content-Disposition: attachment; filename="inventario_kiments.xlsx"`.
