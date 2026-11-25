# reserva-publica-front Specification

## Purpose
Define el comportamiento visible para el paciente de la página pública `/reservar`: cómo elige prestación, fecha y horario libre, cómo completa sus datos con el consentimiento obligatorio, cómo se le comunican los errores de la API y qué resumen ve del turno reservado.

## Requirements

### Requirement: Página pública sin login
La página `/reservar` SHALL tener un único encabezado de nivel 1 "Reservar turno" y SHALL funcionar sin cuenta ni sesión: solo consume los endpoints `/api/public/*` del contrato.

#### Scenario: Paciente anónimo entra a reservar
- **WHEN** alguien sin sesión abre `/reservar`
- **THEN** ve el encabezado "Reservar turno" y la lista de prestaciones, sin pedido de login

### Requirement: Elección de prestación activa
Al entrar la página SHALL pedir `GET /api/public/prestaciones` y SHALL mostrar cada prestación con su nombre y duración como opción elegible con teclado. Sin prestaciones SHALL informar que no hay prestaciones disponibles. Si la carga falla SHALL mostrar un error con una acción para reintentar.

#### Scenario: Lista del contrato
- **WHEN** la API responde el ejemplo `prestaciones-publicas-listar.200.json`
- **THEN** se ofrecen "Consulta (30 min)" y "Limpieza (45 min)" y todavía no se muestran horarios ni el formulario de datos

#### Scenario: Sin prestaciones
- **WHEN** la API responde una lista vacía
- **THEN** se informa "Por ahora no hay prestaciones disponibles para reservar." y no se ofrece elegir fecha

#### Scenario: Falla la carga
- **WHEN** el pedido de prestaciones falla por conexión
- **THEN** se muestra el error en una región de alerta con un botón "Reintentar" que vuelve a pedir la lista

### Requirement: Fecha dentro de la ventana pública
Elegida una prestación la página SHALL ofrecer elegir una fecha entre hoy y hoy + 60 días (calendario de Buenos Aires) y SHALL rechazar en el front una fecha vacía o fuera de ese rango sin consultar la API.

#### Scenario: Límites del selector
- **WHEN** hoy en Buenos Aires es 2026-11-30
- **THEN** la fecha mínima elegible es 2026-11-30 y la máxima 2027-01-29

#### Scenario: Fecha fuera de ventana
- **WHEN** el paciente ingresa 2027-03-01 estando en 2026-11-30
- **THEN** ve un error junto al campo fecha y no se pide disponibilidad

### Requirement: Horarios libres del día elegido
Con prestación y fecha válidas la página SHALL pedir `GET /api/public/disponibilidad` con esa prestación y `desde = hasta = fecha`, y SHALL mostrar cada horario libre como hora local de Buenos Aires (HH:mm) elegible con teclado. Cambiar de prestación o de fecha SHALL descartar el horario elegido.

#### Scenario: Horarios del ejemplo
- **WHEN** la API responde el ejemplo `disponibilidad-publica.200.json` para 2026-12-01
- **THEN** se ofrecen 09:00, 09:30 y 10:00 y el request fue `prestacionId=1&desde=2026-12-01&hasta=2026-12-01`

#### Scenario: Día sin horarios
- **WHEN** el día pedido vuelve con `horarios: []`
- **THEN** se informa "No hay horarios disponibles ese día. Probá con otra fecha." como estado, no como error, y no se muestra el formulario de datos

#### Scenario: Cambio de prestación
- **WHEN** con un horario elegido el paciente elige otra prestación
- **THEN** el horario elegido se descarta y hay que volver a elegir fecha y horario

### Requirement: Formulario de datos con consentimiento obligatorio
Elegido un horario la página SHALL pedir nombre, apellido, DNI, email y teléfono, cada uno con su label visible, y una casilla de tratamiento de datos acompañada de un texto breve de finalidad (Ley 25.326). La casilla SHALL estar desmarcada al inicio y SHALL ser obligatoria para enviar.

#### Scenario: Texto de finalidad visible
- **WHEN** aparece el formulario
- **THEN** junto a la casilla se lee para qué se usan los datos (gestionar el turno y avisos por email) y la casilla está desmarcada

#### Scenario: Sin aceptar
- **WHEN** el paciente envía sin marcar la casilla
- **THEN** ve un error junto a la casilla y no se llama a la API

### Requirement: Validación de formato en el front
Antes de enviar la página SHALL validar con las reglas del contrato: nombre y apellido obligatorios de hasta 100 caracteres; DNI de 7 u 8 dígitos con o sin puntos; email con formato válido de hasta 150 caracteres; teléfono de 6 a 30 caracteres entre dígitos, espacios, `+`, `-` y paréntesis. Con errores SHALL mostrarlos junto a cada campo, marcarlo inválido, enfocar el primero y no llamar a la API.

#### Scenario: Datos válidos
- **WHEN** se ingresan `Ana`, `Ejemplo`, `99.999.999`, `paciente@ejemplo.com`, `+54 9 11 0000-0000` y se acepta la casilla
- **THEN** no hay errores y se envía la reserva

#### Scenario: Formatos inválidos
- **WHEN** se ingresan DNI `99-999-999`, email `paciente@` y teléfono `abc`
- **THEN** cada uno de esos tres campos muestra su error, queda marcado inválido, el foco va al DNI y no se llama a la API

### Requirement: Envío de la reserva según el contrato
Con datos válidos la página SHALL enviar `POST /api/public/turnos` con `nombre`, `apellido`, `dni`, `email` y `telefono` recortados, `prestacionId` e `inicio` del horario elegido tal como vino de la API, y `consentimiento: true`. Mientras espera SHALL evitar envíos duplicados.

#### Scenario: Request del contrato
- **WHEN** se reserva con los datos del ejemplo `turnos-publico-reservar.request.json`
- **THEN** el body enviado es igual a ese ejemplo

#### Scenario: Doble clic
- **WHEN** el paciente activa "Reservar" dos veces seguidas
- **THEN** se envía un solo request

### Requirement: Errores de la API en la reserva
La página SHALL traducir cada error a un mensaje en español sin detalles internos y SHALL conservar los datos escritos. Un 400 `VALIDACION` SHALL mostrar cada `detalle` junto a su campo (y en la alerta general los que no correspondan a un campo). Un 409 `HORARIO_OCUPADO` o un 422 `HORARIO_NO_DISPONIBLE` SHALL avisar y volver a pedir los horarios del día. Un 422 `CONSENTIMIENTO_REQUERIDO` SHALL marcar la casilla.

#### Scenario: Horario ocupado en el medio
- **WHEN** la API responde el ejemplo `turnos-publico-reservar.409-horario-ocupado.json`
- **THEN** se muestra "Ese horario se acaba de ocupar, elegí otro." en la alerta, se vuelve a pedir la disponibilidad del día, el horario elegido se descarta y nombre, DNI, email y teléfono siguen cargados

#### Scenario: Horario fuera de ventana al reservar
- **WHEN** la API responde 422 `HORARIO_NO_DISPONIBLE`
- **THEN** se muestra "Ese horario ya no está disponible. Elegí otro." y se refrescan los horarios del día

#### Scenario: Detalles por campo
- **WHEN** la API responde 400 `VALIDACION` con un detalle para `email`
- **THEN** ese mensaje aparece junto al campo email, marcado inválido y con foco

#### Scenario: Consentimiento rechazado por la API
- **WHEN** la API responde el ejemplo `turnos-publico-reservar.422-consentimiento-requerido.json`
- **THEN** se muestra el error junto a la casilla de tratamiento de datos

#### Scenario: Prestación ya no disponible
- **WHEN** la API responde 422 `PRESTACION_INACTIVA` o 404 al reservar o al pedir horarios
- **THEN** se informa que la prestación ya no está disponible y se vuelve a cargar la lista de prestaciones para elegir otra

#### Scenario: Error inesperado o sin conexión
- **WHEN** la API responde 500 o no hay conexión
- **THEN** se muestra un mensaje genérico de reintento, sin stack ni texto técnico, y los datos siguen cargados

### Requirement: Resumen del turno reservado
Tras el 201 la página SHALL reemplazar los pasos por un resumen con el nombre de pila, la prestación y su duración, la fecha y hora en hora de Buenos Aires, el estado en palabras, el contacto del consultorio y el aviso de revisar el email. Ese aviso SHALL mencionar los links solo si el turno queda `PENDIENTE`. El resumen SHALL mostrar solo datos del propio turno y del email ingresado, y SHALL mover el foco a su encabezado.

#### Scenario: Turno pendiente
- **WHEN** la API responde el ejemplo `turnos-publico-reservar.201.json`
- **THEN** se lee "Ana", "Consulta", "jueves 10 de diciembre de 2026" a las "10:00", "Pendiente de confirmación", el contacto del consultorio y "Revisá tu email (paciente@ejemplo.com)" junto con la mención de los links para gestionarlo, y el foco está en el encabezado del resumen

#### Scenario: Turno que nace confirmado
- **WHEN** la respuesta trae `estado: CONFIRMADO`
- **THEN** el resumen dice "Confirmado", pide revisar el email por los datos del turno sin mencionar links e indica que ante cualquier cambio se comunique con el consultorio

#### Scenario: Reservar otro
- **WHEN** el paciente elige "Reservar otro turno" en el resumen
- **THEN** vuelve al paso de prestación con el formulario vacío y la casilla desmarcada

### Requirement: Accesible con teclado y usable en el celular
Toda la reserva SHALL poder completarse solo con teclado con foco visible; las opciones de prestación y de horario SHALL ser grupos con leyenda; los estados SHALL anunciarse en `role="status"` y los errores en `role="alert"`. A 320 px de ancho SHALL no haber scroll horizontal y los controles táctiles SHALL medir al menos 44 px de alto.

#### Scenario: Solo teclado
- **WHEN** el paciente usa solo Tab, flechas, Espacio y Enter
- **THEN** puede elegir prestación, fecha y horario, completar los datos, aceptar la casilla y reservar

#### Scenario: Pantalla angosta
- **WHEN** la página se ve a 320 px de ancho
- **THEN** los horarios se acomodan en varias filas y no aparece scroll horizontal
