# Spec Delta

## MODIFIED Requirements

### Requirement: Identificación de turnos pendientes

Los turnos `PENDIENTE` SHALL etiquetarse como "sin confirmar". Los que tengan `pendienteMenos24h: true` SHALL resaltarse de forma distinguishable, con el teléfono de contacto visible y puntos de enganche en el DOM para las acciones Confirmar y Cancelar, las cuales SHALL ejecutar las acciones definidas en la capability `agenda-acciones-front`.

#### Scenario: Turno pendiente lejano

- **WHEN** un turno está `PENDIENTE` y `pendienteMenos24h` es `false`
- **THEN** aparece marcado como "sin confirmar" sin el resaltado de urgencia

#### Scenario: Turno pendiente cercano

- **WHEN** un turno está `PENDIENTE` y `pendienteMenos24h` es `true`
- **THEN** aparece resaltado, con su teléfono visible y los puntos de enganche para Confirmar y Cancelar habilitados, que ejecutan la confirmación y la cancelación con confirmación en pantalla

#### Scenario: Turno confirmado

- **WHEN** un turno está `CONFIRMADO`
- **THEN** no se muestra como "sin confirmar" ni se resalta como pendiente cercano
