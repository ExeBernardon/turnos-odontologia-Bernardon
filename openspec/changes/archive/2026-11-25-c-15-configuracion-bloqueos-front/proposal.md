## Why

El odontólogo necesita bloquear la agenda (vacaciones, capacitaciones, imprevistos) desde `/admin/configuracion` y saber qué turnos se cancelarían antes de confirmar (US-004, Flujo 5). Hoy la sección **Bloqueos** es un placeholder ("Sección en construcción") aunque el backend (C-10) ya expone listar, previsualizar, crear y eliminar.

## What Changes

- Sección **Bloqueos**: listado de un rango de fechas (`GET /api/bloqueos?desde&hasta`, máximo 31 días inclusive; por defecto hoy y los 30 días siguientes, editable con dos campos de fecha), alta con inicio, fin y motivo opcional (hasta 200 caracteres) y baja con confirmación inline.
- Flujo de dos pasos: `POST /api/bloqueos/previsualizar` → si hay turnos afectados se listan con un aviso y el odontólogo confirma o cancela → `POST /api/bloqueos` con `cancelarTurnosAfectados=true`; sin turnos afectados se crea directo. Si entre la vista previa y la creación aparece un turno (409 `BLOQUEO_CON_TURNOS_AFECTADOS`) se muestra la lista nueva y se vuelve a pedir confirmación.
- Manejo de 422 `RANGO_HORARIO_INVALIDO` y 400 `VALIDACION` (detalles por campo).
- Fechas y horas mostradas en `America/Argentina/Buenos_Aires` con `Intl.DateTimeFormat('es-AR')`; los `datetime-local` se interpretan como hora de Buenos Aires y se envían como ISO-8601 con offset `-03:00`. Funciones puras de conversión con tests.
- Formularios accesibles y responsive. Sin cambios de contrato ni de backend.

## Capabilities

### New Capabilities
- `configuracion-bloqueos-front`: comportamiento de la sección de bloqueos de la pantalla de configuración del odontólogo.

### Modified Capabilities
(ninguna)

## Impact

- `frontend/src/admin/configuracion/bloqueos.ts` y nuevos `bloqueos.api.ts`, `bloqueos.logica.ts`, `fechas.ts` (+ tests); extensión aditiva de `parseo.ts`, `errores.ts` y `configuracion.css`.
- Consume `/api/bloqueos*` (JWT) vía `src/api/cliente.ts` con tipos de `src/api/types.ts`. No se tocan cliente HTTP, router, sesión, `vite.config.ts` ni `package.json`.
