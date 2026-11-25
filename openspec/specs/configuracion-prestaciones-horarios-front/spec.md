# configuracion-prestaciones-horarios-front Specification

## Purpose

Define el comportamiento visible para el odontólogo de las secciones de prestaciones y de horarios de atención en `/admin/configuracion`: qué ve, qué puede hacer, cómo se validan los datos antes de enviarlos y cómo se comunican los errores de la API.

## Requirements

### Requirement: Listado de prestaciones
La sección de prestaciones SHALL mostrar todas las prestaciones, activas e inactivas, con nombre, duración en minutos y estado, y SHALL distinguir visualmente (con texto, no solo color) las inactivas.

#### Scenario: Lista con activas e inactivas
- **WHEN** la API devuelve prestaciones activas e inactivas
- **THEN** todas se listan y cada una indica si está activa o inactiva

#### Scenario: Sin prestaciones
- **WHEN** la API devuelve una lista vacía
- **THEN** se muestra un mensaje indicando que no hay prestaciones cargadas

#### Scenario: Error al cargar
- **WHEN** la carga falla por error de API o de conexión
- **THEN** se muestra un mensaje de error comprensible y una forma de reintentar

### Requirement: Alta y edición de prestaciones
El odontólogo SHALL poder crear una prestación y editar su nombre y duración. El front SHALL validar antes de enviar que el nombre no esté vacío (máximo 100 caracteres) y que la duración sea un entero entre 1 y 480.

#### Scenario: Alta válida
- **WHEN** completa nombre y duración válidos y envía
- **THEN** se crea la prestación y aparece en el listado

#### Scenario: Duración inválida
- **WHEN** ingresa una duración vacía, 0, negativa, decimal o mayor a 480
- **THEN** no se envía el pedido y se muestra un error asociado al campo duración

#### Scenario: Nombre vacío
- **WHEN** envía con el nombre vacío o solo espacios
- **THEN** no se envía el pedido y se muestra un error asociado al campo nombre

#### Scenario: Edición
- **WHEN** modifica nombre o duración de una prestación existente y guarda
- **THEN** se actualiza conservando su estado activa/inactiva y el listado refleja el cambio

### Requirement: Activar y desactivar prestaciones
El odontólogo SHALL poder activar o desactivar una prestación desde el listado.

#### Scenario: Desactivar
- **WHEN** desactiva una prestación activa
- **THEN** pasa a mostrarse como inactiva

#### Scenario: Activar
- **WHEN** activa una prestación inactiva
- **THEN** pasa a mostrarse como activa

### Requirement: Baja de prestaciones con turnos
El odontólogo SHALL poder intentar eliminar una prestación, previa confirmación. Si la API responde 422 `PRESTACION_CON_TURNOS`, el front SHALL explicar que la prestación tiene turnos y no puede borrarse, y SHALL ofrecer desactivarla en su lugar.

#### Scenario: Eliminación exitosa
- **WHEN** confirma eliminar una prestación sin turnos
- **THEN** desaparece del listado

#### Scenario: Prestación con turnos
- **WHEN** la API responde 422 `PRESTACION_CON_TURNOS`
- **THEN** se muestra un mensaje claro y una acción para desactivarla, y la prestación sigue en el listado

### Requirement: Horarios agrupados por día de la semana
La sección de horarios SHALL mostrar los rangos agrupados por día (lunes a domingo, en ese orden), con los rangos de cada día ordenados por hora de inicio, y SHALL indicar los días sin rangos.

#### Scenario: Varios rangos en un día
- **WHEN** un día tiene dos rangos
- **THEN** ambos se listan bajo ese día en orden de hora de inicio

#### Scenario: Día sin atención
- **WHEN** un día no tiene rangos
- **THEN** se muestra ese día indicando que no hay horario de atención

### Requirement: Alta, edición y baja de rangos horarios
El odontólogo SHALL poder crear, editar y eliminar rangos de horario de atención. El front SHALL validar antes de enviar que ambas horas estén completas y que la hora de inicio sea anterior a la de fin.

#### Scenario: Alta válida
- **WHEN** elige día, hora de inicio y fin válidas y envía
- **THEN** el rango aparece en su día

#### Scenario: Inicio no anterior al fin
- **WHEN** la hora de inicio es igual o posterior a la de fin
- **THEN** no se envía el pedido y se muestra un error de rango

#### Scenario: Edición y baja
- **WHEN** edita un rango y guarda, o confirma eliminarlo
- **THEN** el listado refleja el cambio

### Requirement: Errores de negocio de horarios
Si la API responde 422 `RANGO_HORARIO_INVALIDO` o `HORARIOS_SOLAPADOS` al crear o editar un rango, el front SHALL mostrar un mensaje comprensible junto al formulario y conservar los valores ingresados.

#### Scenario: Rango solapado
- **WHEN** la API responde 422 `HORARIOS_SOLAPADOS`
- **THEN** se muestra que el rango se superpone con otro del mismo día y el formulario conserva lo ingresado

#### Scenario: Rango inválido desde la API
- **WHEN** la API responde 422 `RANGO_HORARIO_INVALIDO`
- **THEN** se muestra que la hora de inicio debe ser anterior a la de fin

### Requirement: Manejo del error uniforme
Ante un error de la API el front SHALL mostrar un mensaje en español derivado de su `codigo`. Para 400 `VALIDACION` con `detalles`, SHALL asociar cada mensaje al campo correspondiente. Ante falla de conexión SHALL informarlo sin perder lo ingresado. Nunca SHALL mostrar stack traces ni datos internos.

#### Scenario: Validación del servidor con detalles
- **WHEN** la API responde 400 `VALIDACION` con detalles para el campo `nombre`
- **THEN** el mensaje se muestra junto al campo nombre

#### Scenario: Sin conexión
- **WHEN** el pedido no llega al servidor
- **THEN** se muestra el mensaje de conexión y el formulario conserva los datos

### Requirement: Accesibilidad y responsive
Los formularios SHALL tener cada control con `<label>` asociado, errores vinculados al campo y anunciados a lectores de pantalla, foco visible y operación completa por teclado. El layout SHALL ser usable en pantallas angostas sin scroll horizontal.

#### Scenario: Navegación por teclado
- **WHEN** se usa solo el teclado
- **THEN** todos los campos y acciones son alcanzables y activables en un orden lógico

#### Scenario: Error anunciado
- **WHEN** falla la validación o la API
- **THEN** el mensaje queda en una región que los lectores de pantalla anuncian y el campo inválido se marca como inválido
