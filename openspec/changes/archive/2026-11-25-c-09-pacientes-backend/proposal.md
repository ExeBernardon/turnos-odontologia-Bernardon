# Proposal

## Why

El odontólogo necesita buscar, dar de alta y corregir los datos de identificación de sus pacientes (US-014). Hoy existen la entidad `Paciente` y `PacienteRepository.findByDni` (C-03), y C-07 ya resuelve pacientes por DNI en la reserva y la carga manual (con `turno/NormalizacionDni` y `paciente/PacienteRequest`), pero no hay ninguna API para gestionarlos; la pantalla de pacientes (C-16) y la precarga del contacto en la carga manual (US-010) dependen de ella.

## What Changes

- `GET /api/pacientes?q`: lista pacientes sin paginación; `q` (opcional) busca por prefijo de DNI (si, sin puntos, son solo dígitos) o por prefijo de apellido sin distinguir mayúsculas. Cada ítem incluye `ultimoContacto` (email y teléfono del último turno del paciente, o `null`).
- `GET /api/pacientes/{id}`: detalle con `ultimoContacto`; 404 si no existe.
- `POST /api/pacientes`: alta con nombre, apellido y DNI (aceptado con o sin puntos, guardado y devuelto solo con dígitos); 422 `DNI_DUPLICADO` si el DNI ya existe.
- `PUT /api/pacientes/{id}`: reemplazo de nombre, apellido y DNI (única vía para modificar al paciente); 404 si no existe, 422 `DNI_DUPLICADO` si el DNI pertenece a otro paciente.
- Sin endpoint de borrado. El paciente no tiene email ni teléfono propios.
- Reutiliza lo que ya dejó C-07: `turno/NormalizacionDni` para normalizar el DNI y `paciente/PacienteRequest` como body de alta y edición. No se crean duplicados ni se modifican esos archivos.
- Sin cambios de contrato: `docs/api/openapi.yaml` ya define rutas, esquemas, ejemplos y errores.

## Capabilities

### New Capabilities
- `pacientes`: búsqueda, detalle, alta y edición de pacientes por el odontólogo, con DNI único normalizado y el contacto de referencia derivado del último turno.

### Modified Capabilities
<!-- Ninguna: modelo-datos (unicidad de DNI, búsqueda por DNI), api-errores (DNI_DUPLICADO, matriz por endpoint) y api-contrato ya cubren lo que este change usa sin cambiar sus requisitos. -->

## Impact

- Código nuevo en `backend/src/main/java/ar/turnos/odontologia/paciente/` (controller, service, DTOs, mapper) y una consulta nueva de contacto del último turno; sin archivos nuevos en `common/`.
- Lee `turno` (`paciente_id`, `email_contacto`, `telefono_contacto`, `creado_en`) sin modificar `Turno` ni `TurnoRepository`.
- No modifica archivos de C-07 (`NormalizacionDni`, `PacienteRequest`, `TurnoService`).
- No toca `SecurityConfig` (`/api/**` ya exige JWT), `GlobalExceptionHandler`, `application.yml`, `pom.xml` ni el contrato.
- Governance BAJO: implementación autónoma con tests verdes.
