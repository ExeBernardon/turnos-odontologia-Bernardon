# Proposal

## Why

Todo el backend (C-04 a C-12) necesita el mismo modelo de datos: entidades, tablas, la restricción anti-solapamiento y un odontólogo para loguearse. Si cada dominio crea sus entidades por su cuenta, los changes en paralelo chocan en las mismas tablas. C-03 es el mini-gate (GATE 3 de `CHANGES.md`) que fija ese modelo una sola vez, sin lógica de negocio, para que el resto arranque en paralelo. Además cierra un riesgo que la KB dejó marcado como no verificado: que `schema-extra.sql` con separador `^^^` cree la constraint `EXCLUDE` dentro de un bloque `DO $$ ... $$` y sea idempotente al reiniciar.

## What Changes

- Entidades JPA y tablas de KB 04: `Odontologo`, `Paciente`, `Prestacion`, `HorarioAtencion`, `Bloqueo`, `Turno`, con `timestamptz` (`Instant`) para todo instante, DNI único, token único, `email_contacto`, `telefono_contacto`, `consentimiento_at`, `recordatorio_enviado`, `alerta_pendiente_enviada` y `cancelado_por`.
- Enums `EstadoTurno` (`PENDIENTE|CONFIRMADO|CANCELADO|ASISTIO|AUSENTE`) y `CanceladoPor` (`PACIENTE|ODONTOLOGO`) persistidos como texto.
- Repositorios Spring Data con CRUD y solo las consultas compartidas por varios dominios: turnos activos que intersecan un rango, bloqueos que intersecan un rango, horarios por día de la semana, odontólogo por email, paciente por DNI, turno por token.
- `schema-extra.sql` ampliado: además de `btree_gist`, crea de forma idempotente la constraint `turno_sin_solapamiento` (`EXCLUDE USING gist (tstzrange(inicio, fin, '[)') WITH &&) WHERE estado IN ('PENDIENTE','CONFIRMADO')`).
- `GlobalExceptionHandler` traduce la violación de esa constraint (SQLSTATE `23P01`) a 409 `HORARIO_OCUPADO` con el formato uniforme.
- `DataSeeder` idempotente con datos ficticios: odontólogo desde `SEED_ODONTOLOGO_*` (BCrypt), 4 prestaciones y horarios de lunes a viernes 09-13 y 15-19, solo si sus tablas están vacías.
- Puerto `NotificacionTurnos` (un método por mail de KB 05 "Mails del sistema") con una implementación no-op registrada por defecto, que C-11 reemplaza sin tocar a quienes la llaman (C-07, C-08, C-10).
- Fuera de alcance: services, controllers y endpoints de dominio; reglas de transición de estado; generación del token; envío real de mails. Esos los hacen C-04 en adelante.

## Capabilities

### New Capabilities
- `modelo-datos`: tablas y entidades del dominio, tipos de columnas, unicidades, estados del turno, restricción de no solapamiento entre turnos activos y consultas por rango compartidas.
- `datos-iniciales`: carga idempotente del odontólogo, las prestaciones y los horarios de ejemplo al arrancar, con datos ficticios.
- `notificaciones-turnos`: puerto con un aviso por cada mail del sistema, con una implementación por defecto que no envía nada hasta que exista la real.

### Modified Capabilities
- `plataforma-backend`: el script SQL complementario ahora también crea la restricción de no solapamiento (idempotente), y se agrega la traducción de su violación a 409 `HORARIO_OCUPADO`.

## Impact

- Código: `backend/src/main/java/ar/turnos/odontologia/{auth,paciente,prestacion,horario,bloqueo,turno,notificacion,seed,common}`, `backend/src/main/resources/schema-extra.sql`; tests de integración nuevos en `backend/src/test/java/...`.
- Contrato: sin cambios. `HORARIO_OCUPADO` (409) ya está en `docs/api/errores.md`; C-03 solo lo implementa.
- Base: aparecen las seis tablas y la constraint. En una base de desarrollo existente no hay datos que migrar (C-02 no creó tablas).
- Dependencias: ninguna nueva (JPA, PostgreSQL, Testcontainers y BCrypt ya están desde C-02).
- Desbloquea: C-04, C-05, C-06, C-07, C-09, C-10, C-11 (GATE 3).
