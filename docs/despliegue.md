# Despliegue en el servidor SIA

Guía para el coordinador de salas, que es quien modifica el servidor `sia.univalle.edu.co` (Debian 13,
PostgreSQL 17.11, Apache 2.4). El equipo de desarrollo no tiene acceso al servidor.

> **Estado del documento:** solo está completa la sección de **variables de entorno del correo (HU-038)**.
> Las demás secciones (copiar artefactos, crear la base y el usuario, servicio, Apache, verificación) están
> pendientes: faltan la ruta de ejecución aprobada (A o B), el nombre del subdominio y las «recomendaciones para
> la publicación del aplicativo» de la OITel.

## Ruta de ejecución (pendiente de aprobación)

No se ha definido cuál de estas rutas está aprobada. Este documento describe las dos:

- **Ruta A:** JDK 21 instalado por la OITel y el JAR (`./gradlew bootJar`) ejecutado como servicio `systemd`.
- **Ruta B:** Docker aprobado y probado en el servidor, con la imagen del `Dockerfile` del repositorio.

## Variables de entorno del correo (HU-038)

El backend envía los correos por **Gmail API con OAuth 2.0** desde la cuenta institucional
`notificaciones.cda.palmira@correounivalle.edu.co`. No usa SMTP ni contraseña de la cuenta.

| Variable | Secreta | Valor |
|---|---|---|
| `GMAIL_CLIENT_ID` | sí | Client ID del cliente OAuth **de envío** (Google Cloud Console). Es distinto de `GOOGLE_CLIENT_ID`, que es el del inicio de sesión. |
| `GMAIL_CLIENT_SECRET` | sí | Secreto de ese cliente OAuth. |
| `GMAIL_REFRESH_TOKEN` | sí | Refresh token de la cuenta de notificaciones, con el scope `https://www.googleapis.com/auth/gmail.send`. |
| `MAIL_FROM` | no | `notificaciones.cda.palmira@correounivalle.edu.co` |
| `MAIL_COPIA_NOTIFICACIONES` | no | `notificaciones.cda.palmira@correounivalle.edu.co` |
| `APP_URL` | no | Dirección pública de la aplicación, por ejemplo `https://sia.univalle.edu.co/<subdominio>/` |
| `RECORDATORIO_DIAS_ANTES` | no | `3` (días antes del cierre del plazo docente) |
| `APP_TIMEZONE` | no | `America/Bogota` |

Si faltan los secretos, o si Google responde `invalid_grant`, **la aplicación arranca y funciona**, pero los
correos quedan en estado `FALLIDO` en la tabla `notificaciones_envios` y el log muestra un aviso
(«Envío de correos deshabilitado…» o «Gmail respondió invalid_grant…»). El log nunca muestra los valores de los
secretos ni datos personales.

### Ruta A: systemd

1. Crear el archivo de variables (solo root puede leerlo):

   ```bash
   sudo install -d -m 700 -o root -g root /etc/cda-backend
   sudo install -m 600 -o root -g root /dev/null /etc/cda-backend/cda-backend.env
   sudo nano /etc/cda-backend/cda-backend.env
   ```

2. Contenido del archivo (una variable por línea, sin comillas ni espacios alrededor del `=`):

   ```properties
   GMAIL_CLIENT_ID=<client ID del cliente OAuth de envío>
   GMAIL_CLIENT_SECRET=<secreto del cliente>
   GMAIL_REFRESH_TOKEN=<refresh token de la cuenta de notificaciones>
   MAIL_FROM=notificaciones.cda.palmira@correounivalle.edu.co
   MAIL_COPIA_NOTIFICACIONES=notificaciones.cda.palmira@correounivalle.edu.co
   APP_URL=https://sia.univalle.edu.co/<subdominio>/
   RECORDATORIO_DIAS_ANTES=3
   APP_TIMEZONE=America/Bogota
   ```

   Ahí mismo van las demás variables del backend (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `GOOGLE_CLIENT_ID`,
   `JWT_SECRET`…), que se documentarán en su sección.

3. Verificar dueño y permisos: `sudo ls -l /etc/cda-backend/cda-backend.env` debe mostrar `-rw------- root root`.

4. En la unidad del servicio (`/etc/systemd/system/cda-backend.service`), en la sección `[Service]`:

   ```ini
   EnvironmentFile=/etc/cda-backend/cda-backend.env
   ```

5. Aplicar: `sudo systemctl daemon-reload && sudo systemctl restart cda-backend`.

6. Revisar el log sin exponer valores: `sudo journalctl -u cda-backend --since "10 min ago" | grep -i "correo\|gmail"`.
   Si la configuración está completa, no aparece el aviso «Envío de correos deshabilitado».

### Ruta B: Docker

1. Crear el mismo archivo `/etc/cda-backend/cda-backend.env` (pasos 1 a 3 de la ruta A).
2. Pasarlo al contenedor con `--env-file` (no con `-e`, para que los secretos no queden en el historial de la
   terminal ni en `ps`):

   ```bash
   sudo docker run -d --name cda-backend --restart unless-stopped \
     --env-file /etc/cda-backend/cda-backend.env \
     -p 127.0.0.1:8080:8080 \
     cda-backend:<versión>
   ```

3. Para cambiar un valor: editar el archivo y recrear el contenedor (`docker rm -f cda-backend` y repetir el
   `docker run`). `docker restart` no vuelve a leer el archivo.

### Regenerar el refresh token (si se revoca)

El refresh token deja de servir si se cambia la contraseña de la cuenta de notificaciones, si se revoca el
acceso desde la cuenta de Google o si un administrador de Workspace lo invalida. Síntoma: los correos quedan
`FALLIDO` con «Autorización de Gmail revocada o vencida» y el log muestra «Gmail respondió invalid_grant».

1. En Google Cloud Console, en el cliente OAuth **de envío**, verificar que
   `https://developers.google.com/oauthplayground` esté en «URI de redireccionamiento autorizados».
2. Abrir el [OAuth 2.0 Playground](https://developers.google.com/oauthplayground).
3. Con el ícono de engranaje: marcar «Use your own OAuth credentials» y escribir el client ID y el secreto del
   cliente de envío. Dejar «Access type: Offline».
4. En «Step 1», escribir el scope `https://www.googleapis.com/auth/gmail.send` y pulsar «Authorize APIs».
5. Iniciar sesión **con la cuenta `notificaciones.cda.palmira@correounivalle.edu.co`** y aceptar el permiso
   «Enviar correo electrónico en tu nombre».
6. En «Step 2», pulsar «Exchange authorization code for tokens» y copiar el **Refresh token**.
7. Reemplazar `GMAIL_REFRESH_TOKEN` en `/etc/cda-backend/cda-backend.env` y reiniciar el servicio (ruta A) o
   recrear el contenedor (ruta B).
8. Por seguridad, en el engranaje del Playground desmarcar las credenciales propias y cerrar la pestaña. No
   guardar el token en ningún otro lugar.

Los correos que quedaron `FALLIDO` no se reenvían solos. Los avisos futuros (por ejemplo, el recordatorio de
cierre) sí salen con el token nuevo.

### Pendiente con sistemas de Univalle

- Confirmar que el dominio permite a la cuenta de notificaciones usar Gmail API con el scope `gmail.send`, y si
  el cliente OAuth debe ser de tipo «interno» del Workspace.
- Confirmar si en lugar de OAuth por usuario se prefiere un relay SMTP de Workspace. Hoy el código solo
  implementa Gmail API.
