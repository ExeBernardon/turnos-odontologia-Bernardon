## 1. Prestaciones (TDD)

- [x] 1.1 DTOs, mapper y `PrestacionService` con alta, listado y actualización (Bean Validation, 404)
- [x] 1.2 `PrestacionController` `GET/POST /api/prestaciones` y `PUT /api/prestaciones/{id}` con tests MockMvc + Postgres y conformidad con ejemplos
- [x] 1.3 `DELETE /api/prestaciones/{id}` con 204 / 422 `PRESTACION_CON_TURNOS` / 404
- [x] 1.4 `GET /api/public/prestaciones` solo activas, sin autenticación
- [x] 1.5 Test: cambiar la duración no modifica el `fin` de turnos existentes

## 2. Horarios de atención (TDD)

- [x] 2.1 DTOs, mapper (diaSemana 1..7, `HH:mm`) y `HorarioAtencionService` con listado ordenado
- [x] 2.2 `POST` y `PUT` con 422 `RANGO_HORARIO_INVALIDO` y `HORARIOS_SOLAPADOS` (adyacentes permitidos, varios rangos por día, PUT ignora el propio id)
- [x] 2.3 `DELETE` y 404 en PUT/DELETE
- [x] 2.4 Tests 401 sin autenticación y conformidad con ejemplos del contrato

## 3. Verificación

- [x] 3.1 `./mvnw verify` en verde
- [x] 3.2 `openspec validate c-05-prestaciones-horarios-backend --strict`
- [x] 3.3 Smoke con Docker Compose: `/api/public/prestaciones` devuelve las 4 activas y `/api/prestaciones` responde 401
