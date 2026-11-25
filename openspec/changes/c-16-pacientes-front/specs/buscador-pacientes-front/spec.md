## Purpose

Define el comportamiento del componente reutilizable de búsqueda y selección de paciente del área admin: cómo se busca por DNI o apellido, cómo se muestran los resultados y estados y cómo informa el paciente elegido a la pantalla que lo contiene (pacientes y carga manual de turnos).

## ADDED Requirements

### Requirement: Búsqueda por DNI o apellido
El componente SHALL ofrecer un campo de texto con label visible y un botón de búsqueda. Al enviar (botón o Enter) SHALL consultar `GET /api/pacientes` con `q` igual al texto sin espacios de borde; con el texto vacío SHALL consultar sin `q` (todos los pacientes). El texto SHALL enviarse tal cual lo escribió el usuario (el backend decide si es DNI o apellido).

#### Scenario: Búsqueda por apellido
- **WHEN** el usuario escribe `  Ejem ` y envía
- **THEN** se consulta `GET /api/pacientes?q=Ejem`

#### Scenario: Búsqueda por DNI con puntos
- **WHEN** el usuario escribe `99.999` y envía
- **THEN** se consulta con `q=99.999` codificado en la URL

#### Scenario: Búsqueda vacía
- **WHEN** el usuario envía el campo vacío o solo con espacios
- **THEN** se consulta `GET /api/pacientes` sin parámetro `q`

### Requirement: Resultados de la búsqueda
Cada resultado SHALL mostrar apellido, nombre y DNI del paciente en el orden que devuelve la API, y SHALL tener un botón cuyo nombre accesible incluye el apellido y el nombre del paciente para seleccionarlo.

#### Scenario: Lista con resultados
- **WHEN** la API devuelve el ejemplo `pacientes-listar.200.json`
- **THEN** se muestran dos resultados, "Ejemplo, Ana" y "Muestra, Carla", cada uno con su DNI y su botón de selección

### Requirement: Selección de paciente
Al activar el botón de un resultado (clic o teclado) el componente SHALL notificar a la pantalla contenedora el paciente elegido con los datos recibidos de la API, sin navegar ni hacer otra consulta por su cuenta.

#### Scenario: Selecciona un paciente
- **WHEN** el usuario activa el botón de "Muestra, Carla"
- **THEN** la pantalla contenedora recibe el paciente con id 3 y no se hace ninguna llamada adicional a la API

### Requirement: Estados de la búsqueda anunciados
El componente SHALL anunciar en una región `role="status"` que está buscando y la cantidad de resultados, SHALL mostrar un mensaje cuando no hay coincidencias y SHALL mostrar en `role="alert"` un mensaje comprensible ante un error de API o de conexión, sin detalles internos, con una forma de reintentar.

#### Scenario: Sin coincidencias
- **WHEN** la API devuelve una lista vacía
- **THEN** se anuncia que no hay pacientes que coincidan con la búsqueda

#### Scenario: Cantidad de resultados
- **WHEN** la API devuelve dos pacientes
- **THEN** la región de estado anuncia "2 pacientes encontrados"

#### Scenario: Error de conexión
- **WHEN** la consulta falla por un error de conexión
- **THEN** se muestra el mensaje de conexión en la alerta y un botón para reintentar la misma búsqueda

### Requirement: Respuestas fuera de orden
Si se envía una búsqueda nueva antes de que responda la anterior, el componente SHALL mostrar solo los resultados de la última búsqueda enviada.

#### Scenario: Respuesta vieja llega tarde
- **WHEN** se busca `Ej` y luego `Mu`, y la respuesta de `Ej` llega después de la de `Mu`
- **THEN** la lista muestra solo los resultados de `Mu`

### Requirement: Componente independiente y accesible
El componente SHALL poder montarse más de una vez en el área admin sin ids repetidos, SHALL operarse completo con teclado con foco visible y SHALL adaptarse a pantallas angostas sin scroll horizontal.

#### Scenario: Dos instancias
- **WHEN** se montan dos buscadores en la misma página
- **THEN** cada campo tiene un id único y su label apunta a su propio campo
