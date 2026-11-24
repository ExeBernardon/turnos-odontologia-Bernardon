# Spec Delta — api-errores

## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Error interno genérico sin filtrar detalles
Ante una excepción no prevista, la API SHALL responder 500 con el formato uniforme y `codigo` `ERROR_INTERNO`, con un `mensaje` genérico en español. La respuesta SHALL NOT incluir el mensaje de la excepción, el stack trace ni datos internos; el detalle SHALL registrarse solo en el log del servidor.

#### Scenario: Excepción inesperada
- **WHEN** un endpoint lanza una excepción no prevista por el contrato
- **THEN** la respuesta es 500 con `status` 500, `error` `Internal Server Error`, `codigo` `ERROR_INTERNO`, `mensaje`, `path` y `timestamp`, sin `detalles`

#### Scenario: Sin filtración de detalles
- **WHEN** la excepción inesperada tiene un mensaje interno (por ejemplo un error SQL)
- **THEN** ese mensaje no aparece en el cuerpo de la respuesta
