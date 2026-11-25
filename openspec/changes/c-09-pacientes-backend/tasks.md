# Tasks

> Strict TDD: en cada tarea primero el test que falla (RED), después el código mínimo (GREEN), triangular con un segundo caso y refactorizar con los tests en verde.

## 1. Base

- [x] 1.1 Safety net: `./mvnw verify` en `backend/` y registrar la cantidad de tests verdes de base (si algo falla, reportarlo como falla preexistente y no seguir)
- [x] 1.2 Reuso de `turno/NormalizacionDni` (sin modificarla): confirmar que `NormalizacionDniTest` cubre con puntos, sin puntos, 7 dígitos y espacios de borde; no crear otra clase de normalización
- [x] 1.3 Records `PacienteResponse`, `ContactoResponse` y `PacienteMapper`, reusando el `PacienteRequest` existente de C-07 (sin modificarlo): test unitario de validación del request (DNI con puntos válido, `99-999-997` y letras inválidos, nombre en blanco) y del mapper (`ultimoContacto` null serializa la clave como `null`)

## 2. Consultas del repositorio

- [x] 2.1 Búsqueda en `PacienteRepository` por prefijo de DNI y por prefijo de apellido sin mayúsculas, con escape de `%`/`_`/`\` y orden apellido, nombre, id: tests en `PacienteRepositoryIT` (Testcontainers) primero — prefijo de DNI, prefijo de apellido en minúsculas, sin coincidencias, `q=%` no trae todo, orden
- [x] 2.2 Consulta nativa `DISTINCT ON (paciente_id)` del contacto del último turno creado para una lista de ids: tests en `PacienteRepositoryIT` primero — tres turnos con emails distintos (gana el de `creado_en` mayor aunque otro tenga `inicio` posterior), último turno `CANCELADO` cuenta, paciente sin turnos ausente del resultado, dos pacientes no se mezclan

## 3. Service y endpoints de lectura

- [x] 3.1 `PacienteService.buscar(q)`: decisión DNI vs apellido (q sin puntos solo dígitos → DNI), `q` nulo o en blanco → todos, armado de `ultimoContacto` con 2 consultas (la segunda se omite si no hay pacientes); test unitario de la decisión y del armado primero
- [x] 3.2 `GET /api/pacientes?q` en `PacienteController`: `PacienteApiIT` (`@IntegracionPostgres` + MockMvc) primero — 200 con claves exactas de `pacientes-listar.200.json`, búsqueda por DNI con puntos y por apellido, lista vacía, 401 sin token
- [x] 3.3 `GET /api/pacientes/{id}`: tests en `PacienteApiIT` primero — 200 con claves de `pacientes-obtener.200.json` y `ultimoContacto` del último turno, `ultimoContacto: null` sin turnos, 404 `RECURSO_NO_ENCONTRADO`, 400 `SOLICITUD_MAL_FORMADA` con id `abc`

## 4. Alta y edición

- [x] 4.1 Traducción de la violación de `paciente_dni_uk` (recorriendo la cadena de causas) a `ApiException` 422 `DNI_DUPLICADO`; test unitario primero (violación de `paciente_dni_uk` → true, otra constraint → false)
- [x] 4.2 `POST /api/pacientes`: tests en `PacienteApiIT` primero — 201 con el request de ejemplo y `dni` normalizado, 400 `VALIDACION` con detalle `dni`/`nombre`, 400 `SOLICITUD_MAL_FORMADA` con JSON ilegible, body con `email`/`telefono` → 201 sin esas claves, 422 `DNI_DUPLICADO` con DNI existente escrito con puntos (mensaje del catálogo), 401 sin token
- [x] 4.3 Carrera de altas: test en `PacienteApiIT` con dos hilos creando el mismo DNI → uno 201 y otro 422, nunca 500; verificar que pasa con el `saveAndFlush` + catch del service
- [x] 4.4 `PUT /api/pacientes/{id}`: tests en `PacienteApiIT` primero — 200 con `pacientes-actualizar.request.json`, conservar el propio DNI no da 422, DNI de otro paciente → 422 y datos intactos, 404 inexistente, los turnos del paciente conservan su contacto
- [x] 4.5 Sin borrado: test en `PacienteApiIT` de que `DELETE /api/pacientes/{id}` responde 404 `RECURSO_NO_ENCONTRADO` y el paciente sigue existiendo

## 5. Cierre

- [x] 5.1 Refactor final con tests verdes, `./mvnw verify` en `backend/` completo (nuevos + baseline) y `openspec validate c-09-pacientes-backend --strict`
- [ ] 5.2 Plantear al equipo en el PR (sin editar el contrato): criterio de "último turno" elegido (creado más reciente), búsqueda sensible a acentos aceptada, campos extra ignorados pese a `additionalProperties: false`, y sugerirle a Salinas (C-07) que `resolverPacienteManual` reutilice la traducción de `DNI_DUPLICADO` para cubrir la carrera de altas

## Workflow follow-up

- Abrir PR de `casado/c-09-pacientes-backend` a `main` y, tras la revisión, `/opsx:archive c-09-pacientes-backend` y marcar `[x]` C-09 en `CHANGES.md`.
