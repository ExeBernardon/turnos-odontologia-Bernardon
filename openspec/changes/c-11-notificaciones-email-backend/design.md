# Design

## Context

`notificacion/NotificacionTurnos` (C-03) tiene un método por mail que recibe el `Turno` y devuelve `boolean` (salió / no salió); su javadoc fija que se invoca **dentro de la transacción del caller, después del `saveAndFlush`**, para poder leer `paciente` y `prestacion` (LAZY), y que nunca lanza por fallo de envío. Los callers ya existen y no se tocan:

- `TurnoService` (`@Transactional` a nivel clase): `avisar()` en reserva y carga manual (usa el `boolean` del Recordatorio y del Aviso para `recordatorio_enviado` / `alerta_pendiente_enviada`), `cancelar()` → `cancelacionPorOdontologo`, `reprogramar()` → `reprogramacion` / `reprogramacionMenos24h`. Todos síncronos dentro de la transacción.
- `BloqueoService.crear()`: ya avisa **después del commit**, en una segunda transacción de lectura, y además envuelve la llamada en `try/catch`.

El único implementador es `NotificacionTurnosNoOp` (`@Component @Fallback`): cualquier otro `@Component` que implemente la interfaz lo reemplaza sin `@Primary`.

Ya disponible: `spring-boot-starter-mail` y `spring-boot-starter-thymeleaf` en el `pom.xml`; `AppProperties` con `zonaHoraria`, `frontendBaseUrl`, `mail.from` y `consultorio.contacto`; `templates/` vacío (`.gitkeep`); `management.health.mail.enabled=false`. `application.yml` no define `spring.mail.*` (llegan por env `SPRING_MAIL_*` del `.env`); el perfil `local` apunta a `localhost:1025`; `application-test.yml` a `localhost:1025`. El token es Base64 URL-safe sin padding (no necesita encoding en la URL). `OdontologoRepository` tiene `findByEmail`; en la v1 hay un único odontólogo (seed).

IT que **no** mockean `NotificacionTurnos` (van a usar la implementación real): `TurnoPublicoApiIT`, `TurnoTokenApiIT`, `TurnoAltaConcurrenciaIT`, `PlataformaSmokeIT` y los demás `@IntegracionPostgres` fuera de turnos/bloqueos. Ninguno asserta flags de envío.

## Goals / Non-Goals

**Goals:**
- Implementación real de los 8 avisos con el contrato actual de la interfaz (síncrona, `boolean`, nunca lanza).
- Plantillas testeables sin levantar Spring ni SMTP; un test con SMTP real en memoria para destinatario, remitente y contenido.
- Que C-12 pueda llamar `recordatorio(turno)` y `turnoNoConfirmado(turno)` dentro de su transacción y usar el `boolean` para los flags, sin código nuevo en `notificacion/`.

**Non-Goals:**
- El job `@Scheduled` de recordatorios y alertas (C-12).
- Envío asíncrono, cola o reintentos propios: los reintentos son del job (RN-NOT-05).
- Cambiar la interfaz `NotificacionTurnos`, `TurnoService`, `BloqueoService`, `OdontologoRepository`, el contrato OpenAPI, `docker-compose.yml` o `.env.example`.
- Versión texto plano (multipart/alternative) de los mails, imágenes o adjuntos.

## Decisions

1. **Estructura en `notificacion/`**:
   - `NotificacionTurnosEmail` (`@Component`, implementa `NotificacionTurnos`): por cada método arma el modelo del mail (`TipoMail` + variables) y delega en `EmailService`. Resuelve el destinatario: `turno.getEmailContacto()` o el email del odontólogo.
   - `EmailService`: renderiza la plantilla con el `TemplateEngine` de Thymeleaf, arma el `MimeMessage` (`MimeMessageHelper`, UTF-8, HTML, `from = app.mail.from`) y lo envía con `JavaMailSender`. Atrapa `MailException` y cualquier `RuntimeException` del render, loguea `warn` con id del turno + tipo + clase de la excepción y devuelve `false`.
   - `TipoMail` (enum): una constante por fila de la tabla con nombre de plantilla, asunto y botones (`CONFIRMAR`, `CANCELAR`). Es la única fuente de "qué botones lleva cada mail", y el test de botones itera el enum.
   - `LinksTurno`: arma `{base}/turno/confirmar?token=…`, `{base}/turno/cancelar?token=…` y `{base}/reservar` con `UriComponentsBuilder`, normalizando la barra final de `APP_FRONTEND_BASE_URL`.
   - `FormatoTurno`: fecha y hora de inicio en `app.zona-horaria` con `Locale` `es-AR` (ej. "jueves 10 de diciembre de 2026, 10:00").
   Alternativa descartada: un único `EmailService` con los 8 métodos (mezcla negocio de cada mail con el transporte y es difícil de testear sin SMTP).

2. **Thymeleaf HTML** (`templates/email/<tipo>.html` + un fragmento común de layout con los datos del turno y el pie con `app.consultorio.contacto`). Ya está en el `pom.xml` y KB 08 ubica las plantillas en `templates/`; `th:text` escapa HTML (nombres de pacientes) y `th:href` toma los links ya armados. Los botones son `<a>` con estilo inline (los clientes de correo ignoran CSS externo). La plantilla solo muestra un botón si el modelo trae su link, y el modelo solo trae los links que `TipoMail` permite: así "exactamente los botones" se asegura en un lugar y se verifica en el HTML final. Alternativa descartada: texto plano con `String.format` (sin escape, botones como URLs sueltas, peor lectura en Gmail).

3. **Envío síncrono dentro de la transacción del caller**. Lo exige el contrato existente: `TurnoService.avisar()` usa el `boolean` para setear `recordatorio_enviado` / `alerta_pendiente_enviada` antes del commit, y C-12 hará lo mismo. Alternativas descartadas: `@Async` (no puede devolver el resultado a tiempo para el flag y pierde la sesión JPA para los LAZY) y `@TransactionalEventListener(AFTER_COMMIT)` (cambia la interfaz y los callers de otros integrantes; los bloqueos ya avisan post-commit por su cuenta). Riesgo aceptado: el mail puede salir y la transacción fallar después (el único paso posterior es el commit de una fila ya flusheada; improbable).

4. **Timeouts SMTP**: en `application.yml`, `spring.mail.properties.mail.smtp.connectiontimeout`, `timeout` y `writetimeout` en 5000 ms y `spring.mail.default-encoding=UTF-8`; en `application-test.yml`, 1000 ms. Sin timeouts, JavaMail espera indefinidamente y una request de reserva quedaría colgada con la transacción abierta. Son propiedades `spring.mail.properties.*`, así que siguen funcionando con Mailpit y Gmail sin nuevas variables de entorno.

5. **Email del odontólogo**: `OdontologoRepository.findAll(Sort.by("id"))` y el primero (único odontólogo en la v1, RN-REC-01), leído en cada envío (es un mail por turno y por ciclo del job; no amerita caché). Sin odontólogo → `warn` sin datos y `false`. No se modifica el repositorio. Alternativa descartada: `app.seed.odontologo.email` (es el valor inicial del seed, no necesariamente el email de login vigente).

6. **Eliminar el no-op**: con `NotificacionTurnosEmail` siempre registrado, `NotificacionTurnosNoOp` es código muerto. Se borran la clase y `NotificacionTurnosNoOpTest`, y `NotificacionTurnosIT` pasa a verificar que la app inyecta `NotificacionTurnosEmail`. Los IT que no deben enviar ya usan `@MockitoBean NotificacionTurnos`. Alternativa descartada: conservarlo detrás de una propiedad `app.mail.habilitado` (agrega una variable de entorno y un modo que la demo no necesita).

7. **Logs**: solo `turnoId`, `TipoMail` y `getClass().getSimpleName()` de la excepción; nunca el mensaje de la excepción (JavaMail incluye la dirección en `SendFailedException`), el destinatario, el teléfono, el nombre ni el token. Éxito → `debug` con id y tipo.

8. **Tests por capa** (datos ficticios: `paciente@ejemplo.com`, `odontologo@ejemplo.com`, DNI `99999999`, tel. `1155555555`):
   - Unit: `LinksTurnoTest` (barra final, rutas, token), `FormatoTurnoTest` (UTC → Buenos Aires, cambio de día cerca de medianoche), `PlantillasMailTest` (TemplateEngine de Thymeleaf configurado a mano sobre `classpath:/templates/`, sin Spring): para cada `TipoMail`, presencia/ausencia exacta de `/turno/confirmar?token=` y `/turno/cancelar?token=`, token ausente en mails sin botones, datos del turno, teléfono en el Mail 6, link a `/reservar` en el Mail 5, escape de `<b>`.
   - Unit con mock: `NotificacionTurnosEmailTest` (Mockito `JavaMailSender` y `OdontologoRepository`): destinatario por tipo, sin odontólogo → `false`, `MailSendException` → `false` sin lanzar, log capturado sin email/teléfono/DNI/nombre/token (`OutputCaptureExtension`).
   - SMTP real en memoria: `EmailServiceGreenMailTest` (GreenMail JUnit 5 en puerto dinámico + `JavaMailSenderImpl` armado a mano): llega el mensaje, `From = app.mail.from`, destinatario, asunto, UTF-8 con acentos; y SMTP caído (puerto cerrado) → `false` en menos del timeout.
   - IT: `NotificacionTurnosIT` (`@IntegracionPostgres`) confirma el bean real inyectado; un IT de reserva pública con SMTP caído confirma 201 y turno guardado (puede vivir en `TurnoPublicoApiIT` si su dueña lo acepta, o en un IT nuevo de `notificacion/`).
   Alternativa descartada: Mailpit por Testcontainers (más pesado y lento que GreenMail para asserts de contenido).

## Risks / Trade-offs

- [SMTP lento dentro de la transacción] → timeouts de 5 s; en la demo con Gmail un envío tarda ~1 s. El bloqueo de muchos turnos ya avisa fuera de la transacción de escritura.
- [Gmail reescribe el `From` a la cuenta autenticada] → documentar en el README/KB 11 que en la demo `APP_MAIL_FROM` debe ser la misma cuenta de Gmail; no cambia el código.
- [IT sin mock intentan SMTP real en `localhost:1025`] → sin servidor fallan rápido (conexión rechazada) y devuelven `false`; con un Mailpit local levantado los mails ficticios caen ahí, sin efecto en los tests. Timeout de test de 1 s para que un puerto filtrado no los alargue.
- [Thymeleaf autoconfigura un `ViewResolver` web] → la API es `@RestController`, no hay vistas; sin efecto. Se verifica en el IT de contexto.
- [Archivos de otros changes] → `NotificacionTurnosNoOp*` y `NotificacionTurnosIT` (C-03, Int 1), `application.yml` / `application-test.yml` (C-02, Int 2): cambios mínimos y avisados en el PR (regla 16).
- [El javadoc de `NotificacionTurnos` menciona "el job de C-11"] → corrección a C-12 en el mismo PR, solo comentario.

## Migration Plan

Sin migración de datos ni de esquema. Deploy: merge a `main` y `docker compose up --build`; con `.env` por defecto los mails caen en Mailpit (`http://localhost:8025`). Rollback: revertir el PR (vuelve el no-op).

## Open Questions

- Texto final de asuntos y cuerpos: se proponen en las plantillas y el equipo los ajusta en el PR sin cambiar specs ni tareas.
