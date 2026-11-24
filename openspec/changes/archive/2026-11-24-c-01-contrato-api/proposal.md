# Proposal — C-01 contrato-api

## Why

Seis integrantes, cada uno con su agente, van a implementar backend y front en paralelo a partir de mañana; sin un contrato único cada uno inventaría rutas, DTOs y códigos de error distintos (DD-11). El borrador de endpoints de la KB 08 deja además ambigüedades abiertas (disponibilidad por `fecha` o por `desde`/`hasta`, forma de los horarios libres, lista final de códigos de negocio) que hay que cerrar antes de ramificar. C-01 es el gate 1 del roadmap: se mergea a `main` antes que cualquier otro change.

## What Changes

- Nuevo `docs/api/openapi.yaml` (OpenAPI 3.1) con **todos** los endpoints del MVP: auth, públicos (prestaciones, disponibilidad, reserva, resumen/confirmar/cancelar por token), configuración (prestaciones, horarios de atención, bloqueos y su vista previa), pacientes, turnos/agenda (alta manual, reprogramar, confirmar, cancelar, marcar asistencia) y disponibilidad privada. Schemas de request/response (DTOs) con tipos, formatos, nullabilidad y validaciones; esquema de seguridad Bearer JWT y `security: []` en las rutas públicas.
- Cierre de ambigüedades del borrador:
  - Disponibilidad (pública y privada) por `prestacionId` + `desde` + `hasta` (`YYYY-MM-DD`, inclusivos, máximo 31 días); un solo día es `desde = hasta`. Respuesta agrupada por día, con días sin horarios como lista vacía.
  - Respuesta del login (`token`, `tipo`, `expiraEn`, `nombre`).
  - Body de `POST /api/bloqueos` con `cancelarTurnosAfectados` y respuesta 409 que trae la lista de turnos afectados.
  - Resumen público del turno sin DNI, email ni teléfono, con `puedeConfirmar` / `puedeCancelar` calculados por el backend.
  - El token del turno **nunca** viaja en una respuesta de la API (solo en los emails).
  - Orden de evaluación para confirmar/cancelar (404 → idempotencia → transición → plazo).
- Nuevo `docs/api/errores.md`: formato de error uniforme (`timestamp, status, error, codigo, mensaje, path, detalles`), tabla de códigos HTTP (400/401/404/409/422), **catálogo final de códigos de negocio** y matriz de códigos por endpoint.
- Nuevo `docs/api/tipos.ts`: tipos TypeScript estrictos del contrato (sin `any`) para el front vanilla; C-02 los copia a `frontend/src/api/types.ts`.
- Nuevos `docs/api/ejemplos/*.json`: un request y un response por endpoint, incluidos los de error, con datos ficticios; son los mocks del front y los fixtures de tests hasta que exista el backend.
- Ajuste de referencias en la KB (07 y 08) para que apunten al contrato como fuente de verdad (el borrador queda marcado como superado). No se toca `knowledge-base/10_preguntas_abiertas.md` en este change.
- Sin código de aplicación ni infraestructura.

## Capabilities

### New Capabilities
- `api-contrato`: contrato de la API REST del sistema: inventario de endpoints, autenticación por ruta, convenciones de datos (instantes, fechas, DNI), forma de los DTOs, privacidad del resumen público, disponibilidad agrupada por día y artefactos derivados (tipos TS y ejemplos) sincronizados con el OpenAPI.
- `api-errores`: formato uniforme de error, asignación única de códigos HTTP por tipo de problema y catálogo estable de códigos de negocio (`codigo`) que el front usa para reaccionar sin parsear texto.

### Modified Capabilities
<!-- Ninguna: `openspec/specs/` está vacío. -->

## Impact

- **Archivos nuevos**: `docs/api/openapi.yaml`, `docs/api/errores.md`, `docs/api/tipos.ts`, `docs/api/ejemplos/*.json`.
- **Archivos ajustados**: `knowledge-base/07_flujos_principales.md` (diagrama de reserva usa `desde`/`hasta`), `knowledge-base/08_arquitectura_propuesta.md` (nota de que el borrador quedó superado por `docs/api/`).
- **Consumidores**: todos los changes siguientes. C-02 copia `tipos.ts` y arma `ApiError`/`GlobalExceptionHandler` con este formato; C-04..C-12 implementan contra el OpenAPI; C-13..C-20 usan los ejemplos como mocks.
- **Regla de equipo**: después del merge, cualquier cambio de endpoint, DTO o código de error requiere su propio change (AGENTS.md regla 5, DD-11).
- **Governance**: CRÍTICO (CHANGES.md). Requiere revisión humana de al menos otro integrante antes del merge.
- **Dependencias de tooling**: `npx @redocly/cli lint` y `npx tsc` solo para verificar (no se agregan al repo).
