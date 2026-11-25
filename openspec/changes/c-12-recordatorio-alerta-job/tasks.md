# Tasks

> Strict TDD: en cada tarea primero el test que falla (RED), después el código mínimo (GREEN), triangular con un segundo caso y refactorizar con los tests en verde. Datos ficticios siempre (`paciente@ejemplo.com`, `odontologo@ejemplo.com`, DNI `99999999`, tel. `1155555555`).
>
> Governance ALTO: antes de 2.1, confirmar con el equipo las decisiones de `design.md` (D2 repositorio propio, D4 transacción por turno, D5 `UPDATE` condicionado, D6 job deshabilitado en test, D7 sin `initialDelay`).
>
> Instantes de referencia de los tests: `AHORA = 2026-12-07T12:00:00Z` (lunes 09:00 en Buenos Aires). Bordes con `AHORA.plus(...)`.

## 1. Base

- [x] 1.1 Safety net: `./mvnw verify` en `backend/` y registrar la cantidad de tests verdes de base (si algo falla, reportarlo como falla preexistente y no seguir)
- [x] 1.2 `application-test.yml`: agregar `app.recordatorio.job-habilitado: false`; verificar que `PlataformaSmokeIT` y `AppPropertiesTest` siguen verdes (la clave extra no rompe el binding de `AppProperties`)

## 2. Selección y marcado contra PostgreSQL (`TurnoAvisosRepository`)

- [x] 2.1 Consulta de ids para el recordatorio: `TurnoAvisosRepositoryIT` (`@IntegracionPostgres`, turnos insertados con `TurnoRepository`) primero — entra `PENDIENTE` sin flag a 48 h exactas y a 30 h; no entran 48 h + 1 min, `CONFIRMADO`, `CANCELADO`, `ASISTIO`, flag en `true` ni un inicio 10 min en el pasado; resultado ordenado por inicio
- [x] 2.2 Consulta de ids para la alerta: tests en `TurnoAvisosRepositoryIT` primero — entra `PENDIENTE` sin alerta a 23 h 59 min; no entran 24 h exactas, `CONFIRMADO` con `alerta_pendiente_enviada = false`, flag en `true` ni un inicio pasado
- [x] 2.3 `UPDATE` condicionado de ambos flags: tests primero — con `PENDIENTE` y el mismo inicio afecta 1 fila y deja el flag en `true`; con el turno `CONFIRMADO`, `CANCELADO` o con otro inicio afecta 0 filas y no cambia estado, inicio ni flags
- [x] 2.4 Verificar que las dos consultas usan `turno_estado_inicio_idx`: test en `TurnoAvisosRepositoryIT` con `EXPLAIN` (tras `ANALYZE` o `SET enable_seqscan = off`) que muestre el índice en el plan

## 3. Ciclo de avisos (`AvisosProgramadosService`)

- [x] 3.1 Tarea de recordatorios: `AvisosProgramadosServiceTest` (Mockito, `TransactionTemplate` sobre un `PlatformTransactionManager` mock) primero — por cada id elegible relee el turno, llama a `notificaciones.recordatorio` y marca con el inicio leído solo si devolvió `true`; con `false` no marca; un turno que al releer ya no es elegible (confirmado o fuera de ventana) no se envía
- [x] 3.2 Tarea de alertas: tests primero — llama a `turnoNoConfirmado` y marca `alerta_pendiente_enviada` solo con `true`; nunca llama a `recordatorio` desde esta tarea
- [x] 3.3 Ciclo completo: tests primero — `ejecutarCiclo()` toma `clock.instant()` una sola vez (`Clock.fixed`) y lo usa en ambas tareas con los umbrales de `AppProperties` (`AppPropertiesFixture`; un caso con `horasAntes = 72` triangula); recordatorios antes que alertas; sin turnos elegibles no llama a `NotificacionTurnos`; `ResultadoCiclo` cuenta enviados y fallidos por tarea
- [x] 3.4 Aislamiento de fallos: tests primero — con tres ids y una `RuntimeException` en el segundo, el primero y el tercero se marcan; si fallan todos los recordatorios la tarea de alertas igual corre; el log (`OutputCaptureExtension`) tiene el `turnoId` y la tarea y no contiene email, teléfono, DNI, nombre ni token del turno ficticio ni el mensaje de la excepción

## 4. Disparador programado (`RecordatorioJob`)

- [x] 4.1 `RecordatorioJob` con `@Scheduled(fixedDelayString = "${app.recordatorio.intervalo-min}", timeUnit = MINUTES)` y `@ConditionalOnProperty(app.recordatorio.job-habilitado, matchIfMissing = true)`: `RecordatorioJobTest` (contexto mínimo `@SpringJUnitConfig` con `SchedulingConfig`, el job y `AvisosProgramadosService` mockeado, sin base) primero — `ScheduledTaskHolder` registra una tarea fixed-delay de 15 min; con `intervalo-min = 5` es de 5 min; invocar el método del job delega en `ejecutarCiclo()`
- [x] 4.2 Job deshabilitado en test: test primero — en el contexto `@IntegracionPostgres` no existe el bean `RecordatorioJob` y el servicio sí; con la propiedad ausente (contexto mínimo de 4.1) el job se registra

## 5. Integración de punta a punta

- [x] 5.1 `AvisosProgramadosIT` (`@IntegracionPostgres`, `@MockitoBean NotificacionTurnos`, `Clock.fixed(AHORA)` vía `@TestConfiguration`) primero — dos tareas en el mismo ciclo; idempotencia (segundo ciclo sin reenvío); reintento (mock `false` y después `true`); lote de tres con el segundo fallando; turno creado entre 24 y 48 h con el recordatorio fallido al crear se reintenta en el ciclo
- [x] 5.2 Estados y nacimiento: tests en `AvisosProgramadosIT` primero — turno creado con `TurnoService` a menos de 24 h (nace `CONFIRMADO`) no recibe recordatorio ni alerta; turno `PENDIENTE` confirmado por el odontólogo no recibe la alerta
- [x] 5.3 Reprogramación reinicia flags: tests primero — turno con ambos flags en `true` reprogramado con `TurnoService.reprogramar` a 72 h: ciclo a `AHORA` no lo avisa, ciclo a `AHORA + 25 h` (faltan 47 h) envía un único Recordatorio; reprogramado a 6 h queda `CONFIRMADO` y el ciclo no lo avisa
- [x] 5.4 Cambios concurrentes: tests primero — el mock de `recordatorio`, antes de devolver `true`, reprograma la fila con `JdbcTemplate` (otra conexión: inicio + 5 días, flag en `false`); al terminar el turno conserva el nuevo inicio y `recordatorio_enviado = false`. Idem con la alerta y la fila pasada a `CONFIRMADO`: queda `CONFIRMADO`
- [x] 5.5 SMTP caído real: `AvisosProgramadosSmtpCaidoIT` (`@IntegracionPostgres` con `NotificacionTurnosEmail` real y `spring.mail.port` en un puerto cerrado vía `@DynamicPropertySource`, como `ReservaConSmtpCaidoIT`) primero — el ciclo termina sin excepción, en menos de 5 s para dos turnos, y ambos flags quedan en `false`

## 6. Cierre

- [x] 6.1 Refactor final con tests verdes, `./mvnw verify` en `backend/` completo (nuevos + baseline, sin regresiones en los IT de otros dominios) y `openspec validate c-12-recordatorio-alerta-job --strict`
- [ ] 6.2 Prueba manual: `docker compose up --build`; crear un turno `PENDIENTE` a 30 h con el SMTP apagado (sin Recordatorio), levantar Mailpit y verificar en `http://localhost:8025` que el ciclo siguiente lo envía con Confirmar y Cancelar; con `APP_ALERTA_PENDIENTE_HORAS` subido en el `.env` local, verificar el "Turno no confirmado" en `odontologo@ejemplo.com`; el log del backend no muestra emails ni tokens

## Workflow follow-up

- Marcar C-12 como `[x]` en `CHANGES.md` y archivar con `/opsx:archive c-12-recordatorio-alerta-job` después del merge del PR.
- En el PR, avisar al dueño de C-07/C-08 (regla 16): umbrales fijos en `EstadoInicialTurno` vs. propiedades del job, y que C-12 agrega `TurnoAvisosRepository` sobre la entidad `Turno` sin tocar `TurnoRepository`.
