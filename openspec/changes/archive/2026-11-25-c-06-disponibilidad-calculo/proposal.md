# Proposal

## Why

La reserva pública y la agenda necesitan saber qué horarios están libres, pero hoy no existe ese cálculo: el modelo de datos lo deja explícito fuera de las entidades y los changes C-07/C-08 dependen de una validación de horario común. Sin este núcleo testeable, cada dominio terminaría reimplementando reglas de atención, bloqueos, pasado y ventana con criterios distintos (RN-DIS-01..06).

## What Changes

- Agregar `DisponibilidadService` como lógica pura sobre listas de intervalos con `Clock` inyectado: restar bloqueos, turnos activos, pasado y ventana a los rangos de atención, y discretizar en pasos de `app.agenda.paso-minutos` alineados al inicio de cada rango, exigiendo que el turno completo quepa en el rango.
- Exponer `validarHorario(prestacion, inicio, contexto)` como interfaz pública del módulo para que reserva pública (C-07), carga manual y reprogramación (C-08) reutilicen la misma validación y devuelvan 422 `HORARIO_NO_DISPONIBLE`.
- Implementar `GET /api/public/disponibilidad` (pública, ventana de 2 h a 60 días) y `GET /api/disponibilidad` (JWT, solo "no en el pasado"), con el schema `Disponibilidad` del contrato: un elemento por fecha del rango y `horarios: []` cuando no hay lugar.
- Resolver prestación inexistente (404) e inactiva (422 `PRESTACION_INACTIVA`), y validar el rango `desde`/`hasta` (400 `VALIDACION` si está invertido o supera 31 días).
- Convertir horas locales de atención a instantes con `America/Argentina/Buenos_Aires` y devolver ISO-8601 con offset, sin depender del huso del sistema.
- Cubrir con tests unitarios TDD los bordes: rangos múltiples por día, bloqueo parcial, turno activo que parte el rango, duración que no entra, paso de 15, bordes de 2 h y 60 días con `Clock` fijo, turno cancelado que no ocupa y día sin atención.

## Capabilities

### New Capabilities

- `disponibilidad-calculo`: cálculo al vuelo de horarios libres por prestación y fecha, su discretización en grilla, la ventana pública frente a la consulta del odontólogo y la validación reutilizable de horarios para crear o reprogramar turnos.

### Modified Capabilities

Ninguna: el contrato (`api-contrato`), los códigos de error (`api-errores`) y los specs de prestaciones, horarios y bloqueos ya definen este comportamiento; C-06 solo lo implementa.

## Impact

- Backend: paquete nuevo `ar.turnos.odontologia.disponibilidad` (service puro, DTOs de respuesta, controllers público y privado); reutiliza `TurnoRepository`, `BloqueoRepository`, `HorarioAtencionRepository`, `PrestacionRepository`, `Clock`, `AppProperties` y `ApiException` ya existentes.
- APIs: `GET /api/public/disponibilidad` y `GET /api/disponibilidad` (según `docs/api/openapi.yaml`); sin cambios de contrato.
- Dependencias: C-03 (ya en `main`). Desbloquea C-07 y C-08, que consumen `validarHorario(...)`; la interfaz se publica al arrancar el change.
- Seguridad: la ruta pública ya está en `permitAll` y la privada exige JWT por `SecurityConfig` de C-02; sin cambios de configuración.
- Datos: ninguna entidad, tabla ni migración nueva; los horarios libres no se persisten.
