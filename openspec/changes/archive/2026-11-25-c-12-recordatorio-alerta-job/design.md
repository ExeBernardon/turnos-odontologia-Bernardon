# Design

## Context

Ver proposal.md (Why). Estado actual relevante, observado en el código de `main` tras C-11:

- `SchedulingConfig` ya tiene `@EnableScheduling` y `ClockConfig` expone un `Clock` (`systemUTC`). No existe todavía ningún `@Scheduled`.
- `AppProperties` ya trae `recordatorio.intervaloMin`, `recordatorio.horasAntes` y `alerta.pendienteHoras`, mapeadas en `application.yml` a `APP_RECORDATORIO_INTERVALO_MIN`, `APP_RECORDATORIO_HORAS_ANTES` y `APP_ALERTA_PENDIENTE_HORAS` (15 / 48 / 24).
- `Turno` ya tiene `recordatorio_enviado`, `alerta_pendiente_enviada` y el índice `turno_estado_inicio_idx (estado, inicio)`. No tiene `@Version`, y Hibernate actualiza todas las columnas del turno al hacer flush de una entidad modificada.
- `TurnoService` ya fija los flags al crear (`EstadoInicialTurno`: `PENDIENTE` → ambos `false`; `CONFIRMADO` → ambos `true`; entre 24 y 48 h, `recordatorio_enviado` = resultado del envío) y al reprogramar (los reinicia según el nuevo inicio). C-12 no tiene que tocar eso, solo probar que el job lo respeta.
- `NotificacionTurnos.recordatorio(turno)` y `.turnoNoConfirmado(turno)` devuelven `boolean`, nunca lanzan por SMTP y leen `paciente`/`prestacion` (LAZY): hay que llamarlos dentro de una transacción. `turnoNoConfirmado` resuelve el email del odontólogo desde la tabla `odontologo` y devuelve `false` si no hay. Timeout SMTP: 5 s (1 s en test).
- Los tests de integración (`@IntegracionPostgres`) levantan la app completa; varios contextos sobrescriben el `Clock` con un `Clock.fixed` (p. ej. `TurnosApiITBase`) y crean turnos en la misma base.
- `TurnoRepository` y `TurnoService` son del dominio de C-07/C-08 (otro integrante).

## Goals / Non-Goals

**Goals:**
- Que una falla con un turno (SMTP o inesperada) no afecte a los demás del mismo ciclo ni marque su flag.
- Que el job nunca pise un cambio concurrente del turno (confirmar, cancelar, reprogramar).
- Bordes deterministas y testeables: un único "ahora" por ciclo, tomado del `Clock`.
- Que el job no corra solo durante los tests de integración de otros dominios.

**Non-Goals:**
- Varias instancias del backend o lock distribuido (ShedLock, `SELECT ... SKIP LOCKED`): v1 corre una sola instancia (SU-05, DD-06).
- Reintentos con backoff, límite de reintentos o cola de mensajes: el reintento es el ciclo siguiente (DD-06).
- Avisar turnos `PENDIENTE` ya empezados (si el backend estuvo caído toda la ventana, ese aviso se pierde).
- Resaltar los pendientes en la agenda (C-19/C-20) y cambiar `EstadoInicialTurno` o el alta (C-07).

## Decisions

### D1. Tres piezas en el paquete `notificacion`
- `RecordatorioJob`: solo el disparador. `@Scheduled(fixedDelayString = "${app.recordatorio.intervalo-min}", timeUnit = MINUTES)` que llama a `AvisosProgramadosService.ejecutarCiclo()`. Sin lógica.
- `AvisosProgramadosService`: `ejecutarCiclo()` toma `clock.instant()` una sola vez y delega en `ejecutarCiclo(Instant ahora)`, que corre la tarea de recordatorios y después la de alertas y devuelve un `ResultadoCiclo` (enviados/fallidos por tarea) para tests y para un log de resumen sin datos personales.
- `TurnoAvisosRepository`: las consultas de selección y el `UPDATE` condicionado de flags.

KB 08 ubica `RecordatorioJob` en `notificacion/`. *Alternativa descartada*: poner el ciclo dentro de `TurnoService` (archivo de otro integrante, regla 16, y mezcla un proceso batch con los casos de uso HTTP).

### D2. Repositorio propio en vez de agregar métodos a `TurnoRepository`
`TurnoAvisosRepository extends Repository<Turno, Long>` (Spring Data admite varios repositorios por entidad). Así C-12 no modifica archivos del dominio `turno`.
*Alternativa descartada*: sumar los métodos a `TurnoRepository` (más natural, pero toca un archivo de C-07/C-08 y obliga a coordinar el PR). Si el equipo prefiere un solo repositorio, se mueve en el review sin cambiar comportamiento.

### D3. Selección: ids ordenados por inicio, con el índice `(estado, inicio)`
Dos consultas JPQL que devuelven solo ids:
- Recordatorio: `estado = PENDIENTE and recordatorioEnviado = false and inicio > :ahora and inicio <= :ahora + horasAntes`.
- Alerta: `estado = PENDIENTE and alertaPendienteEnviada = false and inicio > :ahora and inicio < :ahora + pendienteHoras`.

Bordes: 48 h inclusive (RN-NOT-02 "a 48 h o menos", igual que `EstadoInicialTurno.entre24y48Horas`) y 24 h exclusivo (RN-NOT-06 "a menos de 24 h"; a exactamente 24 h el turno nace `PENDIENTE`, así que no queda hueco: lo alerta el ciclo siguiente). `inicio > ahora` deja afuera los ya empezados. La igualdad en `estado` y el rango en `inicio` usan `turno_estado_inicio_idx`; no hace falta migración.
*Alternativa descartada*: traer entidades completas en la consulta (cargaría `paciente`/`prestacion` de todo el lote fuera de la transacción de cada turno y obligaría a fetch joins).

### D4. Una transacción por turno con `TransactionTemplate`
Por cada id, `transactionTemplate.execute(...)`: relee el turno, verifica que siga elegible con el mismo "ahora", llama a `notificaciones.recordatorio(turno)` (o `turnoNoConfirmado`) dentro de la transacción (relaciones LAZY) y, si devolvió `true`, marca el flag (D5). La llamada está envuelta en `try/catch (RuntimeException)`: se loguea `turnoId`, la tarea y la clase de la excepción, y se sigue con el próximo id.
*Alternativas descartadas*:
- Una sola transacción `@Transactional` para todo el ciclo: una excepción de un turno revierte las marcas de los anteriores (que ya recibieron el mail → reenvío en el ciclo siguiente) y la conexión queda tomada todo el lote.
- `@Transactional(propagation = REQUIRES_NEW)` en un método del mismo bean: no funciona por auto-invocación del proxy; exigiría un cuarto bean solo para eso.
- Enviar fuera de la transacción: `NotificacionTurnosEmail` necesita las relaciones LAZY abiertas.

Trade-off aceptado: la conexión a la base queda tomada mientras dura el envío (hasta 5 s si el SMTP cuelga). Con un solo sillón y pocos turnos por ciclo es despreciable.

### D5. Marcado con `UPDATE` condicionado, sin modificar la entidad
`update Turno t set t.recordatorioEnviado = true where t.id = :id and t.estado = PENDIENTE and t.inicio = :inicioAvisado and t.recordatorioEnviado = false` (idem para `alertaPendienteEnviada`). La entidad leída **no** se modifica con setters, así Hibernate no hace flush de la fila completa.
Si entre la lectura y el `UPDATE` el turno se confirmó, se canceló o se reprogramó, el `UPDATE` afecta 0 filas: el estado, el horario y los flags que dejó la otra operación prevalecen. En el caso de reprogramación el flag sigue en `false` y el Recordatorio del nuevo horario sale cuando corresponda (spec "Cambios concurrentes del turno no se pisan").
*Alternativas descartadas*:
- `turno.setRecordatorioEnviado(true)` + dirty checking: el `UPDATE` de Hibernate reescribe `estado` e `inicio` con los valores leídos y revierte una confirmación o reprogramación concurrente.
- `@Version` en `Turno`: toca la entidad de C-03 y haría fallar con `OptimisticLockException` las acciones HTTP concurrentes, que hoy no tienen código de error en el contrato.
- `SELECT ... FOR UPDATE` por turno: bloquearía la confirmación del paciente hasta 5 s mientras se envía el mail; el `UPDATE` condicionado da la misma garantía sin bloquear.

Riesgo residual aceptado: si la reprogramación llega justo después de que el mail salió, el paciente recibe el Recordatorio del horario viejo y enseguida el Mail 3 con el nuevo.

### D6. El job no se registra en el perfil de test
`RecordatorioJob` lleva `@ConditionalOnProperty(name = "app.recordatorio.job-habilitado", havingValue = "true", matchIfMissing = true)`; `application-test.yml` pone `false`. Los tests invocan `ejecutarCiclo(ahora)` directamente. No se agrega variable de entorno ni campo a `AppProperties` (es un interruptor técnico, no un parámetro de negocio).
*Alternativas descartadas*:
- `initialDelay` enorme solo en test: el job igual existe y una corrida larga de la suite con contextos cacheados podría dispararlo.
- `@MockitoBean RecordatorioJob` en cada IT: fácil de olvidar en los ITs de otros dominios.
- Quitar `@EnableScheduling` en test: es una configuración global que algún otro componente futuro podría necesitar.

El cableado del `@Scheduled` (intervalo leído de la propiedad, en minutos) se prueba con un contexto mínimo de Spring sin base (`SchedulingConfig` + `RecordatorioJob` + servicio mockeado, inspeccionando `ScheduledTaskHolder`).

### D7. `fixedDelay` sin `initialDelay`
`fixedDelay` (no `fixedRate` ni `cron`) para que dos ciclos nunca se solapen aunque el SMTP cuelgue. Sin `initialDelay`: al arrancar el backend corre un ciclo enseguida, lo que pone al día lo que quedó pendiente mientras estuvo apagado. Si corre antes que el seed del odontólogo, la alerta devuelve `false` y se reintenta en el ciclo siguiente.

### D8. Logs del job
Por fallo: `turnoId`, tarea (`RECORDATORIO` / `TURNO_NO_CONFIRMADO`) y clase de la excepción, **sin** `e.getMessage()` ni stack trace (un rechazo SMTP puede incluir la dirección del destinatario). Por ciclo, una línea `info` con los contadores de `ResultadoCiclo` solo si hubo algún envío o fallo, para no ensuciar el log cada 15 min. Los fallos SMTP "normales" ya los loguea `EmailService` (C-11).

### D9. Estrategia de tests (TDD estricto)
- **Unit** (`AvisosProgramadosServiceTest`, Mockito, sin Spring): orden de tareas, un solo "ahora", flag marcado solo con `true`, excepción de un turno no frena el resto, contadores de `ResultadoCiclo`, logs sin datos personales (`OutputCaptureExtension`). La transacción se reemplaza por un `TransactionTemplate` con un `PlatformTransactionManager` mockeado.
- **Integración** (`AvisosProgramadosIT`, `@IntegracionPostgres` + `@MockitoBean NotificacionTurnos` + `Clock.fixed` en un lunes a las 09:00 de Buenos Aires): bordes de 48 h / 24 h contra PostgreSQL real, estados excluidos, idempotencia, reintento, lote que sigue, reprogramado que reinicia flags (vía `TurnoService.reprogramar`), concurrencia simulada (el mock, al enviar, modifica la fila con `JdbcTemplate` desde otra conexión antes de devolver `true`) y turno nacido `CONFIRMADO` creado con `TurnoService`. Datos ficticios (DNI `99999999`, `paciente@ejemplo.com`).
- **SMTP caído real** (`AvisosProgramadosSmtpCaidoIT`, `@IntegracionPostgres` con la implementación real `NotificacionTurnosEmail` y `spring.mail.port` apuntando a un puerto cerrado vía `@DynamicPropertySource`, igual que `ReservaConSmtpCaidoIT` de C-11; no se asume que `localhost:1025` esté libre porque un integrante puede tener Mailpit corriendo): el ciclo termina sin excepción y los flags quedan en `false`.

## Risks / Trade-offs

- [Dos instancias del backend duplicarían envíos] → Fuera de alcance en v1 (SU-05); documentado. Si se escala, agregar ShedLock o `SKIP LOCKED`.
- [`EstadoInicialTurno` tiene 24/48 h fijos en código y el job usa las propiedades] → Si alguien cambia `APP_RECORDATORIO_HORAS_ANTES` o `APP_ALERTA_PENDIENTE_HORAS`, el alta y el job dejarían de coincidir. Con los valores por defecto coinciden; se avisa al dueño de C-07 en el PR (no se toca su código acá).
- [Un SMTP colgado alarga el ciclo: 5 s × turnos elegibles] → `fixedDelay` evita solapamiento; con el volumen de un consultorio son segundos.
- [Un turno con un error permanente (no SMTP) se reintenta y loguea cada 15 min] → Visible en el log por `turnoId`; aceptado sin límite de reintentos (DD-06).
- [Un turno cuya ventana pasa entera con el backend apagado nunca recibe el aviso] → Aceptado: ya empezó, el aviso no sirve.
- [Contextos de Spring extra en la suite (`@MockitoBean` + reloj fijo en un IT, puerto SMTP cerrado en otro)] → Dos contextos y contenedores más; aceptable para el tamaño de la suite.

## Migration Plan

Sin migraciones de base (columnas e índice existen desde C-03). Despliegue: merge a `main` y `docker compose up --build`; el primer ciclo corre al arrancar. Verificación manual en Mailpit: un turno creado a más de 48 h recibe el Recordatorio cuando entra en la ventana; para ver el "Turno no confirmado" sin esperar un día hace falta un `PENDIENTE` que cruce las 24 h (un turno creado a menos de 24 h nace `CONFIRMADO` y no alerta), por ejemplo subiendo temporalmente `APP_ALERTA_PENDIENTE_HORAS` en el `.env` local. Rollback: revertir el PR; ningún dato queda en un estado que el resto del sistema no entienda (solo flags en `true`).

## Open Questions

- ¿Conviene alinear `EstadoInicialTurno` con `AppProperties` (24/48 h configurables) en un change de C-07? No cambia este diseño: el job ya lee las propiedades.
- ¿El equipo prefiere que las consultas vivan en `TurnoRepository` (D2)? Es un movimiento mecánico en el review.
