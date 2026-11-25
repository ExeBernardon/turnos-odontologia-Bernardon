## Why

El odontólogo hoy abre `/admin/agenda` y encuentra un placeholder, aunque C-08 ya expone el listado de turnos del rango con paciente, prestación, teléfono de contacto y el indicador `pendienteMenos24h`. Sin una vista diaria/semanal no puede organizar la jornada ni detectar los turnos `PENDIENTE` próximos que necesitan una decisión (US-009, Flujo 6).

## What Changes

- Reemplazar el placeholder de `/admin/agenda` por una pantalla de solo lectura con vista diaria y semanal: paciente, prestación, inicio, fin, estado y teléfono de contacto del turno; navegación entre días y semanas.
- Marcar los `PENDIENTE` como "sin confirmar" y resaltar los que tienen `pendienteMenos24h: true`, haciendo visible su teléfono. Los botones Confirmar y Cancelar de esos turnos quedan como puntos de enganche en el DOM para C-20; este change **no** ejecuta acciones sobre turnos.
- Mostrar bloqueos y rangos fuera del horario de atención de forma distinguible, consultando `GET /api/bloqueos` y `GET /api/horarios-atencion` junto con `GET /api/turnos`.
- Los turnos cancelados se ocultan por defecto y pueden mostrarse atenuados mediante un control.
- Formatear todas las horas en `America/Argentina/Buenos_Aires` con `Intl.DateTimeFormat('es-AR')`, sin depender de la zona del navegador; funciones puras de grilla y formato bajo `src/admin/agenda/`, con tests Vitest.
- Manejar errores de API y de conexión con mensajes comprensibles y opción de reintentar, respetando el formato uniforme del contrato. Sin cambios de contrato, endpoints ni backend.

## Capabilities

### New Capabilities
- `agenda-vistas-front`: comportamiento observable de las vistas diaria y semanal de la agenda del odontólogo, incluida la diferenciación visual de estados, bloqueos y horarios fuera de atención.

### Modified Capabilities
(ninguna)

## Impact

- `frontend/src/admin/agenda.ts` deja de ser placeholder y pasa a orquestar la pantalla; nuevos módulos en `frontend/src/admin/agenda/` (grilla, formato de fechas, acceso a API y parseo defensivo) con sus tests. Extensión aditiva de `frontend/src/admin/admin.css`.
- Consume `GET /api/turnos`, `GET /api/bloqueos` y `GET /api/horarios-atencion` (JWT) a través de `src/api/cliente.ts`, usando los tipos de `src/api/types.ts` y los ejemplos `docs/api/ejemplos/`.
- No se tocan el contrato, el backend, el cliente HTTP, el router, la sesión ni `package.json`. Deja puntos de enganche DOM para que C-20 agregue las acciones sin rehacer la grilla.
