# Spec Delta

## Purpose

Permitir al odontólogo buscar, ver, dar de alta y editar a sus pacientes, identificados por un DNI único normalizado, y ofrecer el contacto de su último turno como referencia para la carga manual, sin que el paciente guarde email ni teléfono propios.

## ADDED Requirements

### Requirement: Endpoints de pacientes privados
Todos los endpoints de `/api/pacientes` SHALL requerir un JWT válido del odontólogo. Sin token o con un token inválido SHALL responder 401 `NO_AUTENTICADO` con el formato uniforme de error.

#### Scenario: Búsqueda sin token
- **WHEN** se llama `GET /api/pacientes` sin header `Authorization`
- **THEN** la respuesta es 401 con `codigo=NO_AUTENTICADO`

#### Scenario: Alta sin token
- **WHEN** se llama `POST /api/pacientes` sin token
- **THEN** la respuesta es 401 y no se crea ningún paciente

### Requirement: Forma del paciente en las respuestas
Cada paciente devuelto SHALL tener exactamente las claves `id`, `nombre`, `apellido`, `dni` y `ultimoContacto`; `dni` SHALL ir solo con dígitos y `ultimoContacto` SHALL estar presente siempre, como `{email, telefono}` o `null`. La respuesta SHALL NOT incluir email, teléfono ni otros datos propios del paciente.

#### Scenario: Paciente con turnos
- **WHEN** se obtiene un paciente que tiene al menos un turno
- **THEN** la respuesta tiene las cinco claves del contrato y `ultimoContacto` con `email` y `telefono`

#### Scenario: Paciente sin turnos
- **WHEN** se obtiene un paciente sin turnos
- **THEN** `ultimoContacto` es `null` y la clave está presente

### Requirement: Contacto del último turno
`ultimoContacto` SHALL tomar `email_contacto` y `telefono_contacto` del turno del paciente creado más recientemente (desempate por id mayor), cualquiera sea su estado, incluido `CANCELADO`. Es de solo lectura y SHALL NOT modificarse por ningún endpoint de pacientes.

#### Scenario: Varios turnos con contactos distintos
- **WHEN** un paciente tiene tres turnos creados en distinto momento con emails distintos
- **THEN** `ultimoContacto` trae el email y teléfono del turno creado último, aunque otro turno tenga un `inicio` posterior

#### Scenario: Último turno cancelado
- **WHEN** el turno creado más recientemente del paciente está `CANCELADO`
- **THEN** `ultimoContacto` igual trae el contacto de ese turno

#### Scenario: Contacto en el listado
- **WHEN** se listan varios pacientes, algunos con turnos y otros sin turnos
- **THEN** cada ítem trae su propio `ultimoContacto` o `null`, sin mezclar contactos entre pacientes

### Requirement: Búsqueda de pacientes
`GET /api/pacientes` SHALL devolver una lista sin paginación ordenada por apellido, nombre e id. Sin `q` o con `q` en blanco SHALL devolver todos. Si `q` sin espacios de borde y sin puntos es solo dígitos, SHALL filtrar por DNI que empiece con esos dígitos; si no, por apellido que empiece con `q` sin distinguir mayúsculas. Los caracteres de `q` SHALL tratarse literalmente.

#### Scenario: Búsqueda por prefijo de DNI
- **WHEN** existen pacientes con DNI `99999999` y `88888888` y se busca `q=9999`
- **THEN** la respuesta contiene solo el paciente con DNI `99999999`

#### Scenario: Búsqueda por DNI con puntos
- **WHEN** se busca `q=99.999`
- **THEN** se trata como el prefijo de DNI `99999` y devuelve los pacientes cuyo DNI empieza así

#### Scenario: Búsqueda por prefijo de apellido sin distinguir mayúsculas
- **WHEN** existen los apellidos `Ejemplo` y `Muestra` y se busca `q=ejem`
- **THEN** la respuesta contiene solo el paciente de apellido `Ejemplo`

#### Scenario: Sin coincidencias
- **WHEN** se busca un `q` que no es prefijo de ningún DNI ni apellido
- **THEN** la respuesta es 200 con una lista vacía

#### Scenario: Comodines tratados como texto
- **WHEN** se busca `q=%`
- **THEN** solo coinciden apellidos que empiezan con `%` (ninguno en datos normales), no todos los pacientes

#### Scenario: Sin filtro
- **WHEN** se llama `GET /api/pacientes` sin `q`
- **THEN** la respuesta contiene todos los pacientes ordenados por apellido y nombre

### Requirement: Detalle de paciente
`GET /api/pacientes/{id}` SHALL devolver el paciente con su `ultimoContacto`. Un id inexistente SHALL responder 404 `RECURSO_NO_ENCONTRADO` y un id no numérico 400 `SOLICITUD_MAL_FORMADA`.

#### Scenario: Paciente existente
- **WHEN** se pide el detalle de un paciente existente
- **THEN** la respuesta es 200 con sus datos y `ultimoContacto`

#### Scenario: Paciente inexistente
- **WHEN** se pide un id que no existe
- **THEN** la respuesta es 404 `RECURSO_NO_ENCONTRADO`

#### Scenario: Id no numérico
- **WHEN** se pide `GET /api/pacientes/abc`
- **THEN** la respuesta es 400 `SOLICITUD_MAL_FORMADA`

### Requirement: Normalización del DNI
El backend SHALL aceptar el DNI con o sin puntos (7 u 8 dígitos, o `d{1,2}.ddd.ddd`) y SHALL guardarlo y devolverlo solo con dígitos. Un DNI con otro formato SHALL responder 400 `VALIDACION` con el campo `dni` en `detalles`.

#### Scenario: DNI con puntos
- **WHEN** se crea un paciente con `dni=99.999.997`
- **THEN** la respuesta 201 trae `dni=99999997` y así queda guardado

#### Scenario: DNI de 7 dígitos
- **WHEN** se crea un paciente con `dni=9999999`
- **THEN** se acepta y se devuelve `9999999`

#### Scenario: DNI con formato inválido
- **WHEN** se crea un paciente con `dni=99-999-997` o con letras
- **THEN** la respuesta es 400 `VALIDACION` con un detalle para `dni` y no se crea nada

### Requirement: Alta de paciente
`POST /api/pacientes` SHALL crear el paciente con nombre, apellido (1 a 100 caracteres, sin espacios de borde) y DNI normalizado, y responder 201 con la forma del contrato y `ultimoContacto=null`. Campos faltantes o inválidos SHALL responder 400 `VALIDACION` y un JSON ilegible 400 `SOLICITUD_MAL_FORMADA`. Campos de contacto enviados en el body SHALL ignorarse.

#### Scenario: Alta válida
- **WHEN** se envía el ejemplo `pacientes-crear.request.json`
- **THEN** la respuesta es 201 con la forma de `pacientes-crear.201.json`

#### Scenario: Nombre faltante o vacío
- **WHEN** se crea un paciente sin `nombre` o con `nombre` en blanco
- **THEN** la respuesta es 400 `VALIDACION` con un detalle para `nombre`

#### Scenario: JSON ilegible
- **WHEN** el body no es JSON válido
- **THEN** la respuesta es 400 `SOLICITUD_MAL_FORMADA` y no se crea nada

#### Scenario: El paciente no guarda contacto propio
- **WHEN** el body incluye además `email` y `telefono`
- **THEN** la respuesta es 201 sin esas claves, `ultimoContacto` es `null` y la tabla `paciente` no tiene columnas de contacto

### Requirement: DNI único
Un alta o edición cuyo DNI normalizado ya pertenece a otro paciente SHALL responder 422 `DNI_DUPLICADO` ("Ya existe un paciente con ese DNI.") sin crear ni modificar nada, también cuando dos altas con el mismo DNI llegan a la vez. Editar un paciente conservando su propio DNI SHALL NOT dar 422.

#### Scenario: Alta con DNI existente escrito distinto
- **WHEN** existe el DNI `99999997` y se crea otro paciente con `dni=99.999.997`
- **THEN** la respuesta es 422 `DNI_DUPLICADO` y sigue habiendo un solo paciente con ese DNI

#### Scenario: Edición con DNI de otro paciente
- **WHEN** se edita el paciente A poniéndole el DNI del paciente B
- **THEN** la respuesta es 422 `DNI_DUPLICADO` y A conserva sus datos

#### Scenario: Edición conservando el propio DNI
- **WHEN** se edita el apellido de un paciente enviando su mismo DNI
- **THEN** la respuesta es 200 con el apellido nuevo

#### Scenario: Altas simultáneas
- **WHEN** dos altas con el mismo DNI llegan a la vez
- **THEN** una responde 201 y la otra 422 `DNI_DUPLICADO`, nunca 500

### Requirement: Edición de paciente
`PUT /api/pacientes/{id}` SHALL reemplazar nombre, apellido y DNI con las mismas validaciones del alta y responder 200 con la forma del contrato y su `ultimoContacto`. Un id inexistente SHALL responder 404 `RECURSO_NO_ENCONTRADO`. Es la única vía de la API para modificar a un paciente y SHALL NOT modificar sus turnos.

#### Scenario: Edición válida
- **WHEN** se envía `pacientes-actualizar.request.json` a un paciente existente
- **THEN** la respuesta es 200 con `dni` normalizado y los datos nuevos

#### Scenario: Edición de paciente con turnos
- **WHEN** se edita un paciente con turnos
- **THEN** los turnos conservan su `email_contacto` y `telefono_contacto` y la respuesta trae `ultimoContacto`

#### Scenario: Paciente inexistente
- **WHEN** se edita un id que no existe
- **THEN** la respuesta es 404 `RECURSO_NO_ENCONTRADO`

### Requirement: Sin borrado de pacientes
La API SHALL NOT ofrecer borrado de pacientes: `DELETE /api/pacientes/{id}` SHALL responder 404 `RECURSO_NO_ENCONTRADO` (como cualquier ruta no definida) sin eliminar nada.

#### Scenario: Intento de borrado
- **WHEN** se llama `DELETE /api/pacientes/{id}` con token válido
- **THEN** la respuesta es 404 `RECURSO_NO_ENCONTRADO` y el paciente sigue existiendo
