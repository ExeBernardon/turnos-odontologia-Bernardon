# Proposal

## Why

Los emails de alta y de recordatorio ya traen los links `/turno/confirmar?token=...` y `/turno/cancelar?token=...` (C-09/C-11), y el backend ya resuelve el token (C-08), pero en el front esas dos rutas siguen siendo placeholders: el paciente que toca el link no puede confirmar ni cancelar su turno (US-007, US-008). Es la última pieza pública del MVP y bloquea la integración E2E (C-21).

## What Changes

- Reemplaza los placeholders `frontend/src/public/confirmar.ts` y `cancelar.ts` por pantallas reales que, al abrir el link, solo leen el turno con `GET /api/public/turnos/{token}` y muestran el resumen (nombre de pila, prestación y duración, fecha y horario en hora de Buenos Aires, estado, contacto del consultorio). **Abrir el link nunca cambia el estado.**
- Confirmar: botón "Confirmar asistencia" solo cuando `puedeConfirmar` es `true`; recién al apretarlo se hace `POST .../confirmar`.
- Cancelar: botón "Cancelar turno" solo cuando `puedeCancelar` es `true`; al apretarlo se pide una confirmación en pantalla y recién con "Sí, cancelar turno" se hace `POST .../cancelar`.
- Estados visibles: éxito; "ya estaba confirmado/cancelado" (idempotente); link inválido (token ausente, con largo fuera del contrato o 404 `TOKEN_INVALIDO`); 422 con un mensaje fijo por código (`CONFIRMACION_FUERA_DE_PLAZO`, `CANCELACION_FUERA_DE_PLAZO` con el contacto del consultorio, `TRANSICION_INVALIDA`); turno ya pasado; errores de conexión o del servidor con reintento.
- Nunca muestra DNI, apellido, email ni teléfono, y nunca muestra ni loguea el token.
- Reuso del código de C-17 (misma dueña): se exporta el parser `parsearResumen` de `reservar.api.ts` y `textoEstado` de `reservar.logica.ts` pasa a cubrir los cinco estados.

## Capabilities

### New Capabilities
- `turno-token-front`: páginas públicas a las que llevan los links con token de los emails: lectura del resumen sin efectos, confirmación y cancelación explícitas del paciente y los mensajes de cada resultado.

### Modified Capabilities
(ninguna: el cambio en `textoEstado` no altera lo que ve el paciente en `/reservar`, donde una reserva nueva solo puede quedar `PENDIENTE` o `CONFIRMADO`)

## Impact

- **Código (frontend, área pública)**: `src/public/confirmar.ts`, `src/public/cancelar.ts` (reemplazo de placeholders); nuevos `src/public/turno-token.ts`, `turno-token.api.ts`, `turno-token.logica.ts`, `turno-token.css` y sus tests; ajustes menores en `reservar.api.ts` (exportar el parser) y `reservar.logica.ts` (`textoEstado` completo), ambos de C-17, misma dueña.
- **Reuso sin modificar** de helpers de otros integrantes: `admin/configuracion/{parseo,formulario}.ts` y las utilidades de test `servidor.testutil.ts` / `ejemplos.testutil.ts` (Int 2), como ya hace C-17.
- **Contrato y backend**: sin cambios. Se usan `GET /api/public/turnos/{token}`, `POST .../confirmar`, `POST .../cancelar`, `TurnoResumenPublico` y los ejemplos `turnos-publico-obtener.*`, `turnos-publico-confirmar.*`, `turnos-publico-cancelar.*`.
- **Rutas**: `public/rutas.ts` y `rutas.test.ts` no cambian (se conservan los `h1` "Confirmar turno" y "Cancelar turno").
- **Dependencias**: ninguna nueva.
