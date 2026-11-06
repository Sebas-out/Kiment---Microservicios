# 03. Flujo 01: Venta en Caja (Síncrono - Patrón Chain)

> 📌 [Ver archivo de imagen original (03-diagrama-flujo-01-venta-en-caja.png)](./03-diagrama-flujo-01-venta-en-caja.png)

## 1. Visión General del Flujo Síncrono
Este flujo describe la interacción en tiempo real que se ejecuta mientras el cliente se encuentra en la caja registradora. Representa el ciclo completo desde que el vendedor envía la orden de compra hasta que recibe la confirmación en pantalla (`201 Created`). 

El proceso utiliza el **Patrón Chain (Cadena de Responsabilidad)** para orquestar la comunicación secuencial síncrona mediante peticiones HTTP/REST (JSON) a través de la red de microservicios, asegurando que la caja obtenga una respuesta inmediata en cuestión de milisegundos.

---

## 2. Participantes del Flujo
* **Vendedor (Caja App):** Interfaz frontend en el punto de venta que inicia la solicitud `POST /api/v1/ventas`.
* **API Gateway:** Entrada única que enruta la petición y coordina la verificación de seguridad.
* **SVC-09 Usuarios:** Microservicio responsable de verificar la validez del token JWT y confirmar el rol del usuario (`VENDEDOR`).
* **SVC-04 Ventas:** Microservicio orquestador de la transacción de venta.
* **SVC-03 Clientes:** Microservicio que gestiona la consulta y registro del comprador.
* **SVC-06 ValidacionDoc:** Servicio utilitario que consulta APIs externas (RENIEC / SUNAT) para verificar documentos de identidad.
* **SVC-01 Catalogo:** Microservicio que provee precios, nombres y detalles de los productos.
* **SVC-02 Inventario:** Microservicio que valida y descuenta el stock disponible por talla y producto.
* **RabbitMQ:** Broker de mensajería donde se publica el evento de dominio final.

---

## 3. Secuencia Detallada de Interacciones

### A. Autenticación y Entrada (Pasos 1 - 4)
1. El **Vendedor** escanea las prendas y presiona procesar cobro en la **Caja App**, enviando una petición `POST /api/v1/ventas` con los datos de la venta y el Token JWT en el encabezado.
2. El **API Gateway** intercepta la petición y llama a `validarAcceso(Token)` en **SVC-09 Usuarios**.
3. **SVC-09** responde que el acceso es válido y que el usuario posee el rol `VENDEDOR`.
4. El **API Gateway** redirige la solicitud `registrarVenta(VentaDTO)` hacia **SVC-04 Ventas**.

### B. Identificación y Validación de Cliente (Bloque Condicional - Paso 5)
5. **SVC-04 Ventas** consulta a **SVC-03 Clientes** ejecutando `buscarCliente(DNI/RUC)`:
   * **Caso Cliente Nuevo:** Si la persona no está registrada en la base de datos interna, **SVC-03** llama a **SVC-06 ValidacionDoc** (`validarDocumento`), el cual consulta los padrones externos de RENIEC o SUNAT, retorna los datos personales verificados y registra al cliente en la `BD_Clientes`.
   * **Caso Cliente Existente:** **SVC-03** recupera directamente la información persistida y la retorna a **SVC-04**.

### C. Verificación de Precios y Descuento de Stock (Bloque Síncrono - Paso 6)
6. **SVC-04 Ventas** interactúa con el catálogo y el almacén:
   * Consulta a **SVC-01 Catalogo** (`obtenerProducto`) para obtener los precios unitarios vigentes y descripciones oficiales.
   * Solicita a **SVC-02 Inventario** (`descontarStock`) la reducción de las unidades vendidas por producto y talla. **SVC-02** descuenta las existencias en `BD_Inventario` y retorna la confirmación.

### D. Cierre Transaccional y Publicación de Evento (Pasos 7 - 9)
7. **SVC-04 Ventas** registra la venta en la `BD_Ventas` con estado `REGISTRADA` y publica el evento de dominio `VentaRegistrada` en el topic de **RabbitMQ**.
8. **SVC-04** responde inmediatamente al **API Gateway** con un código HTTP `201 Created` conteniendo el `{ idVenta, estado: REGISTRADA }`.
9. El **API Gateway** transmite la confirmación a la **Caja App**, mostrando en pantalla que la venta fue procesada con éxito y dejando la caja lista para el siguiente cliente.

---

## 4. Beneficios Técnicos del Diseño Síncrono
* **Respuesta Inmediata:** La caja no experimenta bloqueos ni espera la respuesta de SUNAT o el envío de correos.
* **Integridad de Datos:** Garantiza que no se confirme ninguna venta si no hay stock suficiente o si el cliente no cumple los requerimientos de identificación.
* **Desacoplamiento:** El orquestador de ventas interactúa únicamente con APIs REST del dominio sin conocer la implementación interna de los microservicios secundarios.