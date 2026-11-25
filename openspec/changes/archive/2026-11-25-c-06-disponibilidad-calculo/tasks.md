# Tasks

## 1. API pública del módulo y aritmética de intervalos

- [x] 1.1 Publicar primero la API de dominio que consumirán C-07/C-08: `ModoDisponibilidad` (`PUBLICA`/`ODONTOLOGO`), `ContextoValidacion` (modo + `turnoExcluidoId`) y la firma de `DisponibilidadService.validarHorario(prestacion, inicio, contexto)` con su sobrecarga de exclusión; verificar que el paquete `disponibilidad` compila y que un test unitario mínimo afirma 422 `HORARIO_NO_DISPONIBLE` para un inicio inválido (`mvn -f backend/pom.xml test -Dtest='*Disponibilidad*'`).
- [x] 1.2 Crear el record inmutable `Intervalo(Instant inicio, Instant fin)` con operaciones puras `restar`, `interseca` y `contiene`, y sus tests unitarios TDD: solapamiento parcial, inclusión total, adyacentes que solo tocan un borde y un ocupante que parte un rango en dos; verificar con `IntervaloTest` en verde.

## 2. Cálculo puro de horarios (TDD, prioridad 1)

- [x] 2.1 Escribir primero los tests de `CalculoDisponibilidad.horariosLibres(...)` y luego implementarlos: rangos múltiples por día, bloqueo parcial, turno activo que parte el rango, duración que no entra, turno cancelado que no ocupa y grilla de 15 min alineada al inicio del rango original (no al sub-rango); verificar con el test del núcleo en verde sin base de datos.
- [x] 2.2 Agregar con TDD el filtro temporal con `Clock.fixed`: `inicio <= ahora` se descarta, bordes exactos de 2 h y 60 días en modo público (inclusive), modo odontólogo sin ventana y día sin horarios devuelve lista vacía; verificar los tests de bordes en verde.
- [x] 2.3 Implementar `esValido(...)` y sus tests: inicio fuera de la grilla, turno que no cabe en el rango, solape con bloqueo o turno activo, y mensaje de causa concreto para cada rechazo; verificar que todos los casos devuelven `false` y el mensaje esperado.
- [x] 2.4 Conectar `DisponibilidadService.validarHorario(...)` con el núcleo, resolviendo prestación inexistente (404 `RECURSO_NO_ENCONTRADO`), inactiva (422 `PRESTACION_INACTIVA`) y excluyendo de los ocupantes el `turnoExcluidoId` en reprogramación; verificar con tests unitarios con mocks de repositorios que cubren reserva, carga manual y reprogramación.

## 3. Consulta por rango, DTOs y endpoints

- [x] 3.1 Crear `DisponibilidadResponse`, `DiaDisponibleResponse` y `PrestacionRefResponse` y su armado a partir del cálculo, con `OffsetDateTime` en `America/Argentina/Buenos_Aires`; verificar con un test de serialización Jackson que el JSON coincide con las claves y el offset `-03:00` de `docs/api/ejemplos/disponibilidad-publica.200.json`.
- [x] 3.2 Implementar `DisponibilidadService.consultar(prestacionId, desde, hasta, modo)`: validar rango invertido o >31 días (400 `VALIDACION`), resolver la prestación y hacer una consulta por repositorio para todo el periodo, devolviendo un elemento por fecha con `horarios: []` cuando corresponda; verificar con tests unitarios (mocks) el rango de un día, el de varios días y el día sin atención.
- [x] 3.3 Agregar `DisponibilidadPublicaController` (`/api/public/disponibilidad`, sin JWT) y `DisponibilidadController` (`/api/disponibilidad`, con JWT) usando los parámetros `prestacionId`, `desde` y `hasta` del contrato; verificar con tests MockMvc/status 200 y los errores 400/404/422 con el formato uniforme, más 401 en la ruta privada sin token.

## 4. Verificación integral del change

- [x] 4.1 Ejecutar `mvn -f backend/pom.xml test` completo y verificar que todos los tests (nuevos y existentes) pasan, sin Testcontainers en los tests unitarios nuevos.
- [x] 4.2 Levantar `docker compose up --build` y comprobar manualmente que `GET /api/public/disponibilidad?prestacionId=1&desde=<lunes>&hasta=<lunes>` y su versión privada con JWT devuelven horarios coherentes con el seed (Consulta 30 min, lunes 09-13 y 15-19), sin datos personales en la respuesta.

## Workflow follow-up

- Abrir PR a `main` desde `cande/c-06-disponibilidad-calculo`, marcar `[x]` el change C-06 en `CHANGES.md` en el mismo PR y avisar a C-07/C-08 que `validarHorario(...)` ya está disponible.
- Archivar el change con `/opsx:archive c-06-disponibilidad-calculo` después de la revisión.
