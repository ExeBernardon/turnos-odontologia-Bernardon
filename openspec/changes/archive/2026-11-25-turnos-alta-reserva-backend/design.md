# Design

## Context

Ver `proposal.md` para la motivación. Estado actual verificado en el repo:

- `Turno` ya tiene todos los campos necesarios (`inicio`, `fin`, `estado`, `token`, `emailContacto`, `telefonoContacto`, `consentimientoAt`, `recordatorioEnviado`, `alertaPendienteEnviada`) y `TurnoRepository.findByToken` / `findActivosQueIntersecan`.
- `schema-extra.sql` ya crea la extensión `btree_gist` y la restricción `turno_sin_solapamiento` (`EXCLUDE … WHERE estado IN ('PENDIENTE','CONFIRMADO')`); `ViolacionDeSolapamiento` y `GlobalExceptionHandler` ya traducen SQLSTATE `23P01` a 409 `HORARIO_OCUPADO`.
- `DisponibilidadService.validarHorario(...)` (C-06) ya expone las sobrecargas pública/odontólogo y devuelve 422 `HORARIO_NO_DISPONIBLE` con motivo concreto.
- `NotificacionTurnos` (C-03) ya define los ocho avisos con contrato booleano (nunca lanza por fallo de envío) y existe una implementación no-op reemplazable por C-11.
- `SecurityConfig` ya deja `/api/public/**` sin token y exige JWT para `/api/**`; `AppProperties` y el bean `Clock` ya están configurados.
- `Paciente` no guarda contacto: `email`/`teléfono` viven en el turno, y `PacienteRepository.findByDni` ya existe.

## Goals / Non-Goals

**Goals:**

- Un único flujo de alta compartido por la reserva pública y la carga manual, con diferencias explícitas de contexto (ventana, consentimiento, JWT).
- Persistir el turno de forma atómica en el service, con la base como última garantía anti-solapamiento.
- Calcular estado inicial y flags con reloj inyectado y zona de negocio, sin depender de la zona del servidor.
- Emitir los avisos de creación sin acoplar la reserva al SMTP y sin exponer datos personales ni el token.

**Non-Goals:**

- Confirmar, cancelar, reprogramar ni marcar asistencia (C-08).
- Enviar emails reales (C-11) ni construir pantallas (C-17, C-20).
- Cambiar `docs/api/openapi.yaml`, códigos de error ni el esquema de base de datos.
- Modificar los datos de un paciente ya existente.

## Decisions

1. **Un service de alta, dos controllers delgados.** `TurnoService` expone `reservar(...)` y `crearManual(...)`; los controllers solo validan el body con Bean Validation, invocan el service y devuelven DTOs. *Alternativa descartada:* duplicar reglas en cada controller (riesgo de divergencia entre reserva y carga manual) o devolver entidades JPA (prohibido por las reglas del proyecto).
2. **Validar antes e insertar con `saveAndFlush` dentro del service.** `validarHorario` da el 422 con causa concreta, pero no baja la carrera; el `saveAndFlush` fuerza la violación de la exclusión dentro de la transacción del service y el handler existente la convierte en 409. *Alternativa descartada:* confiar solo en la validación Java (dos hilos pueden pasarla) o dejar el flush al commit del controller (la excepción llega envuelta y se pierde el control del respuesta).
3. **Estado inicial y flags en una función pura con `Clock` inyectado.** Devuelve `PENDIENTE` con flags en `false` cuando faltan 24 h o más, y `CONFIRMADO` con los flags correspondientes a "ya avisado" cuando faltan menos; el umbral se evalúa con `Instant` y `Duration`, nunca con `LocalDateTime`. *Alternativa descartada:* calcularlo en el controller o usar la hora del servidor (UTC) sin convertir a `America/Argentina/Buenos_Aires`.
4. **Paciente por DNI normalizado, sin upsert destructivo.** Se normaliza el DNI a dígitos, se busca con `findByDni`; si existe se reutiliza tal cual (sin `setNombre`/`setApellido`), si no se crea uno nuevo con datos ficticios del request. En la carga manual con `paciente` nuevo y DNI ya registrado se devuelve 422 `DNI_DUPLICADO` según el contrato; en la reserva pública el DNI existente se reutiliza. *Alternativa descartada:* actualizar nombre/apellido del paciente existente con cada reserva (violaría RN-PAC y sobrescribiría datos históricos). La unicidad de DNI sigue garantizada por la base.
5. **Token opaco generado con `SecureRandom`.** 32 bytes criptográficamente aleatorios codificados Base64 URL-safe sin padding; la columna ya tiene `turno_token_uk`. El token solo viaja en el link del email: no se devuelve en ningún DTO ni se escribe en logs. *Alternativa descartada:* `UUID.randomUUID()` o un id incremental (predecibles y no cumplen el contrato de opacidad).
6. **DTOs de request/response separados por caso de uso.** Request de reserva pública (con `consentimiento` booleano), request de carga manual (`pacienteId` o paciente nuevo, de forma excluyente), y un `TurnoPublicoResponse` con solo prestación, `inicio`, `fin`, `estado`, nombre de pila, `puedeConfirmar`, `puedeCancelar` y contacto del consultorio. *Alternativa descartada:* reutilizar la entidad o un único DTO con campos opcionales (filtraría datos personales o rompería el contrato).
7. **Avisos invocados después del `saveAndFlush`, dentro de la transacción.** Se usa el resultado booleano de `NotificacionTurnos` para ajustar los flags que correspondan; un fallo de envío nunca lanza ni revierte la reserva. *Alternativa descartada:* `ApplicationEvent` post-commit (la implementación real de C-11 necesita leer relaciones LAZY del turno y actualizar flags en la misma operación; además cambiaría el contrato ya publicado por C-03).
8. **`Clock` fijo en unitarios, PostgreSQL real en integración.** Las reglas puras (estado,normalización, flags) se prueban con `Clock` fijo; la carrera, el 409 real y la consulta por token se prueban con `@IntegracionPostgres` (Testcontainers PG16), nunca H2. *Alternativa descartada:* H2 (no tiene `EXCLUDE USING gist`).

## Risks / Trade-offs

- **[Carrera por el mismo horario]** La validación previa puede pasar en dos hilos → `saveAndFlush` + restricción de exclusión + traducción de `23P01` a 409; test de carrera con dos hilos.
- **[Fallo SMTP]** El envío ocurre en la misma request → el contrato de `NotificacionTurnos` garantiza no lanzar; se prueba con una implementación de prueba que devuelve `false` y se verifica que el turno persista. C-11 debe respetar timeouts al implementar el envío real.
- **[DNI duplicado simultáneo]** Dos reservas públicas nuevas con el mismo DNI pueden chocar con `paciente_dni_uk` → ante una violación de integridad que no sea solapamiento, reintentar una vez la búsqueda por DNI y reutilizar el paciente; en la carga manual explícita se responde 422 `DNI_DUPLICADO`. El paciente existente nunca se modifica.
- **[Zona horaria]** El JVM de tests corre en UTC → todos los instantes se manipulan como `Instant`/`OffsetDateTime` y la conversión de día/hora usa `app.zonaHoraria()`.
- **[Fuga de datos]** El resumen público puede filtrar contacto del paciente → DTO dedicado sin DNI, apellido, email, teléfono, ids ni token, con test de serialización.

## Migration Plan

No hay cambios de esquema: se usa la tabla `turno` y la restricción ya creadas por C-03. El deploy es code-only tras PR a `main`; el rollback es revertir el commit (los turnos creados durante la demo usan datos ficticios y no requieren limpieza de producción).

## Open Questions

_Ninguna que pueda resolverse después sin cambiar specs, diseño o tareas; las decisiones de anticipación y mails están definidas en KB 05 y en el contrato (C-01)._
