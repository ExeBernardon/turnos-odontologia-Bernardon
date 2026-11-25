# Spec Delta

## Purpose

Calcula en el momento cada consulta los horarios en que el consultorio puede atender una prestación, y ofrece una única validación de horario reutilizable por la reserva pública, la carga manual y la reprogramación, sin persistir los horarios libres.

## ADDED Requirements

### Requirement: Cálculo al vuelo de horarios libres

El sistema SHALL calcular los horarios libres para una prestación y un rango de fechas restando de los rangos de atención los bloqueos, los turnos en estado `PENDIENTE` o `CONFIRMADO`, el pasado y, cuando corresponda, la ventana de reserva. Los horarios libres SHALL NOT persistirse ni constituir una entidad.

#### Scenario: Día con atención y sin ocupaciones

- **WHEN** se consultan los horarios de una prestación de 30 min para un lunes con atención 09:00-13:00, sin bloqueos ni turnos
- **THEN** la respuesta incluye los inicios libres de ese rango en la grilla correspondiente

#### Scenario: Bloqueo parcial

- **WHEN** existe un bloqueo de 10:00 a 11:00 dentro de un rango de atención 09:00-13:00
- **THEN** no se ofrecen inicios cuya atención se superponga con ese bloqueo y se ofrecen los comprendidos antes y después

#### Scenario: Turno activo que parte el rango

- **WHEN** existe un turno activo de 10:00 a 10:30 dentro de un rango 09:00-13:00
- **THEN** ese tramo deja de ofrecerse y los tramos 09:00-10:00 y 10:30-13:00 siguen ofreciéndose según la duración

#### Scenario: Turno cancelado no ocupa

- **WHEN** el único turno que intersecta el rango está `CANCELADO`
- **THEN** sus horarios siguen apareciendo como libres

#### Scenario: Bordes semiabiertos

- **WHEN** un turno termina exactamente a las 10:00 y un bloqueo empieza exactamente a las 10:00
- **THEN** un candidato que empieza a las 10:00 no se descarta por tocar solo esos bordes

### Requirement: Grilla estable y duración completa

Los inicios ofrecidos SHALL generarse con el paso configurable (15 min por defecto) alineado al inicio de cada rango de atención, y SHALL descartarse cuando `inicio + duracionMinutos` de la prestación supere el fin del mismo rango de atención. Un turno SHALL NOT apoyarse en dos rangos separados.

#### Scenario: Duración que no entra

- **WHEN** se consulta una prestación de 60 min con un rango de atención 09:00-09:45
- **THEN** ese rango no aporta ningún horario libre

#### Scenario: Paso alineado al inicio del rango

- **WHEN** un rango de atención empieza a las 09:07 con paso de 15 min
- **THEN** los candidatos se alinean a 09:07, 09:22, 09:37 y así sucesivamente

#### Scenario: Varios rangos el mismo día

- **WHEN** un día tiene rangos 09:00-13:00 y 15:00-19:00 y una prestación de 45 min
- **THEN** se ofrecen horarios de ambos rangos sin candidatos a caballo entre las 13:00 y las 15:00

### Requirement: Zona horaria de negocio

Las horas de atención SHALL interpretarse en `America/Argentina/Buenos_Aires` y los instantes devueltos SHALL expresarse en ISO-8601 con offset, sin depender de la zona horaria del servidor. Las fechas `desde` y `hasta` SHALL corresponder a días calendario de esa zona.

#### Scenario: Horario local convertido a instante

- **WHEN** hay un rango de atención local 09:00-13:00 y se consulta una fecha en Buenos Aires
- **THEN** los horarios devueltos tienen offset `-03:00` y equivalen a las 09:00 locales

### Requirement: Ventana pública y consulta del odontólogo

La consulta pública SHALL ofrecer solo inicios con al menos `APP_TURNO_ANTICIPACION_MIN_HORAS` (2 h por defecto) y como máximo `APP_TURNO_VENTANA_MAX_DIAS` (60 días por defecto) desde el momento de la consulta. La consulta autenticada del odontólogo SHALL aplicar solo la regla de no ofrecer instantes en el pasado.

#### Scenario: Límite inferior de la ventana

- **WHEN** con reloj fijo un candidato cae exactamente a 2 h del momento actual
- **THEN** se ofrece en la consulta pública; si cae 1 minuto antes, no se ofrece

#### Scenario: Límite superior de la ventana

- **WHEN** un candidato cae exactamente en 60 días desde el momento actual
- **THEN** se ofrece en la consulta pública; si cae en el día 61, no se ofrece

#### Scenario: Consulta del odontólogo sin ventana

- **WHEN** el odontólogo consulta un horario dentro de las próximas 2 h que no está en el pasado
- **THEN** el horario aparece en su respuesta

#### Scenario: Horario en el pasado

- **WHEN** un candidato es anterior o igual al momento actual
- **THEN** no aparece en ninguna de las dos consultas

### Requirement: Respuesta agrupada por día y validación del rango

Ambos endpoints SHALL responder con la prestación y un elemento por cada fecha del intervalo `desde`..`hasta` inclusivo, con `horarios: []` cuando ese día no tenga lugar. Un rango invertido o de más de 31 días SHALL responder 400 con `codigo` `VALIDACION`; un día sin atención SHALL NOT ser un error.

#### Scenario: Rango de varios días

- **WHEN** se consulta `desde=2026-12-01&hasta=2026-12-03`
- **THEN** la respuesta contiene exactamente tres elementos, uno por fecha, con sus listas de horarios

#### Scenario: Día sin atención

- **WHEN** el rango incluye un domingo sin rangos de atención configurados
- **THEN** ese día aparece con `horarios: []` y la respuesta sigue siendo 200

#### Scenario: Rango inválido

- **WHEN** `hasta` es anterior a `desde` o la diferencia supera 31 días
- **THEN** la respuesta es 400 `VALIDACION`

### Requirement: Prestación inexistente o inactiva

Consultar disponibilidad de una prestación que no existe SHALL responder 404 con `codigo` `RECURSO_NO_ENCONTRADO`. Consultar una prestación inactiva SHALL responder 422 con `codigo` `PRESTACION_INACTIVA`, tanto en la consulta pública como en la del odontólogo.

#### Scenario: Prestación inexistente

- **WHEN** se consulta disponibilidad con un `prestacionId` que no existe
- **THEN** la respuesta es 404 `RECURSO_NO_ENCONTRADO`

#### Scenario: Prestación inactiva

- **WHEN** se consulta disponibilidad de una prestación desactivada
- **THEN** la respuesta es 422 `PRESTACION_INACTIVA`

### Requirement: Validación reutilizable del horario de un turno

Antes de crear o reprogramar un turno, el sistema SHALL verificar que el inicio propuesto caiga en la grilla de un rango de atención, que el turno completo quepa en él, que no esté en el pasado, que no intersecte un bloqueo ni un turno activo y, en la reserva pública, que cumpla la ventana de 2 h a 60 días. Cualquier incumplimiento SHALL responder 422 con `codigo` `HORARIO_NO_DISPONIBLE`; el mensaje SHALL explicar la causa concreta.

#### Scenario: Validación al reservar

- **WHEN** un paciente reserva un inicio sobre un bloqueo
- **THEN** la respuesta es 422 `HORARIO_NO_DISPONIBLE` y no se crea el turno

#### Scenario: Validación al cargar manualmente

- **WHEN** el odontólogo carga un turno en un inicio fuera de la grilla
- **THEN** la respuesta es 422 `HORARIO_NO_DISPONIBLE`

#### Scenario: Validación al reprogramar

- **WHEN** se reprograma un turno a un horario fuera de atención
- **THEN** la respuesta es 422 `HORARIO_NO_DISPONIBLE` y el turno conserva su horario original

#### Scenario: Regla de no pasado sin mínimo de 2 h

- **WHEN** el odontólogo carga manualmente un turno dentro de la próxima hora y libre
- **THEN** la validación pasa y la única restricción temporal aplicada es no estar en el pasado
