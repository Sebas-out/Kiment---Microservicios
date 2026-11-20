# KIMENTS POS · Etapa 5I-B

5 de noviembre de 2026. Proyecto `C:\Users\Bryan\Downloads\kiments-frontend`.

La implementación frontend de 5I-B está terminada. La comprobación contra el servicio real queda pendiente. El informe de 5I-A indica que V7 ya fue aplicada en `kiments_db`. Las sondas realizadas durante esta ejecución en `localhost:8080` y `127.0.0.1:8080` fallaron al conectar. Los contratos se comprobaron por lectura del código de Spring Boot.

## Sesiones y seguridad

- Login, refresh, logout y cambio de contraseña usan el mismo bloqueo por origen del navegador. Se espera la respuesta de cada mutación de cookies antes de soltar el bloqueo. Las renovaciones en cola consultan nuevamente `/api/auth/me` para aprovechar una renovación exitosa previa. Una respuesta 401 de refresh ya no envía instrucciones de borrado. El cliente ejecuta la limpieza coordinada tras un rechazo definitivo y hace un único intento de renovación.
- Una marca aleatoria en localStorage señala cambios de sesión. No contiene JWT, credenciales ni identidad. Las operaciones pendientes contrastan la marca antes de renovar y al recibir el resultado. Las otras pestañas ocultan el panel anterior y navegan al servidor para obtener la sesión vigente. La interfaz verifica la identidad al volver al foco o a la pestaña y periódicamente mientras está visible.
- HTTP 403 conserva su significado de acceso denegado y no provoca refresh. V7 valida la familia activa y devuelve 401 genérico ante credenciales expiradas, revocadas o desplazadas. El aviso mostrado es «Tu sesión finalizó. Inicia sesión nuevamente». Se reserva «Tu sesión se cerró porque se inició sesión en otro dispositivo» para una señal inequívoca futura del backend. Un parámetro de URL o un 401 genérico no acreditan esa causa.
- Web Locks ordena las mutaciones de esta aplicación dentro del mismo perfil y origen, incluso si un balanceador las dirige a réplicas diferentes. El mapa de deduplicación del BFF es local a cada proceso. Entre dispositivos, clientes directos o réplicas independientes, la seguridad de la familia depende de V7. Queda por probar la rotación y la revocación en el despliegue real. La coordinación requiere un navegador con Web Locks en un contexto seguro.
- Login limita el cuerpo a 4096 bytes. Operaciones administrativas JSON limitan a 16384 bytes. Se aplican al flujo real, incluso sin Content-Length. Se conservan Origin, Fetch Metadata, CSRF y cookies host-only, HttpOnly, SameSite Strict y Secure en producción.
- `KIMENTS_CSP_REPORT_ONLY=1` genera la política de observación en el build. Puede revelar infracciones de scripts inline de Next, lo que debe revisarse antes de convertirla en CSP obligatoria. `KIMENTS_ENABLE_HSTS=1` añade HSTS cuando el despliegue usa HTTPS estable. Se añadió Permissions-Policy para cámara, micrófono y geolocalización. Se verificaron CSP Report-Only y Permissions-Policy en la respuesta aislada.
- npm audit reportó ocho entradas moderadas en desarrollo por la cadena ESLint/minimatch y cero en el árbol de producción. La solución automática propuesta implicaba un cambio mayor de eslint-config-next. Se mantuvieron las versiones instaladas.

## Usuarios

La tabla y el detalle comparten el componente de avatar. El BFF sirve `GET /api/management/users/{id}/avatar`, valida UUID, sesión y ADMIN con USER_READ, y delega al contrato de Spring Boot `GET /api/v1/users/{id}/avatar`. Transmite JPEG o PNG con respuesta privada, sin caché y de mismo origen. Usa el indicador `hasProfileImage`, muestra iniciales cuando falta la foto y conserva el manejo de foto propia en Mi cuenta. Esta etapa integra la lectura administrativa. El DELETE de terceros del backend queda fuera del alcance funcional pedido.

Se mantienen filtros, columnas, detalle y acciones. El panel pasa debajo de la tabla cuando el ancho de escritorio es menor. Se ajustaron los anchos de columnas, el texto y la distribución de acciones.

| Ventana | Tabla disponible | Contenido |
|---:|---:|---:|
| 390 px | 343 px | 700 px, desplazamiento interno |
| 1280 px | 957 px | 957 px |
| 1366 px | 1043 px | 1043 px |
| 1440 px | 1117 px | 1117 px |
| 1920 px | 1257 px | 1257 px |

La página no se ensanchó horizontalmente en los cinco casos. La medición usó dos filas del entorno aislado con el detalle abierto.

## Archivos

- Sesión: `src/features/auth/session-client.ts`, `src/features/auth/client.ts`, `src/features/auth/components/session-guard.tsx`, `src/features/auth/components/login-session-notice.tsx`, `src/app/(admin)/layout.tsx`, `src/app/api/auth/refresh/route.ts`, `src/features/users/client.ts`, `src/features/account/client.ts`.
- BFF y cabeceras: `src/app/api/auth/login/route.ts`, `src/app/api/management/[...path]/route.ts`, `src/app/api/management/users/[id]/avatar/route.ts`, `src/app/api/account/avatar/route.ts`, `src/features/account/server.ts`, `next.config.ts`.
- Usuarios: `src/features/users/contracts.ts`, `src/features/users/components/worker-avatar.tsx`, `src/features/users/components/users.tsx`, `src/features/users/components/user-detail.tsx`, `src/features/users/users.css`.
- Pruebas: `tests/auth-client.test.mjs`, `tests/session-client.test.mjs`, `tests/management-fixture.mjs`, `tests/account-integration.mjs`.

El directorio no contiene un repositorio Git detectable. Se ejecutaron git status y git diff, y se revisaron directamente los archivos guardados. Ante errores EPERM de Windows, se conservaron las salidas generadas anteriores en directorios `.next-build-5ib-backup-*` del mismo proyecto y se compiló en una carpeta de salida nueva.

## Resultados

| Prueba | Resultado |
|---|---:|
| npm test | 64/64 |
| npm run test:http | 16/16 |
| npm run test:account | 33/33 |
| npm run test:users | 30/30 |
| npm run test:branches | 45/45 |
| TypeScript, ESLint y build de producción | Correctos |

En memoria se verificaron orden refresh/logout, coordinación de renovaciones, rechazo de operaciones antiguas, 401 terminal genérico sin borrado tardío, 403, atributos de cookies de producción, cuerpos por bytes sin Content-Length, CSRF, lectura de foto, bandera de foto en listado y detalle y 404 para otro usuario sin imagen. Se cubrieron regresiones de login, Dashboard, Usuarios, Sucursales y Mi cuenta con las suites existentes.

En dos pestañas de un mismo perfil se verificó que cerrar sesión en una lleva a ambas al login. Se inició sesión con una segunda cuenta de prueba y la otra pestaña dejó de mostrar el perfil anterior. Se confirmó visualmente la imagen y las iniciales en la tabla. El detalle usa el mismo componente y se comprobó el indicador por HTTP. La sesión única entre dos navegadores independientes y la entrega de fotografías desde el almacenamiento real siguen pendientes. Las operaciones de prueba usaron únicamente datos aislados.

## Veredicto

Frontend 5I-B implementado y aprobado en pruebas aisladas. La validación integral se cierra cuando el servicio real sea accesible y se confirme la lectura autorizada de fotografías y la revocación de la sesión anterior entre dos perfiles de navegador con una cuenta de prueba autorizada. No se ejecutaron migraciones, cambios del backend, escrituras en cuentas reales ni despliegues.

