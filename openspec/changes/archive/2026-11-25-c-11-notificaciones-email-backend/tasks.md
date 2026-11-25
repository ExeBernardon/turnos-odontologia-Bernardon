# Tasks

> Strict TDD: en cada tarea primero el test que falla (RED), después el código mínimo (GREEN), triangular con un segundo caso y refactorizar con los tests en verde. Datos ficticios siempre (`paciente@ejemplo.com`, `odontologo@ejemplo.com`, DNI `99999999`, tel. `1155555555`).
>
> Governance ALTO: antes de 1.2, confirmar con el equipo las decisiones de `design.md` (Thymeleaf, envío síncrono, borrar el no-op, timeouts SMTP, email del odontólogo desde la tabla `odontologo`).

## 1. Base

- [x] 1.1 Safety net: `./mvnw verify` en `backend/` y registrar la cantidad de tests verdes de base (si algo falla, reportarlo como falla preexistente y no seguir)
- [x] 1.2 Agregar `com.icegreen:greenmail-junit5` (scope `test`) al `pom.xml` y verificar con `./mvnw test-compile` que resuelve
- [x] 1.3 Timeouts SMTP: `spring.mail.default-encoding=UTF-8` y `spring.mail.properties.mail.smtp.{connectiontimeout,timeout,writetimeout}=5000` en `application.yml`, `1000` en `application-test.yml`; verificar que el contexto levanta (`PlataformaSmokeIT` verde)

## 2. Piezas puras: links y formato

- [x] 2.1 `LinksTurno`: `LinksTurnoTest` primero — confirmar y cancelar con `http://localhost:8080` y token `abc123` dan `http://localhost:8080/turno/{confirmar|cancelar}?token=abc123`; base con `/` final sin doble barra; link de reserva `{base}/reservar`
- [x] 2.2 `FormatoTurno`: `FormatoTurnoTest` primero — `2026-12-10T13:00:00Z` se muestra como 10 de diciembre de 2026, 10:00 en `America/Argentina/Buenos_Aires` (es-AR); un inicio `2026-12-11T02:30:00Z` cae el día 10 a las 23:30
- [x] 2.3 `TipoMail` (enum con plantilla, asunto y botones por cada una de las 8 filas): test primero — hay exactamente 8 tipos, asuntos distintos, solo `RECORDATORIO` lleva `CONFIRMAR`, `CONFIRMACION_RESERVA` y `REPROGRAMACION` llevan solo `CANCELAR`, el resto ninguno

## 3. Plantillas Thymeleaf

- [x] 3.1 Layout común y plantillas `templates/email/*.html` de los 8 mails: `PlantillasMailTest` (TemplateEngine armado a mano, sin Spring) primero, parametrizado por `TipoMail` — presencia/ausencia exacta de `/turno/confirmar?token=` y `/turno/cancelar?token=`, el token no aparece en los mails sin botones, nombre del paciente, prestación y horario en Buenos Aires presentes
- [x] 3.2 Contenido específico: tests en `PlantillasMailTest` primero — el Mail 6 muestra el teléfono del turno; el Mail 5 tiene el link a `/reservar` e invita a reservar; 1b, 3b, 4 y 5 muestran `app.consultorio.contacto`; un nombre `<b>Ficticio</b>` aparece escapado

## 4. Envío y avisos

- [x] 4.1 `EmailService` (render + `MimeMessageHelper` UTF-8 HTML + `from = app.mail.from` + `JavaMailSender`): `EmailServiceGreenMailTest` primero con GreenMail en puerto dinámico — el mensaje llega al destinatario indicado, `From = turnos@consultorio.local`, asunto del tipo, acentos intactos; devuelve `true`
- [x] 4.2 Fallo de envío sin propagación: tests primero — con `JavaMailSender` mock que lanza `MailSendException` devuelve `false` sin lanzar; con un puerto SMTP cerrado devuelve `false` antes del timeout; error de render (plantilla inexistente) devuelve `false`; el log (`OutputCaptureExtension`) tiene id del turno y tipo y no contiene email, teléfono, DNI, nombre ni token
- [x] 4.3 `NotificacionTurnosEmail` (implementa los 8 métodos): `NotificacionTurnosEmailTest` (mocks de `EmailService`/`JavaMailSender` y `OdontologoRepository`) primero — cada método usa su `TipoMail`; los 7 mails al paciente van al `email_contacto` del turno; `turnoNoConfirmado` va al email del odontólogo (`findAll(Sort.by("id"))`, primero) y no al del turno; sin odontólogo devuelve `false` sin lanzar ni enviar
- [x] 4.4 Cobertura de los 8 mails por SMTP: test con GreenMail primero — invocar los 8 métodos de `NotificacionTurnosEmail` sobre un turno ficticio en memoria y verificar 8 mensajes recibidos con asuntos distintos y los destinatarios correctos

## 5. Reemplazo del no-op

- [x] 5.1 Reescribir `NotificacionTurnosIT` primero para que exija `NotificacionTurnosEmail` inyectado (RED con el no-op), luego borrar `NotificacionTurnosNoOp` y `NotificacionTurnosNoOpTest`; verificar `NotificacionTurnosIT` verde y que ningún otro archivo referencia el no-op (`grep`)
- [x] 5.2 Corregir el javadoc de `NotificacionTurnos` ("el job de C-11" → C-12), solo comentario; verificar que compila
- [x] 5.3 Reserva con SMTP caído: IT (`@IntegracionPostgres`, en `notificacion/` o en `TurnoPublicoApiIT` si su dueña acepta) primero — `POST` de reserva pública con SMTP en un puerto cerrado responde 201, el turno queda guardado y el request termina en menos de 3 s

## 6. Cierre

- [x] 6.1 Refactor final con tests verdes, `./mvnw verify` en `backend/` completo (nuevos + baseline, sin regresiones en los IT que no mockean `NotificacionTurnos`) y `openspec validate c-11-notificaciones-email-backend --strict`
- [x] 6.2 Prueba manual: `docker compose up --build`, reservar un turno a más de 48 h, a 24–48 h y a menos de 24 h desde el front y verificar en Mailpit (`http://localhost:8025`) Confirmación de reserva (solo Cancelar), Recordatorio (Confirmar y Cancelar) y Aviso (sin botones), que los links abren `/turno/cancelar` y `/turno/confirmar` del front y que el log del backend no muestra emails ni tokens
- [x] 6.3 Plantear al equipo en el PR (sin editar el contrato): borrado del no-op y cambios en `NotificacionTurnosIT` (Int 1, C-03), timeouts en `application.yml`/`application-test.yml` (Int 2, C-02), que en la demo con Gmail `APP_MAIL_FROM` debe ser la cuenta autenticada, y para C-12 que el job invoque `recordatorio` / `turnoNoConfirmado` dentro de una transacción (lectura LAZY) y use el `boolean` para los flags

## Workflow follow-up

- Abrir PR de `agus/c-11-notificaciones-email-backend` a `main` y, tras la revisión, `/opsx:archive c-11-notificaciones-email-backend` y marcar `[x]` C-11 en `CHANGES.md`.
