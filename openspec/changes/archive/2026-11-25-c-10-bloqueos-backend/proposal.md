# Proposal

## Why

El odontólogo necesita ausentarse (capacitación, feriado, vacaciones) sin dejar pacientes sin aviso (US-004). Hoy existe la entidad `Bloqueo` (C-03) pero ninguna API para gestionarla, y el front de configuración (C-15) y la disponibilidad (C-06) dependen de ella.

## What Changes

- `GET /api/bloqueos?desde&hasta`: lista los bloqueos que se superponen con el rango de días (máximo 31 días).
- `POST /api/bloqueos/previsualizar`: lista los turnos activos (`PENDIENTE`/`CONFIRMADO`) que intersecan `[inicio, fin)` sin modificar nada.
- `POST /api/bloqueos`: crea el bloqueo. Con `cancelarTurnosAfectados=true` cancela los turnos afectados (`CANCELADO`, `canceladoPor=ODONTOLOGO`) en la misma transacción y después del commit avisa a cada uno (Mail 5). Con `false` y turnos afectados responde 409 `BLOQUEO_CON_TURNOS_AFECTADOS` con `turnosAfectados`. Sin turnos afectados crea directo (201).
- `DELETE /api/bloqueos/{id}`: elimina el bloqueo (404 si no existe); no restituye turnos cancelados.
- Validación `inicio < fin` (422 `RANGO_HORARIO_INVALIDO`); los bloqueos pueden solaparse entre sí.
- Sin cambios de contrato: `docs/api/openapi.yaml` ya define todo.

## Capabilities

### New Capabilities
- `bloqueos-agenda`: alta, listado, vista previa y baja de bloqueos de agenda, con cancelación atómica de los turnos afectados y aviso al paciente.

### Modified Capabilities
<!-- Ninguna: los requisitos de api-contrato, api-errores y notificaciones-turnos no cambian. -->

## Impact

- Código nuevo solo en `backend/src/main/java/ar/turnos/odontologia/bloqueo/` y sus tests.
- Usa `TurnoRepository.findActivosQueIntersecan`, `BloqueoRepository.findQueIntersecan` y `NotificacionTurnos.cancelacionPorBloqueo` ya existentes.
- No toca `SecurityConfig`, `GlobalExceptionHandler`, `application.yml` ni `pom.xml` (C-04 en paralelo).
- Governance MEDIO: las decisiones no obvias están en `design.md`.
