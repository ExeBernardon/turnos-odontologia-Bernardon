## ADDED Requirements

### Requirement: Listado de prestaciones del odontólogo
`GET /api/prestaciones` SHALL requerir autenticación y devolver todas las prestaciones, activas e inactivas, con los campos `id`, `nombre`, `duracionMinutos`, `activa`.

#### Scenario: Lista completa
- **WHEN** el odontólogo autenticado pide `GET /api/prestaciones` y existen prestaciones activas e inactivas
- **THEN** responde 200 con todas, cada una con exactamente `id`, `nombre`, `duracionMinutos` y `activa`

#### Scenario: Sin autenticación
- **WHEN** se pide `GET /api/prestaciones` sin credenciales
- **THEN** responde 401 `NO_AUTENTICADO`

### Requirement: Alta y reemplazo de prestaciones
`POST /api/prestaciones` SHALL crear una prestación y responder 201; `PUT /api/prestaciones/{id}` SHALL reemplazar el recurso completo y responder 200. `nombre` es obligatorio (1 a 100 caracteres) y `duracionMinutos` es obligatorio entre 1 y 480; `activa` por defecto es `true`.

#### Scenario: Alta válida
- **WHEN** se hace POST con `{"nombre":"Consulta","duracionMinutos":30}`
- **THEN** responde 201 con `id` generado y `activa: true`

#### Scenario: Duración inválida
- **WHEN** se hace POST con `duracionMinutos` 0 o 481
- **THEN** responde 400 `VALIDACION` con un detalle para el campo `duracionMinutos`

#### Scenario: Nombre en blanco
- **WHEN** se hace POST con `nombre` vacío
- **THEN** responde 400 `VALIDACION` con un detalle para el campo `nombre`

#### Scenario: Actualización
- **WHEN** se hace PUT sobre una prestación existente con nueva duración
- **THEN** responde 200 con los valores nuevos y los turnos existentes conservan su `fin`

#### Scenario: Actualizar inexistente
- **WHEN** se hace PUT sobre un id inexistente
- **THEN** responde 404 `RECURSO_NO_ENCONTRADO`

### Requirement: Eliminación condicionada a no tener turnos
`DELETE /api/prestaciones/{id}` SHALL eliminar la prestación y responder 204 solo si no tiene turnos; si tiene turnos SHALL responder 422 `PRESTACION_CON_TURNOS` sin eliminarla; si no existe, 404.

#### Scenario: Sin turnos
- **WHEN** se elimina una prestación sin turnos
- **THEN** responde 204 y deja de existir

#### Scenario: Con turnos
- **WHEN** se elimina una prestación que tiene al menos un turno
- **THEN** responde 422 `PRESTACION_CON_TURNOS` y la prestación sigue existiendo

#### Scenario: Inexistente
- **WHEN** se elimina un id inexistente
- **THEN** responde 404 `RECURSO_NO_ENCONTRADO`

### Requirement: Listado público de prestaciones activas
`GET /api/public/prestaciones` SHALL ser accesible sin autenticación y devolver solo las prestaciones activas con los campos `id`, `nombre`, `duracionMinutos`.

#### Scenario: Solo activas
- **WHEN** existen prestaciones activas e inactivas y se pide el listado público sin credenciales
- **THEN** responde 200 solo con las activas y sin el campo `activa`
