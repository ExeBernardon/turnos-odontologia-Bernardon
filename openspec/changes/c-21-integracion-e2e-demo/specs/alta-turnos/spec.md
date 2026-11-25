## MODIFIED Requirements

### Requirement: Estado inicial y fin del turno

El sistema SHALL calcular el estado inicial con el instante actual: `PENDIENTE` si faltan 24 h o más, `CONFIRMADO` si faltan menos. El umbral de 24 h SHALL tomarse de `APP_ALERTA_PENDIENTE_HORAS` (24 por defecto), el mismo que usa la alerta al odontólogo, sin cambiar código. El `fin` SHALL persistirse igual a `inicio` más la duración de la prestación. Los flags `recordatorioEnviado` y `alertaPendienteEnviada` SHALL quedar en `false` para `PENDIENTE` y en `true` para `CONFIRMADO` creado por debajo del umbral, según el aviso ya emitido.

#### Scenario: Reserva con 24 h o más de anticipación

- **WHEN** se crea un turno que empieza exactamente a las 24 h de ahora
- **THEN** el turno queda `PENDIENTE` con ambos flags en `false`

#### Scenario: Reserva con menos de 24 h de anticipación

- **WHEN** se crea un turno que empieza a las 23 h y 59 min de ahora
- **THEN** el turno queda `CONFIRMADO` con ambos flags en `true`

#### Scenario: Fin derivado de la duración

- **WHEN** se crea un turno de una prestación de 45 min que empieza a las 10:00
- **THEN** el turno se persiste con `inicio` 10:00 y `fin` 10:45

#### Scenario: Umbral de confirmación configurado

- **WHEN** `APP_ALERTA_PENDIENTE_HORAS` vale 12 y se crea un turno que empieza a las 20 h de ahora
- **THEN** el turno queda `PENDIENTE` con ambos flags en `false`

### Requirement: Avisos de creación según anticipación

Después de persistir el turno, el sistema SHALL pedir un aviso por la anticipación: más de 48 h → confirmación de reserva con solo Cancelar; de 24 h a 48 h, ambos inclusive → recordatorio con Confirmar y Cancelar; menos de 24 h → aviso de turno confirmado sin botones. Los umbrales de 48 h y 24 h SHALL tomarse de `APP_RECORDATORIO_HORAS_ANTES` y `APP_ALERTA_PENDIENTE_HORAS`. El destinatario SHALL ser siempre `emailContacto` del turno. Un fallo de envío SHALL NOT revertir la reserva ni borrar el turno, y los flags SHALL reflejar solo los avisos que salieron.

#### Scenario: Reserva con más de 48 h

- **WHEN** se crea un turno `PENDIENTE` con más de 48 h de anticipación
- **THEN** se pide la confirmación de reserva con link de cancelación y el turno sigue `PENDIENTE`

#### Scenario: Reserva entre 24 y 48 h

- **WHEN** se crea un turno `PENDIENTE` entre 24 y 48 h antes del inicio
- **THEN** se pide el recordatorio en el momento con links de confirmación y cancelación, y `recordatorioEnviado` es `true` si el aviso salió

#### Scenario: Bordes exactos de 48 h y 24 h

- **WHEN** se crean turnos que empiezan exactamente a las 48 h y a las 24 h de ahora
- **THEN** ambos reciben el recordatorio en el momento, no la confirmación de reserva ni la alerta al odontólogo

#### Scenario: Reserva con menos de 24 h

- **WHEN** se crea un turno `CONFIRMADO` con menos de 24 h de anticipación
- **THEN** se pide el aviso sin botones al `emailContacto` del turno

#### Scenario: Fallo del envío de mail

- **WHEN** el mecanismo de avisos falla al crear un turno
- **THEN** la reserva permanece persistida, la respuesta sigue siendo 201 y el fallo no se propaga al paciente como error de la API

#### Scenario: Ventana del recordatorio configurada

- **WHEN** `APP_RECORDATORIO_HORAS_ANTES` vale 72 y se crea un turno que empieza a las 60 h de ahora
- **THEN** se pide el recordatorio en el momento y no la confirmación de reserva
