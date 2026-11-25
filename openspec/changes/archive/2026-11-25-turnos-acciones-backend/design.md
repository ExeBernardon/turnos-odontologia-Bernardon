# Design

## Context

C-07 dejó en `main` la alta (pública y manual), la consulta por token, `TurnoService` con `saveAndFlush`, `TurnoMapper`, `EstadoInicialTurno`, `NotificacionTurnos` y la sobrecarga de `DisponibilidadService.validarHorario` con exclusión de turno. `SecurityConfig` ya protege `/api/**` y libera `/api/public/**`. El contrato de C-01 ya define todos los endpoints, DTOs y códigos de este change; `docs/api/` no se toca.

## Goals / Non-Goals

**Goals:**
- Un único lugar para decidir si una transición de estado es válida y con qué mensaje.
- Plazos de 48 h y 24 h deterministas en tests (`Clock` inyectado) con bordes inclusivos.
- Idempotencia y orden de evaluación idénticos en las acciones por token y por id.
- Reprogramar con la misma garantía de no solapamiento de la base que el alta.

**Non-Goals:**
- No implementar envío de emails real (C-11), ni jobs de recordatorio/alerta (C-11).
- No modificar el contrato OpenAPI, los ejemplos ni `errores.md`.
- No agregar borrado de turnos, lista de espera ni exportación (C-22, C-24, C-25).

## Decisions

### 1. Extender `TurnoService` en lugar de crear otro service

El service ya tiene las dependencias que las acciones necesitan (`TurnoRepository`, `DisponibilidadService`, `NotificacionTurnos`, `Clock`, `TurnoMapper`) y la anotación `@Transactional` que asegura `saveAndFlush` + avisos en la misma transacción. Crear un `TurnoAccionesService` separado duplicaría inyecciones y rompería la coherencia de las reglas de alta/reprogramación. Se mantienen controllers delgados: solo validan `@Valid` y delegan.

### 2. Máquina de estados como clase pura `TransicionesTurno`

Clase sin Spring ni repositorios, con métodos `confirmar`, `cancelar(actor)`, `marcarAsistencia` y `reprogramar` que reciben el `Turno`, el `Clock` y, cuando hace falta, el actor (`PACIENTE`/`ODONTOLOGO`). Centraliza: estados activos/terminales, idempotencia, mensajes exactos de `docs/api/errores.md` y lanza `ApiException(422, TRANSICION_INVALIDA, ...)`. Alternativa descartada: `if (estado == ...)` repartido en service y controllers (duplicación y mensajes divergentes).

### 3. Plazos en un helper puro `PlazosTurno`

`Duration.between(clock.instant(), inicio)` con umbrales de 24 h y 48 h; `confirmablePorPaciente` exige `anticipacion <= 48 h` e `inicio` no pasado; `cancelablePorPaciente` exige `anticipacion >= 24 h`. El borde exacto es válido en ambos casos (lo que C-07 ya aplica para el estado inicial). Los mensajes de `CONFIRMACION_FUERA_DE_PLAZO` y `CANCELACION_FUERA_DE_PLAZO` salen de `errores.md`. Alternativa descartada: comparar `Instant` directamente en el service, sin reutilización ni tests aislados.

### 4. Orden de evaluación una sola vez

Los cuatro métodos públicos de acción siguen: (1) buscar por token/id → 404; (2) si ya está en el estado destino → 200 sin efectos ni mails; (3) `TransicionesTurno` → 422 `TRANSICION_INVALIDA`; (4) `PlazosTurno` → 422 con código de plazo. La excepción es la cancelación del odontólogo, que no es idempotente según el contrato (sobre cancelado o terminal → 422). Así un turno cancelado con menos de 24 h responde 200 al paciente, no 422 de plazo.

### 5. Reprogramación sobre la restricción de exclusión existente

Se valida con `disponibilidad.validarHorario(prestacion, inicio, new ContextoValidacion(ODONTOLOGO, turno.getId()))` (el turno propio no se cuenta como ocupante), se actualizan `inicio` y `fin = inicio + duración` y se llama a `saveAndFlush`. PostgreSQL rechaza solapamientos con SQLSTATE `23P01` y `GlobalExceptionHandler` ya lo traduce a 409 `HORARIO_OCUPADO`. Después se recalcula estado y flags con `EstadoInicialTurno.calcular` y se pide `reprogramacion` o `reprogramacionMenos24h` según el nuevo inicio. El aviso no revierte la operación: la implementación real de `NotificacionTurnos` no lanza por fallo SMTP.

### 6. Agenda por rango con filtro en memoria

`TurnoRepository.findByInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(desde, hasta)` con los bordes del día en `AppProperties.zonaHoraria()`. El filtro opcional por `estado` se aplica en el service sobre esa lista (máximo 31 días, un solo odontólogo). Alternativa descartada: `JPA Specification` dinámica, más infraestructura para una lista chica. `TurnoMapper.aAgenda` ya produce el DTO con `pendienteMenos24h` y sin token.

### 7. Validación de parámetros de agenda con detalles

Se agrega un constructor sobrecargado de `ApiException` que acepta `List<DetalleError>` y el handler lo propaga al `ApiError`. Así `desde`, `hasta` y `estado` inválidos producen 400 `VALIDACION` con `detalles`, como el resto del contrato. Alternativa descartada: Bean Validation en `@RequestParam`, porque un parámetro ausente cae en `MissingServletRequestParameterException` y se traduce a `SOLICITUD_MAL_FORMADA`. El cambio es retrocompatible: las llamadas actuales siguen sin detalles.

### 8. DTOs de cuerpo con validación explicita

`ReprogramarRequest` reutiliza `InstanteConOffsetDeserializer` y `@NotNull` para que un `inicio` sin offset sea 400 `VALIDACION` con detalle. `EstadoAsistenciaRequest` usa `@NotNull EstadoTurno estado` más un `@AssertTrue` en el accessor que restringe el valor a `ASISTIO`/`AUSENTE` (el mismo patrón de `TurnoManualRequest.getPacienteUnico`), para responder 400 `VALIDACION` en vez de depender del mensaje de deserialización del enum.

## Risks / Trade-offs

- **[Riesgo] Un paciente confirma justo cuando el odontólogo cancela** → cada operación es `@Transactional` y la transición se decide sobre el estado leído; el perdedor recibe 422 `TRANSICION_INVALIDA`. No se agrega bloqueo pesimista: no está en el contrato y el volumen es de un consultorio.
- **[Riesgo] Reprogramar a un horario que otro turno tomó en paralelo** → `saveAndFlush` + `23P01` → 409; se cubre con un IT concurrente.
- **[Riesgo] Fallo SMTP al cancelar o reprogramar** → `NotificacionTurnos` garantiza no lanzar; la operación queda persistida igual y se documenta en tests.
- **[Trade-off] Filtro de estado en memoria** → simple y suficiente; si la agenda creciera, se reemplaza por una query sin cambiar el contrato.

## Migration Plan

Sin migración de esquema: se reutilizan tablas, columnas y la restricción de exclusión ya desplegadas. El deploy es el merge del PR a `main`. Rollback: revertir el commit; no hay datos nuevos que limpiar porque no se agregan columnas ni tablas.

## Open Questions

Ninguna: los plazos, bordes, mensajes y formatos ya están decididos en `docs/api/` y en la KB.
