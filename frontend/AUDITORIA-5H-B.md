# KIMENTS POS · Auditoría 5H-B

5 de noviembre de 2026 · Proyecto: `C:\Users\Bryan\Downloads\kiments-frontend`

El BFF pasó las pruebas aisladas de acceso, autorización, cookies, CSRF y fotografías. La revisión confirmó desplazamiento horizontal interno en Usuarios y encontró tres áreas para priorizar: coordinación de sesión, límites de solicitudes y cabeceras de producción. Las fotos de terceros dependen de un contrato de lectura autorizado que el frontend actual no consume. Este informe es el único cambio de la auditoría. Las correcciones quedan sujetas a autorización.

## Protecciones existentes

- Las páginas privadas verifican la sesión en el servidor. El BFF de administración consulta el perfil autenticado, exige ADMIN y el permiso de cada operación, y limita método, recurso, UUID, consulta y cuerpo a contratos concretos. Los esquemas también recortan los datos devueltos. El navegador usa rutas del mismo origen. Los JWT viajan al backend desde código exclusivo del servidor, con destino configurado, redirecciones deshabilitadas y HTTPS en producción salvo loopback. Véanse `src/lib/auth/backend.ts`, `src/lib/auth/current-session.ts` y `src/features/users/operations.ts`.
- En el build de producción se observaron dos cookies host-only con prefijo `__Host-`, `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/` y sin `Domain`. Acceso toma el `Max-Age` del backend, 900 segundos en el entorno de prueba. Renovación queda como cookie de sesión del navegador. El vencimiento absoluto y la revocación corresponden al backend. La restauración de sesión del navegador puede recuperar cookies de sesión. Desarrollo omite Secure y el prefijo. Logout elimina ambas con `Max-Age=0`. Véase `src/lib/auth/session.ts`.
- Login, refresh, logout, mutaciones administrativas, contraseña y avatar verifican `Origin` exacto y rechazan `Sec-Fetch-Site: cross-site`. El tipo de contenido se valida cuando la operación lleva cuerpo. DELETE de foto mantiene el control de origen. Un GET autenticado con Origin ajeno y un OPTIONS de preflight carecieron de cabeceras CORS de autorización. Un DELETE con cookie y Fetch Metadata cross-site devolvió 403. Son pruebas del servidor Next aislado. La configuración del proxy y el CORS de Spring requieren revisión en su entorno.
- La búsqueda de fuentes no encontró HTML crudo inyectado por la aplicación. React presenta los datos y errores como texto. El único uso de localStorage detectado guarda el tema. Los DTO de sesión y administración descartan campos ajenos al contrato. Nombres, correo y DNI siguen siendo datos personales enviados a vistas autorizadas y deben quedar fuera de logs y cachés compartidas. HttpOnly limita la lectura de cookies desde scripts, aunque un XSS podría efectuar acciones con la sesión activa.
- Ante 401, el coordinador revalida el perfil y puede renovar. Las lecturas de administración se reintentan y las escrituras requieren acción explícita. Un 403 no dispara renovación. Logout cierra localmente aunque la revocación remota falle y la interfaz distingue ese caso. Véanse `src/features/auth/session-client.ts`, `src/features/users/client.ts` y `src/app/api/auth/logout/route.ts`.
- Mi cuenta usa un endpoint fijo del usuario autenticado. El BFF comprueba origen, cuerpo acotado, un único archivo, extensión, MIME, firma y 2 MB. El servidor upstream confirma el guardado y devuelve 204 al eliminar. La lectura transmite JPEG/PNG con `private, no-store`, `Vary: Cookie`, `nosniff` y `Cross-Origin-Resource-Policy: same-origin`. El cliente revoca la URL temporal, actualiza el contexto compartido tras el éxito, cambia la versión de la URL al reemplazar y muestra iniciales si falla la carga. Las suites confirmaron rechazo de WebP, contenido incoherente y sobrepeso, 404 sin imagen, 401 expirado, error upstream, reemplazo y vuelta a iniciales. El almacenamiento y la decodificación completa se delegan al backend.

## Hallazgos y cambios propuestos

| Prioridad | Estado | Hallazgo y propuesta |
|---|---|---|
| Alta antes de escalar | Riesgo identificado en código | Web Locks coordina refresh entre pestañas del mismo perfil y origen, y `rotation.ts` agrupa solicitudes solapadas dentro de un proceso Next. Logout queda fuera de ese bloqueo y una respuesta tardía de refresh puede volver a escribir cookies después del borrado. Dos réplicas tampoco comparten el mapa. Coordinar refresh/logout, invalidar el estado de otras pestañas y acordar con backend una política de rotación y revocación resistente a carreras. Confirmar orden de respuestas, reutilización y expiración en pruebas con tokens distintos. |
| Media | Riesgo identificado en código | Una pestaña puede retener datos o identidad antiguos tras logout o cambio de cuenta en otra pestaña. El servidor decide según las cookies actuales. Revalidar al recuperar el foco o recibir un evento de sesión y cerrar formularios si cambia la identidad. Probar también historial y restauración de páginas privadas. |
| Media | Comprobado en fuente | Login usa `request.json()` sin límite local específico. Administración comprueba Content-Length y luego lee el cuerpo entero antes de validar longitud de texto. Sin longitud declarada, esa segunda comprobación llega después de reservar memoria. Adoptar lectura acotada por bytes, como en Mi cuenta, y fijar límites y rate limiting en el proxy para login, refresh y escrituras. |
| Media | Comprobado en respuesta local | La página autenticada respondió `private, no-cache, no-store, max-age=0, must-revalidate`, `nosniff`, `X-Frame-Options: DENY` y `Referrer-Policy: strict-origin-when-cross-origin`. No emitió CSP, HSTS ni Permissions-Policy. En staging probar CSP primero en Report-Only y luego aplicar una política compatible con scripts de Next, estilos y la vista previa blob. Habilitar HSTS en el terminador HTTPS y definir Permissions-Policy según las funciones realmente usadas. |
| Media | Limitación funcional comprobada en código | La actualización de foto llega inmediatamente a Mi cuenta y topbar en la misma instancia de interfaz. Otros navegadores o pestañas pueden mostrar la versión anterior hasta revalidar. Un 401 de imagen cae a iniciales porque el elemento img no ejecuta el coordinador de sesión. Revalidar perfil al recuperar foco o después de renovar. Limitar reintentos para 404 y fallos. |
| Media de desarrollo | Comprobado con npm audit | Ocho entradas moderadas en el árbol completo por una alerta de minimatch y su cadena ESLint/TypeScript. El árbol de producción reportó cero avisos en esa consulta. Revisar actualizaciones compatibles de la cadena, revisar el lockfile y repetir auditoría, lint y build. La reparación automática sugería cambiar `eslint-config-next` de versión mayor. [Aviso GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249) |
| UX | Reproducido en navegador | El contenedor de la tabla desplaza horizontalmente a varios anchos de escritorio. El sidebar, el panel de detalle de 320 px, el espacio entre columnas y los mínimos de la tabla compiten por el ancho disponible. Llevar el detalle debajo o a un panel lateral cuando la tabla no quepa, usar un breakpoint del contenedor y compactar columnas secundarias sin esconder acciones. Mantener desplazamiento accesible cuando sea inevitable. |

Medición de Usuarios con el build actual y datos aislados:

| Ventana | Ancho disponible | Ancho del contenido | Desborde de página |
|---:|---:|---:|---|
| 1280 px | 628 px | 1024 px | 0 px |
| 1366 px | 714 px | 1024 px | 0 px |
| 1440 px | 788 px | 1024 px | 0 px |
| 1920 px | 1268 px | 1268 px | 0 px |

El resultado de 1920 px corresponde a los datos aislados. Conviene repetir con nombres y correos largos, sidebar colapsado, zoom del navegador y panel de detalle abierto.

## Sesiones simultáneas

En dos almacenes de cookies HTTP independientes se hicieron dos logins simultáneos y se confirmó que el logout responde con las dos instrucciones de borrado para su cliente. Tras omitir esas cookies, la consulta devolvió 401. El otro almacén siguió obteniendo 200. La fixture devuelve tokens fijos y no modela revocación o rotación real, por lo que esta prueba acredita separación del lado cliente y los encabezados del BFF. Queda pendiente una prueba con dos perfiles de navegador y backend de staging. Debe cubrir misma cuenta con dos familias, dos cuentas distintas, expiración y rotación simultáneas, logout de un dispositivo, cambio de cuenta en pestañas del mismo perfil y propagación de foto. Si el backend admite varias instancias, incluir dos réplicas Next.

## Plan para fotografías reales en Usuarios

1. Confirmar con el chat de backend si existe una lectura autorizada de imagen por usuario y si los DTO de listado y detalle incluyen el indicador de foto. El código auditado consume únicamente `GET /api/v1/users/me/avatar` para la propia sesión. Una llamada a esa ruta por cada fila devolvería la imagen del operador. Si el backend carece de lectura para terceros, mantener iniciales hasta que se defina y autorice ese contrato.
2. Tras confirmar el contrato, conservar un indicador opcional en los esquemas Zod de listado y detalle, compatible con registros antiguos. Si existe una revisión de imagen en el contrato, usarla para cambiar la URL del navegador tras un reemplazo. Excluir rutas del disco y claves de almacenamiento del DTO público.
3. Integrar una lectura específica en el BFF, con UUID validado, autenticación, permiso de lectura y autorización del usuario objetivo en el backend. Devolver solo JPEG/PNG, con respuesta privada y sin caché compartida. Evitar optimización de imágenes en un intermediario público.
4. Compartir un componente para fila y detalle, con carga diferida solo para filas que indiquen foto y regreso a iniciales ante ausencia o error. Probar usuarios con y sin foto, 401/403/404, un UUID fuera del alcance, reemplazo y eliminación, paginación y tabla a 1280–1920 px. Si se decide mostrar primero solo la foto propia en su fila, el endpoint actual es suficiente siempre que se compare con el ID autenticado y se conserve el contexto compartido.

## Dependencias del backend y producción

- Confirmar en staging verificación y caducidad de tokens, familias de renovación por dispositivo, revocación efectiva del refresh y alcance del logout. Confirmar el tiempo de validez residual del token de acceso. El frontend por sí solo no puede revocar credenciales emitidas.
- Confirmar que el backend valida permisos en cada operación incluso si se omite el BFF, y que la lectura de fotos verifica al propietario o el alcance del operador. Verificar límites de dimensiones, decodificación, almacenamiento privado y persistencia tras reemplazo y borrado.
- Publicar por HTTPS con `APP_ORIGIN` fijo y una URL de backend confiable. Mantener el backend fuera del acceso público cuando la arquitectura lo permita. Verificar que el proxy conserve Origin y Fetch Metadata y que no añada CORS permisivo ni almacene HTML autenticado, RSC o imágenes privadas.
- Registrar rechazos y fallos de sesión con identificadores de correlación, excluyendo cookies, JWT, contraseñas, archivos y datos personales innecesarios. Auditar dependencias con el lockfile en CI e instalar solo el árbol de producción en el entorno de ejecución.

## Pruebas ejecutadas

| Verificación | Resultado |
|---|---:|
| npm test | 61/61 |
| npm run test:http | 16/16, contra fixture local |
| npm run test:account | 23/23, contra fixture local |
| npm run test:users | 30/30, contra fixture local |
| npm run test:branches | 45/45, contra fixture local |
| Sondas HTTP adicionales | Cookies de producción, CORS, CSRF, logout y cabeceras verificados |
| Navegador integrado | Login de prueba, acceso a Usuarios y Sucursales, medición de tabla en cuatro anchos |
| npm run typecheck, npm run lint, npm run build | Correctos |
| npm audit y npm audit --omit=dev | 8 entradas moderadas en árbol completo y 0 en producción |

Las 175 comprobaciones automatizadas incluyen acceso anónimo, login inválido, rechazo de cookie inventada, permisos, rutas restringidas y regresión de Usuarios y Sucursales. Las pruebas de dashboard cubren filtros y acceso anónimo. La navegación autenticada del login pasó por el dashboard al llegar a Usuarios. Todas las operaciones de prueba con escritura usaron memoria aislada. No se verificaron Spring Boot, PostgreSQL, credenciales reales ni cabeceras del proxy desplegado.

