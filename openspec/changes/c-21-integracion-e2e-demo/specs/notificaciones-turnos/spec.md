## MODIFIED Requirements

### Requirement: Contenido de los mails
Cada mail SHALL incluir el nombre del paciente, la prestación y la fecha y hora de inicio del turno en `America/Argentina/Buenos_Aires`. El "Turno no confirmado" SHALL incluir además el teléfono de contacto del turno. Los mails sin botones al paciente SHALL indicar el contacto del consultorio. La Cancelación por bloqueo SHALL invitar a reservar un nuevo turno. Los asuntos SHALL estar escritos con la ortografía del español (tildes incluidas). El asunto y el cuerpo del "Turno no confirmado" SHALL expresar el umbral configurado en `APP_ALERTA_PENDIENTE_HORAS`, no un número de horas fijo.

#### Scenario: Hora en Buenos Aires
- **WHEN** se envía la confirmación de reserva de un turno que empieza a las `2026-12-10T13:00:00Z`
- **THEN** el email muestra la hora `10:00` y la fecha del 10 de diciembre de 2026

#### Scenario: Teléfono en el turno no confirmado
- **WHEN** se envía el turno no confirmado de un turno con teléfono de contacto `1155555555`
- **THEN** el email muestra ese teléfono, el nombre del paciente, la prestación y el horario

#### Scenario: Cancelación por bloqueo invita a reservar
- **WHEN** se envía la cancelación por bloqueo de un turno
- **THEN** el email invita a reservar un nuevo turno desde la reserva pública y no trae links con token

#### Scenario: Datos del paciente escapados
- **WHEN** el nombre del paciente contiene caracteres HTML como `<b>`
- **THEN** el email los muestra como texto y no como marcado

#### Scenario: Asunto del aviso con tilde
- **WHEN** se envía el aviso de turno confirmado de un turno creado con menos de 24 h
- **THEN** el asunto del email es "Tu turno quedó reservado"

#### Scenario: Turno no confirmado con el umbral por defecto
- **WHEN** `APP_ALERTA_PENDIENTE_HORAS` vale 24 y se envía el turno no confirmado
- **THEN** el asunto y el cuerpo del email hablan de menos de 24 horas

#### Scenario: Turno no confirmado con otro umbral
- **WHEN** `APP_ALERTA_PENDIENTE_HORAS` vale 12 y se envía el turno no confirmado
- **THEN** el asunto y el cuerpo del email hablan de menos de 12 horas y no mencionan 24 horas
