# Spec Delta: acceso-admin-front

## Purpose

Define el comportamiento observable del acceso del odontólogo desde el navegador: ingreso con credenciales, sesión con vencimiento, protección de las rutas privadas, reacción ante la expiración y layout de navegación del área de administración.

## ADDED Requirements

### Requirement: Formulario de login accesible

El área privada SHALL exponer un formulario de acceso con campos para email y contraseña, cada uno con su etiqueta asociada, atributos de autocompletado, foco visible y operación completa por teclado. El formulario SHALL validar que ambos campos estén completos y que el email tenga formato válido antes de enviar la solicitud.

#### Scenario: Campos etiquetados y navegables

- **WHEN** se abre `/admin/login`
- **THEN** hay un campo etiquetado "Email" y otro "Contraseña", el foco es visible y se puede completar y enviar el formulario usando solo el teclado

#### Scenario: Campos vacíos

- **WHEN** se envía el formulario sin email o sin contraseña
- **THEN** no se envía la solicitud y se muestra un mensaje de error asociado al campo faltante

#### Scenario: Email con formato inválido

- **WHEN** se envía el formulario con un email que no tiene formato de correo
- **THEN** no se envía la solicitud y el campo email marca el error

### Requirement: Login exitoso

Al enviar credenciales válidas, el front SHALL llamar a `POST /api/auth/login` con un cuerpo `LoginRequest` del contrato (`email` y `password`) y, ante un 200 con `LoginResponse`, SHALL iniciar la sesión y mostrar `/admin/agenda`.

#### Scenario: Credenciales válidas

- **WHEN** se envía el formulario con el email y la contraseña del odontólogo sembrado
- **THEN** se guarda la sesión con el token recibido y se navega a `/admin/agenda`

#### Scenario: Solicitud según el contrato

- **WHEN** se envía el formulario
- **THEN** la solicitud usa el método POST, la ruta `/api/auth/login` y un cuerpo JSON con `email` y `password`, sin enviar la contraseña en la URL ni en logs

### Requirement: Error de credenciales inválidas

Ante un 401 con `codigo: "CREDENCIALES_INVALIDAS"`, el formulario SHALL mostrar el mensaje genérico del error sin revelar si falló el email o la contraseña, SHALL mantener al usuario en `/admin/login` y NO SHALL iniciar sesión ni redirigir por sesión expirada.

#### Scenario: Credenciales incorrectas

- **WHEN** la API responde 401 `CREDENCIALES_INVALIDAS` al enviar el formulario
- **THEN** se ve "Email o contraseña incorrectos." o un mensaje equivalente del contrato, sigue visible el formulario y no queda ningún token guardado

### Requirement: Errores de validación del login

Ante un 400 con `codigo: "VALIDACION"`, el formulario SHALL mostrar los `detalles` del contrato asociados a cada campo; ante cualquier otro error, SHALL mostrar un mensaje genérico sin exponer detalles internos.

#### Scenario: Detalle por campo

- **WHEN** la API responde 400 `VALIDACION` con un detalle para `email`
- **THEN** el mensaje del detalle se muestra junto al campo email

#### Scenario: Error sin formato uniforme

- **WHEN** la respuesta de error no tiene el formato uniforme del contrato
- **THEN** se muestra un mensaje genérico en español y no se muestra la respuesta cruda

### Requirement: Sesión con vencimiento

La sesión SHALL persistir en el almacenamiento local del navegador e incluir el token, el tipo Bearer, el instante de expiración informado por el login y el nombre del odontólogo. Una sesión SHALL considerarse válida solo mientras exista un token y su expiración no haya pasado.

#### Scenario: Persistencia entre recargas

- **WHEN** se inicia sesión y se recarga la página
- **THEN** la sesión sigue disponible con el mismo token y datos

#### Scenario: Expiración local

- **WHEN** el instante de expiración guardado es anterior al momento actual
- **THEN** la sesión se considera inválida, aunque el token siga almacenado

#### Scenario: Datos sin expiración

- **WHEN** existe un token legacy guardado sin instante de expiración
- **THEN** la sesión se considera válida mientras el token exista, para no romper sesiones ya iniciadas

### Requirement: Cierre de sesión

El área privada SHALL ofrecer un control visible para cerrar sesión que elimine los datos guardados y deje al usuario en `/admin/login`, sin llamar a un endpoint de logout.

#### Scenario: Cerrar sesión

- **WHEN** el odontólogo activa el control "Cerrar sesión"
- **THEN** se eliminan el token y los datos de la sesión y se navega a `/admin/login`

#### Scenario: Vuelta atrás después de cerrar sesión

- **WHEN** después de cerrar sesión se navega a `/admin/agenda`
- **THEN** el guard redirige a `/admin/login` porque ya no hay sesión válida

### Requirement: Guard de rutas privadas

Las rutas `/admin/agenda`, `/admin/configuracion` y `/admin/pacientes` SHALL requerir una sesión válida. Sin una sesión válida, el front SHALL redirigir a `/admin/login` sin renderizar la pantalla privada; `/admin/login` SHALL permanecer accesible sin sesión.

#### Scenario: Ruta privada sin sesión

- **WHEN** se abre `/admin/configuracion` sin token guardado
- **THEN** se navega a `/admin/login` y no se muestra el contenido de configuración

#### Scenario: Ruta privada con sesión vencida

- **WHEN** se abre `/admin/pacientes` con una sesión cuyo vencimiento ya pasó
- **THEN** se navega a `/admin/login` y no se muestra el contenido de pacientes

#### Scenario: Ruta pública del área

- **WHEN** se abre `/admin/login` sin sesión
- **THEN** se muestra el formulario de acceso sin redirección

#### Scenario: Sesión válida

- **WHEN** se abre `/admin/agenda` con una sesión vigente
- **THEN** se muestra la agenda

### Requirement: Reacción ante 401 de sesión expirada

Cuando una solicitud a una ruta privada responda 401 con `codigo: "NO_AUTENTICADO"`, el front SHALL eliminar la sesión guardada y redirigir a `/admin/login`, mientras sigue entregando el error tipado al código que esperaba la respuesta.

#### Scenario: Sesión expirada durante el uso

- **WHEN** una pantalla del área admin recibe un 401 `NO_AUTENTICADO` de la API
- **THEN** se borra la sesión, se navega a `/admin/login` y el llamador recibe el error con status 401 y código `NO_AUTENTICADO`

#### Scenario: Error 401 del login

- **WHEN** `POST /api/auth/login` responde 401 `CREDENCIALES_INVALIDAS`
- **THEN** no se borra ninguna sesión por expiración ni se dispara la redirección de sesión expirada

#### Scenario: Handler no registrado

- **WHEN** una prueba o pantalla llama a la API fuera del bootstrap del área admin, donde no hay redirección registrada
- **THEN** el error 401 se entrega al llamador sin provocar una navegación

### Requirement: Layout del área admin

Las pantallas privadas SHALL mostrar una navegación con enlaces a Agenda, Configuración y Pacientes y un control de cerrar sesión. La navegación SHALL marcar la sección activa y SHALL permitir cambiar de sección sin recargar el documento. El formulario de login SHALL mostrarse sin esa navegación.

#### Scenario: Navegación en pantalla privada

- **WHEN** se abre cualquier pantalla privada con sesión válida
- **THEN** se ven los enlaces a Agenda, Configuración y Pacientes, el enlace de la sección actual está marcado como activo y hay un control de cerrar sesión

#### Scenario: Cambio de sección sin recarga

- **WHEN** se activa el enlace "Configuración" desde la navegación
- **THEN** la URL cambia a `/admin/configuracion` y se muestra esa pantalla sin recargar el documento

#### Scenario: Login sin navegación

- **WHEN** se abre `/admin/login`
- **THEN** no se muestran los enlaces de navegación del área admin
