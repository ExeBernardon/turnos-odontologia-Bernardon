# bloqueos-agenda Specification

## Purpose

Permitir al odontólogo bloquear rangos de su agenda, ver qué turnos activos se ven afectados antes de confirmar y cancelarlos de forma atómica avisando a cada paciente, para ausentarse sin dejar turnos sin aviso.

## Requirements

### Requirement: Listado de bloqueos por rango de días
`GET /api/bloqueos` SHALL requerir autenticación y los parámetros `desde` y `hasta` (`YYYY-MM-DD`, inclusivos, `hasta >= desde`, máximo 31 días, día calendario en `America/Argentina/Buenos_Aires`) y devolver, ordenados por inicio, los bloqueos cuyo rango se superpone con ese intervalo, con los campos `id`, `inicio`, `fin`, `motivo`, `creadoEn` y los instantes con offset `-03:00`. Un rango inválido o parámetros ausentes SHALL responder 400 `VALIDACION`.

#### Scenario: Bloqueo dentro del rango
- **WHEN** existe un bloqueo el 2026-12-24 y se consulta `desde=2026-12-20&hasta=2026-12-27`
- **THEN** la respuesta 200 lo incluye con los campos del contrato

#### Scenario: Bloqueo fuera del rango
- **WHEN** se consulta un rango que no se superpone con ningún bloqueo
- **THEN** la respuesta 200 es una lista vacía

#### Scenario: Rango mayor a 31 días o invertido
- **WHEN** `hasta` es anterior a `desde` o el rango supera 31 días
- **THEN** la respuesta es 400 `VALIDACION`

### Requirement: Vista previa de turnos afectados
`POST /api/bloqueos/previsualizar` SHALL devolver `{turnosAfectados}` con los turnos en estado `PENDIENTE` o `CONFIRMADO` cuyo rango interseca `[inicio, fin)` del bloqueo propuesto, con la forma `Turno` del contrato, y SHALL NOT modificar ningún dato. `inicio >= fin` SHALL responder 422 `RANGO_HORARIO_INVALIDO`.

#### Scenario: Vista previa sin efectos
- **WHEN** se previsualiza un rango con dos turnos activos
- **THEN** la respuesta 200 lista ambos, ningún bloqueo se crea y los turnos conservan su estado

#### Scenario: Turnos no activos o fuera del rango
- **WHEN** el rango contiene un turno `CANCELADO` y otro que solo toca el borde `fin`
- **THEN** ninguno aparece en `turnosAfectados`

#### Scenario: Rango inválido
- **WHEN** `inicio` es igual o posterior a `fin`
- **THEN** la respuesta es 422 `RANGO_HORARIO_INVALIDO`

### Requirement: Alta de bloqueo sin turnos afectados
`POST /api/bloqueos` SHALL crear el bloqueo y responder 201 `{bloqueo, turnosCancelados: []}` cuando el rango no contiene turnos activos, sin importar `cancelarTurnosAfectados`. Los bloqueos MAY solaparse entre sí. `inicio >= fin` SHALL responder 422 `RANGO_HORARIO_INVALIDO`; `motivo` es opcional y de hasta 200 caracteres.

#### Scenario: Alta directa
- **WHEN** se crea un bloqueo en un rango sin turnos activos
- **THEN** responde 201 con el bloqueo y `turnosCancelados` vacío

#### Scenario: Bloqueos solapados
- **WHEN** se crea un bloqueo que se superpone con otro existente
- **THEN** responde 201 y ambos quedan guardados

#### Scenario: Rango inválido
- **WHEN** `inicio` es posterior o igual a `fin`
- **THEN** responde 422 `RANGO_HORARIO_INVALIDO` y no se crea nada

### Requirement: Rechazo sin confirmación cuando hay turnos afectados
`POST /api/bloqueos` con `cancelarTurnosAfectados` ausente o `false` y turnos activos en el rango SHALL responder 409 `BLOQUEO_CON_TURNOS_AFECTADOS` con el formato uniforme de error más `turnosAfectados` (lista de `Turno`), y SHALL NOT crear el bloqueo ni modificar turnos.

#### Scenario: Conflicto con lista
- **WHEN** se crea un bloqueo con `cancelarTurnosAfectados=false` sobre un rango con dos turnos activos
- **THEN** responde 409 con `codigo=BLOQUEO_CON_TURNOS_AFECTADOS` y esos dos turnos en `turnosAfectados`, y no existe el bloqueo

### Requirement: Cancelación atómica de los turnos afectados
`POST /api/bloqueos` con `cancelarTurnosAfectados=true` SHALL crear el bloqueo y pasar los turnos activos afectados a `CANCELADO` con `canceladoPor=ODONTOLOGO` en una sola transacción, y responder 201 `{bloqueo, turnosCancelados}` con esos turnos ya cancelados. Si algo falla antes del commit, ni el bloqueo ni las cancelaciones SHALL persistir. Los turnos cancelados SHALL liberar su horario.

#### Scenario: Bloqueo y cancelaciones juntos
- **WHEN** se confirma un bloqueo sobre un rango con turnos `PENDIENTE` y `CONFIRMADO`
- **THEN** el bloqueo existe y ambos turnos quedan `CANCELADO` por `ODONTOLOGO`

#### Scenario: Falla antes del commit
- **WHEN** ocurre un error al persistir las cancelaciones
- **THEN** no existe el bloqueo y los turnos conservan su estado

#### Scenario: Horario liberado
- **WHEN** se cancelan turnos por un bloqueo
- **THEN** se puede insertar un turno activo nuevo en ese mismo horario sin violar la restricción de no solapamiento

### Requirement: Aviso al paciente después del commit sin afectar el resultado
Después de confirmar la transacción, el sistema SHALL invocar `NotificacionTurnos.cancelacionPorBloqueo` una vez por cada turno cancelado. Un aviso que devuelva `false` o lance una excepción SHALL NOT revertir el bloqueo ni las cancelaciones, ni impedir el aviso de los demás turnos, ni cambiar la respuesta 201.

#### Scenario: Un aviso por turno
- **WHEN** se cancelan dos turnos por un bloqueo
- **THEN** se pide el aviso de cancelación por bloqueo exactamente una vez para cada uno, y el bloqueo y las cancelaciones ya estaban confirmados

#### Scenario: Falla del aviso
- **WHEN** el aviso del primer turno lanza una excepción
- **THEN** responde 201, el bloqueo y las cancelaciones persisten y el segundo turno igual se avisa

### Requirement: Baja de bloqueo
`DELETE /api/bloqueos/{id}` SHALL eliminar el bloqueo y responder 204; un id inexistente SHALL responder 404 `RECURSO_NO_ENCONTRADO`. No SHALL restituir turnos cancelados por ese bloqueo.

#### Scenario: Baja existente
- **WHEN** se elimina un bloqueo existente
- **THEN** responde 204 y deja de listarse

#### Scenario: Baja inexistente
- **WHEN** se elimina un id que no existe
- **THEN** responde 404 `RECURSO_NO_ENCONTRADO`

#### Scenario: No restituye turnos
- **WHEN** se elimina un bloqueo que había cancelado turnos
- **THEN** esos turnos siguen `CANCELADO`

### Requirement: Endpoints privados y conformes al contrato
Todos los endpoints de `/api/bloqueos` SHALL exigir autenticación (401 sin ella) y devolver exactamente los campos de los schemas `Bloqueo`, `BloqueoPrevisualizacion`, `BloqueoCreado`, `ErrorBloqueoConflicto` y `Turno` de `docs/api/openapi.yaml`, sin exponer el token del turno ni entidades JPA. Un cuerpo inválido (`inicio`/`fin` ausentes, `motivo` de más de 200 caracteres) SHALL responder 400 `VALIDACION`, y un JSON ilegible 400 `SOLICITUD_MAL_FORMADA`.

#### Scenario: Sin autenticación
- **WHEN** se llama a cualquier endpoint de bloqueos sin credenciales
- **THEN** responde 401

#### Scenario: Claves del contrato
- **WHEN** se compara cada respuesta con los ejemplos de `docs/api/ejemplos/bloqueos-*.json`
- **THEN** las claves coinciden, incluidas las del 409 y las del turno
