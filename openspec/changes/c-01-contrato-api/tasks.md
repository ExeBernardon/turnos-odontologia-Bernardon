# Tasks — C-01 contrato-api

> Change de documentación: no hay código de aplicación. La verificación de cada tarea es un comando (`npx @redocly/cli lint docs/api/openapi.yaml`, `npx tsc --noEmit --strict docs/api/tipos.ts`) o una revisión explícita contra los specs. Cada grupo deja el OpenAPI en verde, su parte de `tipos.ts`, sus ejemplos y sus filas de la matriz de `errores.md`.

## 1. Base del contrato y errores

- [x] 1.1 Crear `docs/api/openapi.yaml` con `openapi: 3.1.0`, `info` (título, descripción, `version: 1.0.0`), `servers: [{url: /}]`, `security` global `bearerAuth` y `components.securitySchemes.bearerAuth` (D2, D11); verificar con `npx @redocly/cli lint docs/api/openapi.yaml` (sin errores; warnings de "no paths" aceptables solo en este paso)
- [x] 1.2 Agregar a `components`: schemas `Error`, `DetalleError`, `CodigoError` (enum con los 18 códigos de D4), `EstadoTurno`, `CanceladoPor`; responses reutilizables `Validacion` (400), `SolicitudMalFormada` (400), `NoAutenticado` (401), `NoEncontrado` (404); parámetros reutilizables `Id`, `Token`, `Desde`, `Hasta`, `PrestacionId`; verificar con el lint
- [x] 1.3 Crear `docs/api/errores.md` con formato uniforme, tabla de códigos HTTP (RN-GLO-04), catálogo de D4 con `mensaje` de referencia en español, orden de evaluación de confirmar/cancelar (D5) y la sección "Matriz por endpoint" vacía para completar en cada grupo; verificar que el catálogo coincide 1 a 1 con el enum `CodigoError`
- [x] 1.4 Crear `docs/api/tipos.ts` con `CodigoError`, `EstadoTurno`, `CanceladoPor`, `ApiError`, `DetalleError` y crear los ejemplos compartidos `docs/api/ejemplos/error.400-validacion.json`, `error.400-solicitud-mal-formada.json`, `error.401-no-autenticado.json`, `error.404-recurso-no-encontrado.json`; verificar con `npx tsc --noEmit --strict docs/api/tipos.ts`
- [x] 1.5 Spike de D10: referenciar `error.400-validacion.json` desde una response con `externalValue` y comprobar si `redocly lint` detecta un ejemplo inválido a propósito; dejar elegida la técnica (externalValue, `value` + `$ref`, o checklist manual) anotada en `errores.md` y revertir el ejemplo inválido

## 2. Autenticación y área pública

- [x] 2.1 Definir `POST /api/auth/login` (`security: []`, `LoginRequest`, `LoginResponse`, 200/400/401 `CREDENCIALES_INVALIDAS`) y `GET /api/auth/me` (`Usuario`, 200/401); verificar con el lint
- [x] 2.2 Definir `GET /api/public/prestaciones` (`PrestacionPublica[]`) y `GET /api/public/disponibilidad` con `prestacionId`, `desde`, `hasta` y schema `Disponibilidad` agrupado por día (D3; 200/400/404/422 `PRESTACION_INACTIVA`); verificar con el lint
- [x] 2.3 Definir `POST /api/public/turnos` (`ReservaPublicaRequest`, 201 `TurnoResumenPublico`; 400, 404, 409 `HORARIO_OCUPADO`, 422 `HORARIO_NO_DISPONIBLE`/`CONSENTIMIENTO_REQUERIDO`/`PRESTACION_INACTIVA`) con formatos de D8 (DNI con o sin puntos, teléfono, email, instante con offset); verificar con el lint
- [x] 2.4 Definir `GET /api/public/turnos/{token}`, `POST .../confirmar` y `POST .../cancelar` (`TurnoResumenPublico` sin DNI/apellido/email/teléfono/ids/token, flags `puedeConfirmar`/`puedeCancelar`, `contactoConsultorio`; 404 `TOKEN_INVALIDO`; 422 `TRANSICION_INVALIDA`, `CONFIRMACION_FUERA_DE_PLAZO`, `CANCELACION_FUERA_DE_PLAZO`) documentando en la descripción el orden de evaluación D5; verificar con el lint
- [x] 2.5 Agregar a `tipos.ts` los tipos de 2.1–2.4 y crear sus ejemplos en `docs/api/ejemplos/` (requests, éxitos y cada 422/409/404 específico, con datos ficticios de D10), referenciarlos desde el OpenAPI con la técnica de 1.5; verificar con `tsc --strict` y el lint
- [x] 2.6 Completar las filas de auth y públicos en la matriz de `errores.md`; verificar que cada status de la matriz está declarado en la operación del OpenAPI y viceversa

## 3. Configuración del consultorio

- [x] 3.1 Definir `/api/prestaciones` (GET lista, POST 201, PUT 200, DELETE 204 con 422 `PRESTACION_CON_TURNOS`, 404) con `Prestacion` y `PrestacionRequest` (`duracionMinutos` 1..480); verificar con el lint
- [x] 3.2 Definir `/api/horarios-atencion` (GET, POST 201, PUT 200, DELETE 204; 422 `RANGO_HORARIO_INVALIDO` y `HORARIOS_SOLAPADOS`; 404) con `HorarioAtencion` (`diaSemana` 1..7, horas `HH:mm`); verificar con el lint
- [x] 3.3 Definir `GET /api/bloqueos?desde&hasta`, `POST /api/bloqueos/previsualizar` (200 `BloqueoPrevisualizacion`, no modifica nada), `POST /api/bloqueos` (`BloqueoCrearRequest` con `cancelarTurnosAfectados` default `false`; 201 `BloqueoCreado`; 409 `ErrorBloqueoConflicto` con `turnosAfectados`; 422 `RANGO_HORARIO_INVALIDO`) y `DELETE /api/bloqueos/{id}` (204/404); verificar con el lint
- [x] 3.4 Agregar a `tipos.ts` los tipos de 3.1–3.3, crear sus ejemplos (incluido el 409 de bloqueo con dos turnos afectados) referenciados desde el OpenAPI y completar sus filas de la matriz; verificar con `tsc --strict`, el lint y el cruce matriz ↔ OpenAPI

## 4. Pacientes, turnos y disponibilidad privada

- [x] 4.1 Definir `/api/pacientes` (GET `?q` sin paginación, GET `{id}`, POST 201, PUT 200; 422 `DNI_DUPLICADO`; 404) con `Paciente` (`ultimoContacto: Contacto | null`) y `PacienteRequest` sin email ni teléfono; verificar con el lint
- [x] 4.2 Definir `GET /api/turnos?desde&hasta&estado` y `GET /api/turnos/{id}` con el schema único `Turno` (incluye `telefonoContacto`, `emailContacto`, `canceladoPor`, `consentimientoAt`, `pendienteMenos24h`; sin `token`); verificar con el lint
- [x] 4.3 Definir `POST /api/turnos` con `TurnoManualRequest` (`oneOf` `pacienteId` | `paciente`; 201; 400; 404; 409 `HORARIO_OCUPADO`; 422 `HORARIO_NO_DISPONIBLE`/`PRESTACION_INACTIVA`/`DNI_DUPLICADO`) y `PATCH /api/turnos/{id}/reprogramar` (`ReprogramarRequest`; 200 con estado recalculado; 404; 409; 422 `HORARIO_NO_DISPONIBLE`/`TRANSICION_INVALIDA`); verificar con el lint
- [x] 4.4 Definir `POST /api/turnos/{id}/confirmar` (idempotente sobre `CONFIRMADO`), `POST /api/turnos/{id}/cancelar` (422 sobre terminal o cancelado, D5) y `PATCH /api/turnos/{id}/estado` (`ASISTIO`/`AUSENTE`, 422 `TRANSICION_INVALIDA` antes del inicio), y `GET /api/disponibilidad` reutilizando el schema `Disponibilidad` (sin ventana 2 h / 60 d); verificar con el lint
- [x] 4.5 Agregar a `tipos.ts` los tipos de 4.1–4.4, crear sus ejemplos referenciados desde el OpenAPI (incluido un turno `PENDIENTE` con `pendienteMenos24h: true` y uno `CANCELADO` con `canceladoPor`) y completar sus filas de la matriz; verificar con `tsc --strict`, el lint y el cruce matriz ↔ OpenAPI

## 5. Alineación de la KB

- [x] 5.1 Actualizar `knowledge-base/07_flujos_principales.md` (diagrama de reserva: `GET /api/public/disponibilidad?prestacionId&desde&hasta` con `desde = hasta`) y agregar en `knowledge-base/08_arquitectura_propuesta.md` §Borrador una nota de que el contrato vigente es `docs/api/openapi.yaml` y la lista final de códigos está en `docs/api/errores.md`; verificar con `git diff --stat` que solo cambian esos dos archivos de la KB (no tocar `10_preguntas_abiertas.md`)

## 6. Verificación integral del contrato

- [x] 6.1 Correr `npx @redocly/cli lint docs/api/openapi.yaml` y `npx tsc --noEmit --strict docs/api/tipos.ts` sobre el contrato completo; ambos sin errores
- [x] 6.2 Verificación cruzada contra los specs: los 32 endpoints del inventario existen y no hay extras; ninguna respuesta pública ni privada contiene `token`; el resumen público no contiene `dni`/`apellido`/`email`/`telefono`/ids; solo se usan 200/201/204/400/401/404/409/422; enum `CodigoError` = catálogo de `errores.md` = union de `tipos.ts`; cada `codigo` específico tiene ejemplo. Dejar el resultado como checklist en la descripción del PR
- [x] 6.3 Revisar `docs/api/ejemplos/` buscando datos reales (emails no `@ejemplo.com`, DNIs distintos de los ficticios definidos, nombres reales); verificar con `grep -rE "@" docs/api/ejemplos | grep -v "@ejemplo.com"` sin resultados

## Workflow follow-up

- Revisión humana de al menos otro integrante (governance CRÍTICO) antes del merge.
- PR de `feat/c-01-contrato-api` a `main`; marcar `[x]` en C-01 de `CHANGES.md` en el mismo PR.
- Avisar al equipo que el gate 1 quedó cerrado; C-02 copia `docs/api/tipos.ts` a `frontend/src/api/types.ts`.
- Archivar con `/opsx:archive c-01-contrato-api` tras el merge.
