# Proposal

## Why

Desde C-11 el backend ya envía los 8 mails del sistema, pero solo cuando alguien hace algo (reservar, reprogramar, cancelar). Nadie manda el Recordatorio 48 h antes a los turnos reservados con más anticipación, nadie reintenta el Recordatorio que falló al crear un turno entre 24 y 48 h, y el odontólogo no recibe el "Turno no confirmado" cuando un `PENDIENTE` llega a menos de 24 h. Sin eso no se cumplen US-015 ni US-022, y el link de Confirmar (que solo existe en el Recordatorio, RN-NOT-08) casi nunca le llega al paciente.

## What Changes

- Un job programado único (`RecordatorioJob`, `@Scheduled(fixedDelay)`) que corre cada `APP_RECORDATORIO_INTERVALO_MIN` minutos (15 por defecto) con el `Clock` inyectado, y ejecuta dos tareas por ciclo (DD-06, DD-13):
  1. **Recordatorio al paciente**: turnos `PENDIENTE` con inicio dentro de las próximas `APP_RECORDATORIO_HORAS_ANTES` horas (48, borde inclusive) y `recordatorio_enviado = false` → Recordatorio al `email_contacto` del turno → `recordatorio_enviado = true` solo si el envío salió. Cubre el envío de 48 h antes y el reintento de los que fallaron al crear (RN-NOT-02, RN-NOT-04).
  2. **Alerta al odontólogo**: turnos `PENDIENTE` con inicio a menos de `APP_ALERTA_PENDIENTE_HORAS` horas (24, borde exclusivo) y `alerta_pendiente_enviada = false` → "Turno no confirmado" al email de login del odontólogo → `alerta_pendiente_enviada = true` solo si salió (RN-NOT-06).
- Cada turno se procesa en su propia transacción: un error de SMTP (o cualquier excepción inesperada con un turno) no frena el resto del lote, no revierte a los demás y no marca el flag, así el próximo ciclo reintenta (RN-NOT-05).
- El flag se marca con un `UPDATE` condicionado (sigue `PENDIENTE` y con el mismo inicio que se avisó) para no pisar una confirmación, cancelación o reprogramación concurrente.
- Nunca recordatorio ni alerta a `CONFIRMADO`, `CANCELADO`, `ASISTIO`/`AUSENTE` ni a turnos ya empezados; los que nacen `CONFIRMADO` no entran (ya tienen ambos flags en `true`).
- Consultas de selección apoyadas en el índice existente `turno_estado_inicio_idx (estado, inicio)`; sin migraciones.
- Propiedad interna `app.recordatorio.job-habilitado` (por defecto `true`) para no registrar el job en el perfil de test: los tests de integración invocan el ciclo a mano con un reloj fijo.
- Sin cambios de contrato: `docs/api/openapi.yaml` no se toca (el job no expone endpoints) ni se agregan variables de entorno nuevas (las tres ya existen en `AppProperties`, `application.yml` y `.env.example`).

## Capabilities

### New Capabilities
- `avisos-programados`: avisos que el sistema emite solo por el paso del tiempo, sin acción de un usuario: el Recordatorio al paciente dentro de la ventana de 48 h y la alerta "Turno no confirmado" al odontólogo a menos de 24 h; selección de turnos, idempotencia por flags, reintento en el ciclo siguiente y aislamiento de fallos dentro del lote.

### Modified Capabilities
<!-- Ninguna: notificaciones-turnos ya define el contenido, destinatario y fallo sin propagación de cada mail;
     ciclo-vida-turnos ya define que reprogramar reinicia los flags; alta-turnos ya define los flags iniciales.
     C-12 solo los consume y prueba de punta a punta que el job los respeta. -->

## Impact

- **Código nuevo** (`backend/src/main/java/ar/turnos/odontologia/notificacion/`): `RecordatorioJob` (disparador `@Scheduled`), `AvisosProgramadosService` (ciclo, transacción por turno, marcado de flags) y `TurnoAvisosRepository` (consultas de selección y `UPDATE` condicionado de flags, sin tocar `TurnoRepository`).
- **Configuración**: `application-test.yml` agrega `app.recordatorio.job-habilitado: false`. Se reutilizan `ClockConfig`, `SchedulingConfig` (`@EnableScheduling` ya activo) y `AppProperties.recordatorio()/alerta()`.
- **Tests**: unit (Mockito, `Clock.fixed`) del ciclo y de los bordes; integración con Testcontainers PostgreSQL 16 para selección, `UPDATE` condicionado, transacción por turno y reprogramación que reinicia flags; test liviano de cableado del `@Scheduled`.
- **Sin cambios** en `TurnoService`, `EstadoInicialTurno`, `NotificacionTurnos*`, `schema-extra.sql` ni en el front. La agenda que resalta los pendientes a menos de 24 h es C-19/C-20.
- **Governance ALTO**: envío de mails a pacientes y al odontólogo; se revisa el diseño antes de implementar.
