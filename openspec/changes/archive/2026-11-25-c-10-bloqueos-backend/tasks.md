# Tasks

## 1. Base y DTOs

- [x] 1.1 Safety net: `./mvnw verify` y registrar la cantidad de tests de base
- [x] 1.2 DTOs (`BloqueoRequest`, `BloqueoCrearRequest`, `BloqueoResponse`, `BloqueoPrevisualizacionResponse`, `BloqueoCreadoResponse`, `TurnoAfectadoResponse`) y `BloqueoMapper` con instantes en `-03:00` y `pendienteMenos24h` (test unitario del mapper)

## 2. Listado, baja y vista previa

- [x] 2.1 `GET /api/bloqueos` con validación del rango (400) y tests (dentro/fuera de rango, invertido, > 31 días, 401, claves del contrato)
- [x] 2.2 `DELETE /api/bloqueos/{id}` (204/404) y test de que no restituye turnos
- [x] 2.3 `POST /api/bloqueos/previsualizar` (422 rango inválido, solo activos, borde excluido, no modifica nada, 401, claves)

## 3. Alta con cancelación

- [x] 3.1 `POST /api/bloqueos` sin turnos afectados: 201 directo, bloqueos solapados, 422, 400 de validación
- [x] 3.2 409 `BLOQUEO_CON_TURNOS_AFECTADOS` con `turnosAfectados` (excepción + advice acotado al controller); el bloqueo no se crea
- [x] 3.3 Cancelación atómica con `TransactionTemplate` (bloqueo + turnos `CANCELADO`/`ODONTOLOGO`), test de rollback conjunto y de horario liberado (insertar turno nuevo)
- [x] 3.4 Avisos post-commit: uno por turno, tolerantes a excepción y a `false`; test con `NotificacionTurnos` que lanza y test de que el bloqueo ya estaba confirmado

## 4. Cierre

- [x] 4.1 Refactor, `./mvnw verify` verde, `openspec validate c-10-bloqueos-backend --strict`
- [x] 4.2 `docker compose up -d --build --wait` y `GET /api/bloqueos` sin token devuelve 401
