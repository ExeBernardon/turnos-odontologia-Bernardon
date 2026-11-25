# Spec Delta

## Purpose

Define la gestión del ciclo de vida del turno y de la agenda: transiciones de estado, acciones del paciente por token y del odontólogo, reprogramación y consultas, con plazos e idempotencia observables.

## ADDED Requirements

### Requirement: Agenda por rango autenticada

`GET /api/turnos?desde&hasta&estado` SHALL exigir JWT y devolver los turnos cuyo `inicio` cae en el rango de fechas inclusivo, sin incluir el token. `desde` y `hasta` SHALL ser obligatorios, con `hasta >= desde` y un máximo de 31 días; cualquier incumplimiento o un `estado` fuera del enum SHALL responder 400 `VALIDACION`.

#### Scenario: Rango con turnos de varios estados

- **WHEN** el odontólogo pide la agenda entre dos fechas con turnos `PENDIENTE`, `CONFIRMADO` y `CANCELADO` dentro del rango
- **THEN** recibe 200 con los tres turnos, cada uno con su teléfono de contacto, `pendienteMenos24h` y sin token

#### Scenario: Filtro por estado

- **WHEN** el odontólogo pide la agenda con `estado=CONFIRMADO`
- **THEN** la respuesta incluye solo los turnos confirmados del rango

#### Scenario: Rango inválido

- **WHEN** se pide un rango de 32 días o con `hasta` anterior a `desde`
- **THEN** la respuesta es 400 `VALIDACION`

#### Scenario: Agenda sin JWT

- **WHEN** se pide la agenda sin header `Authorization`
- **THEN** la respuesta es 401 `NO_AUTENTICADO`

### Requirement: Detalle de un turno por id

`GET /api/turnos/{id}` SHALL exigir JWT y devolver el turno de agenda con la misma forma que el listado, sin el token. Un id inexistente SHALL responder 404 `RECURSO_NO_ENCONTRADO`.

#### Scenario: Detalle existente

- **WHEN** el odontólogo pide un turno existente con JWT
- **THEN** recibe 200 con sus datos de agenda y sin token

#### Scenario: Detalle inexistente

- **WHEN** el odontólogo pide un id que no existe
- **THEN** la respuesta es 404 `RECURSO_NO_ENCONTRADO`

### Requirement: Máquina de estados centralizada

El sistema SHALL centralizar las transiciones permitidas: `PENDIENTE` y `CONFIRMADO` son activos; `CANCELADO`, `ASISTIO` y `AUSENTE` son terminales. Las acciones SHALL respetar: confirmar solo desde `PENDIENTE`; cancelar solo desde activos; marcar asistencia solo desde activos y desde la hora de inicio; reprogramar solo desde activos. Cualquier otra transición SHALL responder 422 `TRANSICION_INVALIDA`.

#### Scenario: Transición permitida

- **WHEN** se confirma un turno `PENDIENTE` activo
- **THEN** el estado pasa a `CONFIRMADO` y la operación responde 200

#### Scenario: Transición desde terminal

- **WHEN** se intenta reprogramar o cancelar un turno `ASISTIO`
- **THEN** la respuesta es 422 `TRANSICION_INVALIDA` y el turno no cambia

#### Scenario: Asistencia antes del inicio

- **WHEN** se intenta marcar `ASISTIO` antes de la hora de inicio del turno
- **THEN** la respuesta es 422 `TRANSICION_INVALIDA`

### Requirement: Orden de evaluación de acciones

Las acciones de confirmar y cancelar SHALL evaluar en este orden y detenerse en el primer caso: (1) recurso inexistente, 404; (2) turno ya en el estado destino, 200 idempotente sin efectos ni mails; (3) transición no permitida, 422 `TRANSICION_INVALIDA`; (4) plazo vencido o no alcanzado, 422 con el código específico.

#### Scenario: Idempotencia antes que transición

- **WHEN** el paciente cancela por token un turno ya `CANCELADO` con menos de 24 h de anticipación
- **THEN** la respuesta es 200 sin efectos y no se envía ningún mail

#### Scenario: Transición antes que plazo

- **WHEN** el paciente confirma por token un turno `CANCELADO` que además empieza en más de 48 h
- **THEN** la respuesta es 422 `TRANSICION_INVALIDA`, no `CONFIRMACION_FUERA_DE_PLAZO`

### Requirement: Confirmación del paciente por token

`POST /api/public/turnos/{token}/confirmar` SHALL pasar a `CONFIRMADO` un turno `PENDIENTE` que empieza en 48 h o menos y aún no empezó. Con más de 48 h SHALL responder 422 `CONFIRMACION_FUERA_DE_PLAZO`; sobre `CONFIRMADO` SHALL ser idempotente (200); sobre cancelado, terminal o iniciado SHALL responder 422 `TRANSICION_INVALIDA`. Un token inexistente SHALL responder 404 `TOKEN_INVALIDO` sin revelar datos. La respuesta SHALL ser el resumen público, sin token.

#### Scenario: Confirmación dentro de la ventana

- **WHEN** el paciente confirma por token un turno `PENDIENTE` que empieza en 36 h
- **THEN** recibe 200 con el resumen público y el turno queda `CONFIRMADO`

#### Scenario: Borde exacto de 48 h

- **WHEN** el paciente confirma un turno `PENDIENTE` que empieza exactamente en 48 h
- **THEN** la respuesta es 200 y el turno queda `CONFIRMADO`

#### Scenario: Más de 48 h

- **WHEN** el paciente confirma un turno `PENDIENTE` que empieza en 49 h
- **THEN** la respuesta es 422 `CONFIRMACION_FUERA_DE_PLAZO` y el turno sigue `PENDIENTE`

#### Scenario: Confirmación idempotente

- **WHEN** el paciente confirma dos veces el mismo turno `PENDIENTE` dentro de la ventana
- **THEN** ambas respuestas son 200 y el estado final es `CONFIRMADO`

#### Scenario: Token inexistente al confirmar

- **WHEN** se confirma con un token que no existe
- **THEN** la respuesta es 404 `TOKEN_INVALIDO`

### Requirement: Cancelación del paciente por token

`POST /api/public/turnos/{token}/cancelar` SHALL cancelar un turno `PENDIENTE` con 24 h o más de anticipación, registrando `canceladoPor: PACIENTE` y liberando el horario. Con menos de 24 h SHALL responder 422 `CANCELACION_FUERA_DE_PLAZO`; sobre `CONFIRMADO` o terminal SHALL responder 422 `TRANSICION_INVALIDA`; sobre `CANCELADO` SHALL ser idempotente (200). Un token inexistente SHALL responder 404 `TOKEN_INVALIDO`.

#### Scenario: Cancelación dentro del plazo

- **WHEN** el paciente cancela por token un turno `PENDIENTE` que empieza en 30 h
- **THEN** recibe 200, el turno queda `CANCELADO` con `canceladoPor: PACIENTE` y el horario se libera

#### Scenario: Borde exacto de 24 h

- **WHEN** el paciente cancela un turno `PENDIENTE` que empieza exactamente en 24 h
- **THEN** la respuesta es 200 y el turno queda `CANCELADO`

#### Scenario: Menos de 24 h

- **WHEN** el paciente cancela un turno `PENDIENTE` que empieza en 23 h
- **THEN** la respuesta es 422 `CANCELACION_FUERA_DE_PLAZO` y el turno sigue activo

#### Scenario: Turno confirmado

- **WHEN** el paciente intenta cancelar por token un turno `CONFIRMADO`
- **THEN** la respuesta es 422 `TRANSICION_INVALIDA` con el mensaje de que confirmar es definitivo

#### Scenario: Cancelación idempotente

- **WHEN** el paciente cancela dos veces el mismo turno por token
- **THEN** ambas respuestas son 200 y no se envía ningún mail en la segunda

### Requirement: Confirmación por el odontólogo

`POST /api/turnos/{id}/confirmar` SHALL exigir JWT y pasar a `CONFIRMADO` un turno `PENDIENTE` sin la restricción de 48 h del paciente. Sobre `CONFIRMADO` SHALL ser idempotente (200); sobre cancelado, iniciado o terminal SHALL responder 422 `TRANSICION_INVALIDA`; un id inexistente SHALL responder 404 `RECURSO_NO_ENCONTRADO`.

#### Scenario: Confirmación sin restricción de plazo

- **WHEN** el odontólogo confirma un turno `PENDIENTE` que empieza en 10 días
- **THEN** recibe 200 y el turno queda `CONFIRMADO`

#### Scenario: Confirmación idempotente del odontólogo

- **WHEN** el odontólogo confirma dos veces el mismo turno
- **THEN** ambas respuestas son 200 y el turno queda `CONFIRMADO`

#### Scenario: Turno cancelado

- **WHEN** el odontólogo confirma un turno `CANCELADO`
- **THEN** la respuesta es 422 `TRANSICION_INVALIDA`

#### Scenario: Confirmación sin JWT

- **WHEN** se llama al endpoint sin header `Authorization`
- **THEN** la respuesta es 401 `NO_AUTENTICADO`

### Requirement: Cancelación por el odontólogo

`POST /api/turnos/{id}/cancelar` SHALL exigir JWT y cancelar cualquier turno activo en cualquier momento, registrando `canceladoPor: ODONTOLOGO`, liberando el horario y pidiendo el aviso al `emailContacto` del turno. Sobre un turno cancelado o terminal SHALL responder 422 `TRANSICION_INVALIDA`; un id inexistente SHALL responder 404 `RECURSO_NO_ENCONTRADO`.

#### Scenario: Cancelación de un confirmado

- **WHEN** el odontólogo cancela un turno `CONFIRMADO` con menos de 2 h de anticipación
- **THEN** recibe 200, el turno queda `CANCELADO` con `canceladoPor: ODONTOLOGO` y se pide el aviso al paciente

#### Scenario: Cancelación ya cancelada

- **WHEN** el odontólogo cancela un turno ya `CANCELADO`
- **THEN** la respuesta es 422 `TRANSICION_INVALIDA`

#### Scenario: Fallo del aviso

- **WHEN** falla el envío del aviso de cancelación
- **THEN** la respuesta sigue siendo 200 y el turno queda cancelado

### Requirement: Marcado de asistencia

`PATCH /api/turnos/{id}/estado` SHALL exigir JWT y aceptar solo `ASISTIO` o `AUSENTE`, aplicables solo a turnos activos y solo cuando `now >= inicio`. Antes del inicio, sobre un turno cancelado o terminal, o con un valor distinto SHALL responder 422 `TRANSICION_INVALIDA` o 400 `VALIDACION` según corresponda. El estado resultante SHALL ser terminal.

#### Scenario: Asistencia marcada desde la hora de inicio

- **WHEN** el odontólogo marca `ASISTIO` exactamente a la hora de inicio del turno
- **THEN** recibe 200 con el estado `ASISTIO`

#### Scenario: Ausente antes del inicio

- **WHEN** el odontólogo marca `AUSENTE` un minuto antes del inicio
- **THEN** la respuesta es 422 `TRANSICION_INVALIDA` y el turno sigue activo

#### Scenario: Estado inválido en el body

- **WHEN** el body trae `estado: "PENDIENTE"`
- **THEN** la respuesta es 400 `VALIDACION` o 400 `SOLICITUD_MAL_FORMADA` según el formato del cuerpo

### Requirement: Reprogramación de un turno activo

`PATCH /api/turnos/{id}/reprogramar` SHALL exigir JWT, aceptar solo `inicio` con offset, aplicar la validación de horario en contexto de odontólogo excluyendo el propio turno (422 `HORARIO_NO_DISPONIBLE`) y actualizar `inicio` y `fin` con `saveAndFlush` para traducir solapamientos a 409 `HORARIO_OCUPADO`. Solo SHALL aplicar a turnos activos; sobre cancelado o terminal SHALL responder 422 `TRANSICION_INVALIDA`.

#### Scenario: Reprogramación válida

- **WHEN** el odontólogo mueve un turno activo a otro horario libre
- **THEN** recibe 200 con el nuevo `inicio`, el `fin` recalculado y el mismo paciente y prestación

#### Scenario: Horario no disponible

- **WHEN** el odontólogo mueve el turno a un horario sobre un bloqueo
- **THEN** la respuesta es 422 `HORARIO_NO_DISPONIBLE` y el turno conserva su horario

#### Scenario: Horario ocupado por otro turno

- **WHEN** el nuevo horario se superpone con otro turno activo
- **THEN** la respuesta es 409 `HORARIO_OCUPADO` y el turno no se mueve

#### Scenario: Reprogramación de un cancelado

- **WHEN** el odontólogo intenta reprogramar un turno `CANCELADO`
- **THEN** la respuesta es 422 `TRANSICION_INVALIDA`

### Requirement: Estado y flags tras reprogramar

Al reprogramar, el sistema SHALL recalcular el estado con la regla de anticipación de 24 h: con 24 h o más, `PENDIENTE` con `recordatorioEnviado` y `alertaPendienteEnviada` en `false`; con menos de 24 h, `CONFIRMADO` con ambos en `true`. SHALL pedir el aviso al `emailContacto` del turno: reprogramación con solo Cancelar si queda `PENDIENTE`, aviso sin links si queda `CONFIRMADO`. Un fallo de envío SHALL NOT revertir la reprogramación.

#### Scenario: Reprogramar cruzando a menos de 24 h

- **WHEN** se mueve un turno `PENDIENTE` a un inicio que falta a las 23 h
- **THEN** el turno queda `CONFIRMADO` con ambos flags en `true` y se pide el aviso sin links

#### Scenario: Reprogramar cruzando a 24 h o más

- **WHEN** se mueve un turno `CONFIRMADO` a un inicio que falta a las 30 h
- **THEN** el turno vuelve a `PENDIENTE` con ambos flags en `false` y se pide la reprogramación con link de cancelar

#### Scenario: Borde exacto de 24 h

- **WHEN** se reprograma un turno a un inicio que falta exactamente 24 h
- **THEN** el turno queda `PENDIENTE` con ambos flags en `false`
