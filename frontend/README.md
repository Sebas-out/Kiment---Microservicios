# KIMENTS POS — Frontend · Etapa 4A

Proyecto existente en `C:\Users\Bryan\Downloads\kiments-frontend`. Next.js 16.4.3, App Router, TypeScript estricto y Tailwind. El programa anterior en `kiments-front/Kiments-Ecommerce` se conserva aparte.

## Ejecutar

Con Node 24 y dependencias instaladas:

```powershell
cd C:\Users\Bryan\Downloads\kiments-frontend
npm run dev
```

Abrir http://localhost:3000. Para verificar producción: `npm run build`, luego `npm start`. Se mantiene Webpack y la configuración Windows del proyecto. `.env.local` utiliza:

```dotenv
AUTH_API_URL=http://localhost:8080
APP_ORIGIN=http://localhost:3000
```

Ambas variables son privadas. El backend recibe llamadas únicamente desde Next.js. En producción se requiere HTTPS hacia el servicio, salvo loopback para la prueba local de `npm start`; configurar APP_ORIGIN con el origen público exacto y servir el frontend por HTTPS.

Backend independiente: `C:\Users\Bryan\Downloads\kiments-auth-service`. Desde una terminal con su JDK 21 y sus variables de PostgreSQL/RSA **ya configuradas**, ejecutar ` .\mvnw.cmd spring-boot:run` o el JAR existente. Consultar su README para las variables. Mantener desactivados los flags de bootstrap; esta etapa no crea cuentas, modifica el backend ni administra PostgreSQL.

## Alcance

- Login, fotografía/carrusel y notificaciones existentes conservados.
- Layout privado compartido: sidebar marino de 260 px, topbar de 66 px, búsqueda sincronizada (Ctrl/Cmd+K), grupos plegables, contracción de escritorio, navegación móvil con foco controlado, tema y perfil.
- `/dashboard`: resumen vacío, sin métricas o gráficos inventados.
- `/usuarios`: estructura inicial; exige usuario activo, rol ADMIN y permiso real USER_READ. Todavía no hay tabla/CRUD ni consumo de endpoints de usuarios.
- Otros módulos autorizados por el alcance aparecen deshabilitados con etiqueta Pronto. No hay rutas falsas. No se incluyeron pedidos, GRE, conductores, CRM, asistencias, turnos ni ecommerce.
- La configuración tipada de navegación está en `src/features/navigation/config.ts`. Los permisos fueron contrastados con V2__roles_and_permissions.sql; los historiales aún no tienen un permiso de lectura específico y permanecen deshabilitados. Sucursales/configuración pendientes no implican una autorización nueva del backend.
- Montserrat se sirve localmente solo en el panel; licencia en `public/fonts/Montserrat-OFL.txt`. El login mantiene su tipografía. Las dos fotos reemplazables siguen en `public/images/`.

## Contrato real de autenticación

Verificado leyendo AuthenticationDtos, UserDtos, AuthController, AuthenticationServiceImpl, UserController y las migraciones del backend local; no se asumieron los DTO provisionales anteriores.

| BFF del navegador | Spring Boot |
| --- | --- |
| POST /api/auth/login | POST /api/v1/auth/login |
| GET /api/auth/me | GET /api/v1/auth/me |
| POST /api/auth/refresh | POST /api/v1/auth/refresh |
| POST /api/auth/logout | POST /api/v1/auth/logout |

Login envía `email` y `password` (máximo 128 caracteres). Responde `accessToken`, `tokenType: Bearer`, `expiresIn` y `refreshToken`. `/me` devuelve `{ user, permissions }`; user incluye id, firstName, lastName, email, role, enabled y branchId, entre otros campos que el BFF descarta si no los necesita. Los roles son ADMIN, VENTAS, ALMACEN y VENTAS_ALMACEN.

Refresh y logout envían `{ refreshToken }`. Logout devuelve 204. No existe `refreshExpiresIn` en la respuesta: no se inventó ese campo. La cookie de refresh dura la sesión del navegador y Spring Boot valida el vencimiento absoluto de su familia (configuración actual: 7 días). La rotación no extiende ese plazo.

## Sesión, autorización y límites

- Tokens exclusivamente en cookies HttpOnly, SameSite=Strict y Path=/. En producción llevan Secure y prefijo __Host-. En desarrollo HTTP se usan nombres sin prefijo. No se guardan tokens o contraseñas en almacenamiento web.
- El BFF valida origen exacto, Sec-Fetch-Site y application/json en POST. No devuelve tokens en JSON. Respuestas de autenticación no-store. No se registra el cuerpo de credenciales o tokens.
- Después del login, se verifica `/me` antes de establecer la sesión. La identidad del menú procede del perfil real; no hay un usuario de demostración en las rutas de la aplicación.
- Layout **y cada página privada** verifican la sesión; no se depende solo de un layout que puede persistir durante la navegación. Las consultas de perfil se deduplican por petición con React cache, no entre usuarios.
- Ante 401: si existe cookie de refresh, se ofrece `/sesion` y el botón Continuar sesión; si no, se vuelve al login con aviso de expiración. No se rota desde un GET ni automáticamente desde un Server Component.
- La renovación se coordina con Web Locks entre pestañas: después del bloqueo se vuelve a consultar `/me` para evitar una segunda rotación. Sin Web Locks se pide iniciar sesión nuevamente. Next.js también reúne solicitudes solapadas de un mismo token dentro del proceso. Para varias instancias hace falta un bloqueo compartido; no declarar esa configuración lista para producción sin resolverlo.
- Un 403 se conserva como acceso denegado y no dispara refresh. Un fallo de servicio conserva las cookies y muestra un estado recuperable; no se suplanta al usuario ni se considera una sesión válida.
- Logout verifica CSRF, intenta revocar la familia y elimina las cookies incluso si el servicio falla. En ese caso informa que el cierre fue local y la revocación remota no se confirmó. Según el backend actual, un access JWT ya emitido puede seguir vigente hasta su expiración; no se prometió una revocación de access inmediata.
- Usuarios usa ADMIN + USER_READ, igual que el controlador real. La autorización de futuras operaciones REST seguirá siendo del backend.
- branchId es nullable y la autorización por sucursal aún no está implementada en el servicio. No se creó ni se mostró una sucursal ficticia GLOBAL ni se simuló aislamiento por sucursal.

## Verificaciones

```powershell
npm test
npm run typecheck
npm run lint
npm run build
# Con el frontend iniciado en localhost:3000:
npm run test:http
```

23 pruebas aisladas de cliente/contratos/permisos/sesión y 16 comprobaciones HTTP sin credenciales. Las fixtures solo viven en pruebas; no crean cuentas o sesiones reales ni habilitan acceso al panel. La auditoría inicial de herramientas ya registraba 16 vulnerabilidades moderadas de desarrollo; no se ejecutó npm audit fix --force.

El servicio no respondió en localhost:8080 durante esta implementación. El login correcto con el ADMIN real, `/me` real, rotación remota, logout remoto y navegación autenticada deben verificarse cuando el operador lo inicie e ingrese con su cuenta. No se han declarado esas pruebas como ejecutadas.

La revisión visual usó una vista estática aislada de los componentes, fuera de las rutas de la aplicación, marcada sin sesión autenticada. Sirve para comparar dimensiones, tipografía y responsive CSS; no demuestra autenticación ni prueba las interacciones hidratadas del panel. Se conservan esas limitaciones en el informe.

## Próximas etapas

4B: indicadores y gráficos con datos reales. 4C: listado y CRUD de usuarios, roles/permisos y endpoints reales. Ninguna se implementó en esta entrega.

## Revisión real de 4A

Se verificó una sesión real abierta por el operador: dashboard, acceso ADMIN a Usuarios y /api/auth/me con identidad, rol y permisos del servicio. Se ejecutó logout y se comprobó después el rechazo de Usuarios y la ausencia de sesión en /me. La denegación de otros roles está cubierta por pruebas aisladas; no se iniciaron sesiones adicionales con esos roles ni se crearon cuentas.

Las salidas generadas se separaron: `.next-dev` para desarrollo y `.next-build` para producción, evitando compartir la caché entre el servidor de desarrollo y el build. En Windows se regeneraron carpetas de salida tras errores EPERM. Compilación final completa, 23 pruebas de cliente/contratos/sesión y 16 comprobaciones HTTP correctas. No se modificaron Spring Boot ni PostgreSQL.

## Dashboard · Etapa 4B

El dashboard incorpora cuatro indicadores, filtros de fecha locales, tendencia, pagos, productos destacados, comprobantes y stock crítico. Recharts prepara los gráficos; no existen consultas comerciales reales ni cifras de demostración. El selector de sucursal permanece deshabilitado. La implementación está en `src/features/dashboard/`, con componentes, tipos de presentación, filtros y adaptador separados. La ruta conserva requireSession de 4A.

El adaptador devuelve estado unavailable y cantidades null; los componentes muestran Sin datos. No sustituir ese estado por ceros o datos ficticios. Al conectar futuras APIs, verificar contratos, aplicar permisos reales y mapearlos a los modelos de presentación. Los tipos de comprobantes se obtendrán de esa fuente, sin catálogo inventado.

Verificación 4B: 28 pruebas automatizadas, TypeScript, ESLint, build y 16 comprobaciones HTTP correctas. Dashboard probado con sesión real, preset de fechas, navegación a Usuarios y responsive móvil. No se avanzó a 4C.


## Etapa 4C — Usuarios

El módulo `/usuarios` consume SVC-09 mediante `/api/management/[...path]`, con sesión HttpOnly, verificación de ADMIN y permiso específico en el BFF y autorización definitiva de Spring Boot. Se reutilizan el transporte del servidor y la coordinación de renovación de sesión existentes; no hay JWT en localStorage.

Contratos comprobados en `UserDtos`, `UserController`, `RoleService` y `UserServiceImpl`: nombre, apellido, DNI de ocho dígitos, teléfono opcional, correo, contraseña inicial de 12–128 caracteres no vacía, rol y estado. No hay asignación de sucursales en los DTO de escritura. La búsqueda de nombre es parcial; correo es exacto. Filtros y paginación se envían al servidor (10 filas por página).

Incluye listado, detalle, creación, edición de datos personales, consulta de roles/permisos, cambio de rol y activación/desactivación confirmada. No hay eliminación ni restablecimiento de contraseñas de terceros. El propio rol no puede cambiarse; la protección del último ADMIN permanece en el backend.

Verificación:

- `npm test`: pruebas unitarias, incluidas validación, permisos, errores y renovación sin repetir escrituras.
- `npm run lint` y `npm run typecheck`.
- `npm run build`.
- `npm run test:http`: requiere el frontend en localhost:3000; comprobaciones anónimas de regresión.
- `npm run test:users`: requiere un build previo. Levanta otro Next en puerto efímero y un backend en memoria aislado. No usa Spring Boot ni PostgreSQL; comprueba las operaciones del BFF, restricciones y errores. Cierra ambos procesos al terminar.

Las lecturas del módulo se verificaron con el backend real. Las escrituras se verificaron únicamente con mocks controlados, respetando la prohibición de crear cuentas o modificar usuarios reales durante pruebas. La persistencia real de esas operaciones requiere una cuenta expresamente autorizada o una base aislada.


## Etapas 5B + 5C — Sucursales y Usuarios con asignación

Esta etapa sustituye la limitación sobre sucursales descrita en la entrega 4C. Se verificaron los contratos 5A del backend mediante lectura de BranchDtos, BranchController y UserDtos/UserServiceImpl.

- `/sucursales`: ADMIN + BRANCH_READ, búsqueda y estado paginados por servidor, tarjetas, creación, edición (código inmutable), activación/desactivación confirmada. Personal consultado mediante GET users con branchId y size=4; el total proviene de la respuesta, nunca de estimaciones.
- Campos soportados: code, name, address, phone y enabled. El cambio de enabled usa su operación independiente. Ciudad, correo, ubigeo, establecimiento SUNAT, tipo y empresa son campos deshabilitados pendientes de V5 y no forman parte de los payloads.
- Usuarios conserva sus endpoints y sesión: tabla compacta, avatares, badges por rol, columna Sucursal, detalle lateral y selector visual de roles. Los permisos y las diferencias entre roles se calculan exclusivamente con respuestas de roles/{id}/permissions.
- Crear una cuenta operativa exige branchId. ADMIN envía null. El selector de sucursales activas recorre todas las páginas, incluso más de 100 registros, y falla de forma explícita si la consulta queda incompleta.
- Cambiar rol puede enviar branchId en la misma operación. Reasignar sucursal usa PATCH users/{id}/branch y USER_UPDATE. No hay eliminación física ni sucursal ficticia GLOBAL.
- Se reutilizan usersRequest, el BFF management, cookies HttpOnly, restoreSession, Sonner, React Hook Form, Zod y Lucide. No hay dependencias nuevas ni cambios de autenticación/JWT/backend.

Pruebas: npm test (47), npm run test:users (30 comprobaciones BFF con mocks), npm run test:branches (28 comprobaciones de sucursales/asignación con mocks), npm run test:http (16), typecheck, lint y build.

`npm run test:fixture` inicia un backend exclusivamente en memoria y otra instancia Next en puertos efímeros; imprime ISOLATED_FIXTURE_UI. Es únicamente una herramienta de pruebas: la cuenta qa@example.invalid y su contraseña fixture-password son datos ficticios del stub, sin acceso a Spring Boot. Usa 127.0.0.1 para no compartir cookies con localhost. Ctrl+C cierra la simulación. No usar ese entorno como aplicación real.

En el backend real se comprobaron lecturas, permisos, la cuenta existente y cero sucursales. Las escrituras se probaron en memoria; no se crearon sucursales ni usuarios en PostgreSQL. La prueba de persistencia real sigue requiriendo datos autorizados o una base aislada.

## Etapa 5D — Integración Sucursales V5

Esta sección reemplaza la limitación de campos V5 indicada en 5B + 5C. Se comprobaron los DTO, controlador y servicio Spring Boot mediante lectura, sin modificarlos.

- Formularios y tarjetas habilitan city, ubigeo, email, branchType y sunatCode. Tipo obligatorio al crear: VENTA/ALMACEN/MIXTO. Un registro antiguo con null muestra Sin clasificar.
- Validación: ciudad hasta 100 caracteres, correo hasta 150 según la expresión del DTO, ubigeo de seis dígitos y SUNAT de cuatro; los códigos conservan ceros iniciales.
- Editar envía únicamente campos cambiados: omitido/null conserva; cadena vacía limpia los opcionales. Código inmutable y estado separado mediante /status.
- Listado transmite name, code, type, city, sunatCode, enabled, page y size mediante el BFF existente. Los conflictos SUNAT 409 muestran un mensaje seguro y comprensible.
- Empresa continúa deshabilitada. No se envía companyId ni RUC. No se añadieron dependencias, módulos ni cambios de autenticación.
- Usuarios conserva opciones de sucursales activas obtenidas de todas las páginas, asignación UUID para roles operativos, ADMIN global con branchId null y operaciones separadas de rol/sucursal.

Verificación del 4 de noviembre de 2026: npm test 57/57, test:users 30/30, test:branches 45/45 y test:http 16/16. TypeScript, ESLint y build completaron correctamente. Las pruebas de escritura y la revisión visual usaron únicamente el backend en memoria de test:fixture, sin tocar PostgreSQL. Se verificaron creación, precarga, refresco, edición parcial y selector por rol; modal móvil revisado a 390 px sin desbordamiento horizontal y consola sin errores/avisos observados.

El servicio localhost:8080 rechazó la conexión en esta ejecución. Quedan pendientes las lecturas autenticadas contra Spring Boot V5 (incluidas GAMARRA y la asignación existente), por lo que no se certifica esa validación real. La conexión de producción conserva los endpoints reales; el stub existe exclusivamente en tests. El dashboard conserva su estado Sin datos donde aún no hay APIs comerciales.

## Etapa 5G — Mi cuenta y fotografía de perfil

- `/mi-cuenta` usa la sesión existente y los datos del usuario autenticado de `/api/v1/auth/me`. Los datos ausentes se indican como no disponibles. ADMIN tiene alcance Global; para roles operativos se muestra que hay una sucursal asignada, ya que `/me` solo devuelve `branchId` y el detalle de sucursal exige permisos administrativos.
- `GET`, `POST` y `DELETE /api/account/avatar` se conectan únicamente a `/api/v1/users/me/avatar`. POST transmite un `multipart/form-data` con `file`. Se admiten JPG/PNG de hasta 2 MB; el BFF comprueba MIME, extensión, firma y tamaño y Spring Boot valida el contenido definitivo. GET entrega binario autenticado con `private, no-store`. POST y DELETE comprueban el origen.
- Mi cuenta y topbar comparten el estado de la foto. La subida confirmada genera una URL nueva; la eliminación confirmada devuelve ambos avatares a iniciales. La vista previa temporal se revoca. Las fotos no se guardan en el cliente.
- Seguridad usa `POST /api/account/password` → `/api/v1/auth/change-password`. La contraseña nueva debe tener entre 12 y 128 caracteres. Tras el 204 se limpian las cookies locales y se vuelve al login porque Spring Boot revoca las sesiones de renovación.

Validación: `npm test` 61/61, `npm run test:account` 23/23, `npm run test:users` 30/30, `npm run test:branches` 45/45, TypeScript, ESLint y build correctos. `test:account` requiere un build previo y usa un servidor en memoria en 127.0.0.1; nunca apunta a PostgreSQL. Revisión visual en escritorio y móvil con el mismo entorno aislado.

Para comprobar contra el entorno real después de que el responsable aplique V6: inicia Spring Boot y el frontend con la configuración local existente, entra con tu cuenta y navega a Configuración → Mi cuenta. Verifica la foto existente o las iniciales y su coincidencia con topbar. Solo con autorización explícita o en una base aislada, carga un JPG/PNG válido, recarga, reemplázalo y elimina la foto para verificar persistencia y sincronización. Esta etapa no aplica migraciones ni ejecuta escrituras reales.
