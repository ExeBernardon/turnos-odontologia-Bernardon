## Purpose

Define el comportamiento visible para el odontólogo de la sección de bloqueos en `/admin/configuracion`: cómo lista, crea y elimina bloqueos, cómo previsualiza y confirma la cancelación de turnos afectados, cómo se muestran y envían las fechas en hora de Buenos Aires y cómo se comunican los errores de la API.

## ADDED Requirements

### Requirement: Listado de bloqueos por rango
La sección SHALL listar los bloqueos que se superponen con un rango de fechas (`desde` y `hasta`, ambos inclusive, máximo 31 días). Por defecto el rango va de hoy a 30 días después (Buenos Aires) y SHALL poder cambiarse con dos campos de fecha. Cada bloqueo SHALL mostrar inicio, fin y motivo.

#### Scenario: Lista con bloqueos
- **WHEN** la API devuelve bloqueos para el rango
- **THEN** se listan con inicio y fin formateados en hora de Buenos Aires y su motivo (o "Sin motivo")

#### Scenario: Sin bloqueos
- **WHEN** la API devuelve una lista vacía
- **THEN** se muestra un mensaje indicando que no hay bloqueos en el rango

#### Scenario: Rango inválido
- **WHEN** el odontólogo elige un rango con `desde` posterior a `hasta`, vacío o de más de 31 días
- **THEN** no se consulta y se muestra un error asociado al campo correspondiente

#### Scenario: Error al cargar
- **WHEN** la carga falla por error de API o de conexión
- **THEN** se muestra un mensaje comprensible y una forma de reintentar

### Requirement: Conversión de fechas en hora de Buenos Aires
Los valores de `datetime-local` SHALL interpretarse como hora de `America/Argentina/Buenos_Aires` y enviarse como ISO-8601 con offset `-03:00`. Los instantes recibidos SHALL mostrarse en esa zona con `Intl.DateTimeFormat('es-AR')`, sin depender de la zona del navegador.

#### Scenario: Envío con offset
- **WHEN** el odontólogo ingresa `2026-12-24T09:00`
- **THEN** el pedido lleva `2026-12-24T09:00:00-03:00`

#### Scenario: Visualización independiente de la zona del navegador
- **WHEN** se recibe `2026-12-24T12:00:00Z`
- **THEN** se muestra como las 09:00 del 24/12/2026

### Requirement: Alta de bloqueo en dos pasos
El odontólogo SHALL poder crear un bloqueo con inicio, fin y motivo opcional (máximo 200 caracteres). El front SHALL validar antes de enviar que inicio y fin estén completos y que inicio sea anterior a fin. Antes de crear SHALL previsualizar; si hay turnos afectados SHALL listarlos con un aviso y pedir confirmación explícita, y al confirmar SHALL crear con `cancelarTurnosAfectados=true`. Sin turnos afectados SHALL crear directamente.

#### Scenario: Sin turnos afectados
- **WHEN** la vista previa devuelve una lista vacía
- **THEN** se crea el bloqueo sin pedir confirmación y aparece en el listado

#### Scenario: Con turnos afectados confirma
- **WHEN** la vista previa devuelve turnos y el odontólogo confirma
- **THEN** se crea con `cancelarTurnosAfectados=true`, se informa cuántos turnos se cancelaron y el listado se actualiza

#### Scenario: Con turnos afectados cancela el flujo
- **WHEN** la vista previa devuelve turnos y el odontólogo cancela
- **THEN** no se crea nada y el formulario conserva sus valores

#### Scenario: Turno nuevo entre vista previa y creación
- **WHEN** la creación responde 409 `BLOQUEO_CON_TURNOS_AFECTADOS`
- **THEN** se reemplaza la lista por los `turnosAfectados` de la respuesta y se pide confirmar de nuevo

#### Scenario: Validación local
- **WHEN** falta inicio o fin, inicio no es anterior a fin, o el motivo supera 200 caracteres
- **THEN** no se envía y se muestra un error asociado al campo

### Requirement: Errores de la API en el alta
La sección SHALL mostrar el 422 `RANGO_HORARIO_INVALIDO` y el 400 `VALIDACION` (con `detalles` junto a cada campo) en español, conservando los valores ingresados, y SHALL no exponer detalles internos en otros errores.

#### Scenario: Rango inválido del servidor
- **WHEN** la API responde 422 `RANGO_HORARIO_INVALIDO`
- **THEN** se muestra un mensaje claro y el formulario conserva sus valores

#### Scenario: Validación por campo
- **WHEN** la API responde 400 `VALIDACION` con `detalles` para `motivo`
- **THEN** el mensaje aparece junto al campo motivo con `aria-invalid`

### Requirement: Baja de bloqueo con confirmación
El odontólogo SHALL poder eliminar un bloqueo tras una confirmación inline. La baja no restituye turnos cancelados y la interfaz SHALL indicarlo.

#### Scenario: Confirma la baja
- **WHEN** pulsa eliminar y confirma
- **THEN** se llama a `DELETE /api/bloqueos/{id}` y el bloqueo desaparece del listado

#### Scenario: Cancela la baja
- **WHEN** pulsa eliminar y cancela
- **THEN** no se llama a la API y el bloqueo permanece

#### Scenario: Bloqueo inexistente
- **WHEN** la API responde 404 al eliminar
- **THEN** se muestra que el elemento ya no existe y se recarga el listado

### Requirement: Accesibilidad y responsive
Cada campo SHALL tener un `<label>` asociado, foco visible y operación completa con teclado; los errores SHALL asociarse con `aria-invalid`/`aria-describedby`, los avisos con `role="status"` y las fallas con `role="alert"`. El diseño SHALL adaptarse a pantallas angostas.

#### Scenario: Campos accesibles
- **WHEN** se renderiza la sección
- **THEN** cada campo tiene su label y las regiones de estado y de alerta existen
