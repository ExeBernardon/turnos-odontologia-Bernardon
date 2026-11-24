# modelo-datos Specification

## Purpose

Define el modelo persistente del dominio que comparten todos los changes del backend: tablas, tipos, unicidades, estados del turno, la garantía en base de que no haya dos turnos activos superpuestos y las consultas por rango que usan varios dominios.

## Requirements

### Requirement: Tablas del dominio
Al arrancar contra una base vacía, el backend SHALL dejar creadas las tablas `odontologo`, `paciente`, `prestacion`, `horario_atencion`, `bloqueo` y `turno` con las columnas de KB 04. Los "horarios libres" no SHALL tener tabla: se calculan.

#### Scenario: Base nueva
- **WHEN** el backend arranca contra una base PostgreSQL vacía
- **THEN** existen las seis tablas del dominio y ninguna tabla de horarios libres

#### Scenario: Columnas del turno
- **WHEN** se inspecciona la tabla `turno`
- **THEN** tiene `paciente_id`, `prestacion_id`, `inicio`, `fin`, `estado`, `token`, `email_contacto`, `telefono_contacto`, `consentimiento_at`, `recordatorio_enviado`, `alerta_pendiente_enviada`, `cancelado_por`, `creado_en` y `actualizado_en`

### Requirement: Instantes con zona
Toda columna que represente un instante (`inicio`, `fin`, `consentimiento_at`, `creado_en`, `actualizado_en`) SHALL ser `timestamptz`. Los horarios de atención SHALL guardarse como hora local sin zona (`time`) y día ISO de la semana (1 = lunes a 7 = domingo).

#### Scenario: Instante persistido y leído
- **WHEN** se guarda un turno con inicio `2026-11-25T10:00:00-03:00` y se lo vuelve a leer
- **THEN** el inicio leído es el mismo instante (`2026-11-25T13:00:00Z`)

#### Scenario: Tipos de columnas
- **WHEN** se inspeccionan los tipos de `turno.inicio`, `bloqueo.inicio` y `horario_atencion.hora_inicio`
- **THEN** los dos primeros son `timestamp with time zone` y el tercero es `time without time zone`

### Requirement: Unicidades del dominio
La base SHALL rechazar dos odontólogos con el mismo email, dos pacientes con el mismo DNI y dos turnos con el mismo token.

#### Scenario: DNI repetido
- **WHEN** se insertan dos pacientes con DNI `99999999`
- **THEN** la segunda inserción falla por violación de unicidad

#### Scenario: Token repetido
- **WHEN** se insertan dos turnos con el mismo token
- **THEN** la segunda inserción falla por violación de unicidad

### Requirement: Estados del turno y origen de la cancelación
El estado del turno SHALL persistirse como texto con uno de `PENDIENTE`, `CONFIRMADO`, `CANCELADO`, `ASISTIO` o `AUSENTE`, y `cancelado_por` como texto con `PACIENTE` u `ODONTOLOGO`, o nulo. Los activos son `PENDIENTE` y `CONFIRMADO`.

#### Scenario: Estado legible en la base
- **WHEN** se guarda un turno `CONFIRMADO` y se consulta la columna `estado` por SQL
- **THEN** el valor es el texto `CONFIRMADO`

#### Scenario: Cancelado con origen
- **WHEN** se guarda un turno `CANCELADO` con `cancelado_por` `PACIENTE`
- **THEN** al leerlo conserva el estado y el origen de la cancelación

### Requirement: Valores por defecto del turno
Un turno nuevo SHALL tener `recordatorio_enviado` y `alerta_pendiente_enviada` en falso si no se indica otro valor, y `consentimiento_at` SHALL admitir nulo (turnos cargados por el odontólogo).

#### Scenario: Turno sin flags indicados
- **WHEN** se guarda un turno sin indicar los flags de aviso ni consentimiento
- **THEN** ambos flags quedan en falso y `consentimiento_at` queda nulo

### Requirement: Sin turnos activos superpuestos
La base SHALL rechazar un turno activo cuyo rango semiabierto `[inicio, fin)` se superponga con el de otro turno activo, mediante la restricción de exclusión `turno_sin_solapamiento`, con SQLSTATE `23P01`.

#### Scenario: Dos activos que se pisan
- **WHEN** existe un turno `PENDIENTE` de 09:00 a 09:30 y se inserta otro `CONFIRMADO` de 09:15 a 09:45 del mismo día
- **THEN** la inserción falla con SQLSTATE `23P01` por `turno_sin_solapamiento`

#### Scenario: Turnos adyacentes
- **WHEN** existe un turno activo de 09:00 a 09:30 y se inserta otro activo de 09:30 a 10:00
- **THEN** la inserción se acepta

#### Scenario: Un cancelado no ocupa el horario
- **WHEN** existe un turno `CANCELADO` de 09:00 a 09:30 y se inserta un turno `PENDIENTE` de 09:00 a 09:30
- **THEN** la inserción se acepta

#### Scenario: Reactivar sobre un horario ocupado
- **WHEN** hay un turno `CANCELADO` y otro `PENDIENTE` en el mismo rango y se actualiza el cancelado a `PENDIENTE`
- **THEN** la actualización falla con SQLSTATE `23P01`

### Requirement: Consultas por rango compartidas
El backend SHALL poder obtener los turnos activos y los bloqueos cuyo rango `[inicio, fin)` interseca un rango dado, y los horarios de atención de un día de la semana. Los que solo tocan un borde del rango no SHALL incluirse.

#### Scenario: Turnos activos en un rango
- **WHEN** se piden los turnos activos entre 09:00 y 12:00 y existen uno activo de 10:00 a 10:30, uno cancelado de 11:00 a 11:30 y uno activo de 12:00 a 12:30
- **THEN** se devuelve solo el de 10:00 a 10:30

#### Scenario: Bloqueo que cruza el borde
- **WHEN** se piden los bloqueos entre 09:00 y 12:00 y existe uno de 08:00 a 09:30 y otro de 07:00 a 09:00
- **THEN** se devuelve solo el de 08:00 a 09:30

#### Scenario: Horarios de un día
- **WHEN** se piden los horarios del lunes (día 1) con el seed cargado
- **THEN** se devuelven los rangos 09:00-13:00 y 15:00-19:00, ordenados por hora de inicio

### Requirement: Búsquedas por clave natural
El backend SHALL poder buscar un odontólogo por email, un paciente por DNI y un turno por token, devolviendo vacío cuando no existe.

#### Scenario: Token inexistente
- **WHEN** se busca un turno por un token que no existe
- **THEN** el resultado es vacío, sin error
