# Design

## Context

Ver `proposal.md` para la motivación y `specs/disponibilidad-calculo/spec.md` para el comportamiento exigido. El repo ya tiene (C-01 a C-05, C-10): contrato OpenAPI con los schemas `Disponibilidad`/`DiaDisponible`/`PrestacionRef`, `AppProperties` con zona horaria, paso de grilla y ventana configurable, `Clock` inyectable, `ApiException` + `GlobalExceptionHandler`, y las consultas por rango en `TurnoRepository.findActivosQueIntersecan` y `BloqueoRepository.findQueIntersecan`. La security ya habilita `/api/public/**` y exige JWT en `/api/disponibilidad`. El paquete `disponibilidad/` existe vacío.

## Goals / Non-Goals

**Goals:**

- Núcleo de cálculo determinista, testeable con `Clock.fixed`, sin base de datos ni contenedores.
- Una sola implementación de la validación de horario compartida por reserva, carga manual y reprogramación, con una interfaz publica desde el primer día del change para desbloquear C-07/C-08.
- Respuestas idénticas a los ejemplos `disponibilidad-publica.200.json` y `disponibilidad-privada.200.json` del contrato.

**Non-Goals:**

- Persistir horarios libres, crear entidades o migraciones.
- Bloquear atómicamente un horario: eso sigue siendo responsibilidad del INSERT y la restricción `EXCLUDE` (C-03/C-07); la disponibilidad es informativa (RN-DIS-05).
- Cambiar el contrato, los códigos de error o `SecurityConfig`.
- Calcular disponibilidad de múltiples profesionales o sillones (fuera de v1, RN-REC-01).

## Decisions

### 1. Núcleo puro + fachada de consulta

`Intervalo(Instant inicio, Instant fin)` es un record inmutable con operaciones puras `restar(Collection<Intervalo>)`, `interseca(...)`, `contiene(Instant)`. `CalculoDisponibilidad` es una clase sin anotaciones Spring que recibe por constructor los parámetros del cálculo (`ZoneId`, paso en minutos, anticipación mínima, ventana máxima) y expone:

- `List<Instant> horariosLibres(LocalDate dia, int duracionMinutos, List<HorarioAtencion> horarios, List<Intervalo> ocupados, ModoDisponibilidad modo, Instant ahora)`
- `boolean esValido(Instant inicio, int duracionMinutos, List<HorarioAtencion> horarios, List<Intervalo> ocupados, ModoDisponibilidad modo, Instant ahora)`

`DisponibilidadService` (`@Service`, `@Transactional(readOnly = true)`) inyecta repositorios, `Clock` y `AppProperties`; carga datos y delega en el núcleo. Expone la API de dominio `validarHorario(Prestacion prestacion, Instant inicio, ContextoValidacion contexto)` y su sobrecarga con `turnoExcluidoId` para reprogramación.

*Alternativa descartada*: un único `@Service` con repositorios y aritmética mezclada obligaría a Testcontainers para probar los bordes de grilla; la separación permite TDD unitario puro (prioridad 1 de KB 08).

### 2. Interfaz de validación publicada primero

`validarHorario` y el enum `ModoDisponibilidad` (`PUBLICA`, `ODONTOLOGO`) viven en el paquete `disponibilidad`, sin dependencias de `turno`. `ContextoValidacion` es un record con `modo` y `turnoExcluidoId` nullable: en reprogramación se excluye el propio turno de la lista de ocupantes para que no se valide contra sí mismo. Cualquier incumplimiento lanza `ApiException(422, "HORARIO_NO_DISPONIBLE", mensajeConCausa)`. La prestación inactiva e inexistente se resuelve antes, con 422 `PRESTACION_INACTIVA` y 404 `RECURSO_NO_ENCONTRADO`.

*Alternativa descartada*: que C-07/C-08 consulten `GET /api/disponibilidad` por HTTP; acopla servicios y no da el mensaje de causa.

### 3. Resta de intervalos y grilla estable

Cada rango de atención se convierte a un `Intervalo` con `hora.atDate(dia).atZone(zona).toInstant()` (los rangos son locales, recurrentes semanalmente; el día de la semana se obtiene de `LocalDate.getDayOfWeek()`). Se restan primero los ocupantes (bloqueos y turnos activos que intersecan el día) y después se acota por `ahora`/ventana; el resultado es una lista de sub-rangos. Los candidatos se generan desde `inicioDelRangoOriginal.plus(n * paso)` —nunca desde el borde de un sub-rango— y se aceptan solo si `candidato + duracion <= finDelSubrango`. Así un bloqueo parcial no reordena la grilla. Todos los rangos son semiabiertos `[inicio, fin)`: tocar un borde no cuenta como solapamiento (RN-TUR-02).

*Alternativa descartada*: generar candidatos desde el fin del ocupante anterior; produce grillas distintas según los turnos existentes (contradice RN-DIS-03).

### 4. Consulta por rango con tres lecturas

`DisponibilidadService.consultar(prestacionId, desde, hasta, modo)` valida el rango (máx. 31 días), resuelve la prestación, calcula `ini = desde.atStartOfDay(zona)` y `fin = hasta.plusDays(1).atStartOfDay(zona)`, y hace una única consulta por repositorio para todo el periodo: horarios de atención agrupados por `DiaOfWeek` presentes en el rango, bloqueos y turnos activos que intersecan. Luego distribuye los ocupantes por día y llama al núcleo por cada fecha.

*Alternativa descartada*: una consulta por día (hasta 31 × 3). Más round-trips y el mismo resultado.

### 5. DTOs y serialización

`DisponibilidadResponse(PrestacionRefResponse prestacion, List<DiaDisponibleResponse> dias)`, `DiaDisponibleResponse(LocalDate fecha, List<OffsetDateTime> horarios)` y `PrestacionRefResponse(Long id, String nombre, int duracionMinutos)`, sin exponer entidades. Cada instante se convierte con `OffsetDateTime.ofInstant(instante, zona)` para garantizar `-03:00`. Los controllers son `DisponibilidadPublicaController` (`/api/public/disponibilidad`) y `DisponibilidadController` (`/api/disponibilidad`), con `@GetMapping`, parámetros `prestacionId`, `desde`, `hasta` y el mismo `DisponibilidadService` con el `ModoDisponibilidad` correspondiente. La validación del rango se hace en el service con `ApiException(400, "VALIDACION", ...)`, igual que `BloqueoService.listar`, para no depender del mensaje del binding de Spring.

### 6. Estrategia de tests

Tests unitarios con `Clock.fixed` para: rangos múltiples por día, bloqueo parcial, turno que parte el rango, duración que no entra, paso de 15 alineado al inicio, bordes exactos de 2 h y 60 días, `inicio <= ahora`, turno cancelado, día sin atención y reprogramación que excluye el turno propio. Test de API (`@WebMvcTest` o MockMvc con el service mockeado, sin Testcontainers) para las claves del JSON y los códigos 400/404/422. La constraint de solapamiento ya está cubierta por C-03; no se repite acá.

## Risks / Trade-offs

- [La resta de intervalos con DST o cambios de zona podría desalinear la grilla] → Argentina no tiene horario de verano (DD-10) y la conversión usa siempre `app.zona-horaria`; si se agregara otra zona, el cálculo queda aislado en `CalculoDisponibilidad`.
- [Un turno que cruza medianoche y horarios de atención que terminan después de las 24 h] → fuera del seed y de la v1; los rangos se validan con `horaInicio < horaFin` en C-05, por lo que no existen rangos nocturnos.
- [Consultar cada minuto de un rango de 31 días puede ser costoso] → se generan solo los candidatos de la grilla (máximo ~96 por día), no instantes por minuto.
- [C-07/C-08 podrían seguir su propio criterio si la interfaz llega tarde] → publicar `validarHorario` y los DTOs en el primer task del change, antes de completar los controllers.
- [La disponibilidad es informativa y puede quedar desactualizada entre consulta y reserva] → es decisión de diseño (RN-DIS-05); el 409 de la base es la garantía final y el front ya sabe refrescar horarios.

## Migration Plan

Sin migración de esquema ni datos. Se mergea a `main` por PR de la rama `cande/c-06-disponibilidad-calculo` y se marca `[x]` el change en `CHANGES.md` en el mismo PR. Rollback: revertir el PR; no deja datos persistentes.

## Open Questions

Ninguna que bloquee el change. El paso, la anticipación y la ventana ya son configurables por `application.yml`; los valores por defecto (15 / 2 / 60) están fijados en la KB y en el contrato.
