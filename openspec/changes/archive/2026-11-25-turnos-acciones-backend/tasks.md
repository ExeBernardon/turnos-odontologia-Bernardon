# Tasks

## 1. Reglas puras de plazos y transiciones (TDD)

- [x] 1.1 Crear `PlazosTurno` con `confirmablePorPaciente` y `cancelablePorPaciente` usando `Clock` inyectado, bordes inclusivos de 48 h y 24 h y los mensajes de `docs/api/errores.md`; verificar con `PlazosTurnoTest` (rojo → verde) cubriendo 49 h / 48 h / 47 h y 25 h / 24 h / 23 h.
- [x] 1.2 Crear `TransicionesTurno` con `confirmar`, `cancelar(actor)`, `marcarAsistencia` y `reprogramar`, centralizando idempotencia, estados activos/terminales y `ApiException(422, TRANSICION_INVALIDA, ...)` con los mensajes del contrato; verificar con `TransicionesTurnoTest` para cada transición válida e inválida.

## 2. Soporte de errores, DTOs y consulta de agenda en base

- [x] 2.1 Sobrecargar `ApiException` con `List<DetalleError>` y propagar los detalles en `GlobalExceptionHandler` sin romper las llamadas existentes; verificar que un 400 `VALIDACION` con detalles siga armando el `ApiError` del contrato.
- [x] 2.2 Crear `ReprogramarRequest` (`inicio` con `InstanteConOffsetDeserializer` y `@NotNull`) y `EstadoAsistenciaRequest` (`estado` con `@AssertTrue` que solo admita `ASISTIO`/`AUSENTE`); verificar con tests de Bean Validation (body sin offset y estado inválido → violaciones).
- [x] 2.3 Agregar en `TurnoRepository` la consulta de turnos por rango de `inicio` ordenada; verificar con un IT que excluya los bordes y devuelva todos los estados.

## 3. Agenda autenticada

- [x] 3.1 Implementar `listarAgenda(desde, hasta, estado)` y `obtener(id)` en `TurnoService`, validando rango (obligatorio, `hasta >= desde`, máximo 31 días, enum válido) con 400 `VALIDACION` y detalles, y 404 `RECURSO_NO_ENCONTRADO`; verificar con `TurnoAgendaApiIT` (JWT, filtro, sin token, 400, 401 y 404).

## 4. Acciones del paciente por token

- [x] 4.1 Implementar `confirmarPublico(token)` y `cancelarPublico(token)` en `TurnoService` y sus endpoints en `TurnoPublicoController`, con orden existencia → idempotencia → transición → plazo y respuesta de resumen público; verificar con `TurnoAccionesTokenApiIT` (200, 404, 422 de transición y de plazo, idempotencia, bordes exactos de 48 h y 24 h y mensajes del contrato).

## 5. Acciones del odontólogo

- [x] 5.1 Implementar `confirmar(id)`, `cancelar(id)` y `marcarAsistencia(id, estado)` en `TurnoService` y sus endpoints en `TurnoController`, con cancelación no idempotente y aviso por cancelación vía `NotificacionTurnos`; verificar con `TurnoAccionesApiIT` (200, 401, 404, 422, idempotencia al confirmar, mensaje de asistencia antes del inicio y aviso mock verificado).

## 6. Reprogramación

- [x] 6.1 Implementar `reprogramar(id, inicio)` en `TurnoService` y su endpoint en `TurnoController`: solo activos, `validarHorario` con exclusión del propio turno, `saveAndFlush`, recálculo de `fin`/estado/flags con `EstadoInicialTurno` y aviso `reprogramacion`/`reprogramacionMenos24h`; verificar con `TurnoReprogramarApiIT` (200, 400 sin offset, 401, 404, 409, 422 de horario y de transición, cruce de umbral de 24 h en ambos sentidos, borde exacto y aviso mock verificado).
- [x] 6.2 Verificar con un IT concurrente que dos reprogramaciones sobre el mismo horario terminen en 200 y 409 `HORARIO_OCUPADO`, con un solo turno activo en ese rango.

## 7. Verificación integral

- [x] 7.1 Ejecutar la suite completa (`$env:JAVA_HOME=...; .\mvnw.cmd -q test` desde `backend`) y verificar 0 fallos, 0 errores y 0 omitidos.
- [x] 7.2 Verificar `git diff -- docs/api` vacío (ni OpenAPI, ni `errores.md`, ni ejemplos) y `openspec validate turnos-acciones-backend --strict` correcto.

## Workflow follow-up

- Hacer commit, push y PR a `main` desde la rama `feat/c-08-turnos-acciones-backend`.
- Sincronizar el delta a `openspec/specs/ciclo-vida-turnos/spec.md`, archivar el change y marcar C-08 como `[x]` en `CHANGES.md` con el número del PR.
