# Spec Delta

## Purpose

Define cómo el odontólogo actúa sobre los turnos desde `/admin/agenda`: confirmar, cancelar, marcar asistencia, reprogramar y cargar turnos manualmente, con confirmaciones en pantalla accesibles y mensajes claros ante los errores del contrato.

## ADDED Requirements

### Requirement: Acciones disponibles según estado y hora

Cada turno de la agenda SHALL ofrecer solo las acciones válidas para su estado. Un turno `PENDIENTE` ofrece Confirmar, Reprogramar, Cancelar y Marcar asistió / ausente. Uno `CONFIRMADO` ofrece las mismas, sin Confirmar. Los turnos `CANCELADO`, `ASISTIO` y `AUSENTE` no ofrecen acciones. Cada acción SHALL tener un nombre accesible que identifique al paciente y el horario.

#### Scenario: Turno pendiente futuro

- **WHEN** la agenda muestra un turno `PENDIENTE` cuyo inicio todavía no llegó
- **THEN** la tarjeta ofrece Confirmar, Reprogramar y Cancelar habilitados, y Marcar asistió / ausente deshabilitados

#### Scenario: Turno confirmado

- **WHEN** la agenda muestra un turno `CONFIRMADO`
- **THEN** la tarjeta ofrece Reprogramar, Cancelar y Marcar asistió / ausente, y no ofrece Confirmar

#### Scenario: Turno terminal o cancelado

- **WHEN** la agenda muestra un turno `ASISTIO`, `AUSENTE` o `CANCELADO`
- **THEN** la tarjeta muestra el estado y no ofrece ninguna acción

#### Scenario: Pendiente a menos de 24 h

- **WHEN** un turno `PENDIENTE` tiene `pendienteMenos24h: true`
- **THEN** sigue resaltado como en la agenda de solo lectura y su acción Confirmar aparece primero

### Requirement: Confirmar un turno desde la agenda

La acción Confirmar SHALL enviar `POST /api/turnos/{id}/confirmar` sin pedir confirmación previa. Si la respuesta es exitosa, el turno SHALL quedar `CONFIRMADO` en la agenda. Mientras la solicitud está en curso, la acción SHALL quedar deshabilitada para evitar envíos duplicados.

#### Scenario: Confirmación exitosa

- **WHEN** el odontólogo activa Confirmar en un turno `PENDIENTE` y la API responde 200
- **THEN** se anuncia "Turno confirmado", la agenda se vuelve a cargar y el turno aparece `CONFIRMADO` sin el resaltado de urgencia

#### Scenario: Doble activación

- **WHEN** el odontólogo activa Confirmar dos veces seguidas
- **THEN** se envía una sola solicitud

### Requirement: Cancelar un turno con confirmación en pantalla

La acción Cancelar SHALL abrir un diálogo de confirmación con el paciente, la prestación, el día y la hora en Buenos Aires, y el email al que se avisará. Solo al aceptar SHALL enviar `POST /api/turnos/{id}/cancelar`. Si se desiste, SHALL cerrarse sin llamar a la API.

#### Scenario: Cancelación aceptada

- **WHEN** el odontólogo activa Cancelar, revisa el diálogo y acepta, y la API responde 200
- **THEN** se anuncia "Turno cancelado" y el turno desaparece de la grilla, salvo que estén visibles los cancelados

#### Scenario: Cancelación desistida

- **WHEN** el odontólogo activa Cancelar y elige "Volver" o presiona Escape
- **THEN** el diálogo se cierra, no se llama a la API y el foco vuelve al botón Cancelar

### Requirement: Marcar asistencia o ausencia

Las acciones Asistió y Ausente SHALL estar deshabilitadas antes de la hora de inicio del turno, con un texto visible que indique desde qué hora se pueden usar. Una vez habilitadas, SHALL pedir confirmación en pantalla porque el estado es terminal. Solo al aceptar SHALL enviar `PATCH /api/turnos/{id}/estado` con `ASISTIO` o `AUSENTE`.

#### Scenario: Antes de la hora de inicio

- **WHEN** la agenda muestra un turno activo que empieza a las 16:00 y en Buenos Aires son las 15:00
- **THEN** Asistió y Ausente están deshabilitados y se lee "Se puede marcar desde las 16:00"

#### Scenario: Marcar asistió

- **WHEN** el turno ya empezó, el odontólogo activa Asistió, acepta el diálogo y la API responde 200
- **THEN** se anuncia "Asistencia registrada" y el turno aparece como "Asistió", sin acciones

#### Scenario: El servidor todavía no lo permite

- **WHEN** el reloj del dispositivo adelanta y la API responde 422 `TRANSICION_INVALIDA`
- **THEN** se muestra "Solo se puede marcar la asistencia desde la hora de inicio del turno." y la agenda se vuelve a cargar

### Requirement: Diálogo de confirmación accesible

Los diálogos de confirmación SHALL ser modales para teclado y lectores de pantalla. Tienen título y descripción asociados, y al abrirse el foco va a la opción que no modifica nada. El foco SHALL quedar dentro del diálogo mientras esté abierto. Escape SHALL cerrarlo sin efectos y, al cerrarse, el foco SHALL volver al control que lo abrió.

#### Scenario: Foco inicial y ciclo de foco

- **WHEN** se abre el diálogo de cancelar
- **THEN** el foco está en "Volver", y Tab y Shift+Tab recorren solo los botones del diálogo

#### Scenario: Lectura por lector de pantalla

- **WHEN** un lector de pantalla llega al diálogo
- **THEN** anuncia su título y la descripción con los datos del turno

### Requirement: Selección del paciente en la carga manual

La carga manual SHALL permitir elegir un paciente existente con el buscador de pacientes, por DNI o apellido, o cargar uno nuevo con nombre, apellido y DNI. Esos datos se validan igual que en la pantalla de pacientes. Un paciente nuevo SHALL crearse en la misma solicitud de alta del turno, nunca en una solicitud aparte.

#### Scenario: Paciente existente

- **WHEN** el odontólogo busca "99999998" y elige a "Prueba, Bruno"
- **THEN** el formulario muestra el paciente elegido con la opción de cambiarlo, y la solicitud de alta lleva `pacienteId` y no `paciente`

#### Scenario: Paciente nuevo

- **WHEN** el odontólogo elige "Paciente nuevo" y completa nombre, apellido y DNI válidos
- **THEN** la solicitud de alta lleva `paciente` con esos datos y no lleva `pacienteId`

#### Scenario: Datos de paciente nuevo inválidos

- **WHEN** el DNI tiene letras o el nombre está vacío
- **THEN** se muestra el error junto al campo y no se llama a la API

### Requirement: Prestación y horario libre en la carga manual

La carga manual SHALL ofrecer solo prestaciones activas. Para el día elegido SHALL consultar `GET /api/disponibilidad` con la prestación, y SHALL ofrecer como horarios solo los instantes que devuelve, mostrados en hora de Buenos Aires. No SHALL permitir elegir días anteriores a hoy en Buenos Aires.

#### Scenario: Horarios del día

- **WHEN** el odontólogo elige "Consulta" y el día 2026-12-01
- **THEN** se consulta la disponibilidad con esa prestación para ese día, y se ofrecen 09:00 y 09:30 como opciones excluyentes

#### Scenario: Día sin lugar

- **WHEN** la disponibilidad devuelve el día sin horarios
- **THEN** se informa que no hay horarios libres ese día y no se puede enviar el alta

#### Scenario: Cambio de prestación

- **WHEN** el odontólogo cambia la prestación después de elegir un horario
- **THEN** el horario elegido se descarta y se vuelven a pedir los horarios del día

### Requirement: Contacto del turno precargado y editable

El email y el teléfono de contacto del turno SHALL precargarse desde el `ultimoContacto` del paciente elegido y SHALL poder editarse antes de enviar. Si el paciente no tiene último contacto, o si es nuevo, los campos SHALL quedar vacíos. Ambos son obligatorios y SHALL validarse con el formato del contrato antes de enviar.

#### Scenario: Paciente con turnos previos

- **WHEN** se elige un paciente con `ultimoContacto` (`bruno@ejemplo.com`, `+54 9 11 0000-0001`)
- **THEN** los campos de email y teléfono muestran esos valores y se pueden modificar

#### Scenario: Paciente sin turnos previos

- **WHEN** se elige un paciente con `ultimoContacto: null`
- **THEN** los campos quedan vacíos, con una indicación de que hay que ingresar el contacto

#### Scenario: Contacto inválido

- **WHEN** el email no tiene formato válido o el teléfono tiene letras
- **THEN** se muestra el error junto al campo y no se llama a la API

### Requirement: Envío del alta manual y errores

El alta SHALL enviar `POST /api/turnos` con la prestación, el inicio elegido y el contacto. Con 201, la pantalla SHALL anunciar el estado con que nació el turno según la respuesta, cerrar el formulario y recargar la agenda. Ante un error SHALL conservar los datos cargados y mostrar un mensaje propio en español, nunca el texto del servidor.

#### Scenario: Alta exitosa

- **WHEN** la API responde 201 con un turno `PENDIENTE`
- **THEN** se anuncia "Turno cargado: pendiente de confirmación" y la agenda se vuelve a cargar

#### Scenario: Alta a menos de 24 h

- **WHEN** la API responde 201 con un turno `CONFIRMADO`
- **THEN** se anuncia "Turno cargado: confirmado"

#### Scenario: Horario ocupado

- **WHEN** la API responde 409 `HORARIO_OCUPADO`
- **THEN** se informa que el horario se acaba de ocupar, se vuelven a pedir los horarios del día y se pide elegir otro

#### Scenario: Horario no disponible

- **WHEN** la API responde 422 `HORARIO_NO_DISPONIBLE`
- **THEN** se informa que el horario ya no está disponible y se vuelven a pedir los horarios del día

#### Scenario: Prestación inactiva

- **WHEN** la API responde 422 `PRESTACION_INACTIVA`
- **THEN** se informa que la prestación ya no está activa y se recarga la lista de prestaciones

#### Scenario: DNI duplicado

- **WHEN** el alta lleva un paciente nuevo y la API responde 422 `DNI_DUPLICADO`
- **THEN** el campo DNI queda marcado como inválido, con el foco y un mensaje que sugiere buscarlo como paciente existente

#### Scenario: Validación del servidor

- **WHEN** la API responde 400 `VALIDACION` con detalle para `emailContacto`
- **THEN** el mensaje se muestra junto al campo de email

### Requirement: Reprogramar un turno

Reprogramar SHALL mostrar el horario actual y ofrecer los horarios libres del día elegido para la prestación del turno, con la misma consulta de disponibilidad que el alta. Al enviar, SHALL llamar a `PATCH /api/turnos/{id}/reprogramar` con el nuevo inicio. Con 200, SHALL anunciar el nuevo horario y el estado resultante que devuelve la API.

#### Scenario: Reprogramación a más de 24 h

- **WHEN** el odontólogo elige un nuevo horario y la API responde 200 con estado `PENDIENTE`
- **THEN** se anuncia el nuevo día y hora con "queda pendiente de confirmación", y la agenda se vuelve a cargar

#### Scenario: Reprogramación a menos de 24 h

- **WHEN** la API responde 200 con estado `CONFIRMADO`
- **THEN** se anuncia el nuevo horario con "queda confirmado"

#### Scenario: Conflicto al reprogramar

- **WHEN** la API responde 409 `HORARIO_OCUPADO` o 422 `HORARIO_NO_DISPONIBLE`
- **THEN** se informa el motivo, se vuelven a pedir los horarios y el turno conserva su horario original

#### Scenario: Turno que ya no está activo

- **WHEN** la API responde 422 `TRANSICION_INVALIDA`
- **THEN** se informa que el turno ya no se puede reprogramar, se cierra el formulario y se recarga la agenda

### Requirement: Errores comunes de las acciones

Ante 422 `TRANSICION_INVALIDA` o 404 `RECURSO_NO_ENCONTRADO`, una acción sobre un turno SHALL informar en `role="alert"` que el turno cambió o ya no existe, y SHALL recargar la agenda. Ante un error de conexión o 500 SHALL mostrar un mensaje genérico sin detalles técnicos y no SHALL recargar. Un 401 `NO_AUTENTICADO` SHALL dejarse al manejo de sesión expirada del área admin.

#### Scenario: Turno cancelado por el paciente mientras tanto

- **WHEN** el odontólogo confirma un turno que el paciente ya canceló y la API responde 422 `TRANSICION_INVALIDA`
- **THEN** se informa que el turno ya no se puede confirmar y la agenda se vuelve a cargar con su estado actual

#### Scenario: Error de conexión

- **WHEN** la API no responde al cancelar
- **THEN** se muestra un mensaje genérico en español, el turno sigue igual en pantalla y la acción puede reintentarse

#### Scenario: Sesión expirada

- **WHEN** una acción responde 401 `NO_AUTENTICADO`
- **THEN** no se muestra un error propio de la acción y se aplica el manejo de sesión del área admin

### Requirement: Foco y anuncios después de una acción

Después de una acción exitosa, el resultado SHALL anunciarse en una región `role="status"` de las acciones, separada del aviso de pendientes de la agenda. Cuando la agenda se recargue, el foco SHALL ir a la tarjeta del turno afectado si sigue visible, o a la región del anuncio si no.

#### Scenario: Turno visible después de confirmar

- **WHEN** se confirma un turno y la agenda se recarga
- **THEN** el foco queda en la tarjeta de ese turno

#### Scenario: Turno oculto después de cancelar

- **WHEN** se cancela un turno con los cancelados ocultos
- **THEN** el foco queda en el anuncio "Turno cancelado"

### Requirement: Horas de Buenos Aires en las acciones

Los horarios ofrecidos, los diálogos y los anuncios SHALL mostrarse en `America/Argentina/Buenos_Aires`, sin importar la zona del navegador. El día elegido SHALL interpretarse como fecha de Buenos Aires. Los instantes enviados a la API SHALL ser exactamente los que devolvió la disponibilidad.

#### Scenario: Navegador en otra zona

- **WHEN** el navegador usa otra zona horaria y la disponibilidad devuelve `2026-12-01T09:00:00-03:00`
- **THEN** el horario se muestra como 09:00 y el alta envía `inicio` `2026-12-01T09:00:00-03:00`

### Requirement: Accesibilidad y responsive de las acciones

Los formularios de carga manual y reprogramación SHALL tener `<label>` asociados, grupos de opciones con leyenda, errores vinculados al campo y anunciados, foco visible y uso completo con teclado. Las acciones y los formularios SHALL usarse a 320 píxeles de ancho sin scroll horizontal, con objetivos táctiles de al menos 44 píxeles.

#### Scenario: Carga manual solo con teclado

- **WHEN** el odontólogo completa un alta usando solo el teclado
- **THEN** puede buscar o cargar el paciente, elegir prestación, día y horario, editar el contacto y enviar, con el foco visible en cada paso

#### Scenario: Pantalla angosta

- **WHEN** la agenda con acciones se abre a 320 píxeles de ancho
- **THEN** las acciones de cada tarjeta y los formularios se apilan sin desbordar la pantalla
