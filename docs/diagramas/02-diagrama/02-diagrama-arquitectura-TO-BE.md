# 02. Diagrama de Arquitectura SOA (Situación Propuesta - TO-BE)

> 📌 [Ver archivo de imagen original (02-diagrama-arquitectura-TO-BE.png)](./02-diagrama-arquitectura-TO-BE.png)

## 1. Visión General del Sistema Propuesto
El presente documento describe la **arquitectura objetivo (TO-BE)** de **KIMENT'S**, la cual evoluciona desde un sistema monolítico hacia una **arquitectura de microservicios orientada a servicios (SOA) y guiada por eventos (Event-Driven Architecture - EDA)**. Toda la solución se despliega de manera aislada y escalable mediante contenedores Docker.

Esta propuesta elimina los cuellos de botella en el punto de venta al desacoplar el registro síncrono de la venta respecto al procesamiento asíncrono de emisión tributaria y despacho de comprobantes.

---

## 2. Capas de la Arquitectura

### A. Capa de Presentación / Consumidores
* **Aplicación Web de Caja (App Caja):** Interfaz cliente utilizada por vendedores y administradores en el punto de venta. Realiza peticiones HTTP/REST bajo protocolo HTTPS hacia el API Gateway.

### B. Capa de Periferia y Seguridad
* **API Gateway:** Único punto de entrada a la red de microservicios. Se encarga de:
  * Enrutamiento dinámico de peticiones REST.
  * Autenticación y autorización centralizada mediante Tokens JWT consumiendo el servicio de usuarios (`SVC-09`).
  * Aislamiento de la red interna de microservicios.

### C. Capa de Microservicios Síncronos (REST / JSON)
Servicios responsables de la ejecución en tiempo real durante la atención en caja:
* **SVC-09 ServicioUsuarios:** Gestión de credenciales, roles (VENDEDOR, ADMIN) y validación de tokens JWT.
* **SVC-01 ServicioCatalogo:** Mantenimiento de productos, precios y detalles del catálogo.
* **SVC-02 ServicioInventario:** Control y descuento en tiempo real del stock por producto y talla.
* **SVC-03 ServicioClientes:** Gestión del directorio de clientes y sus datos de contacto.
* **SVC-06 ServicioValidacionDocumentos:** Servicio utilitario que consulta APIs externas para la verificación de identidad (DNI vía RENIEC / RUC vía SUNAT).
* **SVC-04 ServicioVentas:** Microservicio orquestador del flujo síncrono. Registra la venta y publica los eventos de dominio correspondientes.

### D. Capa de Mensajería Orientada a Eventos (Broker)
* **RabbitMQ Event Broker:** Componente encargado de garantizar la comunicación asíncrona y el desacoplamiento entre servicios. Maneja exchanges de tipo *topic* (`amq.topic`) y colas dedicadas:
  * `VentaRegistrada`
  * `ComprobanteEmitido`
  * `ComprobanteRechazado`
  * Colas de reintento / mensajes fallidos (Dead Letter Queue - DLQ).

### E. Capa de Microservicios Asíncronos (Workers)
Servicios que procesan tareas pesadas en segundo plano consumiendo eventos desde RabbitMQ:
* **SVC-05 ServicioFacturacion:** Construye y firma digitalmente la estructura XML en estándar UBL 2.1 y la transmite al servicio web de SUNAT mediante protocolo SOAP/WSDL.
* **SVC-07 ServicioGeneracionPdf:** Servicio utilitario que compila la representación visual del comprobante electrónico en formato PDF incorporando el código QR de validación.
* **SVC-08 ServicioNotificaciones:** Servicio utilitario que despacha el PDF y XML adjuntos mediante el servidor de correo (SMTP).

### F. Capa de Bases de Datos Independientes (Database per Service)
Para garantizar la independencia tecnológica, el despliegue individual y evitar el acoplamiento a nivel de datos, cada microservicio gestiona su propio almacenamiento persistente:
* `BD_Usuarios`
* `BD_Catalogo`
* `BD_Inventario`
* `BD_Clientes`
* `BD_Ventas`
* `BD_Facturacion`

### G. Capa de Sistemas Externos Integrados
* **API RENIEC / SUNAT:** Proveedor externo para autocompletar y validar la identidad de clientes nuevos en caja.
* **Web Service SUNAT (SOAP/WSDL):** Servidor de la administración tributaria peruana para el envío de comprobantes (`sendBill`) y recepción de la Constancia de Recepción (CDR).
* **Servidor de Correo (SMTP):** Plataforma para el envío masivo e ininterrumpido de correos electrónicos al cliente.

---

## 3. Patrones de Arquitectura Implementados

1. **Database per Service:** Cada microservicio posee su base de datos relacional independiente.
2. **API Gateway:** Centralización de la entrada, seguridad y enrutamiento.
3. **Event-Driven Architecture (EDA):** Comunicación asíncrona a través de RabbitMQ para procesos diferidos.
4. **Chain Pattern (Cadena de Responsabilidad):** Orquestación síncrona en la atención en caja (`Gateway -> Ventas -> Clientes -> Catálogo -> Inventario`).
5. **Router Condicional:** Evaluación en `SVC-05` de las respuestas de SUNAT (Aceptado, Rechazado, Timeout) para tomar rutas alternas de ejecución.

---

## 4. Beneficios Operacionales de la Propuesta
* **Disponibilidad y Fluidez en Caja:** La venta se confirma inmediatamente en pantalla (`201 Created`) en cuestión de milisegundos, ya que el proceso tributario ocurre en segundo plano.
* **Escalabilidad Independiente:** Cada servicio (ej. Facturación o Catálogo) puede escalar sus recursos en Docker según la demanda de la tienda.
* **Tolerancia a Fallos:** Si el servicio de la SUNAT experimenta caídas, la caja sigue vendiendo normalmente; los comprobantes se reencolan en RabbitMQ (DLQ) para reintentos posteriores.