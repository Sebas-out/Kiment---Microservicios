# KIMENTS POS — Contrato de Futura Integración con MS-03 Ventas
## Especificación de Interfaz de Descuento y Devolución de Stock

---

## 1. Principios de Propiedad de Datos (Data Ownership)

1. **MS-03 (Ventas)** es el único propietario de los comprobantes de venta, cotizaciones, carritos de compra y clientes.
2. **MS-02 (Catálogo e Inventario)** es el único propietario de las existencias (`stock_balances`), movimientos (`stock_movements`) y operaciones de inventario.
3. **Prohibición estricta:** MS-03 **nunca** debe escribir directamente en la base de datos de `catalog_inventory`, ni ejecutar `UPDATE` sobre saldos de existencias. Toda mutación debe realizarse a través de la API expuesta por MS-02.

---

## 2. Casos de Uso del Contrato

### 2.1. Descuento de Stock por Venta Confirmada
Cuando se emite una venta en el POS:

* **Método y Ruta:** `POST /api/v1/stock/sales/deduct`
* **Cabeceras obligatorias:**
  - `Authorization: Bearer <token_del_cajero_o_servicio>`
  - `Idempotency-Key: <sale_id_o_uuid_de_venta>`
  - `X-Correlation-Id: <correlation_id>`
* **Cuerpo de la Petición (`SaleStockDeductionRequest`):**
  ```json
  {
    "saleId": "9a1c9f1d-624d-4fe5-8c70-9b43396059b1",
    "branchId": "521c9f1d-624d-4fe5-8c70-9b43396059b2",
    "referenceCode": "B001-00004523",
    "reason": "Venta en tienda física",
    "items": [
      {
        "variantId": "44444444-4444-4444-4444-444444444401",
        "quantity": 2
      },
      {
        "variantId": "44444444-4444-4444-4444-444444444402",
        "quantity": 1
      }
    ]
  }
  ```
* **Lógica Interna en MS-02:**
  1. Adquiere bloqueos pesimistas ordenados determinísticamente sobre los `stock_balances` de la sucursal.
  2. Valida que todas las variantes tengan existencias suficientes (`quantity >= item.quantity`).
  3. Si alguna variante carece de existencias: revierte la transacción y retorna `409 Conflict` con detalle exacto del producto sin stock.
  4. Si hay existencias: descuenta los saldos de forma atómica.
  5. Crea una `stock_operations` con `type = SALE_OUT`, `reference_type = 'SALE'`, `reference_id = saleId`.
  6. Inserta las líneas de `stock_movements` con `delta` negativo.
* **Respuesta Exitosa (200 OK):**
  ```json
  {
    "operationId": "fa1c9f1d-624d-4fe5-8c70-9b43396059c5",
    "saleId": "9a1c9f1d-624d-4fe5-8c70-9b43396059b1",
    "status": "DEDUCTED",
    "processedAt": "2026-11-19T20:45:00Z"
  }
  ```

---

### 2.2. Devolución de Mercadería / Reversión de Venta (Nota de Crédito)
Cuando un cliente devuelve un producto o se anula una venta:

* **Método y Ruta:** `POST /api/v1/stock/sales/return`
* **Cabeceras obligatorias:**
  - `Authorization: Bearer <token_del_operador>`
  - `Idempotency-Key: <return_id_o_nota_credito_id>`
* **Cuerpo de la Petición (`SaleStockReturnRequest`):**
  ```json
  {
    "saleId": "9a1c9f1d-624d-4fe5-8c70-9b43396059b1",
    "branchId": "521c9f1d-624d-4fe5-8c70-9b43396059b2",
    "referenceCode": "NC01-00000120",
    "reason": "Devolución por cambio de talla",
    "items": [
      {
        "variantId": "44444444-4444-4444-4444-444444444401",
        "quantity": 1
      }
    ]
  }
  ```
* **Lógica Interna en MS-02:**
  1. Incrementa los saldos correspondientes con bloqueo pesimista.
  2. Registra la operación `stock_operations` con `type = SALE_RETURN`.
  3. Inserta movimientos con `delta` positivo.

---

## 3. Resiliencia, Idempotencia y Comportamiento ante Fallos

1. **Idempotencia Garantizada:** Si MS-03 envía la solicitud de descuento, la base de datos de MS-02 confirma la transacción, pero ocurre un corte de red antes de que MS-03 reciba la respuesta HTTP 200:
   - MS-03 reintentará con la misma `Idempotency-Key`.
   - MS-02 detectará el registro previo en `catalog_inventory.idempotency_records` y responderá inmediatamente `200 OK` con el resultado idéntico, **sin volver a descontar el stock**.
2. **Patrón Transaccional Outbox (Recomendado para eventos futuros):**
   - Cuando se introduzca un broker de mensajería (Kafka o RabbitMQ) en fases futuras, la publicación del evento de venta o descuento debe implementarse mediante una tabla Outbox en la misma transacción que modifica los saldos, garantizando entrega garantizada (At-Least-Once Delivery) sin riesgo de publicación prematura antes del commit.
