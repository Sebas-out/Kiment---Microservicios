# KIMENTS POS · Preparación 5J-B

## Auditoría inicial

El proyecto usa Next.js 16.4.3, React y React DOM 19.3.1 según el lockfile. La máquina de pruebas tenía Node 24.11.0 y npm 11.6.1. Next exige Node 20.9 o posterior. El Dockerfile usa Node 24 en Debian slim y conserva los comandos existentes `npm run build` y `next start`, con la salida `.next-build`. El lockfile incluye los paquetes nativos de Linux x64. El proyecto no tenía configuración Docker al empezar.

Las rutas de login, refresh, logout, perfil, password, gestión y avatares pasan por el BFF. Dashboard y las demás páginas privadas verifican sesión en el servidor. Los bearer tokens se añaden en el servidor, las redirecciones del upstream están deshabilitadas y las respuestas privadas evitan caché compartida. El BFF valida Origin, Fetch Metadata y los permisos de gestión. En producción las cookies son HttpOnly, Secure, SameSite Strict, host-only y Path=/, con prefijo __Host-. El proxy preserva el origen público, el esquema HTTPS y las cabeceras del navegador. Los selectores visuales y permisos sin tachado siguen en los estilos originales.

Variables encontradas en el código: AUTH_API_URL para el BFF, APP_ORIGIN para CSRF, NODE_ENV para las cookies y el directorio de build, y los interruptores KIMENTS_CSP_REPORT_ONLY y KIMENTS_ENABLE_HSTS en Next. Las opciones de cabeceras se resuelven al construir. El Dockerfile solo expone el interruptor CSP de observación. HSTS debe revisarse después de confirmar HTTPS.

El Compose de Antigravity crea una red bridge nombrada `kiments-internal`, con `auth-service:8080` y `postgres:5432` sin publicar esos puertos. El backend espera PostgreSQL saludable antes de arrancar. Se revisaron Dockerfile, Compose, filtros de contexto, ejemplo de variables, ejemplos Nginx/Caddy, documentación y scripts de claves y respaldos. Los ejemplos de proxy del backend incluyen exposición directa de /api/v1 y uno activa HSTS de entrada. Esta propuesta pública bloquea /api/v1 y /actuator. Comprueba los permisos de los archivos de claves montados con el UID del backend antes de arrancar. No se necesita entregar ninguna clave de Spring o PostgreSQL al frontend.

## Decisión de transporte

El código de producción rechaza HTTP salvo en loopback. Por tanto, `AUTH_API_URL=http://auth-service:8080` no funciona con el código actual. Se usa el Caddy previsto para HTTPS público también como pasarela TLS interna. El navegador entra por HTTPS → Caddy → Next/BFF. El BFF conecta a `https://auth-gateway:8443`, con una CA interna de Caddy montada en modo lectura. Caddy reenvía al servicio Spring `auth-service:8080` en la misma bridge. El tramo Caddy → Spring usa HTTP dentro de la red y no se publica. La pasarela solo admite rutas del API y el health del backend. Esta configuración mantiene la verificación TLS del BFF y evita editar código existente. No configures NODE_TLS_REJECT_UNAUTHORIZED=0.

El Dockerfile construye en Linux con npm ci, instala solo dependencias de producción en una etapa separada y copia el build y recursos públicos. La ejecución usa usuario node, inicio directo del proceso Node e init de Compose para señales. El contexto usa una lista de archivos permitidos y excluye las carpetas de salida de Windows y los secretos. Standalone requeriría modificar next.config.ts y pedir autorización. Esta entrega utiliza el servidor estándar soportado por Next. [Documentación de despliegue de Next.js](https://nextjs.org/docs/app/getting-started/deploying)

## Variables

| Variable | Uso |
|---|---|
| APP_ORIGIN | Origen exacto del sitio, como https://pos.example.com. Usa tu dominio real y omite la barra final. |
| KIMENTS_PUBLIC_HOST | Nombre DNS del mismo sitio, sin esquema ni puerto. El dominio de ejemplo es ilustrativo. |
| AUTH_API_URL | Dirección del BFF, por defecto https://auth-gateway:8443. |
| KIMENTS_AUTH_CA_FILE | Archivo público de la CA interna. Por defecto deploy/trust/auth-root.crt, relativo al archivo Compose. |
| NODE_EXTRA_CA_CERTS | Ruta interna fijada por Compose. Node carga la CA al iniciar. |
| FRONTEND_IMAGE | Etiqueta de release. Usa una etiqueta única por build y conserva la anterior. |
| CADDY_IMAGE | Imagen de proxy. El ejemplo usa caddy:2-alpine. Registra la versión o digest que se apruebe. |
| KIMENTS_CSP_REPORT_ONLY | Valor 1 para observar CSP en un build de pruebas. |
| KIMENTS_ENABLE_HSTS | Interruptor existente de Next. Se deja desactivado en este Dockerfile. |

## Arranque propuesto en Ubuntu 24.04 x64

Usa el Compose del backend con sus variables privadas y desde su ruta habitual. Confirma sus backups y salud antes de avanzar. El Compose del frontend referencia como externa la red del backend y no crea otra red, base de datos ni volúmenes de PostgreSQL.

Desde el directorio del frontend, copia el ejemplo a `deploy/.env.prod` si todavía no existe y configura los dos valores de dominio. Comprueba DNS público hacia la VM, disponibilidad de 80/443 y reglas de entrada de la VM. Si ya existe un reverse proxy en esos puertos, integra allí el Caddyfile en vez de ejecutar dos proxies. La red compartida debe existir antes del arranque.

```sh
docker network inspect kiments-internal --format '{{.Name}}'
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml config --quiet
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml build frontend
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml up -d proxy
mkdir -p deploy/trust
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml cp proxy:/data/caddy/pki/authorities/local/root.crt deploy/trust/auth-root.crt
chmod 644 deploy/trust/auth-root.crt
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml up -d --no-build frontend
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml ps
```

Si el archivo CA aún no existe, revisa el arranque del proxy antes de copiar. Copia únicamente root.crt. Conserva los volúmenes de Caddy para evitar rotar su CA y los certificados sin necesidad. Si cambia la CA, sustituye la copia de confianza y recrea Next para que Node la cargue. La montura exige que exista el archivo.

El healthcheck de Next prueba la página inicial en loopback. Para readiness de integración, verifica el upstream desde el contenedor y exige HTTP 200 y status UP. Un frontend sano por sí solo no acredita que el BFF alcance a Spring.

```sh
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml exec -T frontend node -e 'fetch(process.env.AUTH_API_URL+"/actuator/health",{signal:AbortSignal.timeout(5000)}).then(async r=>{if(!r.ok||(await r.json()).status!=="UP")process.exitCode=1;else console.log("SVC-09 UP por TLS interno")}).catch(()=>{console.error("Fallo DNS, TLS o upstream");process.exitCode=1})'
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml port frontend 3000
docker stats --no-stream
```

El comando port debe indicar que 3000 no está publicado. Confirma también que Spring y PostgreSQL carecen de puertos publicados. Verifica HTTPS y el certificado público, redirección HTTP→HTTPS, 404 en /api/v1 y /actuator, y que el proxy preserve Cache-Control privado. No habilites caché de respuestas BFF. Con una cuenta de pruebas autorizada, revisa las cookies por sus atributos sin copiar valores y comprueba login, perfil, refresh, logout, sesión única, permisos, Usuarios, Sucursales y fotos. Confirma que las vistas previas blob se muestran si se agrega una CSP. Revisa limitación de login en el proxy si se requiere una política por IP pública, ya que Spring observa el salto interno.

Frontend propone 1 CPU y 1536 MiB, reinicio unless-stopped y logs de 10 MiB × 3. El período de parada de 30 s permite SIGTERM. Usa docker stats y los logs en la VM para ajustar según carga. Son límites propuestos, sin medición de consumo. Solo Caddy publica 80 y 443.

## Actualización y rollback

1. Conserva la etiqueta y el ID de la imagen previa. Asigna a FRONTEND_IMAGE una etiqueta nueva y construye el frontend.
2. Aplica solo frontend con `docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml up -d --no-deps --no-build frontend`. Repite salud, conectividad TLS y pruebas de sesión.
3. Para rollback, vuelve a fijar la etiqueta anterior y ejecuta el mismo up sin build. Conserva el proxy, su CA y los servicios del backend. El procedimiento revierte la imagen del frontend y deja fuera migraciones y volúmenes.

## Estado de comprobación

El cliente Docker y Compose están instalados. El motor Linux no respondió por su pipe y el intento de arrancar Docker Desktop falló al localizar el lanzador en el registro. docker build además devolvió acceso denegado al lock local de Buildx. El Compose pasó config --quiet. Quedan pendientes la construcción Linux, el arranque del contenedor, la validación runtime de Caddy, la inspección de la imagen, el healthcheck y la conexión real vía TLS interno. Antes de usar en producción se deben confirmar también HTTPS público, cookies, autenticación y persistencia. Esta etapa no ha accedido a Azure.

Archivos de despliegue nuevos: deploy/Dockerfile, deploy/Dockerfile.dockerignore, deploy/docker-compose.prod.yml, deploy/.env.prod.example, deploy/Caddyfile.example y este README. No se editaron archivos existentes del frontend ni del backend.


### Resultados locales

| Comprobación | Resultado |
|---|---:|
| npm test | 64/64 |
| npm run test:http | 16/16 sobre localhost:3000 |
| npm run test:account | 33/33 con backend en memoria |
| npm run test:users | 30/30 con backend en memoria |
| npm run test:branches | 45/45 con backend en memoria |
| TypeScript, ESLint y npm run build | Correctos |
| npm run dev existente | HTTP 200 con recursos de desarrollo |
| docker compose config | Correcto, con comprobación del upstream TLS, red externa y puertos publicados |

El build de Windows pasó con UV_THREADPOOL_SIZE=1 en el proceso de compilación y una salida generada nueva. No se cambiaron variables globales de Windows. Las 188 comprobaciones cubren regresiones de aplicación y HTTP local. La validación del Compose es estática y queda separada de la prueba de contenedor.
