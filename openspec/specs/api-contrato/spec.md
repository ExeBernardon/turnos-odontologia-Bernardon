# api-contrato Specification

## Purpose

Fija el contrato de la API REST del sistema de turnos (rutas, autenticación, DTOs, convenciones de datos y artefactos derivados) como fuente única de verdad para que backend y front se implementen en paralelo sin inventar interfaces.

## Requirements

### Requirement: Contrato OpenAPI como fuente única de verdad
El repositorio SHALL contener `docs/api/openapi.yaml` en OpenAPI 3.1, válido para un linter de OpenAPI, que describa todos los endpoints del MVP con sus schemas de request y response, sus respuestas de error y sus ejemplos.

#### Scenario: El contrato pasa el linter
- **WHEN** se ejecuta `npx @redocly/cli lint docs/api/openapi.yaml`
- **THEN** el comando termina sin errores

#### Scenario: El contrato declara la versión
- **WHEN** se lee `info.version` del OpenAPI
- **THEN** es una versión semántica, `1.0.0` en este change

### Requirement: Inventario de endpoints del MVP
El contrato SHALL declarar exactamente estos endpoints bajo `/api`: `POST auth/login`, `GET auth/me`; `GET public/prestaciones`, `GET public/disponibilidad`, `POST public/turnos`, `GET public/turnos/{token}`, `POST public/turnos/{token}/confirmar`, `POST public/turnos/{token}/cancelar`; CRUD de `prestaciones`, `horarios-atencion` y `bloqueos` (más `POST bloqueos/previsualizar`); `pacientes`; `turnos`, sus acciones y `GET disponibilidad`.

#### Scenario: Endpoints privados de turnos
- **WHEN** se listan las operaciones de `/api/turnos` en el contrato
- **THEN** existen `GET /api/turnos`, `GET /api/turnos/{id}`, `POST /api/turnos`, `PATCH /api/turnos/{id}/reprogramar`, `POST /api/turnos/{id}/confirmar`, `POST /api/turnos/{id}/cancelar` y `PATCH /api/turnos/{id}/estado`

#### Scenario: Sin endpoints de opcionales
- **WHEN** se buscan rutas de lista de espera, `.ics` o CSV en el contrato
- **THEN** no existen

### Requirement: Autenticación por ruta
Las rutas `POST /api/auth/login` y `/api/public/**` SHALL declararse públicas (`security: []`); todas las demás SHALL exigir JWT en `Authorization: Bearer` y declarar la respuesta 401 `NO_AUTENTICADO`.

#### Scenario: Ruta privada sin token
- **WHEN** se llama `GET /api/turnos` sin header `Authorization`
- **THEN** la respuesta es 401 con `codigo` `NO_AUTENTICADO`

#### Scenario: Ruta pública sin token
- **WHEN** se llama `GET /api/public/prestaciones` sin header `Authorization`
- **THEN** la respuesta es 200 con la lista de prestaciones activas

### Requirement: Login devuelve token y vencimiento
`POST /api/auth/login` SHALL recibir `email` y `password` y, con credenciales válidas, responder 200 con `token`, `tipo` (`Bearer`), `expiraEn` (instante ISO-8601 con offset) y `nombre`. Con credenciales inválidas SHALL responder 401 `CREDENCIALES_INVALIDAS` con un mensaje que no revela qué dato falló.

#### Scenario: Login exitoso
- **WHEN** el odontólogo envía email y contraseña correctos
- **THEN** recibe 200 con `token`, `tipo: "Bearer"`, `expiraEn` y `nombre`

#### Scenario: Login con contraseña incorrecta
- **WHEN** se envía un email existente con una contraseña incorrecta
- **THEN** la respuesta es 401 `CREDENCIALES_INVALIDAS` con el mismo mensaje que para un email inexistente

### Requirement: Convenciones de datos
Los instantes SHALL ser ISO-8601 con offset (zona `America/Argentina/Buenos_Aires`); las fechas "día", `YYYY-MM-DD`; las horas de atención, `HH:mm`; los ids, enteros. El DNI SHALL aceptarse con o sin puntos (7 u 8 dígitos) y devolverse solo con dígitos. Los nombres de campo SHALL estar en camelCase.

#### Scenario: Instante sin offset
- **WHEN** un request envía `inicio: "2026-12-01T10:00:00"` sin offset
- **THEN** la respuesta es 400 `VALIDACION` con detalle sobre `inicio`

#### Scenario: DNI con puntos
- **WHEN** se crea un paciente con `dni: "99.999.999"`
- **THEN** el paciente se devuelve con `dni: "99999999"`

### Requirement: Disponibilidad por rango de fechas agrupada por día
`GET /api/public/disponibilidad` y `GET /api/disponibilidad` SHALL recibir `prestacionId`, `desde` y `hasta` (`YYYY-MM-DD`, inclusivos, `hasta >= desde`, rango máximo de 31 días) y responder 200 con la prestación y la lista `dias`, un elemento por fecha del rango con su lista `horarios` de instantes de inicio, vacía si no hay lugar.

#### Scenario: Consulta de un solo día
- **WHEN** el front consulta con `desde=2026-12-01&hasta=2026-12-01`
- **THEN** recibe `dias` con un único elemento `fecha: "2026-12-01"` y sus horarios

#### Scenario: Día sin horarios
- **WHEN** el rango incluye un domingo sin atención
- **THEN** ese día aparece con `horarios: []`, sin error

#### Scenario: Rango inválido
- **WHEN** `hasta` es anterior a `desde` o el rango supera 31 días
- **THEN** la respuesta es 400 `VALIDACION`

#### Scenario: Prestación inactiva
- **WHEN** se consulta la disponibilidad pública de una prestación inactiva
- **THEN** la respuesta es 422 `PRESTACION_INACTIVA`

### Requirement: Reserva pública
`POST /api/public/turnos` SHALL recibir `nombre`, `apellido`, `dni`, `email`, `telefono`, `prestacionId`, `inicio` y `consentimiento` (booleano obligatorio), y responder 201 con el resumen público del turno, cuyo `estado` es `PENDIENTE` si faltan 24 h o más y `CONFIRMADO` si faltan menos.

#### Scenario: Reserva con más de 24 h
- **WHEN** un paciente reserva un horario libre para dentro de 3 días con consentimiento
- **THEN** recibe 201 con `estado: "PENDIENTE"`

#### Scenario: Reserva con menos de 24 h
- **WHEN** un paciente reserva un horario libre para dentro de 5 h con consentimiento
- **THEN** recibe 201 con `estado: "CONFIRMADO"`

#### Scenario: Horario fuera de la ventana pública
- **WHEN** el `inicio` está a menos de 2 h, a más de 60 días, fuera de la grilla o sobre un bloqueo
- **THEN** la respuesta es 422 `HORARIO_NO_DISPONIBLE`

### Requirement: Resumen público del turno sin datos personales
El resumen público (respuesta de `GET`, `confirmar` y `cancelar` por token y de la reserva) SHALL contener solo prestación (nombre y duración), `inicio`, `fin`, `estado`, nombre de pila del paciente, `puedeConfirmar`, `puedeCancelar` y `contactoConsultorio`. SHALL no incluir DNI, apellido, email, teléfono, ids internos ni el token.

#### Scenario: Consulta por token
- **WHEN** se llama `GET /api/public/turnos/{token}` con un token válido
- **THEN** la respuesta 200 no contiene `dni`, `email`, `telefono` ni `token`

#### Scenario: GET por token no modifica el turno
- **WHEN** se llama `GET /api/public/turnos/{token}` varias veces
- **THEN** el estado del turno no cambia

### Requirement: El token del turno no se expone por la API
Ninguna respuesta de la API, pública o privada, SHALL incluir el token opaco del turno; el token solo viaja en los links de los emails.

#### Scenario: Detalle privado del turno
- **WHEN** el odontólogo consulta `GET /api/turnos/{id}`
- **THEN** la respuesta no contiene el campo `token`

### Requirement: Confirmar y cancelar por token
`POST .../confirmar` y `POST .../cancelar` por token SHALL evaluar en orden: token inexistente (404 `TOKEN_INVALIDO`); estado ya alcanzado (200, idempotente); transición no permitida (422 `TRANSICION_INVALIDA`); plazo (422 `*_FUERA_DE_PLAZO`). Con éxito SHALL responder 200 con el resumen público.

#### Scenario: Confirmar un turno ya confirmado
- **WHEN** el paciente confirma por token un turno `CONFIRMADO`
- **THEN** la respuesta es 200 con `estado: "CONFIRMADO"`

#### Scenario: Cancelar un turno ya cancelado con menos de 24 h
- **WHEN** el paciente cancela por token un turno `CANCELADO` que empieza en 3 h
- **THEN** la respuesta es 200 con `estado: "CANCELADO"`, no 422

#### Scenario: Cancelar un turno ya confirmado
- **WHEN** el paciente cancela por token un turno `CONFIRMADO` (a cualquier distancia del inicio)
- **THEN** la respuesta es 422 `TRANSICION_INVALIDA` con un `mensaje` del estilo "Tu turno ya está confirmado. Para cancelarlo comunicate con el consultorio." y el turno sigue `CONFIRMADO`

#### Scenario: Confirmar un turno cancelado
- **WHEN** el paciente confirma por token un turno `CANCELADO`
- **THEN** la respuesta es 422 `TRANSICION_INVALIDA`

### Requirement: Confirmar es definitivo para el paciente
El paciente por token SHALL cancelar solo un turno `PENDIENTE` y con 24 h o más de anticipación; sobre un `CONFIRMADO` SHALL recibir 422 `TRANSICION_INVALIDA`. Confirmar por token SHALL aplicar solo a `PENDIENTE` con 48 h o menos de anticipación (`CONFIRMACION_FUERA_DE_PLAZO` si faltan más).

#### Scenario: Cancelar un turno pendiente con más de 24 h
- **WHEN** el paciente cancela por token un turno `PENDIENTE` que empieza en 3 días
- **THEN** la respuesta es 200 con `estado: "CANCELADO"` y `canceladoPor` interno `PACIENTE`

#### Scenario: Resumen de un turno confirmado
- **WHEN** se consulta por token un turno `CONFIRMADO`
- **THEN** `puedeCancelar` es `false` y `puedeConfirmar` es `false`

### Requirement: Acciones del odontólogo sobre turnos
El contrato SHALL definir: alta manual (`pacienteId` o `paciente` nuevo, `prestacionId`, `inicio`, `emailContacto`, `telefonoContacto`) con 201; reprogramar (`inicio`) con 200 y estado recalculado; confirmar sin restricción de 48 h, idempotente sobre `CONFIRMADO`; cancelar en cualquier momento con `canceladoPor: ODONTOLOGO`; marcar `ASISTIO`/`AUSENTE` solo desde el inicio.

#### Scenario: Reprogramar a menos de 24 h
- **WHEN** el odontólogo reprograma un turno `PENDIENTE` a un horario libre dentro de 6 h
- **THEN** recibe 200 con `estado: "CONFIRMADO"`

#### Scenario: Cancelar un turno terminal
- **WHEN** el odontólogo cancela un turno `ASISTIO` o `CANCELADO`
- **THEN** la respuesta es 422 `TRANSICION_INVALIDA`

#### Scenario: Alta manual con paciente y pacienteId a la vez
- **WHEN** el body trae `pacienteId` y `paciente` juntos, o ninguno
- **THEN** la respuesta es 400 `VALIDACION`

### Requirement: Agenda con contacto e indicador de pendiente próximo
`GET /api/turnos` SHALL recibir `desde` y `hasta` (`YYYY-MM-DD`, máximo 31 días) y `estado` opcional, y devolver cada turno con paciente, prestación, `inicio`, `fin`, `estado`, `canceladoPor`, `emailContacto`, `telefonoContacto` y `pendienteMenos24h` (true si está `PENDIENTE` y empieza en menos de 24 h).

#### Scenario: Turno pendiente próximo
- **WHEN** la agenda incluye un turno `PENDIENTE` que empieza en 10 h
- **THEN** ese turno viene con `pendienteMenos24h: true` y su `telefonoContacto`

### Requirement: Pacientes con último contacto
Los endpoints de pacientes SHALL devolver `id`, `nombre`, `apellido`, `dni` y `ultimoContacto` (`email` y `telefono` del último turno del paciente, o `null` si no tiene turnos). El paciente SHALL no tener email ni teléfono propios en ningún request.

#### Scenario: Paciente sin turnos
- **WHEN** se consulta un paciente recién creado
- **THEN** `ultimoContacto` es `null`

### Requirement: Bloqueos con vista previa
`POST /api/bloqueos/previsualizar` SHALL devolver los turnos activos del rango sin modificar nada. `POST /api/bloqueos` SHALL recibir `inicio`, `fin`, `motivo` opcional y `cancelarTurnosAfectados` (por defecto `false`) y responder 201 con el bloqueo y la lista `turnosCancelados`.

#### Scenario: Bloqueo confirmando cancelación
- **WHEN** el odontólogo crea un bloqueo con `cancelarTurnosAfectados: true` sobre un turno activo
- **THEN** recibe 201 con el bloqueo y `turnosCancelados` con ese turno en estado `CANCELADO` y `canceladoPor: "ODONTOLOGO"`

### Requirement: Tipos TypeScript del contrato
`docs/api/tipos.ts` SHALL exportar un tipo por cada schema del OpenAPI, con los mismos nombres de campo y nullabilidad, uniones literales para los enums y sin `any`, y SHALL compilar con `tsc --strict`.

#### Scenario: Tipos compilan en modo estricto
- **WHEN** se ejecuta `npx tsc --noEmit --strict docs/api/tipos.ts`
- **THEN** el comando termina sin errores

### Requirement: Ejemplos JSON por endpoint
`docs/api/ejemplos/` SHALL contener, por cada endpoint, al menos un request (si tiene body) y un response de éxito, y un response por cada código de negocio específico del endpoint, todos válidos contra su schema y con datos ficticios. Los errores genéricos (`VALIDACION`, `SOLICITUD_MAL_FORMADA`, `NO_AUTENTICADO`, `RECURSO_NO_ENCONTRADO`) SHALL tener un ejemplo compartido.

#### Scenario: Ejemplos con datos ficticios
- **WHEN** se revisan los archivos de `docs/api/ejemplos/`
- **THEN** usan datos ficticios (por ejemplo `odontologo@ejemplo.com`, DNI `99999999`) y ningún dato real de personas

#### Scenario: Cobertura de ejemplos
- **WHEN** se cruza la matriz de códigos por endpoint con los archivos de ejemplos
- **THEN** cada combinación endpoint + código de negocio específico tiene su archivo y cada error genérico tiene su ejemplo compartido
