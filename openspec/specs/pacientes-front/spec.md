# pacientes-front Specification

## Purpose
Define el comportamiento visible para el odontólogo de la pantalla `/admin/pacientes`: cómo busca pacientes, cómo ve su detalle con el contacto del último turno en solo lectura, cómo los da de alta y edita y cómo se comunican los errores de la API.

## Requirements

### Requirement: Listado y búsqueda de pacientes
La pantalla SHALL tener un único encabezado de nivel 1 "Pacientes" y SHALL usar el buscador de pacientes del área admin. Al entrar SHALL cargar la lista completa (búsqueda vacía) y SHALL permitir filtrar por DNI o apellido.

#### Scenario: Carga inicial
- **WHEN** el odontólogo entra a `/admin/pacientes`
- **THEN** se consulta `GET /api/pacientes` sin `q` y se listan los pacientes devueltos

#### Scenario: Filtra por apellido
- **WHEN** busca `Muestra`
- **THEN** se consulta con `q=Muestra` y la lista se reemplaza por los resultados

### Requirement: Detalle del paciente con último contacto
Al seleccionar un paciente la pantalla SHALL pedir `GET /api/pacientes/{id}` y mostrar nombre, apellido, DNI y el `ultimoContacto` (email y teléfono) como texto de solo lectura, indicando que es el contacto de su último turno. El detalle SHALL ofrecer editar al paciente y SHALL NOT ofrecer eliminarlo.

#### Scenario: Paciente con turnos
- **WHEN** se selecciona el paciente del ejemplo `pacientes-obtener.200.json`
- **THEN** el detalle muestra `paciente@ejemplo.com` y `+54 9 11 0000-0000` como contacto del último turno, sin campos editables para ellos

#### Scenario: Paciente sin turnos
- **WHEN** el paciente seleccionado tiene `ultimoContacto` en `null`
- **THEN** el detalle muestra "Sin turnos registrados: todavía no hay datos de contacto"

#### Scenario: Paciente inexistente
- **WHEN** la API responde 404 al pedir el detalle
- **THEN** se muestra que el paciente ya no existe y se recarga la búsqueda actual

### Requirement: Alta de paciente
El odontólogo SHALL poder crear un paciente con nombre, apellido y DNI. Al crearse (201) la pantalla SHALL anunciar el alta, mostrar el detalle del paciente creado y actualizar la lista con la búsqueda actual. El formulario SHALL NOT pedir email ni teléfono.

#### Scenario: Alta válida
- **WHEN** completa el ejemplo `pacientes-crear.request.json` y envía
- **THEN** se llama `POST /api/pacientes` con nombre, apellido y DNI sin espacios de borde, se anuncia "Paciente creado" y el detalle muestra el DNI `99999997`

### Requirement: Edición de paciente
Desde el detalle el odontólogo SHALL poder editar nombre, apellido y DNI en un formulario precargado. Al guardar (200) SHALL anunciarse la edición, mostrarse el detalle actualizado y refrescarse la lista. SHALL poder cancelar la edición sin llamar a la API.

#### Scenario: Edición válida
- **WHEN** edita el paciente 3 con el ejemplo `pacientes-actualizar.request.json` y guarda
- **THEN** se llama `PUT /api/pacientes/3` y el detalle muestra el apellido "Muestra Prueba"

#### Scenario: Cancela la edición
- **WHEN** abre la edición y pulsa cancelar
- **THEN** no se llama a la API y el detalle conserva los datos anteriores

#### Scenario: Paciente inexistente al guardar
- **WHEN** la API responde 404 al editar
- **THEN** se muestra que el paciente ya no existe y se recarga la búsqueda actual

### Requirement: Validación local del formulario
Antes de enviar, la pantalla SHALL validar que nombre y apellido no estén vacíos (sin contar espacios de borde) ni superen 100 caracteres, y que el DNI tenga 7 u 8 dígitos o el formato con puntos `d.ddd.ddd` / `dd.ddd.ddd`. Con errores SHALL NOT enviar y SHALL mostrar cada error junto a su campo.

#### Scenario: DNI con formato inválido
- **WHEN** ingresa `99-999-997` como DNI
- **THEN** no se llama a la API y el campo DNI muestra un error con `aria-invalid="true"`

#### Scenario: DNI de 7 dígitos y con puntos
- **WHEN** ingresa `9999999` o `99.999.997`
- **THEN** el DNI se acepta y se envía como fue escrito

#### Scenario: Nombre vacío
- **WHEN** deja el nombre en blanco
- **THEN** no se llama a la API y el campo nombre muestra un error

### Requirement: Errores de la API en alta y edición
La pantalla SHALL mostrar el 422 `DNI_DUPLICADO` junto al campo DNI con el texto "Ya existe un paciente con ese DNI." y el 400 `VALIDACION` con cada `detalle` junto a su campo, conservando los valores ingresados. Otros errores SHALL mostrarse en la alerta general sin detalles internos.

#### Scenario: DNI duplicado en el alta
- **WHEN** la API responde el ejemplo `pacientes-crear.422-dni-duplicado.json`
- **THEN** el campo DNI muestra "Ya existe un paciente con ese DNI.", queda con `aria-invalid="true"` y recibe el foco, y el formulario conserva sus valores

#### Scenario: DNI duplicado en la edición
- **WHEN** la API responde el ejemplo `pacientes-actualizar.422-dni-duplicado.json`
- **THEN** el error aparece en el campo DNI y el detalle no cambia

#### Scenario: Validación por campo del servidor
- **WHEN** la API responde 400 `VALIDACION` con un detalle para `apellido`
- **THEN** el mensaje aparece junto al campo apellido

### Requirement: Accesibilidad y responsive de la pantalla
Cada campo SHALL tener un `<label>` asociado, foco visible y operación completa con teclado; los errores SHALL asociarse con `aria-invalid`/`aria-describedby`, los avisos con `role="status"` y las fallas con `role="alert"`. El estado no SHALL comunicarse solo con color y el diseño SHALL adaptarse a pantallas angostas sin scroll horizontal.

#### Scenario: Formulario accesible
- **WHEN** se abre el formulario de alta
- **THEN** los campos Nombre, Apellido y DNI tienen su label y su región de error asociada, y existen las regiones de estado y de alerta
