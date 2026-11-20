# Integración real de Catálogo e Inventario

Actualización: el usuario informó que los historiales ya responden HTTP 200 y que el resumen cuenta variantes activas únicas. El frontend conserva los mismos endpoints compatibles, limpia errores anteriores al recargar y utiliza directamente las cinco métricas del resumen del servicio. No se ejecutaron nuevas pruebas, compilaciones ni solicitudes al backend en esta actualización, por indicación del usuario.

Fecha de verificación: 19 de noviembre de 2026. Frontend: `C:\Users\Bryan\Downloads\kiments-frontend`.
Backend consultado en modo lectura: `kiments-catalog-inventory-service`. No se modificó su código, configuración ni esquema de base de datos.

## Configuración

```dotenv
AUTH_API_URL=http://localhost:8080
CATALOG_API_URL=http://localhost:8082
APP_ORIGIN=http://localhost:3000
```

El navegador llama a `/api/catalog/*`; Next añade el JWT desde la cookie HttpOnly y reenvía al servicio privado `/api/v1/*`. No se expone el token al JavaScript. Se reutiliza la renovación de sesión existente. Las escrituras expiradas requieren un reintento explícito, conservando la clave de idempotencia para productos y operaciones de stock. En producción configurar también CATALOG_API_URL en el entorno del contenedor con un endpoint HTTPS privado accesible desde Next; localhost solo aplica al desarrollo en esta computadora.

## Operaciones conectadas

| Pantalla | API real |
| --- | --- |
| Categorías, colores, tallas | GET/POST `/categories`, `/colors`, `/sizes`; PUT `/{id}`; PATCH `/{id}/status` |
| Productos globales y variantes | GET/POST `/products`; GET/PUT `/products/{id}`; POST `/products/{id}/variants`; PUT `/variants/{id}`; PATCH estados de producto o variante |
| Imágenes | POST multipart `/products/{id}/images`, GET/DELETE `/{imageId}`, PUT `/order` con colorId cuando corresponde |
| Etiquetas | GET `/products/barcode-data`, búsqueda y cantidades, A4/Carta/ticket 80 mm |
| Excel | GET `/reports/inventory/export` devuelve XLSX binario |
| Analítica | GET `/reports/inventory/summary`, con alcance de sucursal cuando está seleccionado |
| Movimientos | GET `/stock-movements?page=0&size=10`; POST `/stock/adjust` |
| Traspasos | GET/POST `/stock-transfers` |

Los datos antiguos de prueba del navegador no se migraron ni se borraron. El listado ahora refleja exclusivamente el backend.

Los listados de atributos y productos recopilan las páginas reales del API para conservar los filtros visuales existentes. Los historiales de auditoría solicitan diez registros por página al servidor. Las existencias vienen de `stockByBranch` del producto, no de localStorage. Las imágenes globales y por color se mantienen separadas, hasta cinco fotos por color, JPG/PNG/WebP de hasta 5 MB por archivo. El SKU automático y el código definitivo los genera el backend. Los indicadores del formulario antes de guardar son provisionales. El stock de variantes persistidas se modifica mediante Movimientos, para conservar auditoría.

Los permisos se comprueban otra vez en el BFF y en Spring. El directorio de sucursales de Auth está restringido a administradores; los perfiles operativos usan su branchId de sesión y el rótulo «Sucursal asignada», sin inventar nombre ni consultar directorios protegidos. El contrato actual del backend restringe los traspasos entre sucursales a ADMIN; el formulario respeta ese alcance.

## Pruebas reales

- Inicio con la sesión real ya abierta por el usuario; sin cambiar cuentas ni contraseñas.
- Lectura de productos, categorías, cinco tallas y ocho colores.
- Creación de `QA integración catálogo`, precios 20/15; SKU y EAN-13 asignados por el backend. Edición del precio a 21.
- Subida de una imagen global y otra para Azul. Las tarjetas global y variante mostraron URLs de imagen distintas.
- Etiquetas reales, resumen real de 25 unidades y descarga de `inventario.xlsx`; archivo ZIP/XLSX válido.
- Entrada de una unidad y salida de una unidad sobre el producto QA: stock final cero. El stock del producto existente quedó en 25.
- Crear/editar/desactivar categoría QA, crear/desactivar talla QA y color QA. Producto QA desactivado al terminar. La auditoría y los registros desactivados se conservan en el backend por diseño.
- 71 pruebas automáticas, incluyendo rutas permitidas, permisos, no repetir escrituras expiradas, multipart, paginación, mapeo de imágenes y XLSX. Typecheck y ESLint sin errores. La copia aislada, con los mismos 100 archivos de src, compiló el código, verificó TypeScript y generó las 20 rutas. El comando no terminó limpio: Next interrumpió esa ejecución con su aviso de vulnerabilidad; posteriores intentos de build encontraron bloqueos EPERM de Windows en directorios generados, tanto en Descargas como en la copia. No se declara un build final aprobado. Copia de verificación: `C:\Users\Bryan\Documents\Codex\2026-11-01\e\work\catalog-integration-check`.

## Registro histórico de bloqueos (corregidos por el usuario)

En la revisión anterior, las escrituras manuales respondían y estas consultas devolvían HTTP 500:

1. GET `/api/v1/stock-movements?page=0&size=10`. También falla enviando fechas y búsqueda válidas. Correlation ID capturado: `9310b416-9afb-4004-a342-9b1e76257797`.
2. GET `/api/v1/stock-transfers?page=0&size=10`. Correlation ID: `80432021-a66f-4ac2-811e-0a14742abc38`.

Revisar en los logs del servicio las excepciones asociadas a esos IDs, especialmente `StockMovementRepository.searchMovements` y `StockTransferRepository.searchTransfers`. Sin el stacktrace no se afirma una causa. No es un problema de inicio de sesión: otros endpoints funcionan con la misma sesión. La interfaz muestra el fallo, permite operar el formulario independientemente y diferencia una escritura exitosa de una recarga de historial fallida.

Solo hay una sucursal activa accesible, por lo que no se ejecutó un traspaso real completo. Para el traspaso manual es necesario registrar una segunda sucursal activa y elegir origen y destino distintos; el usuario ya informó corregido el error del historial.

Next.js 16.4.3 emitió un aviso de vulnerabilidad durante el build. Hubo una compilación previa exitosa durante la integración, pero la comprobación final quedó interrumpida como se explica arriba. La advertencia sigue pendiente de una actualización de seguridad por separado.

Contrato analítico actualizado según la corrección informada: totalVariants, variantsWithStock y variantsOutOfStock cuentan variantes activas únicas de productos activos; totalQuantity contiene las existencias físicas. El frontend consume estos valores sin recalcular los indicadores principales a partir de las tarjetas.
