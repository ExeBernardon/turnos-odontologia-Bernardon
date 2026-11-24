# Tasks — C-03 entidades-base-y-seed

> TDD estricto: cada tarea con lógica arranca con su test en rojo (RED), después el mínimo código para pasarlo (GREEN) y al menos un segundo caso (TRIANGULATE). Los IT siguen design D13: un contexto compartido (`@IntegracionPostgres`), sin `@Transactional`, con limpieza en `@AfterEach` y datos ficticios. Governance de `CHANGES.md`: **CRITICO**. `schema-extra.sql` y `GlobalExceptionHandler` se revisan con el usuario antes de mergear.
> Safety net antes de tocar archivos existentes: `cd backend && ./mvnw test` en verde (baseline de C-02).

## 1. Infraestructura de tests

- [x] 1.1 Correr `./mvnw test` y anotar el baseline (`PlataformaSmokeIT`, `GlobalExceptionHandlerTest`, `AppPropertiesTest`, `SecurityConfigTest` en verde). Crear la meta-anotación de test `@IntegracionPostgres` (`@SpringBootTest(RANDOM_PORT) @ActiveProfiles("test") @Import(TestcontainersConfig.class)`) y migrar `PlataformaSmokeIT` a ella. Verificar que `./mvnw test` sigue en verde y que el log muestra un solo contenedor PostgreSQL

## 2. Catálogo, horarios y odontólogo

- [x] 2.1 RED/GREEN `DiaSemanaConverterTest` (unitario): `MONDAY` → 1, `SUNDAY` → 7 y vuelta, `null` → `null`. Implementar el `AttributeConverter<DayOfWeek, Short>` en `horario/` (design D3). Verificar con `./mvnw test -Dtest=DiaSemanaConverterTest`
- [x] 2.2 RED/GREEN `CatalogoRepositoryIT`: guardar y leer `Prestacion` (`activa` por defecto `true`), `HorarioAtencion` (columnas `time` y `smallint`), y `findByDiaSemanaOrderByHoraInicio` devuelve los rangos del día ordenados y no los de otro día. Implementar `Prestacion`, `PrestacionRepository`, `HorarioAtencion` y `HorarioAtencionRepository` con longitudes e índices de design D3/D6. Verificar con `./mvnw test -Dtest=CatalogoRepositoryIT`
- [x] 2.3 RED/GREEN `OdontologoRepositoryIT`: `findByEmail` encuentra y devuelve vacío para un email inexistente, y un email repetido falla por `odontologo_email_uk`. Implementar `auth/Odontologo` y `OdontologoRepository` (design D1, D6). Verificar con `./mvnw test -Dtest=OdontologoRepositoryIT`

## 3. Pacientes, bloqueos y turnos

- [x] 3.1 RED/GREEN `PacienteRepositoryIT`: `findByDni("99999999")` encuentra y devuelve vacío para un DNI inexistente, y un DNI repetido falla por `paciente_dni_uk`. Implementar `Paciente` y `PacienteRepository`. Verificar con `./mvnw test -Dtest=PacienteRepositoryIT`
- [x] 3.2 RED/GREEN `BloqueoRepositoryIT`: `findQueIntersecan(09:00, 12:00)` incluye el bloqueo 08:00-09:30, excluye 07:00-09:00 (borde) y 12:00-13:00, y `motivo` admite nulo. Implementar `Bloqueo` y `BloqueoRepository` (design D12). Verificar con `./mvnw test -Dtest=BloqueoRepositoryIT`
- [x] 3.3 RED/GREEN `TurnoRepositoryIT`:
  - un instante guardado con offset `-03:00` se lee como el mismo `Instant`;
  - `information_schema` reporta `timestamp with time zone` en `turno.inicio` y `bloqueo.inicio`, y `time without time zone` en `horario_atencion.hora_inicio`;
  - `estado` y `cancelado_por` se guardan como texto (consulta SQL directa);
  - los flags nacen en `false` y `consentimiento_at` queda nulo;
  - un token repetido falla por `turno_token_uk`;
  - `findByToken` devuelve vacío para un token inexistente;
  - `findActivosQueIntersecan` devuelve solo el activo interno del rango (excluye el cancelado y el que toca el borde).

  Implementar `Turno`, `EstadoTurno` (con `ACTIVOS`), `CanceladoPor` y `TurnoRepository` (design D3 a D6, D12). Verificar con `./mvnw test -Dtest=TurnoRepositoryIT`

## 4. Restricción de no solapamiento (revisar con el usuario)

- [x] 4.1 RED `TurnoSolapamientoIT` con cinco casos:
  - existe en `pg_constraint` una sola fila `turno_sin_solapamiento` con `contype = 'x'` sobre `turno`;
  - activo 09:00-09:30 + activo 09:15-09:45 → `DataIntegrityViolationException` con SQLSTATE `23P01` en la cadena de causas;
  - adyacentes 09:00-09:30 y 09:30-10:00 → OK;
  - `CANCELADO` 09:00-09:30 + `PENDIENTE` 09:00-09:30 → OK;
  - reactivar ese cancelado a `PENDIENTE` → `23P01`.

  Confirmar que falla en rojo con el `schema-extra.sql` actual
- [x] 4.2 GREEN: ampliar `schema-extra.sql` con el bloque `DO $$ ... $$` de design D7 (separador `^^^`, chequeo en `pg_constraint` con `conrelid`, `tstzrange(inicio, fin, '[)')`, `WHERE estado IN ('PENDIENTE','CONFIRMADO')`). Verificar con `./mvnw test -Dtest=TurnoSolapamientoIT` en verde: si el contexto levanta, el separador funciona con el bloque `DO`
- [x] 4.3 RED/GREEN `SchemaExtraIdempotenteIT`: reejecutar `schema-extra.sql` con `ResourceDatabasePopulator` (separador `^^^`) sobre la base ya inicializada no lanza nada, y sigue habiendo exactamente una `turno_sin_solapamiento` y una extensión `btree_gist` (design D8). Verificar con `./mvnw test -Dtest=SchemaExtraIdempotenteIT`

## 5. Traducción de `23P01` a 409 (revisar con el usuario)

- [x] 5.1 RED/GREEN `ViolacionDeSolapamientoTest` (unitario) para el helper de design D9:
  - `SQLException` con estado `23P01` envuelta en `DataIntegrityViolationException` → `true`;
  - envuelta dos niveles (por ejemplo dentro de `TransactionSystemException`) → `true`;
  - `23505` → `false`;
  - sin `SQLException` en la cadena → `false`.

  Implementar el helper en `common/`. Verificar con `./mvnw test -Dtest=ViolacionDeSolapamientoTest`
- [x] 5.2 Ampliar `TurnoSolapamientoIT`: el helper devuelve `true` para la excepción real del caso solapado. Prueba el SQLSTATE con el driver real, no con un mock. Verificar con `./mvnw test -Dtest=TurnoSolapamientoIT`
- [x] 5.3 RED/GREEN en `GlobalExceptionHandlerTest`, con el controller de prueba:
  - un endpoint que tira `DataIntegrityViolationException` con causa `23P01` → 409, `codigo` `HORARIO_OCUPADO`, mensaje "Ese horario ya fue reservado. Elegí otro.", sin `detalles` y sin texto SQL ni nombre de constraint en el body;
  - uno con causa `23505` → 500 `ERROR_INTERNO`.

  Implementar el `@ExceptionHandler(DataIntegrityViolationException.class)` y el chequeo en el catch-all (design D9). Verificar con `./mvnw test -Dtest=GlobalExceptionHandlerTest`

## 6. Puerto de notificaciones

- [x] 6.1 RED/GREEN `NotificacionTurnosNoOpTest` (unitario con `OutputCaptureExtension`):
  - cada uno de los 8 métodos con un `Turno` de fixture (email `paciente@ejemplo.com`, token conocido) devuelve `true`, no lanza nada, y la salida capturada no contiene el email, el teléfono, el DNI ni el token.

  Implementar la interfaz `NotificacionTurnos` con Javadoc por método (número de mail de KB 05, destinatario y cuándo se llama dentro de la transacción) y `NotificacionTurnosNoOp` con `@Fallback` (design D11). Verificar con `./mvnw test -Dtest=NotificacionTurnosNoOpTest`
- [x] 6.2 RED/GREEN `NotificacionTurnosIT`:
  - en el contexto de la app, el bean inyectado de `NotificacionTurnos` es el no-op;
  - en un contexto liviano (`ApplicationContextRunner` con el no-op más otra implementación de prueba) se inyecta la otra sin `@Primary`.

  Verificar con `./mvnw test -Dtest=NotificacionTurnosIT`

## 7. Datos iniciales

- [x] 7.1 RED `DataSeederIT` con cinco casos:
  - después del arranque hay 1 odontólogo con el email de `SEED_ODONTOLOGO_EMAIL` y `passwordEncoder.matches(SEED_ODONTOLOGO_PASSWORD, hash)`, y el hash no es el texto plano;
  - hay 4 prestaciones activas con duraciones 30/45/45/60;
  - hay 10 horarios: dos por día 1..5 (09:00-13:00 y 15:00-19:00) y ninguno en 6 y 7;
  - pacientes, turnos y bloqueos están vacíos.
  - correr `run()` otra vez deja los mismos conteos.

  Confirmar el rojo
- [x] 7.2 GREEN: implementar `seed/DataSeeder` (`CommandLineRunner`, `@Transactional`, tres guardas `count() == 0` independientes, log solo de cantidades; design D10). Verificar con `./mvnw test -Dtest=DataSeederIT`
- [x] 7.3 TRIANGULATE en `DataSeederIT`:
  - un `DataSeeder` construido con otras `AppProperties` (otro email) no crea ni modifica al odontólogo existente;
  - con la tabla de prestaciones con una sola prestación propia, `run()` no agrega ninguna;
  - con horarios vaciados, `run()` vuelve a crear los 10.

  Restaurar el seed en `@AfterEach`. Verificar con `./mvnw test -Dtest=DataSeederIT`

## 8. Verificación integrada

- [x] 8.1 Correr `cd backend && ./mvnw test` completo en verde (baseline de C-02 más los tests nuevos) y revisar con `git diff --stat` que no hay controllers, services ni cambios en `docs/api/`
- [x] 8.2 Hacer una verificación manual con Docker:
  - `docker compose up -d --build` y el backend queda healthy;
  - `docker compose exec db psql -U <usuario> -d <base> -c "\d turno"` muestra `turno_sin_solapamiento`;
  - `docker compose restart backend` arranca sin errores y sin duplicar el seed (`select count(*)` de `odontologo`, `prestacion` y `horario_atencion` = 1, 4 y 10).

  Anotar el resultado en el PR

## Workflow follow-up

- Abrir el PR `feat/c-03-entidades-base-y-seed` → `main` con el usuario de Git del Integrante 1. Pedir revisión explícita de `schema-extra.sql` y `GlobalExceptionHandler` (governance CRITICO). En la descripción, avisar a C-04 a C-11 de la firma de `NotificacionTurnos` y de la recomendación de usar `saveAndFlush` (design D9, D11).
- Después del merge, correr `/opsx:archive c-03-entidades-base-y-seed` y marcar C-03 `[x]` en `CHANGES.md` (GATE 3).
