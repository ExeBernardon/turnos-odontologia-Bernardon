## ADDED Requirements

### Requirement: Listado de horarios de atención
`GET /api/horarios-atencion` SHALL requerir autenticación y devolver todos los rangos con `id`, `diaSemana` (1 = lunes ... 7 = domingo), `horaInicio` y `horaFin` en formato `HH:mm`, ordenados por día y hora de inicio.

#### Scenario: Lista ordenada
- **WHEN** el odontólogo autenticado pide el listado
- **THEN** responde 200 con los rangos ordenados por `diaSemana` y `horaInicio`

#### Scenario: Sin autenticación
- **WHEN** se pide sin credenciales
- **THEN** responde 401 `NO_AUTENTICADO`

### Requirement: Alta y reemplazo de rangos
`POST /api/horarios-atencion` SHALL crear un rango (201) y `PUT /api/horarios-atencion/{id}` SHALL reemplazarlo (200). `diaSemana` debe estar entre 1 y 7 y las horas son obligatorias; un día puede tener varios rangos.

#### Scenario: Varios rangos el mismo día
- **WHEN** se crean 09:00-13:00 y 15:00-19:00 para el lunes
- **THEN** ambos se crean con 201

#### Scenario: Día fuera de rango
- **WHEN** se crea con `diaSemana` 8
- **THEN** responde 400 `VALIDACION` con un detalle para `diaSemana`

#### Scenario: Hora faltante
- **WHEN** se crea sin `horaFin`
- **THEN** responde 400 `VALIDACION` con un detalle para `horaFin`

### Requirement: Rango válido
Si `horaInicio` es mayor o igual a `horaFin`, el sistema SHALL responder 422 `RANGO_HORARIO_INVALIDO`.

#### Scenario: Inicio igual a fin
- **WHEN** se crea con `horaInicio` 09:00 y `horaFin` 09:00
- **THEN** responde 422 `RANGO_HORARIO_INVALIDO`

#### Scenario: Inicio posterior a fin
- **WHEN** se actualiza con `horaInicio` 14:00 y `horaFin` 09:00
- **THEN** responde 422 `RANGO_HORARIO_INVALIDO`

### Requirement: Sin solapamiento en el mismo día
El sistema SHALL responder 422 `HORARIOS_SOLAPADOS` si el rango se superpone con otro del mismo día; los rangos son semiabiertos, por lo que 09:00-13:00 y 13:00-17:00 no se solapan. Al actualizar, el propio rango no cuenta como conflicto.

#### Scenario: Solapado
- **WHEN** existe lunes 09:00-13:00 y se crea lunes 12:00-15:00
- **THEN** responde 422 `HORARIOS_SOLAPADOS`

#### Scenario: Adyacente
- **WHEN** existe lunes 09:00-13:00 y se crea lunes 13:00-17:00
- **THEN** responde 201

#### Scenario: Mismas horas en otro día
- **WHEN** existe lunes 09:00-13:00 y se crea martes 09:00-13:00
- **THEN** responde 201

#### Scenario: Actualizar sin cambiar el rango
- **WHEN** se hace PUT de un rango con sus mismos valores
- **THEN** responde 200

### Requirement: Eliminación de rangos
`DELETE /api/horarios-atencion/{id}` SHALL eliminar el rango (204) o responder 404 `RECURSO_NO_ENCONTRADO` si no existe.

#### Scenario: Eliminar
- **WHEN** se elimina un rango existente
- **THEN** responde 204

#### Scenario: Inexistente
- **WHEN** se elimina o actualiza un id inexistente
- **THEN** responde 404 `RECURSO_NO_ENCONTRADO`
