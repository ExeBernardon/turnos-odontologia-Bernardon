## Context

C-14 dejó en `frontend/src/admin/configuracion/` una estructura por capas (api / lógica / DOM) y helpers compartidos (`errores.ts`, `formulario.ts`, `parseo.ts`, `configuracion.css`, utilidades de test). `bloqueos.ts` es un placeholder. El contrato (`docs/api/openapi.yaml`, `errores.md`, `ejemplos/bloqueos-*.json`) y el backend (C-10) están cerrados; los tipos `Bloqueo*` y `ErrorBloqueoConflicto` ya existen en `src/api/types.ts`.

## Goals / Non-Goals

**Goals:** sección de bloqueos completa y accesible con flujo previsualizar → confirmar, fechas en hora de Buenos Aires, tests TDD.
**Non-Goals:** cambios de contrato/backend, calendario visual, edición de bloqueos (el contrato no tiene PUT), tocar cliente HTTP, router o sesión.

## Decisions

1. **Rango de listado: dos `input type=date` (desde/hasta), por defecto hoy..hoy+30 días.** Son 31 días inclusive (límite del contrato) y es más simple y accesible que una navegación por páginas; se valida localmente (`desde <= hasta`, máximo 31 días) antes de consultar. "Hoy" se calcula en Buenos Aires, no en la zona del navegador.
2. **Fechas en `fechas.ts` (puro).** Buenos Aires no tiene DST desde 2009, por lo que el offset fijo `-03:00` es correcto: `aInstante('2026-12-24T09:00')` devuelve `2026-12-24T09:00:00-03:00`; `formatear(instante)` usa `Intl.DateTimeFormat('es-AR', { timeZone: 'America/Argentina/Buenos_Aires', ... })`. Los tests fijan instantes UTC para no depender de la zona de la máquina.
3. **Pasos en `bloqueos.logica.ts` + DOM.** La lógica pura valida el formulario y arma el `BloqueoCrearRequest`; el DOM orquesta: formulario, previsualización, confirmación con lista (si hay afectados), creación. La confirmación muestra los turnos (fecha, paciente, prestación, estado) y los botones "Confirmar y cancelar turnos" / "Volver".
4. **409 en la creación:** se parsea `turnosAfectados` de `ErrorApi.cuerpo` (parser defensivo en `bloqueos.api.ts`), se re-muestra la confirmación con la lista nueva y no se crea nada hasta una nueva confirmación.
5. **Reuso:** `crearCampo`, `crearAvisos`, `mostrarErrorServidor`, `crearBoton`, `pedir()`. Cambios aditivos en `parseo.ts` (`leerTextoONulo`) y en los mensajes de `errores.ts`.
6. **Baja:** confirmación inline como en C-14, con la nota "No restituye turnos cancelados".

## Risks / Trade-offs

- El offset fijo `-03:00` supone que Argentina no vuelve a usar horario de verano; si cambiara, solo `fechas.ts` se ajusta.
- `datetime-local` no maneja segundos ni zona; se acepta (la agenda va en grilla de 15 minutos).
- jsdom podría no soportar `Intl` con `timeZone`; Node con ICU completo lo soporta, se verifica en los tests.
