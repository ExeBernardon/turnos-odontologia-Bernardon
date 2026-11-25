# Proposal

## Why

C-07 ya permite crear turnos y consultarlos por token, pero el consultorio no puede todavía gestionar el ciclo de vida: confirmar, cancelar, reprogramar ni ver la agenda. Sin esas operaciones el paciente no puede confirmar/cancelar desde los links de los emails y el odontólogo no puede operar los turnos desde la agenda; C-18, C-19, C-20 y C-21 dependen de este backend.

## What Changes

- `GET /api/turnos?desde&hasta&estado` y `GET /api/turnos/{id}` para la agenda, con `pendienteMenos24h`, teléfono de contacto y sin token.
- Confirmación y cancelación por token (`POST /api/public/turnos/{token}/confirmar` y `/cancelar`) con idempotencia, plazos de 48 h y 24 h y evaluación existencia → idempotencia → transición → plazo.
- Confirmación, cancelación y marcado de asistencia del odontólogo (`POST /api/turnos/{id}/confirmar`, `/cancelar`, `PATCH /api/turnos/{id}/estado`), con la máquina de estados centralizada.
- Reprogramación (`PATCH /api/turnos/{id}/reprogramar`) reutilizando `validarHorario` con exclusión del propio turno, recalculando `fin`, estado y flags, con 409 por solapamiento y aviso al paciente.
- Avisos al paciente por cancelación del odontólogo (Mail 4) y reprogramación (Mail 3 / 3b), sin revertir la operación si falla el envío.
- No hay cambios en `docs/api/`: el contrato de C-01 ya declara estos endpoints, códigos y formatos.

## Capabilities

### New Capabilities

- `ciclo-vida-turnos`: transiciones de estado del turno, acciones por token y por odontólogo, reprogramación y consulta de agenda.

### Modified Capabilities

- (ninguna: `alta-turnos` cubre la creación y el resumen público; `modelo-datos`, `notificaciones-turnos` y `disponibilidad-calculo` se reutilizan sin cambiar sus requisitos)

## Impact

- Backend: nuevos DTOs y methods en `ar.turnos.odontologia.turno` (`TurnoService`, `TurnoController`, `TurnoPublicoController`, `TurnoRepository`, `TurnoMapper`) y una máquina de estados dedicada.
- Seguridad: los endpoints bajo `/api/turnos` ya exigen JWT por `SecurityConfig`; los de `/api/public/turnos/**` siguen abiertos.
- Base: sin migraciones nuevas; la restricción `EXCLUDE USING gist` existente sigue siendo la garantía contra solapamientos (también al reprogramar).
- Tests: unitarios de la máquina de estados y de reglas de plazo con `Clock` fijo e IT con PostgreSQL 16 (Testcontainers) para cada endpoint, idempotencia y bordes de 24 h y 48 h.
- Dependencias: C-04 (JWT) y C-07 (alta y token) ya están en `main`.
