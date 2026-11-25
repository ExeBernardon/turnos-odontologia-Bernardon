## Why

El odontólogo necesita administrar desde la pantalla `/admin/configuracion` el catálogo de prestaciones y los rangos de horario de atención (US-002, US-003). Hoy ambas secciones son placeholders ("Sección en construcción") aunque el backend (C-05) ya expone los endpoints reales.

## What Changes

- Sección **Prestaciones**: listado (activas e inactivas), alta, edición de nombre y duración, activar/desactivar y baja; ante 422 `PRESTACION_CON_TURNOS` al borrar, mensaje claro que ofrece desactivar en su lugar.
- Sección **Horarios de atención**: rangos agrupados por día de la semana (varios por día), alta, edición y baja; muestra los 422 `RANGO_HORARIO_INVALIDO` y `HORARIOS_SOLAPADOS`.
- Validación de formato en el front (nombre obligatorio, duración entera 1..480, `inicio < fin`) y manejo del error uniforme (400 `VALIDACION` con `detalles` por campo).
- Formularios accesibles (label asociado, foco visible, teclado completo) y responsive.
- Sin cambios de contrato ni de backend; no se tocan el cliente HTTP, el router ni la sesión (los cubre C-13).

## Capabilities

### New Capabilities
- `configuracion-prestaciones-horarios-front`: comportamiento de las secciones de prestaciones y horarios de atención de la pantalla de configuración del odontólogo.

### Modified Capabilities
(ninguna)

## Impact

- `frontend/src/admin/configuracion/prestaciones.ts`, `horarios.ts` y nuevos módulos/tests/CSS bajo esa carpeta.
- Consume `/api/prestaciones` y `/api/horarios-atencion` (JWT) vía `src/api/cliente.ts`, con tipos de `src/api/types.ts`.
- `bloqueos.ts` (C-15) queda intacto.
