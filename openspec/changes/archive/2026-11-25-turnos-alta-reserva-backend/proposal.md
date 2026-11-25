# Proposal

## Why

El contrato, las entidades y el cálculo de disponibilidad ya están listos (C-01, C-03, C-06), pero el backend todavía no sabe crear turnos: no hay reserva pública, carga manual ni consulta por token. Sin esto no se puede cerrar el camino crítico ni arrancar C-08 (acciones), C-17 (reserva pública) ni C-20 (agenda).

## What Changes

- Implementar `POST /api/public/turnos`: validación de formato (400 con `detalles`), consentimiento obligatorio (422 `CONSENTIMIENTO_REQUERIDO` con `consentimiento_at`), prestación activa, horario validado con `validarHorario` de C-06, paciente resuelto por DNI normalizado (crea o reutiliza **sin modificarlo**) y datos de contacto guardados en el turno.
- Generar el token opaco del turno con 32 bytes de `SecureRandom` en Base64 URL-safe; nunca devolverlo por la API ni loguearlo.
- Implementar `POST /api/turnos` (JWT) para carga manual con paciente existente o alta rápida, `emailContacto`/`telefonoContacto`, sin mínimo de 2 h (solo no en el pasado) y sin consentimiento.
- Calcular el estado inicial según anticipación (24 h): `PENDIENTE` con los flags en `false`, o `CONFIRMADO` con `recordatorio_enviado` y `alerta_pendiente_enviada` en `true`; persistir `fin = inicio + duración` de la prestación.
- Insertar con `saveAndFlush` dentro del service para capturar la violación de la restricción de exclusión de PostgreSQL y devolver 409 `HORARIO_OCUPADO` (la base sigue siendo la garantía anti-solapamiento).
- Enviar los avisos de creación según anticipación (>48 h, 24-48 h, <24 h) a través de `NotificacionTurnos`, sin que un fallo de SMTP revierta la reserva; actualizar los flags solo según el resultado del envío.
- Implementar `GET /api/public/turnos/{token}` como lectura sin efecto: resumen mínimo sin DNI, email, teléfono, ids ni token; 404 `TOKEN_INVALIDO` si no existe.
- Cubrir con tests TDD: carrera de dos hilos por el mismo horario (201 + 409, Testcontainers), paciente existente no modificado, bordes de 24 h, sin consentimiento, token inexistente y fallo de mail sin rollback.
- **Sin cambios al contrato**: todos los paths, DTOs y códigos ya están declarados en `docs/api/openapi.yaml` y `docs/api/errores.md`.

## Capabilities

### New Capabilities

- `alta-turnos`: creación de turnos (reserva pública y carga manual), resolución de paciente por DNI, cálculo del estado inicial y los flags de aviso, token opaco de consulta, resumen público por token y avisos disparados al crear.

### Modified Capabilities

_Ninguna. Los comportamientos de `api-contrato`, `api-errores`, `disponibilidad-calculo` y `notificaciones-turnos` ya cubren lo que este change implementa; no cambian sus requirements._

## Impact

- Backend: nuevos controller/service/DTO en `ar.turnos.odontologia.turno`, reutilización de `paciente`, `prestacion`, `disponibilidad` (`validarHorario`), `notificacion` y `common` (errores y detección de SQLSTATE 23P01).
- Seguridad: las rutas `/api/public/**` ya son públicas y `POST /api/turnos` ya exige JWT en `SecurityConfig`; no se cambian reglas de acceso.
- Base de datos: se usa la restricción `turno_sin_solapamiento` existente en `schema-extra.sql`; no se requieren migraciones nuevas.
- Dependencias: C-03 (entidades y seed) y C-06 (validación de horario), ambos ya en `main`; C-04 aporta el JWT de la carga manual.
- Tests: Testcontainers con PostgreSQL 16 y `Clock` fijo para los bordes temporales; datos ficticios exclusivamente.
- Consumidores desbloqueados: C-08, C-17 y C-20.
