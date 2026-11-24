# Spec Delta — plataforma-backend

## MODIFIED Requirements

### Requirement: Script SQL complementario después de Hibernate
En cada arranque, después de que Hibernate cree o actualice las tablas, el backend SHALL ejecutar el script `schema-extra.sql` usando `^^^` como separador de sentencias, y el script SHALL ser idempotente. El script SHALL instalar `btree_gist` y crear la restricción `turno_sin_solapamiento` sobre `turno` solo si todavía no existe.

#### Scenario: Extensión disponible tras el arranque
- **WHEN** el backend arranca contra una base nueva
- **THEN** la extensión `btree_gist` queda instalada en la base

#### Scenario: Restricción creada en el primer arranque
- **WHEN** el backend arranca contra una base nueva
- **THEN** la tabla `turno` tiene una única restricción de exclusión llamada `turno_sin_solapamiento`

#### Scenario: Reinicio
- **WHEN** el backend se reinicia contra la misma base
- **THEN** el script corre de nuevo sin errores y sin duplicar nada

#### Scenario: Reinicio con la restricción ya creada
- **WHEN** el script se ejecuta otra vez sobre una base que ya tiene `turno_sin_solapamiento`
- **THEN** termina sin error y la tabla `turno` sigue teniendo exactamente una restricción con ese nombre

## ADDED Requirements

### Requirement: Horario ocupado por la restricción de la base
Cuando una operación falla porque la base rechaza un turno por `turno_sin_solapamiento` (SQLSTATE `23P01`), el backend SHALL responder 409 con `codigo` `HORARIO_OCUPADO` y el formato uniforme, sin `detalles` ni información interna de la base. Otras violaciones de integridad no SHALL responderse como `HORARIO_OCUPADO`.

#### Scenario: Solapamiento detectado por la base
- **WHEN** un request termina con la violación de `turno_sin_solapamiento`
- **THEN** la respuesta es 409 con `codigo` `HORARIO_OCUPADO`, `mensaje` "Ese horario ya fue reservado. Elegí otro.", `path` y `timestamp`, sin `detalles` y sin el texto SQL ni el nombre de la restricción

#### Scenario: Otra violación de integridad
- **WHEN** un request termina con una violación de unicidad (SQLSTATE `23505`) que ningún dominio tradujo
- **THEN** la respuesta no es 409 `HORARIO_OCUPADO`, sino el error interno genérico 500 `ERROR_INTERNO`
