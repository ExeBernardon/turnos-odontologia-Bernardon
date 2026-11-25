# Proposal

## Why

Hoy el backend decide cuándo avisar (C-07 reserva y carga manual, C-08 cancelar y reprogramar, C-10 bloqueos) llamando a `NotificacionTurnos`, pero la única implementación es el no-op de C-03: ningún paciente recibe la Confirmación de reserva con el link de Cancelar ni el Recordatorio con Confirmar, y el odontólogo no recibe el "Turno no confirmado". Sin envío real no se cumplen US-006, US-011, US-012, US-004, ni la base de US-015 y US-022, y C-12 (job de recordatorios) no tiene con qué mandar.

## What Changes

- Implementación real de `NotificacionTurnos` que envía por SMTP (Spring Mail) cada uno de los 8 mails de KB 05 §Mails del sistema y reemplaza al no-op sin cambiar a quienes la invocan (C-07, C-08, C-10 y, en el futuro, C-12).
- Un servicio de envío de email con remitente `APP_MAIL_FROM`, links al front construidos con `APP_FRONTEND_BASE_URL` (`/turno/confirmar?token=...`, `/turno/cancelar?token=...`) y fechas/horas en `America/Argentina/Buenos_Aires`.
- Plantillas HTML en `backend/src/main/resources/templates/email/` (Thymeleaf, ya en el `pom.xml`) con exactamente los botones de la tabla: solo el Recordatorio trae Confirmar; Confirmación de reserva y Reprogramación traen solo Cancelar; Aviso, Reprogramación a menos de 24 h, Cancelaciones y Turno no confirmado no traen botones con token.
- Destinatarios: el `email_contacto` del turno en los mails al paciente; el email de login del odontólogo (tabla `odontologo`) en el "Turno no confirmado".
- Un fallo de SMTP (o de armado del mail) se registra en el log con el id del turno y el tipo de mail, devuelve `false` y nunca se propaga (RN-NOT-05). El log no contiene DNI, email, teléfono ni token.
- Timeouts de conexión y escritura SMTP acotados para que un SMTP colgado no deje colgada la request.
- Se elimina el no-op `NotificacionTurnosNoOp` (y su test) porque deja de usarse; se ajusta `NotificacionTurnosIT`.
- Mailpit en dev y Gmail en la demo cambiando **solo** `SPRING_MAIL_*` y `APP_MAIL_FROM` (ya previsto por `entorno-ejecucion`; este change no cambia esas variables).
- Sin cambios de contrato: `docs/api/openapi.yaml` no se toca (los mails no son endpoints).

## Capabilities

### New Capabilities
<!-- Ninguna: el envío real es la implementación de la capability existente notificaciones-turnos. -->

### Modified Capabilities
- `notificaciones-turnos`: deja de arrancar con la implementación sin envío y pasa a enviar cada aviso por email, con destinatario, botones, links con token, horario en Buenos Aires, remitente configurable, fallo de SMTP sin propagación y logs sin datos personales.

## Impact

- Código nuevo en `backend/src/main/java/ar/turnos/odontologia/notificacion/` (implementación real, servicio de email, armado de links y formato de fechas) y plantillas en `backend/src/main/resources/templates/email/`.
- Se borra `notificacion/NotificacionTurnosNoOp.java` y `NotificacionTurnosNoOpTest.java`; se reescribe `NotificacionTurnosIT.java` (archivos de C-03, Int 1: avisar en el PR).
- `backend/pom.xml`: dependencia de test `com.icegreen:greenmail-junit5` (SMTP en memoria para los tests).
- `backend/src/main/resources/application.yml`: timeouts SMTP (`spring.mail.properties.mail.smtp.*timeout`) y `default-encoding` UTF-8; `application-test.yml`: timeouts cortos (archivos de C-02, Int 2: avisar en el PR).
- Lee `odontologo` (email) con `OdontologoRepository` sin modificarlo; lee `Turno`, `Paciente` y `Prestacion` sin modificarlos. No toca `TurnoService` ni `BloqueoService`.
- Los IT que no mockean `NotificacionTurnos` (p. ej. `TurnoPublicoApiIT`, `TurnoTokenApiIT`) pasan a intentar SMTP real contra `localhost:1025`: sin servidor el envío falla rápido, se loguea y devuelve `false`, sin romper esos tests.
- Sin cambios en `docs/api/openapi.yaml`, `SecurityConfig`, `docker-compose.yml` ni `.env.example`.
- Governance ALTO: las decisiones del diseño se revisan con el equipo antes de implementar.
