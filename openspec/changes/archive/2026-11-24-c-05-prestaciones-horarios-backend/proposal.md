## Why

El odontólogo necesita configurar su consultorio (catálogo de prestaciones y horarios de atención) y el paciente necesita ver las prestaciones activas para reservar. C-06 (disponibilidad) y C-14 (front de configuración) dependen de estos endpoints (US-002, US-003).

## What Changes

- `GET/POST /api/prestaciones`, `PUT/DELETE /api/prestaciones/{id}` (JWT); DELETE responde 422 `PRESTACION_CON_TURNOS` si la prestación tiene turnos, 404 si no existe.
- `GET /api/public/prestaciones` (pública): solo prestaciones activas, sin el campo `activa`.
- `GET/POST /api/horarios-atencion`, `PUT/DELETE /api/horarios-atencion/{id}` (JWT); 422 `RANGO_HORARIO_INVALIDO` si `horaInicio >= horaFin`, 422 `HORARIOS_SOLAPADOS` si se solapa con otro rango del mismo día (rangos semiabiertos: adyacentes permitidos; varios rangos por día permitidos).
- Validación con Bean Validation (400 `VALIDACION` con `detalles`); cambiar la duración no modifica turnos existentes.
- DTOs y mappers manuales; las entidades no se exponen. Capas controller, service, repository.
- Sin cambios al contrato: `docs/api/openapi.yaml` ya define todo.

## Capabilities

### New Capabilities
- `prestaciones`: ABM de prestaciones y listado público de activas.
- `horarios-atencion`: ABM de rangos de atención semanales con validación de rango y solapamiento.

### Modified Capabilities

## Impact

- Código nuevo en `backend/.../prestacion/` y `backend/.../horario/` y sus tests.
- No toca `SecurityConfig`, `GlobalExceptionHandler`, `application.yml` ni `pom.xml`.
- Tests de endpoints privados con `@WithMockUser` hasta que C-04 esté en `main`.
- Fuera de alcance: cálculo de disponibilidad (C-06), bloqueos (C-09), UI (C-14).
