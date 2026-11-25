## Why

El odontólogo necesita buscar, dar de alta y corregir los datos de identificación de sus pacientes (US-014) y ver el contacto del último turno como referencia (DD-12). Hoy `/admin/pacientes` es un placeholder ("Pantalla en construcción") aunque el backend (C-09) ya expone buscar, obtener, crear y editar. Además C-20 (carga manual de turnos) necesita elegir un paciente existente, y conviene que esa búsqueda sea un componente único y no una copia.

## What Changes

- Pantalla **Pacientes** (`/admin/pacientes`): búsqueda por DNI o apellido (`GET /api/pacientes?q=`), con la lista completa al entrar; resultados con apellido, nombre y DNI.
- **Detalle** del paciente elegido (`GET /api/pacientes/{id}`) con `ultimoContacto` (email y teléfono) en solo lectura; si es `null`, el texto "Sin turnos registrados: todavía no hay datos de contacto".
- **Alta y edición** con nombre, apellido y DNI (`POST` / `PUT /api/pacientes{/id}`). Validación local antes de enviar (campos obligatorios, máximo 100 caracteres, DNI con el formato de `DniEntrada`). El 422 `DNI_DUPLICADO` se muestra junto al campo DNI; el 400 `VALIDACION` junto a cada campo; el 404 avisa y recarga. No hay borrado (el contrato no lo ofrece).
- **Componente reutilizable de búsqueda de paciente** en `src/shared/`: formulario de búsqueda + lista de resultados que notifica la selección por callback y recibe la función de búsqueda inyectada. Lo usa esta pantalla y lo usará C-20.
- Estados de carga, vacío y error anunciados con `role="status"` / `role="alert"`; formularios accesibles con teclado y responsive. Sin cambios de contrato, backend ni dependencias.

## Capabilities

### New Capabilities
- `pacientes-front`: comportamiento de la pantalla de pacientes del odontólogo (búsqueda, detalle con último contacto, alta y edición, errores de la API).
- `buscador-pacientes-front`: componente de búsqueda y selección de paciente reutilizable por otras pantallas del área admin.

### Modified Capabilities
(ninguna)

## Impact

- `frontend/src/admin/pacientes.ts` (reemplaza el placeholder) y nuevos `pacientes.api.ts`, `pacientes.logica.ts`, `pacientes.css` en `frontend/src/admin/` (+ tests).
- Nuevo `frontend/src/shared/buscador-pacientes.ts` (+ CSS y test).
- Reusa sin modificar `src/api/cliente.ts`, `src/api/types.ts` (`Paciente`, `PacienteRequest`, `Contacto` ya existen), `shared/dom.ts` y los helpers de `admin/configuracion/` (`formulario.ts`, `errores.ts`, `parseo.ts`, utilidades de test); el mensaje de `DNI_DUPLICADO` se resuelve dentro de pacientes para no editar archivos de otro change.
- No se tocan router, sesión, `rutas.ts` (la ruta ya está registrada), `vite.config.ts`, `package.json` ni el contrato.
