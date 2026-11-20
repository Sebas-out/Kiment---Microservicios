# KIMENTS POS · Validación 5I-C

5 de noviembre de 2026. Frontend en `C:\Users\Bryan\Downloads\kiments-frontend`.

**Veredicto:** conectividad y controles sin sesión validados contra el servicio real. Regresión aislada y build aprobados. La integración autenticada, la sesión única V7 y la lectura de fotografías reales permanecen pendientes de una cuenta de prueba autorizada. La etapa queda abierta antes de dar por validado el despliegue de pruebas en Azure.

## Servicio real

- Spring Boot respondió HTTP 200 con `{"status":"UP"}` en `http://localhost:8080/actuator/health`.
- Next.js no estaba activo en el puerto 3000. Se inició con `npm run dev` y la configuración local existente. La página de login respondió HTTP 200. El servidor de desarrollo quedó activo.
- El backend devolvió 401 sin sesión en `/api/v1/auth/me` y la ruta de avatar por UUID. El BFF respondió 401 con una cookie deliberadamente inválida, confirmando que la ruta de sesión llega al servicio real y rechaza el acceso.
- Cinco lecturas anónimas del BFF devolvieron 401 con `no-store`. Dashboard, Usuarios, Sucursales y Mi cuenta redirigieron al login con 307. El BFF de avatar rechazó también la cookie inválida y no concedió CORS a un origen ajeno.
- La revisión de código en modo lectura confirmó el contrato de foto por UUID y la migración V7 con `active_family_id`. La aplicación de V7 en `kiments_db` consta en el informe de 5I-A. PostgreSQL exigió autenticación para consultar sus metadatos con psql en modo de solo lectura y sin solicitar contraseña. No se pudo comprobar el historial de Flyway de forma independiente. El health por sí solo no demuestra la versión de la migración.

## Autenticación, pantallas y fotografías

| Comprobación | Real | Aislada |
|---|---|---|
| Login y perfil | Página de login disponible, entradas inválidas y acceso anónimo rechazados. Login válido y perfil autenticado pendientes. | Login confirmado por el BFF, atributos de cookies de producción y contratos de perfil. |
| Refresh y logout | Refresh sin cookie devuelve 401. Logout anónimo del mismo origen limpia cookies y un origen ajeno recibe 403. Rotación, revocación y logout autenticado pendientes. | Refresh concurrente, orden con logout, rechazo terminal y respuesta obsoleta cubiertos en pruebas unitarias. |
| Sesión única | Chrome y Edge con la misma cuenta, revocación de la anterior y conservación de la nueva pendientes. | La revisión anterior 5I-B probó logout y cambio de identidad en dos pestañas del mismo navegador con fixture. Las pruebas unitarias se repitieron en 5I-C. |
| Fotos en Usuarios y Mi cuenta | Rechazo anónimo verificado. Lectura autenticada, foto en tabla y detalle, iniciales, ausencia de imágenes rotas y 403/404 del servicio real pendientes. | Binario privado autorizado, 401, 403, 404, indicador en listado/detalle y regresión de la foto propia. |
| Dashboard, Usuarios, Sucursales y Mi cuenta | Las cuatro rutas están protegidas. Navegación y vistas autenticadas con datos reales pendientes. | Pruebas de Dashboard y suites de Usuarios, Sucursales y Mi cuenta aprobadas. La medición responsive corresponde a 5I-B. |

Se pidió confirmación de una cuenta de prueba autorizada y coordinación del ingreso manual. Las pruebas que cambiarían una sesión real quedaron detenidas. Un 401 genérico sigue sin distinguir desplazamiento, revocación o caducidad.

## Ejecución de 5I-C

| Prueba | Resultado |
|---|---:|
| npm test | 64/64 |
| npm run test:http, contra Next local configurado para Spring Boot real | 16/16 |
| npm run test:account, backend en memoria | 33/33 |
| npm run test:users, backend en memoria | 30/30 |
| npm run test:branches, backend en memoria | 45/45 |
| Sondas adicionales de rutas, caché y CORS del BFF real | 10/10 |
| npm run typecheck | Correcto |
| npm run lint | Correcto |
| npm run build | Correcto |

El build encontró errores EPERM de Windows al crear directorios generados. Se conservó la salida fallida en directorios de respaldo dentro del proyecto. El build terminó al regenerar la salida con `UV_THREADPOOL_SIZE=1`, seguido por typecheck correcto.

## Archivos y siguiente validación

Se añadió este informe. La configuración y los archivos funcionales del frontend se mantuvieron. Las escrituras de las suites usaron un backend en memoria. No se iniciaron sesiones de prueba contra cuentas reales ni se escribieron usuarios, fotos, sucursales o datos en PostgreSQL.

Para cerrar 5I-C, confirmar V7 mediante el historial de Flyway en una terminal ya autenticada y usar una cuenta de prueba autorizada para iniciar sesión manualmente en Chrome y Edge. Con esa sesión se puede comprobar la rotación, la revocación de la familia anterior y el acceso de lectura al perfil, a los módulos y a fotos existentes. El despliegue de pruebas queda condicionado a esas comprobaciones.
