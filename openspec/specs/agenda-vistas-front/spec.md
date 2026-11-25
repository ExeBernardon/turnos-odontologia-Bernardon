# agenda-vistas-front Specification

## Purpose

Define el comportamiento observable de la agenda del odontólogo en `/admin/agenda`: qué se ve en las vistas diaria y semanal, cómo se distinguen estados, bloqueos y horarios fuera de atención, y cómo se navega y se informan los errores de carga.

## Requirements

### Requirement: Vistas diaria y semanal de la agenda

La agenda SHALL ofrecer una vista diaria y otra semanal. Cada turno del rango SHALL mostrar paciente, prestación, hora de inicio y fin, estado y teléfono de contacto del turno. El rango diario SHALL ser un solo día y el semanal SHALL cubrir de lunes a domingo en `America/Argentina/Buenos_Aires`.

#### Scenario: Vista diaria con turnos

- **WHEN** se abre la agenda en vista diaria con turnos en ese día
- **THEN** se muestran sus datos de paciente, prestación, inicio, fin, estado y teléfono de contacto

#### Scenario: Vista semanal con turnos

- **WHEN** se abre la agenda en vista semanal
- **THEN** los turnos aparecen agrupados por día de lunes a domingo con los mismos datos que en la vista diaria

#### Scenario: Día sin turnos

- **WHEN** la API devuelve una lista vacía para el rango
- **THEN** se muestra un mensaje que indica que no hay turnos, sin confundirlo con un error

### Requirement: Navegación entre días y semanas

La pantalla SHALL permitir avanzar y retroceder un día en la vista diaria y una semana en la vista semanal, y volver al día o la semana actuales. Cada cambio SHALL consultar el rango correspondiente y SHALL reflejar el periodo visible en la interfaz.

#### Scenario: Avanzar un día

- **WHEN** el odontólogo activa "Día siguiente" en la vista diaria
- **THEN** se consulta el día siguiente y la etiqueta del periodo muestra esa fecha

#### Scenario: Avanzar una semana

- **WHEN** el odontólogo activa "Semana siguiente" en la vista semanal
- **THEN** se consulta la semana siguiente completa, de lunes a domingo

#### Scenario: Volver al periodo actual

- **WHEN** el odontólogo activa "Hoy" o "Esta semana"
- **THEN** se consulta y se muestra el periodo que contiene el día actual en Buenos Aires

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

### Requirement: Turnos cancelados

Los turnos `CANCELADO` SHALL ocultarse por defecto y SHALL poder mostrarse atenuados mediante un control explícito. En ninguno de los dos estados SHALL ocupar el lugar de un turno activo en la grilla.

#### Scenario: Ocultos por defecto

- **WHEN** la agenda carga con turnos cancelados y activos
- **THEN** solo se muestran los activos en la grilla

#### Scenario: Mostrar cancelados

- **WHEN** el odontólogo activa el control para mostrar cancelados
- **THEN** los cancelados aparecen atenuados, sin desplazar a los turnos activos de su posición

### Requirement: Bloqueos y horarios fuera de atención

La agenda SHALL distinguir visualmente los bloqueos y los intervalos fuera de los horarios de atención de los turnos, usando la información de `GET /api/bloqueos` y `GET /api/horarios-atencion` para el rango visible.

#### Scenario: Bloqueo dentro del rango

- **WHEN** existe un bloqueo que intersecta el periodo visible
- **THEN** se muestra como una franja diferenciada con su motivo, si lo tiene, sin confundirlo con un turno

#### Scenario: Fuera del horario de atención

- **WHEN** el periodo visible incluye horas fuera de los rangos configurados para ese día
- **THEN** esas horas se distinguen de las horas hábiles

#### Scenario: Sin horarios configurados

- **WHEN** el día no tiene horarios de atención
- **THEN** se indica que no hay atención configurada, sin interpretarlo como un error de carga

### Requirement: Horas en Buenos Aires

Todas las fechas y horas de la agenda SHALL formatearse en `America/Argentina/Buenos_Aires` con `Intl.DateTimeFormat('es-AR')`, independientemente de la zona horaria del navegador. Los parámetros de fecha del rango SHALL interpretarse como fechas locales de Buenos Aires.

#### Scenario: Instante con offset distinto

- **WHEN** la API devuelve un inicio con offset `-03:00`
- **THEN** la hora mostrada es la correspondiente a Buenos Aires, sin aplicar la zona del dispositivo

#### Scenario: Cambio de zona del navegador

- **WHEN** el navegador usa otra zona horaria
- **THEN** la agenda sigue mostrando las mismas horas de Buenos Aires

### Requirement: Carga, errores y datos no confiables

La pantalla SHALL cargar turnos, bloqueos y horarios con las solicitudes autenticadas del contrato, y SHALL tratar las respuestas como datos no confiables. Ante errores de API o de conexión SHALL mostrar un mensaje comprensible y una acción para reintentar, sin exponer respuestas crudas ni datos internos.

#### Scenario: Error de autenticación

- **WHEN** una consulta responde 401 `NO_AUTENTICADO`
- **THEN** se aplica el manejo de sesión expirada del área admin y no se muestra una grilla con datos parciales

#### Scenario: Error de conexión

- **WHEN** la consulta falla por red
- **THEN** se muestra un mensaje en español con una acción para reintentar

#### Scenario: Respuesta con forma inesperada

- **WHEN** la API devuelve una lista con elementos que no respetan el contrato
- **THEN** la agenda no se rompe, ignora los elementos inválidos y mantiene operativa la navegación

### Requirement: Accesibilidad y responsive

La navegación, los controles de periodo, el control de cancelados y las acciones de enganche SHALL ser accesibles por teclado, con foco visible y etiquetas o nombres accesibles. Los avisos SHALL usar `role="status"` y los errores `role="alert"`. La grilla SHALL adaptarse a pantallas angostas sin perder la información de cada turno.

#### Scenario: Navegación por teclado

- **WHEN** se recorren los controles de la agenda con el teclado
- **THEN** el foco es visible en cada control y se puede cambiar de periodo sin usar el mouse

#### Scenario: Vista angosta

- **WHEN** la agenda se abre en una pantalla de 390 píxeles de ancho
- **THEN** cada turno sigue mostrando paciente, horario, estado y teléfono sin desbordar la pantalla
