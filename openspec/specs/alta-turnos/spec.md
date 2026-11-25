# alta-turnos Specification

## Purpose

Define la creación de turnos del consultorio: reserva pública y carga manual, con validación de horario, identificación del paciente por DNI, estado inicial según anticipación, un token opaco de consulta y los avisos que se envían al crear.

## Requirements

### Requirement: Reserva pública con consentimiento

`POST /api/public/turnos` SHALL crear un turno con `nombre`, `apellido`, `dni`, `email`, `telefono`, `prestacionId`, `inicio` y `consentimiento`, responder 201 con el resumen público y registrar `consentimientoAt` con el instante de la reserva. Sin consentimiento SHALL responder 422 `CONSENTIMIENTO_REQUERIDO` sin crear el turno; campos ausentes o con formato inválido SHALL responder 400 `VALIDACION` con `detalles`.

#### Scenario: Reserva pública válida

- **WHEN** un paciente envía todos los campos requeridos con `consentimiento: true` y un horario libre
- **THEN** recibe 201 con el resumen público y el turno queda persistido con `consentimientoAt` no nulo

#### Scenario: Reserva sin consentimiento

- **WHEN** la reserva pública llega con `consentimiento: false` o sin el campo
- **THEN** la respuesta es 422 `CONSENTIMIENTO_REQUERIDO`, no se crea el turno y no se envía ningún aviso

#### Scenario: Campos con formato inválido

- **WHEN** la reserva llega con `inicio` sin offset y `email` vacío
- **THEN** la respuesta es 400 `VALIDACION` con `detalles` para `inicio` y `email`

### Requirement: Carga manual autenticada

`POST /api/turnos` SHALL exigir JWT y crear un turno con `pacienteId` existente o con un paciente nuevo incluido en el body, más `prestacionId`, `inicio`, `emailContacto` y `telefonoContacto`. No SHALL exigir consentimiento ni el mínimo de anticipación pública: solo que el inicio no esté en el pasado. Enviar `pacienteId` y `paciente` juntos, o ninguno, SHALL responder 400 `VALIDACION`.

#### Scenario: Carga manual con paciente existente

- **WHEN** el odontólogo envía `pacienteId`, `prestacionId`, `inicio`, `emailContacto` y `telefonoContacto` válidos con JWT
- **THEN** recibe 201 con el turno creado, `consentimientoAt` nulo y el contacto guardado en el turno

#### Scenario: Carga manual con alta rápida de paciente

- **WHEN** el odontólogo envía un `paciente` nuevo con nombre, apellido y DNI en lugar de `pacienteId`
- **THEN** se crea el paciente y el turno con JWT y recibe 201

#### Scenario: Paciente ambiguo o ausente

- **WHEN** el body trae `pacienteId` y `paciente` a la vez, o ninguno de los dos
- **THEN** la respuesta es 400 `VALIDACION` y no se crea el turno

#### Scenario: Carga manual sin JWT

- **WHEN** se llama a `POST /api/turnos` sin header `Authorization`
- **THEN** la respuesta es 401 `NO_AUTENTICADO`

#### Scenario: Alta rápida con DNI ya registrado

- **WHEN** el odontólogo intenta crear un `paciente` nuevo con un DNI que ya existe en lugar de usar `pacienteId`
- **THEN** la respuesta es 422 `DNI_DUPLICADO` y no se crea ni el paciente ni el turno

### Requirement: Validación de prestación y horario al crear

Antes de insertar, el sistema SHALL resolver la prestación y validar el inicio con la misma regla reutilizable de disponibilidad, con contexto público para la reserva y de odontólogo para la carga manual. Prestación inexistente SHALL responder 404 `RECURSO_NO_ENCONTRADO`, inactiva 422 `PRESTACION_INACTIVA`, y cualquier horario no disponible 422 `HORARIO_NO_DISPONIBLE` con el motivo concreto.

#### Scenario: Prestación inexistente

- **WHEN** se crea un turno con un `prestacionId` que no existe
- **THEN** la respuesta es 404 `RECURSO_NO_ENCONTRADO`

#### Scenario: Prestación inactiva

- **WHEN** se reserva con una prestación desactivada
- **THEN** la respuesta es 422 `PRESTACION_INACTIVA` y no se crea el turno

#### Scenario: Horario ocupado, sobre bloqueo o fuera de la ventana pública

- **WHEN** la reserva pública propone un inicio sobre un turno activo, un bloqueo, fuera de atención o fuera de la ventana de 2 h a 60 días
- **THEN** la respuesta es 422 `HORARIO_NO_DISPONIBLE` con el motivo y no se crea el turno

#### Scenario: Carga manual sin mínimo de dos horas

- **WHEN** el odontólogo carga manualmente un turno libre dentro de la próxima hora
- **THEN** la validación pasa y se crea el turno siempre que el inicio no esté en el pasado

### Requirement: Estado inicial y fin del turno

El sistema SHALL calcular el estado inicial con el instante actual: `PENDIENTE` si faltan 24 h o más, `CONFIRMADO` si faltan menos. El umbral de 24 h SHALL tomarse de `APP_ALERTA_PENDIENTE_HORAS` (24 por defecto), el mismo que usa la alerta al odontólogo, sin cambiar código. El `fin` SHALL persistirse igual a `inicio` más la duración de la prestación. Los flags `recordatorioEnviado` y `alertaPendienteEnviada` SHALL quedar en `false` para `PENDIENTE` y en `true` para `CONFIRMADO` creado por debajo del umbral, según el aviso ya emitido.

#### Scenario: Reserva con 24 h o más de anticipación

- **WHEN** se crea un turno que empieza exactamente a las 24 h de ahora
- **THEN** el turno queda `PENDIENTE` con ambos flags en `false`

#### Scenario: Reserva con menos de 24 h de anticipación

- **WHEN** se crea un turno que empieza a las 23 h y 59 min de ahora
- **THEN** el turno queda `CONFIRMADO` con ambos flags en `true`

#### Scenario: Fin derivado de la duración

- **WHEN** se crea un turno de una prestación de 45 min que empieza a las 10:00
- **THEN** el turno se persiste con `inicio` 10:00 y `fin` 10:45

#### Scenario: Umbral de confirmación configurado

- **WHEN** `APP_ALERTA_PENDIENTE_HORAS` vale 12 y se crea un turno que empieza a las 20 h de ahora
- **THEN** el turno queda `PENDIENTE` con ambos flags en `false`

### Requirement: Respuesta de la carga manual

`POST /api/turnos` SHALL responder 201 con el turno de agenda: `id`, `inicio`, `fin`, `estado`, `canceladoPor` (nulo al crear), paciente y prestación, `emailContacto`, `telefonoContacto`, `consentimientoAt` (nulo), `pendienteMenos24h` y `creadoEn`. La respuesta SHALL NOT incluir el token.

#### Scenario: Turno creado desde la agenda

- **WHEN** el odontólogo crea un turno con JWT
- **THEN** recibe 201 con los campos del turno de agenda, `consentimientoAt: null` y sin `token`

#### Scenario: Indicador de pendiente próximo al crear

- **WHEN** el odontólogo crea un turno que nace `PENDIENTE` con 24 h o más de anticipación
- **THEN** `pendienteMenos24h` es `false` en la respuesta de creación

### Requirement: Paciente identificado por DNI normalizado

La reserva pública SHALL normalizar el DNI a solo dígitos, reutilizar el paciente existente **sin modificar nombre, apellido ni DNI**, o crear uno nuevo si no existe. El email y el teléfono SHALL guardarse solo en el turno, nunca en el paciente.

#### Scenario: Paciente existente con DNI con puntos

- **WHEN** un paciente con DNI `99999999` reserva con `dni: "99.999.999"` y otro nombre
- **THEN** el turno usa el paciente existente, sus datos no cambian y el DNI se guarda como `99999999`

#### Scenario: Paciente nuevo

- **WHEN** un paciente con un DNI aún no registrado reserva por primera vez
- **THEN** se crea el paciente con el DNI normalizado y el turno queda asociado a él

### Requirement: Token opaco no expuesto por la API

Cada turno creado SHALL tener un token opaco de 32 bytes aleatorios criptográficamente seguros, codificado en Base64 URL-safe y único. Ninguna respuesta de los endpoints de alta o consulta SHALL incluir el token.

#### Scenario: Token del turno

- **WHEN** se crea un turno
- **THEN** el token persistido tiene 32 bytes de entropía codificados en Base64 URL-safe y no aparece en la respuesta

#### Scenario: Tokens distintos entre turnos

- **WHEN** se crean dos turnos distintos
- **THEN** sus tokens son diferentes y ninguno es predecible a partir del id del turno

### Requirement: Resumen público por token

`GET /api/public/turnos/{token}` SHALL devolver, sin modificar el turno, un resumen con la prestación (nombre y duración), `inicio`, `fin`, `estado`, `nombrePaciente` (solo el nombre de pila), `puedeConfirmar`, `puedeCancelar` y `contactoConsultorio`. El resumen SHALL NOT incluir DNI, apellido, email, teléfono, ids internos ni token. Un token inexistente SHALL responder 404 `TOKEN_INVALIDO`.

#### Scenario: Consulta válida por token

- **WHEN** se consulta un turno con un token válido
- **THEN** recibe 200 con el resumen público y sin DNI, email, teléfono ni token

#### Scenario: Consulta repetida sin efectos

- **WHEN** se consulta dos veces el mismo token
- **THEN** ambas respuestas son iguales y el estado del turno no cambia

#### Scenario: Token inexistente

- **WHEN** se consulta un token que no corresponde a ningún turno
- **THEN** la respuesta es 404 `TOKEN_INVALIDO` sin revelar datos del turno

### Requirement: Permisos del resumen público

El resumen público SHALL informar `puedeConfirmar` solo para turnos `PENDIENTE` con 48 h o menos de anticipación, y `puedeCancelar` solo para turnos `PENDIENTE` con 24 h o más. Un turno `CONFIRMADO` SHALL tener ambos indicadores en `false`.

#### Scenario: Turno pendiente lejano

- **WHEN** se consulta un turno `PENDIENTE` que empieza en 3 días
- **THEN** `puedeConfirmar` es `false` y `puedeCancelar` es `true`

#### Scenario: Turno pendiente próximo

- **WHEN** se consulta un turno `PENDIENTE` que empieza en 12 h
- **THEN** `puedeConfirmar` es `true` y `puedeCancelar` es `false`

#### Scenario: Turno confirmado

- **WHEN** se consulta un turno `CONFIRMADO`
- **THEN** `puedeConfirmar` y `puedeCancelar` son `false`

### Requirement: Conflicto de horario por inserción concurrente

La creación SHALL ejecutar la inserción dentro de la transacción del service de modo que la restricción de exclusión de la base sea la garantía final contra solapamientos. Si dos creaciones compiten por el mismo intervalo, una SHALL responder 201 y la otra 409 `HORARIO_OCUPADO`; la violación de la restricción SHALL NOT traducirse como error interno.

#### Scenario: Dos reservas simultáneas sobre el mismo horario

- **WHEN** dos hilos reservan el mismo inicio al mismo tiempo
- **THEN** una recibe 201 y la otra 409 `HORARIO_OCUPADO`, y solo existe un turno activo en ese horario

#### Scenario: Turno cancelado no bloquea

- **WHEN** el único turno que intersecta el inicio solicitado está `CANCELADO`
- **THEN** la creación del nuevo turno se concreta y recibe 201

### Requirement: Avisos de creación según anticipación

Después de persistir el turno, el sistema SHALL pedir un aviso por la anticipación: más de 48 h → confirmación de reserva con solo Cancelar; de 24 h a 48 h, ambos inclusive → recordatorio con Confirmar y Cancelar; menos de 24 h → aviso de turno confirmado sin botones. Los umbrales de 48 h y 24 h SHALL tomarse de `APP_RECORDATORIO_HORAS_ANTES` y `APP_ALERTA_PENDIENTE_HORAS`. El destinatario SHALL ser siempre `emailContacto` del turno. Un fallo de envío SHALL NOT revertir la reserva ni borrar el turno, y los flags SHALL reflejar solo los avisos que salieron.

#### Scenario: Reserva con más de 48 h

- **WHEN** se crea un turno `PENDIENTE` con más de 48 h de anticipación
- **THEN** se pide la confirmación de reserva con link de cancelación y el turno sigue `PENDIENTE`

#### Scenario: Reserva entre 24 y 48 h

- **WHEN** se crea un turno `PENDIENTE` entre 24 y 48 h antes del inicio
- **THEN** se pide el recordatorio en el momento con links de confirmación y cancelación, y `recordatorioEnviado` es `true` si el aviso salió

#### Scenario: Bordes exactos de 48 h y 24 h

- **WHEN** se crean turnos que empiezan exactamente a las 48 h y a las 24 h de ahora
- **THEN** ambos reciben el recordatorio en el momento, no la confirmación de reserva ni la alerta al odontólogo

#### Scenario: Reserva con menos de 24 h

- **WHEN** se crea un turno `CONFIRMADO` con menos de 24 h de anticipación
- **THEN** se pide el aviso sin botones al `emailContacto` del turno

#### Scenario: Fallo del envío de mail

- **WHEN** el mecanismo de avisos falla al crear un turno
- **THEN** la reserva permanece persistida, la respuesta sigue siendo 201 y el fallo no se propaga al paciente como error de la API

#### Scenario: Ventana del recordatorio configurada

- **WHEN** `APP_RECORDATORIO_HORAS_ANTES` vale 72 y se crea un turno que empieza a las 60 h de ahora
- **THEN** se pide el recordatorio en el momento y no la confirmación de reserva
