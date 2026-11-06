# 04. Flujo 02: Emisión y Entrega de Comprobante (Asíncrono - Router Condicional + Chain)

> 📌 [Ver archivo de imagen original (04-diagrama-flujo-02-facturacion-asincrona.png)](./04-diagrama-flujo-02-facturacion-asincrona.png)

## 1. Visión General del Flujo Asíncrono
Este flujo describe el procesamiento en segundo plano que se desencadena tras la confirmación de una venta en el punto de venta. Corresponde al ciclo de generación del XML firmado, transmisión e integración tributaria con la **SUNAT** mediante servicios web SOAP/WSDL, renderizado del PDF con código QR y despacho del comprobante por correo electrónico al cliente.

El diseño utiliza el **Patrón Router Condicional combinado con Chain (Cadena de Responsabilidad)** sobre una **Arquitectura Orientada a Eventos (EDA)**. Al estar desacoplado mediante **RabbitMQ**, la atención en la caja registradora no experimenta congelamientos ni demoras por caídas de red o latencias del servidor tributario.

---

## 2. Participantes del Flujo
* **RabbitMQ (Broker):** Intermediario de mensajes que gestiona los eventos de dominio (`VentaRegistrada`, `ComprobanteEmitido`, `ComprobanteRechazado`) y maneja colas de reintento.
* **SVC-05 Facturacion:** Microservicio encargado de procesar la venta, construir y firmar el XML en estándar UBL 2.1 e interactuar con la SUNAT.
* **Web Service SUNAT (SOAP):** Entidad fiscalizadora externa que valida y emite la Constancia de Recepción (CDR).
* **SVC-07 GeneracionPdf:** Servicio utilitario encargado de compilar los datos del comprobante y el CDR para generar el PDF con su código QR.
* **SVC-08 Notificaciones:** Servicio utilitario que despacha los adjuntos (PDF y XML) al correo electrónico del cliente vía SMTP.
* **SVC-04 Ventas:** Microservicio de dominio que actualiza el estado final del comprobante en el sistema.

---

## 3. Secuencia Detallada de Interacciones

### A. Consumo de Evento y Firma del XML (Pasos 1 - 2)
1. **SVC-05 Facturacion** consume el evento `VentaRegistrada` de forma asíncrona desde la cola correspondiente en **RabbitMQ**.
2. **SVC-05** transforma la estructura del DTO de venta, construyendo la trama XML en estándar **UBL 2.1** y aplicando la **firma digital** ($XMLDSig$).

### B. Integración Tributaria - Router Condicional SUNAT (Bloque Condicional - Paso 3)
3. **SVC-05 Facturacion** realiza una invocación síncrona vía SOAP `sendBill` enviando el XML firmado al **Web Service SUNAT**:
   * **Caso 1: SUNAT Acepta Comprobante (con/sin Observaciones):**
     * SUNAT responde con la **CDR (Constancia de Recepción)** de aceptación.
     * **SVC-05** invoca a **SVC-07 GeneracionPdf** (`generarPdf`), enviando los datos de la venta y la CDR.
     * **SVC-07** retorna el archivo PDF renderizado que incluye el código QR normativo.
     * **SVC-05** publica el evento `ComprobanteEmitido` en **RabbitMQ**.
   * **Caso 2: SUNAT Rechaza (CDR Rechazado):**
     * SUNAT responde con un código de error tributario (ej. inconsistencia de datos o RUC inválido).
     * **SVC-05** publica el evento `ComprobanteRechazado` en **RabbitMQ** para su subsanación manual posterior.
   * **Caso 3: Timeout / Red No Disponible:**
     * En caso de caída de conectividad o tiempo de espera agotado con la SUNAT, **SVC-05** no descarta el mensaje y reencola la petición en **RabbitMQ** aplicando políticas de reintento en segundo plano (Dead Letter Queue - DLQ).

### C. Despacho Asíncrono y Actualización de Estado (Bloque Notificaciones - Paso 4)
4. El broker **RabbitMQ** coordina las acciones finales tras la emisión:
   * **SVC-08 Notificaciones** consume el evento `ComprobanteEmitido` desde **RabbitMQ**:
     * **Si el cliente registró correo:** Executa `enviarEmail` adjuntando el XML y PDF mediante el servidor SMTP.
     * **Si no registró correo:** Omite el envío por email, dejando los archivos disponibles en el sistema para impresión física o consulta en línea.
   * **RabbitMQ** notifica a **SVC-04 Ventas** para actualizar el atributo `estadoComprobante` a `EMITIDO` o `RECHAZADO` según corresponda en la `BD_Ventas`.

---

## 4. Beneficios Técnicos del Diseño Asíncrono
* **Tolerancia a Fallos Operativos:** Si los servidores de la SUNAT presentan intermitencias, la venta en caja continúa procesándose con normalidad mientras RabbitMQ gestiona los reintentos.
* **Despliegue y Procesamiento Aislado:** Los servicios de generación de PDF (`SVC-07`) y envío de correos (`SVC-08`) consumen cómputo de forma totalmente independiente del núcleo de ventas.
* **Cumplimiento Tributario Automatizado:** Emisión de archivos en formato oficial UBL 2.1 con firma digital, CDR de recepción y código QR sin requerir la intervención manual del personal de caja.