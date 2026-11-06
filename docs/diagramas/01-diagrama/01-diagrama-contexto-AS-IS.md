# 01. Diagrama de Contexto (Situación Actual - AS-IS)

> 📌 [Ver archivo de imagen original (01-diagrama-contexto-AS-IS.png)](./01-diagrama-contexto-AS-IS.png)

## 1. Visión General del Sistema
El presente diagrama representa la **situación actual (AS-IS)** del sistema de comercialización y atención en caja de **KIMENT'S**. Muestra la arquitectura monolítica centralizada, los actores involucrados en el proceso de venta y la desconexión crítica con las plataformas externas de tributación (SUNAT) y mensajería (servidores de correo).

---

## 2. Componentes y Actores Principales

### A. Actores del Sistema
* **Vendedor:** Personal en tienda encargado de atender al cliente y registrar los productos solicitados en el sistema central.
* **Cliente:** Consumidor final que realiza la compra y efectúa el pago en el punto de venta.
* **Cajero / Personal Operativo:** Encargado de procesar la venta, emitir el comprobante de pago y gestionar su entrega. En la arquitectura actual, asume la carga operativa de re-digitar información y enviar correos manualmente.

### B. Núcleo Tecnológico (Sistema Central)
* **Sistema Comercial Monolítico:** Aplicación web monolítica centralizada que gestiona en una única base de datos relacional los módulos de **Ventas, Inventario, Productos, Clientes y Usuarios**. Carece de APIs expuestas o servicios de integración orientados a microservicios.

### C. Sistemas Externos Aislados
* **Portal SUNAT:** Plataforma web externa de la Administración Tributaria. No posee integración vía Web Services (SOAP/WSDL) ni APIs conectadas directamente con el sistema de la caja.
* **Plataforma de Correo:** Servidor/cliente de correo electrónico comercial independiente, sin integración ni automatización con el sistema core.

---

## 3. Identificación de Cuellos de Botella y Puntos de Falla

1. **Ausencia de Integración Tributaria:**
   * El sistema monolítico no emite comprobantes electrónicos de forma automática.
   * Obliga al cajero a realizar un **proceso manual diferido**: ingresar a la web externa de la SUNAT y volver a digitar los datos de la venta y del cliente.

2. **Proceso Manual de Despacho de Comprobantes:**
   * El sistema no dispone de un módulo de notificaciones o conexión SMTP.
   * El personal debe descargar manualmente los archivos PDF y XML desde el portal de SUNAT, adjuntarlos en un cliente de correo externo e ingresar la dirección del cliente manualmente.

3. **Impacto Operacional:**
   * **Lentitud y Filas en Caja:** La re-digitación duplicada de datos incrementa drásticamente el tiempo de atención por cliente.
   * **Riesgo Elevado de Error Humano:** Inconsistencias entre el registro interno de ventas y los comprobantes declarados ante la SUNAT por equivocaciones de tipeo.
   * **Ineficiencia Operativa:** El personal de caja dedica tiempo significativo a tareas administrativas manuales en lugar de enfocarse en la atención comercial.

---

## 4. Justificación de Cambio
El diagnóstico del escenario **AS-IS** evidencia la necesidad imperativa de migrar hacia una **Arquitectura de Microservicios Orientada a Eventos (TO-BE)**. La nueva propuesta incorporará un **API Gateway**, comunicación asíncrona mediante **RabbitMQ** y servicios dedicados para la validación de documentos (RENIEC/SUNAT), facturación electrónica automática (SOAP/UBL 2.1) y notificaciones automáticas por correo electrónico (SMTP).