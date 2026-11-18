# 02. Diagrama de Arquitectura Propuesta (Situación Futura - TO-BE)

> 📌 [Ver archivo de imagen original (02-diagrama-arquitectura-TO-BE.png)](./02-diagrama-arquitectura-TO-BE.png)

## 1. Visión General del Sistema
El presente diagrama representa la **arquitectura propuesta (TO-BE)** para el sistema comercial y de facturación de **KIMENT'S**, estructurada bajo un **patrón de arquitectura en capas** integrado con microservicios independientes. La solución desacopla los dominios funcionales en cuatro niveles horizontales (**Presentación, Negocio, Persistencia e Integración, y Datos/Externos**), incorporando un **API Gateway**, mensajería asíncrona mediante **RabbitMQ** para los flujos críticos de comprobantes y bases de datos aisladas por servicio en **PostgreSQL**.

---

## 2. Descripción por Capas Arquitectónicas

### A. Capa de Presentación
- **Aplicación web de caja:** Interfaz gráfica utilizada por vendedores y administradores para la gestión comercial, consulta de inventario y registro de operaciones en punto de venta.
- **API Gateway:** Punto único de entrada que centraliza el enrutamiento REST hacia los microservicios, aplica políticas de seguridad y realiza la validación de tokens **JWT**.

### B. Capa de Negocio (Microservicios)
- **Autenticación:**
  - `SVC-09 · Usuarios y roles`: Gestión de credenciales, control de acceso y emisión de tokens JWT.
- **Catálogo e inventario:**
  - `SVC-01 · Catálogo`: Administración de productos, categorías y precios.
  - `SVC-02 · Inventario`: Control de existencias y actualización de stock en tiempo real.
- **Ventas:**
  - `SVC-03 · Clientes`: Registro y consulta de información de clientes.
  - `SVC-04 · Ventas`: Procesamiento síncrono de pedidos y operaciones comerciales en caja mediante comunicación REST interna.
- **Facturación:**
  - `SVC-05 · Facturación`: Generación de comprobantes electrónicos en formato estándar **UBL 2.1**.
  - `SVC-06 · Validación DNI/RUC`: Consulta y verificación de documentos de identidad con servicios externos.
  - `SVC-07 · Generación PDF`: Renderizado de la representación impresa del comprobante electrónico.
  - `SVC-08 · Notificaciones`: Envío automatizado de comprobantes (PDF y XML) al correo electrónico del cliente.

### C. Capa de Persistencia e Integración
- **Acceso a datos (SQL):** Repositorios aislados por microservicio encargados de la persistencia relacional y transacciones sobre PostgreSQL.
- **RabbitMQ (Solo Facturación):** Broker de mensajería encargado de gestionar la cola asíncrona de eventos del dominio de facturación (`ComprobanteEmitido` y `ComprobanteRechazado`), desacoplando la generación de PDF, el envío a SUNAT y las notificaciones por correo.
- **Conectores externos:** Adaptadores técnicos encargados de la comunicación mediante protocolos **XML/CDR**, **REST** y **SMTP**.

### D. Capa de Datos y Sistemas Externos
- **Bases de datos independientes (PostgreSQL):** Esquemas desacoplados bajo el patrón *Database per Service*: `autenticacion`, `catalogo_inventario`, `ventas` y `facturacion`.
- **Sistemas externos integrados:**
  - **SUNAT:** Recepción de comprobantes electrónicos (XML firmado) y retorno de Constancias de Recepción (**CDR**).
  - **Proveedor de datos:** Servicio externo REST para la consulta y validación de **DNI / RUC**.
  - **Servidor de correo (SMTP):** Infraestructura de mensajería para el despacho automático de archivos PDF y XML a los clientes.

---

## 3. Mejoras Clave Respecto al Escenario AS-IS

1. **Automatización Tributaria Integral:**
   - Elimina la re-digitación manual en el portal de la SUNAT mediante la emisión y firma electrónica automatizada desde el dominio de Facturación.
2. **Desacoplamiento y Tolerancia a Fallos en Facturación:**
   - El uso de **RabbitMQ** en el flujo de comprobantes garantiza que la atención en caja no se bloquee ante demoras en la respuesta de SUNAT o en el envío de correos electrónicos.
3. **Aislamiento de Datos y Escalabilidad:**
   - Al contar con esquemas independientes (`autenticacion`, `catalogo_inventario`, `ventas`, `facturacion`), cada dominio puede evolucionar y escalar sin comprometer la integridad de los demás módulos.