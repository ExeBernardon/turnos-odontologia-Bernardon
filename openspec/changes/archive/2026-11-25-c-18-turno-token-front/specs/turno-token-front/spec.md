# Spec Delta

## Purpose

Define lo que ve y puede hacer el paciente en las páginas públicas `/turno/confirmar` y `/turno/cancelar` a las que llevan los links con token de los emails: lectura del resumen sin efectos, confirmación y cancelación explícitas y el mensaje de cada resultado.

## ADDED Requirements

### Requirement: Páginas públicas por token sin login
Las páginas `/turno/confirmar` y `/turno/cancelar` SHALL funcionar sin cuenta ni sesión, SHALL tener un único encabezado de nivel 1 ("Confirmar turno" y "Cancelar turno") visible desde el primer render y SHALL tomar el token solo del parámetro `token` de la URL.

#### Scenario: Paciente abre el link de confirmar
- **WHEN** alguien sin sesión abre `/turno/confirmar?token=TOKEN-DE-EJEMPLO`
- **THEN** ve el encabezado "Confirmar turno" sin pedido de login

#### Scenario: Paciente abre el link de cancelar
- **WHEN** alguien sin sesión abre `/turno/cancelar?token=TOKEN-DE-EJEMPLO`
- **THEN** ve el encabezado "Cancelar turno" sin pedido de login

### Requirement: Abrir el link no cambia el estado
Al abrir cualquiera de las dos páginas con un token, el front SHALL hacer solo `GET /api/public/turnos/{token}` y MUST NOT hacer ningún `POST` hasta que el paciente apriete explícitamente el botón de la acción.

#### Scenario: Previsualización automática del link
- **WHEN** un cliente de mail abre `/turno/confirmar?token=TOKEN-DE-EJEMPLO` y nadie toca la página
- **THEN** el único pedido a la API es `GET /api/public/turnos/TOKEN-DE-EJEMPLO` y el turno sigue `PENDIENTE`

#### Scenario: Abrir el link de cancelar
- **WHEN** se abre `/turno/cancelar?token=TOKEN-DE-EJEMPLO`
- **THEN** solo se pide `GET /api/public/turnos/TOKEN-DE-EJEMPLO`, sin `POST .../cancelar`

### Requirement: Resumen del turno sin datos sensibles
Con el `200` del `GET` la página SHALL mostrar el nombre de pila, la prestación con su duración, la fecha en palabras, el horario de inicio y fin en hora de Buenos Aires, el estado en palabras y el contacto del consultorio. MUST NOT mostrar DNI, apellido, email, teléfono ni el token, aunque la respuesta trajera campos extra.

#### Scenario: Resumen del ejemplo del contrato
- **WHEN** la API responde `turnos-publico-obtener.200-pendiente.json`
- **THEN** se muestran "Ana", "Consulta (30 min)", "jueves 3 de diciembre de 2026", "10:00 a 10:30", "Pendiente de confirmación" y "Consultorio de ejemplo - Tel. +54 9 11 0000-0000"

#### Scenario: Campos extra en la respuesta
- **WHEN** la respuesta del `GET` incluye además `dni`, `email` o `telefono`
- **THEN** ninguno de esos valores aparece en la página

#### Scenario: El token no se muestra
- **WHEN** la página termina de cargar con cualquier resultado
- **THEN** el texto de la página no contiene el valor del token

### Requirement: Link inválido
Si el parámetro `token` falta, está vacío o su largo está fuera de 16 a 128 caracteres, la página SHALL mostrar "El link no es válido" sin llamar a la API. Si la API responde `404` (o `400`) al `GET` o al `POST`, SHALL mostrar el mismo mensaje y ninguna acción sobre el turno.

#### Scenario: Sin token
- **WHEN** se abre `/turno/confirmar` sin `token`
- **THEN** se muestra "El link no es válido" y no se hace ningún pedido a la API

#### Scenario: Token demasiado corto
- **WHEN** se abre `/turno/cancelar?token=abc`
- **THEN** se muestra "El link no es válido" y no se hace ningún pedido a la API

#### Scenario: Token inexistente
- **WHEN** el `GET` responde `turnos-publico-obtener.404-token-invalido.json`
- **THEN** se muestra "El link no es válido", sin resumen ni botones

### Requirement: Confirmación explícita de asistencia
En `/turno/confirmar` el botón "Confirmar asistencia" SHALL mostrarse solo cuando `puedeConfirmar` es `true`; al apretarlo SHALL hacerse un único `POST /api/public/turnos/{token}/confirmar` y, con el `200`, SHALL informarse "Tu turno quedó confirmado" con el resumen actualizado y sin el botón.

#### Scenario: Confirmar un turno pendiente dentro de las 48 h
- **WHEN** el `GET` trae `puedeConfirmar: true` y el paciente aprieta "Confirmar asistencia"
- **THEN** se hace `POST /api/public/turnos/TOKEN-DE-EJEMPLO/confirmar`, se informa "Tu turno quedó confirmado" y el estado pasa a "Confirmado"

#### Scenario: Doble clic
- **WHEN** el paciente aprieta "Confirmar asistencia" dos veces seguidas
- **THEN** se envía un solo `POST` y el botón queda deshabilitado mientras espera

### Requirement: Mensajes de confirmar según el estado leído
Cuando `puedeConfirmar` es `false` la página de confirmar SHALL explicar el motivo según el resumen: confirmado → "Tu turno ya estaba confirmado"; cancelado → "Este turno está cancelado y no se puede confirmar" con un link a `/reservar`; pendiente con más de 48 h → "Todavía no se puede confirmar"; turno pasado → "Este turno ya pasó".

#### Scenario: Ya estaba confirmado (idempotente)
- **WHEN** el `GET` responde `turnos-publico-obtener.200-confirmado.json`
- **THEN** se muestra "Tu turno ya estaba confirmado" y no hay botón de confirmar

#### Scenario: Faltan más de 48 h
- **WHEN** el turno está `PENDIENTE`, `puedeConfirmar` es `false` y el inicio es posterior al momento actual
- **THEN** se muestra "Todavía no se puede confirmar el turno. Podés confirmarlo desde 48 h antes, con el link del recordatorio." sin botón

#### Scenario: Turno cancelado
- **WHEN** el turno está `CANCELADO`
- **THEN** se muestra "Este turno está cancelado y no se puede confirmar." y un link "Reservar otro turno" a `/reservar`

#### Scenario: Turno pasado
- **WHEN** el turno está `ASISTIO` o `AUSENTE`, o está `PENDIENTE` o `CONFIRMADO` con inicio anterior o igual al momento actual
- **THEN** se muestra "Este turno ya pasó." sin botón

### Requirement: Cancelación con confirmación en pantalla
En `/turno/cancelar` el botón "Cancelar turno" SHALL mostrarse solo cuando `puedeCancelar` es `true`. Al apretarlo SHALL preguntarse "¿Seguro que querés cancelar tu turno?" con "Sí, cancelar turno" y "No, mantener turno"; solo "Sí, cancelar turno" SHALL hacer un único `POST .../cancelar`.

#### Scenario: Paciente confirma la cancelación
- **WHEN** el paciente aprieta "Cancelar turno" y luego "Sí, cancelar turno"
- **THEN** se hace `POST /api/public/turnos/TOKEN-DE-EJEMPLO/cancelar`, se informa "Tu turno quedó cancelado. El horario quedó libre." con el estado "Cancelado" y un link "Reservar otro turno"

#### Scenario: Paciente se arrepiente
- **WHEN** el paciente aprieta "Cancelar turno" y luego "No, mantener turno"
- **THEN** no se hace ningún `POST`, desaparece la pregunta y el foco vuelve al botón "Cancelar turno"

#### Scenario: Un clic no cancela
- **WHEN** el paciente aprieta solo "Cancelar turno"
- **THEN** no se hace ningún `POST` hasta que elija "Sí, cancelar turno"

### Requirement: Mensajes de cancelar según el estado leído
Cuando `puedeCancelar` es `false` la página de cancelar SHALL explicar el motivo: cancelado → "Tu turno ya estaba cancelado"; confirmado → "Tu turno ya está confirmado. Para cancelarlo comunicate con el consultorio."; pendiente con menos de 24 h → mensaje de fuera de plazo; turno pasado → "Este turno ya pasó". Los dos casos de contacto SHALL mostrar `contactoConsultorio`.

#### Scenario: Ya estaba cancelado (idempotente)
- **WHEN** el `GET` trae un turno `CANCELADO`
- **THEN** se muestra "Tu turno ya estaba cancelado." sin botón de cancelar

#### Scenario: Turno confirmado
- **WHEN** el `GET` responde `turnos-publico-obtener.200-confirmado.json`
- **THEN** se muestra "Tu turno ya está confirmado. Para cancelarlo comunicate con el consultorio." junto al contacto del consultorio y sin botón

#### Scenario: Faltan menos de 24 h
- **WHEN** el turno está `PENDIENTE`, `puedeCancelar` es `false` y el inicio es posterior al momento actual
- **THEN** se muestra "Ya no se puede cancelar desde el link (faltan menos de 24 h). Comunicate con el consultorio." junto al contacto del consultorio

### Requirement: Errores de negocio al confirmar o cancelar
Si el `POST` responde `422`, la página SHALL mostrar en una alerta un mensaje fijo del front por código (`CONFIRMACION_FUERA_DE_PLAZO`, `CANCELACION_FUERA_DE_PLAZO` con el contacto, `TRANSICION_INVALIDA`), SHALL volver a leer el turno con `GET` para reflejar su estado y MUST NOT mostrar el `mensaje` del servidor.

#### Scenario: Confirmar fuera de plazo
- **WHEN** el `POST .../confirmar` responde `turnos-publico-confirmar.422-confirmacion-fuera-de-plazo.json`
- **THEN** se alerta "Todavía no se puede confirmar el turno. Podés confirmarlo desde 48 h antes, con el link del recordatorio.", se vuelve a pedir el `GET` y ya no se ofrece el botón

#### Scenario: Cancelar fuera de plazo
- **WHEN** el `POST .../cancelar` responde `turnos-publico-cancelar.422-cancelacion-fuera-de-plazo.json`
- **THEN** se alerta "Ya no se puede cancelar desde el link (faltan menos de 24 h). Comunicate con el consultorio." con el contacto del consultorio y se vuelve a pedir el `GET`

#### Scenario: Transición inválida
- **WHEN** el `POST .../cancelar` responde `turnos-publico-cancelar.422-transicion-invalida.json`
- **THEN** se alerta "El turno cambió y ya no se puede cancelar desde el link." y la página muestra el mensaje del estado releído

### Requirement: Fallas de conexión o del servidor
Si el `GET` falla por conexión o responde `500`, la página SHALL mostrar el error en una alerta con un botón "Reintentar" que repite el `GET`. Si falla así el `POST`, SHALL mostrar el error y dejar el botón de la acción habilitado para reintentar.

#### Scenario: Falla la lectura
- **WHEN** el `GET` falla por conexión
- **THEN** se muestra "No pudimos conectarnos con el servidor. Revisá tu conexión e intentá de nuevo." con un botón "Reintentar" que vuelve a pedir el turno

#### Scenario: Falla la confirmación
- **WHEN** el `POST .../confirmar` responde `500`
- **THEN** se muestra un mensaje genérico de error y "Confirmar asistencia" vuelve a estar habilitado

### Requirement: Accesibilidad de las páginas por token
Las páginas SHALL ser operables solo con teclado con botones nativos, SHALL anunciar carga y resultados en una región `status` y los errores en una región `alert`, SHALL mover el foco al mensaje de resultado tras cada acción y MUST NOT transmitir el estado solo con color.

#### Scenario: Resultado anunciado y enfocado
- **WHEN** termina con éxito la confirmación o la cancelación
- **THEN** el foco queda en el mensaje de resultado y su texto está dentro de una región `status`

#### Scenario: Pregunta de cancelación con teclado
- **WHEN** el paciente activa "Cancelar turno" con el teclado
- **THEN** el foco pasa a la pregunta "¿Seguro que querés cancelar tu turno?" y sus dos botones se alcanzan con Tab
