# Tasks

## 1. Reglas puras y contratos de entrada/salida (TDD)

- [x] 1.1 Escribir `EstadoInicialTurnoTest` con `Clock` fijo para los bordes de 24 h y 48 h, luego implementar el cálculo puro que devuelve estado y flags (`PENDIENTE`/flags `false` a 24 h o más; `CONFIRMADO`/flags `true` por debajo); verificar con `mvn -Dtest=EstadoInicialTurnoTest test` en verde.
- [x] 1.2 Escribir tests de validación de los DTO de reserva (campos obligatorios, `inicio` con offset, longitudes; `consentimiento` ausente o `false` debe dar 422 `CONSENTIMIENTO_REQUERIDO`, no 400) y crear los records con Bean Validation; verificar que los tests fallen primero y luego pasen con `mvn -Dtest=ReservaPublicaRequestTest test`.
- [x] 1.3 Escribir `GeneradorTokenTest` que exija 32 bytes de entropía, Base64 URL-safe y unicidad entre generaciones, e implementar el generador con `SecureRandom`; verificar con `mvn -Dtest=GeneradorTokenTest test`.
- [x] 1.4 Escribir tests de normalización de DNI con y sin puntos y del `TurnoPublicoResponse` sin campos sensibles, e implementar ambos; verificar con `mvn -Dtest=NormalizacionDniTest,TurnoPublicoResponseTest test`.

## 2. Service de alta y reglas de negocio (TDD)

- [x] 2.1 Escribir tests unitarios del service de reserva que exijan: consentimiento obligatorio (422 `CONSENTIMIENTO_REQUERIDO`), `validarHorario` con contexto público, prestación inexistente (404) e inactiva (422), `saveAndFlush`, `fin = inicio + duración` y paciente existente reutilizado sin modificar; verificar que fallen antes de implementar.
- [x] 2.2 Implementar el service de reserva con los DTO del paso 1, resolución de paciente por DNI normalizado, generación de token y cálculo de estado/flags; verificar que los tests del paso 2.1 pasen con `mvn -Dtest=TurnoServiceTest test`.
- [x] 2.3 Escribir tests del service con `NotificacionTurnos` de prueba para las tres franjas de anticipación (>48 h, 24-48 h con bordes exactos inclusive, <24 h) y para un fallo de envío que no revierte el turno; implementar las llamadas y el ajuste de flags; verificar con `mvn -Dtest=TurnoAvisosServiceTest test`.
- [x] 2.4 Escribir `TurnoAltaConcurrenciaIT` con Testcontainers PostgreSQL 16: dos hilos reservando el mismo inicio → un 201 y un 409 `HORARIO_OCUPADO`, solo un turno activo; verificar con `mvn -Dtest=TurnoAltaConcurrenciaIT test`.

## 3. Endpoints públicos (TDD)

- [x] 3.1 Escribir tests de API para `POST /api/public/turnos` (201 con resumen sin token, 400 con `detalles`, 422 sin consentimiento, 409 por solapamiento) e implementar el controller público; verificar con `mvn -Dtest=TurnoPublicoApiIT test`.
- [x] 3.2 Escribir tests de `GET /api/public/turnos/{token}` (200 sin DNI/email/teléfono/token, `puedeConfirmar`/`puedeCancelar` según estado y plazo, consultas repetidas sin cambio de estado, 404 `TOKEN_INVALIDO`) e implementar la consulta y el mapper; verificar con `mvn -Dtest=TurnoTokenApiIT test`.

## 4. Carga manual con JWT (TDD)

- [x] 4.1 Escribir tests de API para `POST /api/turnos` con JWT (201 con paciente existente y con alta rápida, 400 si `pacienteId` y `paciente` juntos o ausentes, 422 `DNI_DUPLICADO` si el paciente nuevo ya existe, 401 sin token, horario de menos de 2 h aceptado) e implementar el controller y el método del service; verificar con `mvn -Dtest=TurnoManualApiIT test`.
- [x] 4.2 Verificar en los mismos tests que `consentimientoAt` queda nulo, que el contacto se guarda en el turno y que la respuesta privada no expone el token; verificar con `mvn -Dtest=TurnoManualApiIT test`.

## 5. Verificación integral

- [x] 5.1 Ejecutar `mvn test` completo y verificar que pasan todas las suites (incluidas las de C-03 a C-06) sin H2 ni datos reales.
- [x] 5.2 Verificar con `git diff -- docs/api` que el contrato OpenAPI, `errores.md` y ejemplos no se modificaron: este change solo implementa lo ya declarado.

## Workflow follow-up

- Crear la rama `feat/c-07-turnos-alta-reserva-backend`, abrir PR a `main` y no pushear directo tras C-01.
- Tras la revisión, archivar el change con `/opsx:archive` y marcar `[x]` el C-07 en `CHANGES.md`.
