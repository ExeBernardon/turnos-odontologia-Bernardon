# api-errores Specification

## Purpose

Define el formato uniforme de los errores de la API, el único código HTTP que corresponde a cada tipo de problema y el catálogo estable de códigos de negocio con el que el front reacciona sin parsear mensajes.

## Requirements

### Requirement: Formato de error uniforme
Toda respuesta de error de la API SHALL tener cuerpo JSON con `timestamp` (ISO-8601 con offset), `status` (número), `error` (nombre del estado HTTP), `codigo` (del catálogo), `mensaje` (texto en español apto para mostrar) y `path`. `detalles` SHALL aparecer solo en los 400 de validación, como lista de `{campo, mensaje}`.

#### Scenario: Error de negocio con formato uniforme
- **WHEN** un endpoint rechaza un request por una regla de negocio
- **THEN** responde con `status`, `error`, `codigo`, `mensaje`, `path` y `timestamp`, sin `detalles`

#### Scenario: Error de validación con detalles
- **WHEN** un request llega con el campo `dni` con formato inválido y el campo `email` vacío
- **THEN** responde 400 con `codigo` `VALIDACION` y `detalles` con una entrada para `dni` y otra para `email`

### Requirement: Un único código HTTP por tipo de problema
La API SHALL usar solo 200, 201 y 204 para éxito y solo 400, 401, 404, 409 y 422 para errores de negocio y de request: 400 request mal formado o con formato inválido; 401 no autenticado o credenciales inválidas; 404 recurso o token inexistente; 409 conflicto de horario; 422 violación de regla de negocio. El único otro código permitido es 500 `ERROR_INTERNO`, reservado a errores inesperados del servidor. Ningún caso SHALL documentarse con dos códigos alternativos.

#### Scenario: Conflicto de horario
- **WHEN** un turno nuevo o reprogramado se solapa con otro turno activo, incluida la violación de la restricción de exclusión de la base
- **THEN** la respuesta es 409 con `codigo` `HORARIO_OCUPADO`

#### Scenario: DNI duplicado no es conflicto de horario
- **WHEN** se crea un paciente con un DNI que ya existe
- **THEN** la respuesta es 422 con `codigo` `DNI_DUPLICADO`, no 409

#### Scenario: No se usan otros códigos de error
- **WHEN** se revisa la matriz de códigos por endpoint del contrato
- **THEN** ningún endpoint declara 403, 405 ni otro código de error fuera de 400, 401, 404, 409 y 422, y el 500 `ERROR_INTERNO` figura solo como respuesta genérica compartida, no por endpoint

### Requirement: Catálogo cerrado de códigos de negocio
El campo `codigo` SHALL tomar solo valores del catálogo del contrato: `VALIDACION`, `SOLICITUD_MAL_FORMADA` (400); `NO_AUTENTICADO`, `CREDENCIALES_INVALIDAS` (401); `RECURSO_NO_ENCONTRADO`, `TOKEN_INVALIDO` (404); `HORARIO_OCUPADO`, `BLOQUEO_CON_TURNOS_AFECTADOS` (409); los 422 del requirement siguiente; y `ERROR_INTERNO` (500). Agregar o renombrar un código SHALL requerir un change de contrato.

#### Scenario: Cada código pertenece a un solo status
- **WHEN** se lee el catálogo en `docs/api/errores.md` y el enum `CodigoError` del OpenAPI
- **THEN** ambos listan los mismos códigos y cada código está asociado a exactamente un status HTTP

#### Scenario: Token de turno inexistente
- **WHEN** se consulta, confirma o cancela un turno con un token que no existe
- **THEN** la respuesta es 404 con `codigo` `TOKEN_INVALIDO` y no revela ningún dato

### Requirement: Códigos de negocio 422
Los rechazos por regla de negocio SHALL usar: `HORARIO_NO_DISPONIBLE` (fuera de atención, sobre bloqueo, en el pasado, fuera de la grilla o de la ventana de 2 h a 60 días), `CONSENTIMIENTO_REQUERIDO`, `CANCELACION_FUERA_DE_PLAZO`, `CONFIRMACION_FUERA_DE_PLAZO`, `TRANSICION_INVALIDA`, `PRESTACION_INACTIVA`, `PRESTACION_CON_TURNOS`, `DNI_DUPLICADO`, `RANGO_HORARIO_INVALIDO`, `HORARIOS_SOLAPADOS`.

#### Scenario: Cancelación tardía del paciente
- **WHEN** el paciente cancela por token un turno `PENDIENTE` que empieza en menos de 24 h
- **THEN** la respuesta es 422 con `codigo` `CANCELACION_FUERA_DE_PLAZO` y un `mensaje` que indica comunicarse con el consultorio

#### Scenario: Paciente cancela un turno confirmado
- **WHEN** el paciente cancela por token un turno `CONFIRMADO`
- **THEN** la respuesta es 422 con `codigo` `TRANSICION_INVALIDA` y un `mensaje` que indica comunicarse con el consultorio

#### Scenario: Confirmación anticipada del paciente
- **WHEN** el paciente confirma por token un turno `PENDIENTE` que empieza en más de 48 h
- **THEN** la respuesta es 422 con `codigo` `CONFIRMACION_FUERA_DE_PLAZO`

#### Scenario: Marcar asistencia antes del inicio
- **WHEN** el odontólogo marca `ASISTIO` un turno que todavía no empezó
- **THEN** la respuesta es 422 con `codigo` `TRANSICION_INVALIDA`

#### Scenario: Reserva sin consentimiento
- **WHEN** la reserva pública llega con `consentimiento: false`
- **THEN** la respuesta es 422 con `codigo` `CONSENTIMIENTO_REQUERIDO` y no se crea el turno

### Requirement: Conflicto de bloqueo con turnos afectados
Cuando se crea un bloqueo con `cancelarTurnosAfectados: false` y hay turnos activos en el rango, la API SHALL responder 409 `BLOQUEO_CON_TURNOS_AFECTADOS` con el formato uniforme más el campo `turnosAfectados` (lista de turnos de agenda) y SHALL no crear el bloqueo.

#### Scenario: Bloqueo sobre turnos activos sin confirmar cancelación
- **WHEN** el odontólogo crea un bloqueo que contiene dos turnos activos con `cancelarTurnosAfectados: false`
- **THEN** la respuesta es 409 con `codigo` `BLOQUEO_CON_TURNOS_AFECTADOS` y `turnosAfectados` con esos dos turnos, y el bloqueo no existe

### Requirement: Matriz de códigos por endpoint
`docs/api/errores.md` SHALL incluir una matriz que indique, para cada endpoint, el código de éxito y cada código de error posible con su `codigo` de negocio, coherente con las respuestas declaradas en el OpenAPI.

#### Scenario: Matriz coherente con el OpenAPI
- **WHEN** se compara la matriz con las respuestas de cada operación de `docs/api/openapi.yaml`
- **THEN** cada status de la matriz está declarado en la operación y viceversa

### Requirement: Error interno genérico sin filtrar detalles
Ante una excepción no prevista, la API SHALL responder 500 con el formato uniforme y `codigo` `ERROR_INTERNO`, con un `mensaje` genérico en español. La respuesta SHALL NOT incluir el mensaje de la excepción, el stack trace ni datos internos; el detalle SHALL registrarse solo en el log del servidor.

#### Scenario: Excepción inesperada
- **WHEN** un endpoint lanza una excepción no prevista por el contrato
- **THEN** la respuesta es 500 con `status` 500, `error` `Internal Server Error`, `codigo` `ERROR_INTERNO`, `mensaje`, `path` y `timestamp`, sin `detalles`

#### Scenario: Sin filtración de detalles
- **WHEN** la excepción inesperada tiene un mensaje interno (por ejemplo un error SQL)
- **THEN** ese mensaje no aparece en el cuerpo de la respuesta
