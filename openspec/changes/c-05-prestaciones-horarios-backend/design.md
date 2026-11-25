## Context

C-03 dejó las entidades `Prestacion` y `HorarioAtencion` con sus repositorios, y `ApiException`/`GlobalExceptionHandler`. El contrato (`docs/api/openapi.yaml`) y sus ejemplos son la fuente de verdad. C-04 (JWT) se construye en paralelo; `SecurityConfig` ya exige autenticación fuera de `/api/public/**`.

## Goals / Non-Goals

**Goals:** exponer los endpoints del contrato para prestaciones y horarios, con las reglas RN-PRE y RN-HOR, sin modificar infraestructura compartida.
**Non-Goals:** disponibilidad, bloqueos, UI, cambios al contrato.

## Decisions

- **Paquetes**: `prestacion/` (`PrestacionController`, `PrestacionPublicaController`, `PrestacionService`, DTOs `PrestacionRequest`, `PrestacionResponse`, `PrestacionPublicaResponse`, `PrestacionMapper`) y `horario/` análogo. DTOs como `record` con anotaciones Bean Validation; mappers manuales.
- **Prestación con turnos**: consulta `@Query` en `PrestacionRepository` (`tieneTurnos(id)`) para no tocar `turno/`. Se chequea antes de borrar.
- **Errores**: `ApiException` con códigos de `errores.md` (`RECURSO_NO_ENCONTRADO` 404, `PRESTACION_CON_TURNOS`, `RANGO_HORARIO_INVALIDO`, `HORARIOS_SOLAPADOS` 422).
- **Día de la semana**: el DTO usa entero 1..7 (`@Min(1) @Max(7)`) y el mapper convierte con `DayOfWeek.of`; las horas se serializan como `HH:mm`.
- **Rango y solapamiento** en el service: primero `RANGO_HORARIO_INVALIDO`, luego solapamiento contra los rangos del mismo día excluyendo el propio id; solapa si `inicioNuevo < finExistente && inicioExistente < finNuevo` (semiabierto).
- **`activa`** es opcional en el request (`Boolean`, default `true`).
- **Tests**: Testcontainers PostgreSQL 16 (`@IntegracionPostgres`) con MockMvc; `@WithMockUser` para endpoints privados más un caso 401 sin autenticación; tests de conformidad comparan las claves del JSON con los ejemplos de `docs/api/ejemplos`.

## Risks / Trade-offs

- Sin protección ante condiciones de carrera en el solapamiento: hay un solo odontólogo; aceptado para el MVP.
- Los tests privados usan `@WithMockUser`; siguen siendo válidos cuando entre C-04.
