# Proposal

## Why

La agenda de C-19 es de solo lectura: el odontólogo ve los turnos, pero no puede cargar los que coordina por teléfono, moverlos, confirmarlos después de llamar al paciente, cancelarlos ni registrar la asistencia (US-010, US-011, US-012, US-013, US-021). El backend ya tiene esas operaciones (C-07, C-08, C-09) y C-19 dejó los puntos de enganche en las tarjetas. Este change es el último paso del camino crítico antes de la integración E2E (C-21).

## What Changes

- **Acciones por turno en la agenda** (vista diaria y semanal), según el estado y la hora:
  - **Confirmar** (`POST /api/turnos/{id}/confirmar`) en los `PENDIENTE` que todavía no empezaron, y visible en los resaltados a menos de 24 h. No pide confirmación previa porque no es un estado terminal.
  - **Cancelar** (`POST /api/turnos/{id}/cancelar`) en cualquier turno activo, con un diálogo de confirmación que avisa que se le manda un email al contacto del turno.
  - **Marcar asistió / ausente** (`PATCH /api/turnos/{id}/estado`) en turnos activos, deshabilitado antes de la hora de inicio (con el motivo visible) y con un diálogo de confirmación porque el estado es terminal. Si el servidor responde 422, se informa con un mensaje claro.
  - **Reprogramar** (`PATCH /api/turnos/{id}/reprogramar`): elegir día y horario libre (`GET /api/disponibilidad` con la prestación del turno) y mostrar el estado que queda (`PENDIENTE` o `CONFIRMADO`) según la respuesta.
  - Los turnos `CANCELADO`, `ASISTIO` y `AUSENTE` no muestran acciones.
- **Carga manual de turnos** ("Nuevo turno" en la barra de la agenda): paciente existente con el buscador de C-16, o alta rápida (nombre, apellido y DNI en el mismo `POST /api/turnos`); prestación activa; día y horario libre de `GET /api/disponibilidad`; email y teléfono del turno precargados desde `ultimoContacto` y editables. 409 `HORARIO_OCUPADO` y 422 (`HORARIO_NO_DISPONIBLE`, `PRESTACION_INACTIVA`, `DNI_DUPLICADO`) se muestran con mensajes claros y la acción que corresponde (refrescar horarios, recargar prestaciones, marcar el campo DNI).
- Después de cada acción la agenda se vuelve a cargar, se anuncia el resultado en una región `role="status"` y el foco vuelve a un lugar predecible.
- Los botones deshabilitados de C-19 ("Confirmar (C-20)" y "Cancelar (C-20)") se reemplazan por las acciones reales. Se mantienen los atributos `data-enganche`.
- No se cambia el contrato (`docs/api/`), el backend, el cliente HTTP, el router ni `package.json`.

## Capabilities

### New Capabilities

- `agenda-acciones-front`: acciones del odontólogo sobre los turnos de la agenda (confirmar, cancelar, marcar asistencia, reprogramar) y carga manual de turnos, con diálogos de confirmación accesibles, manejo de errores del contrato y horas de Buenos Aires.

### Modified Capabilities

- `agenda-vistas-front`: el requisito "Identificación de turnos pendientes" decía que los enganches Confirmar y Cancelar no hacían nada. Ahora ejecutan las acciones que define `agenda-acciones-front`.

## Impact

- **Código nuevo (de la dueña del change)**: `frontend/src/admin/agenda-acciones/`, con API, lógica pura, diálogo, formularios de carga manual y reprogramación, estilos y tests.
- **Archivos de otros integrantes que se modifican** (regla 16, coordinarlo en el PR con Julieta, Int 5, dueña de C-19):
  - `frontend/src/admin/agenda/agenda.ts`: montar la barra, el aviso y el panel de acciones, y reemplazar el bloque de botones deshabilitados de `crearTarjeta` por las acciones reales.
  - `frontend/src/admin/agenda/agenda.api.ts`: exportar `parsearTurno`. Es un cambio de una palabra; la función hoy es privada.
  - `frontend/src/admin/agenda/agenda.test.ts`: la aserción de que los enganches están deshabilitados y no hacen nada deja de valer.
- **Módulos de otros integrantes que se importan sin modificarlos**: `admin/configuracion/{parseo,fechas,errores,formulario,prestaciones.api}.ts` (Int 2) y `admin/configuracion/{ejemplos,servidor}.testutil.ts` en los tests. También `shared/buscador-pacientes.ts`, `admin/pacientes.api.ts` y `admin/pacientes.logica.ts`, que son de C-16 y de la misma dueña.
- **API consumida**: `GET /api/turnos` (ya en uso), `POST /api/turnos`, `PATCH /api/turnos/{id}/reprogramar`, `POST /api/turnos/{id}/confirmar`, `POST /api/turnos/{id}/cancelar`, `PATCH /api/turnos/{id}/estado`, `GET /api/disponibilidad`, `GET /api/prestaciones`, `GET /api/pacientes`.
- **Gobernanza**: MEDIO. Se implementa por etapas y las decisiones no obvias se dejan para revisión.
