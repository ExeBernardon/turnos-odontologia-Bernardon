# Design

## Context

`Paciente` (nombre, apellido, `dni` varchar(15) con `paciente_dni_uk`) y `PacienteRepository.findByDni` existen desde C-03; `Turno` tiene `paciente` LAZY, `email_contacto`, `telefono_contacto` y `creado_en`, sin índice por `paciente_id`. `GlobalExceptionHandler` traduce cualquier `DataIntegrityViolationException` no reconocida a 500, así que cada dominio traduce sus propias violaciones (como `prestacion/` y `bloqueo/`). `/api/**` ya exige JWT en `SecurityConfig`. Jackson no tiene `FAIL_ON_UNKNOWN_PROPERTIES` activado (campos desconocidos se ignoran en todo el backend). El contrato (`Paciente`, `PacienteRequest`, `Dni`, `DniEntrada`, `NombrePersona`) ya está definido; ver specs/pacientes/spec.md para los requisitos.

## Goals / Non-Goals

**Goals:**
- Los 4 endpoints de `/api/pacientes` exactamente como el contrato, con `ultimoContacto` sin N+1.
- Reutilizar `turno/NormalizacionDni` y `paciente/PacienteRequest` de C-07 sin modificarlos.

**Non-Goals:**
- Front de pacientes (C-16) y la resolución de paciente por DNI en la reserva/carga manual (C-07).
- Búsqueda insensible a acentos, búsqueda por nombre o por palabra interna del apellido, paginación.
- Cambios en `Turno`, `TurnoRepository`, `TurnoService`, `NormalizacionDni`, `PacienteRequest`, clases compartidas de `common/` existentes, `SecurityConfig` o el contrato.
- Mover `NormalizacionDni` a `paciente/` (sería más prolijo, pero toca archivos de C-07; decisión de Cande Casado: reusarlo tal cual).

## Decisions

1. **Estructura en `paciente/`**: `PacienteController`, `PacienteService` (`@Transactional(readOnly = true)` a nivel clase, escrituras `@Transactional`), `PacienteMapper`, records `PacienteRequest`, `PacienteResponse(id, nombre, apellido, dni, ContactoResponse ultimoContacto)` y `ContactoResponse(email, telefono)`. Mismo estilo que `prestacion/`. `ultimoContacto` se serializa como `null` explícito (Jackson incluye nulls por defecto; un test lo fija).
2. **Reuso de `NormalizacionDni` y `PacienteRequest` (de C-07)**: el service normaliza con `ar.turnos.odontologia.turno.NormalizacionDni.normalizar` (ya testeada en `NormalizacionDniTest`: quita puntos y espacios, exige 7 u 8 dígitos) y recibe el `PacienteRequest` existente (`@NotBlank @Size(max=100)` nombre/apellido, `@NotBlank @Pattern` igual a `DniEntrada`), así el error de formato es 400 `VALIDACION` con detalle `dni`. El service guarda nombre y apellido con `trim()`. Para `q` alcanza con quitar puntos y espacios (no se exige 7-8 dígitos porque es un prefijo). Alternativa descartada: un `DniNormalizador` nuevo en `common/` (duplicaba la de C-07) o mover la clase (toca archivos de otro integrante).
3. **Semántica de `q`**: `trim`; vacío → todos. Si `q` sin puntos es `\d+` → `dni LIKE :prefijo%`. Si no → `lower(apellido) LIKE lower(:prefijo)%`. El prefijo se escapa (`\`, `%`, `_`) y la consulta usa `ESCAPE '\'`. El contrato dice "prefijo de DNI o de apellido", por eso prefijo y no "contiene". Alternativa descartada: `unaccent` (requiere extensión nueva en `schema-extra.sql`, archivo de otro change). `q` mayor a 100 caracteres no se valida: no puede coincidir con nada y el contrato no lista 400 para este endpoint.
4. **Orden y tamaño**: `ORDER BY apellido, nombre, id`. Sin paginación porque el contrato lo declara así; un consultorio de un profesional tiene volumen acotado.
5. **"Último turno" = el creado más recientemente** (`creado_en DESC, id DESC`), de cualquier estado. Es el contacto que el paciente o el odontólogo ingresaron por última vez, que es lo que sirve para precargar la carga manual (US-010). Alternativa descartada: mayor `inicio` (un turno reservado hoy para dentro de 2 meses y otro reservado ayer para mañana darían el contacto más viejo). Confirmado por Cande Casado (dueña del change).
6. **`ultimoContacto` sin N+1**: una consulta nativa en `PacienteRepository` con `SELECT DISTINCT ON (paciente_id) paciente_id, email_contacto, telefono_contacto FROM turno WHERE paciente_id IN (:ids) ORDER BY paciente_id, creado_en DESC, id DESC`, proyectada a una interfaz/record. El listado hace 2 consultas (pacientes filtrados + contactos de esos ids) y arma un `Map<Long, ContactoResponse>`; el detalle usa la misma con un solo id. Con la lista de pacientes vacía no se ejecuta la segunda. Alternativa descartada: subconsulta correlacionada JPQL con `max(creadoEn)` (no desempata por id) o cargar los turnos por paciente (N+1).
7. **DNI duplicado → 422**: chequeo previo (`existsByDni` en alta; `existsByDniAndIdNot` en edición) para el caso normal, y además `saveAndFlush` dentro del service con `catch (DataIntegrityViolationException)` cuyo mensaje/causa nombre `paciente_dni_uk` → `ApiException(422, DNI_DUPLICADO)`. El catch cubre la carrera entre dos altas; cualquier otra violación se relanza (sigue siendo 500). La detección del nombre de constraint va en un helper pequeño de `paciente/` (o `common/` si C-07 lo necesita) con test unitario, a la manera de `ViolacionDeSolapamiento`.
8. **404 y borrado**: `findById` vacío → `ApiException(404, RECURSO_NO_ENCONTRADO)`. No se mapea `DELETE`; el handler global ya responde 404 a métodos no soportados.
9. **Tests por capa**: unitarios para el escape del prefijo y la decisión DNI/apellido; `PacienteRepositoryIT` (Testcontainers PG16) para búsqueda, orden y `DISTINCT ON` con varios turnos; `PacienteApiIT` (`@IntegracionPostgres` + MockMvc, como `PrestacionApiIT`) para status, códigos, claves del contrato contra `docs/api/ejemplos/pacientes-*.json`, 401 sin token y la carrera de altas. Datos ficticios (`99999999`, `@ejemplo.com`).

## Risks / Trade-offs

- [Sin índice por `turno.paciente_id`] → consulta secuencial sobre `turno`; aceptable con el volumen del MVP. Si hiciera falta, un índice `(paciente_id, creado_en)` se agrega en un change coordinado con el dueño de `Turno`.
- [Búsqueda sensible a acentos: `q=perez` no encuentra `Pérez`] → aceptado para el MVP (decisión de Cande Casado); mejora futura con `unaccent`.
- [`paciente/` depende de `turno/NormalizacionDni`] → dependencia entre paquetes algo invertida; aceptada para no tocar archivos de C-07. Si se mueve más adelante, es un refactor coordinado con su dueña.
- [Campos extra en el body se ignoran, aunque el contrato declara `additionalProperties: false`] → es el comportamiento actual de todo el backend; cambiarlo es global y fuera de este change.
- [El mensaje de la `DataIntegrityViolationException` depende del driver] → se detecta por nombre de constraint recorriendo la cadena de causas y un IT lo cubre.

## Open Questions

Ninguna. Resueltas por Cande Casado: "último turno" = creado más recientemente; búsqueda sensible a acentos aceptada para el MVP; se reusan `NormalizacionDni` y `PacienteRequest` de C-07.
