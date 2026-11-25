# Design

## Context

`Bloqueo`, `BloqueoRepository.findQueIntersecan`, `Turno`, `TurnoRepository.findActivosQueIntersecan` y la interfaz `NotificacionTurnos` (con no-op) existen desde C-03. No hay controller de bloqueos ni DTO de `Turno`. El 409 del contrato agrega `turnosAfectados` al error uniforme, que `ApiError`/`GlobalExceptionHandler` no expresan. Jackson serializa `Instant` en UTC (`Z`), pero el contrato exige offset `-03:00`. C-04 (JWT) corre en paralelo y no se pueden tocar `SecurityConfig`, `GlobalExceptionHandler`, `application.yml` ni `pom.xml`.

## Goals / Non-Goals

**Goals:**
- Implementar los 4 endpoints de `/api/bloqueos` exactamente como el contrato.
- Cancelación atómica de turnos + bloqueo; avisos después del commit, tolerantes a fallos.

**Non-Goals:**
- Calcular disponibilidad (C-06) y envío real de mails (C-11).
- Reprogramación automática de turnos o restitución al borrar un bloqueo.
- Cambios al contrato o a clases compartidas.

## Decisions

1. **Estructura en `bloqueo/`**: `BloqueoController`, `BloqueoService`, `BloqueoMapper`, DTOs `BloqueoRequest`, `BloqueoCrearRequest`, `BloqueoResponse`, `BloqueoPrevisualizacionResponse`, `BloqueoCreadoResponse`, `TurnoAfectadoResponse` (+ refs de paciente y prestación), más `BloqueoConTurnosAfectadosException` y `BloqueoExceptionAdvice`. Es el estilo de `prestacion/` y `horario/`.
2. **409 con `turnosAfectados` sin tocar compartidos**: `BloqueoConTurnosAfectadosException extends ApiException` (409, `BLOQUEO_CON_TURNOS_AFECTADOS`) lleva la lista ya mapeada a DTO. Un `@RestControllerAdvice(assignableTypes = BloqueoController.class)` con `@Order(HIGHEST_PRECEDENCE)` la captura antes que el handler global y arma un record con los campos de `ApiError` (vía `ApiErrorFactory`) más `turnosAfectados`. Alternativa descartada: agregar un campo a `ApiError` (clase compartida; además lo rompería para los demás errores).
3. **Avisos después del commit (RN-BLO-04)**: el método público del service no es `@Transactional`. Una `TransactionTemplate` ejecuta la escritura (crear bloqueo + cancelar turnos + `saveAndFlush`) y arma la respuesta dentro de esa transacción; al terminar (commit hecho), una segunda transacción de solo lectura recarga los turnos por id y llama `cancelacionPorBloqueo` por cada uno, con `try/catch(RuntimeException)` por turno y log sin datos personales. Se recarga en transacción nueva porque el contrato del puerto pide poder leer `paciente`/`prestacion` LAZY. Alternativa descartada: invocar el puerto dentro de la transacción (enviaría mails de algo que luego podría no confirmarse).
4. **Instantes con offset**: los DTOs exponen `OffsetDateTime` convertido con `AppProperties.zonaHoraria()`; `pendienteMenos24h` = estado `PENDIENTE` e `inicio` entre ahora y ahora+24 h, con el `Clock` inyectado (el contrato no dice si cuenta el pasado; se exige futuro).
5. **Requests**: `inicio`/`fin` como `Instant` con `@NotNull`, `motivo` `@Size(max=200)`; `cancelarTurnosAfectados` como `Boolean` (nulo = `false`). La previsualización usa un record sin ese campo.
6. **Validación de `GET`**: `desde`/`hasta` como `LocalDate` (parámetros opcionales a nivel Spring: ausentes → 400 `VALIDACION` como pide el contrato; formato inválido → 400 `SOLICITUD_MAL_FORMADA` por el handler global); `hasta < desde` o más de 31 días (conteo inclusivo) → `ApiException` 400 `VALIDACION` sin `detalles`. Rango consultado: `[desde 00:00, hasta+1 00:00)` en la zona de negocio.
7. **Concurrencia**: si entre la lectura y el commit entra un turno nuevo, no se lo cancela; la ventana es mínima en un consultorio de un sillón y se acepta (documentado como riesgo).
8. **Turno cancelado libera el horario**: la restricción `EXCLUDE` solo cubre estados activos, así que no hace falta nada más; un test lo prueba.

## Risks / Trade-offs

- Un turno reservado entre la lectura y el commit queda dentro del bloqueo sin cancelar; se acepta y la disponibilidad (C-06) ya excluye bloqueos.
- El 400 de `GET` no incluye `detalles` porque se emite con `ApiException`; el front solo reacciona al código.
- Duplicación de un DTO `Turno` en `bloqueo/` hasta que C-07 defina el propio; se podrá unificar luego.
