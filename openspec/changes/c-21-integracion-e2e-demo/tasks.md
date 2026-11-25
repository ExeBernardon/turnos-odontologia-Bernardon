# Tasks

## 1. Safety net

- [x] 1.1 Baseline backend: `cd backend && ./mvnw verify` (Docker corriendo para Testcontainers); registrar la cantidad de tests verdes; si alguno falla, reportarlo como falla preexistente y no seguir
- [x] 1.2 Baseline frontend: `cd frontend && npm test && npm run lint && npm run typecheck && npm run build`; registrar la cantidad de tests verdes; si algo falla, reportarlo como preexistente y no seguir

## 2. Fixes de notificaciones (área de Agus, C-11)

- [x] 2.1 RED → GREEN: en `notificacion/TipoMailTest.java`, el asunto de `AVISO_TURNO_CONFIRMADO` es exactamente "Tu turno quedó reservado"; corregir `notificacion/TipoMail.java`; verificar con `./mvnw test -Dtest=TipoMailTest`
- [x] 2.2 RED: en `notificacion/PlantillasMailTest.java`, render de `TURNO_NO_CONFIRMADO` con horas de alerta 24 → el cuerpo contiene "24 horas"; con 12 → contiene "12 horas" y no "24 horas" (falla porque la plantilla fija 24)
- [x] 2.3 GREEN: `ModeloMail.de` recibe las horas de la alerta y agrega `horasAlerta` al modelo de `TURNO_NO_CONFIRMADO`; `templates/email/turno-no-confirmado.html` la muestra con `th:text`; `NotificacionTurnosEmail` pasa `props.alerta().pendienteHoras()`; ajustar los call sites de tests de `ModeloMail.de`; verificar con `./mvnw test -Dtest=PlantillasMailTest`
- [x] 2.4 RED → GREEN: en `notificacion/TipoMailTest.java` (o `EmailServiceGreenMailTest.java`), el asunto final de `TURNO_NO_CONFIRMADO` con `horasAlerta` 24 es "Turno sin confirmar a menos de 24 h" y con 12 es "... 12 h"; los asuntos fijos no cambian; implementar en `TipoMail` un método de asunto a partir del modelo y usarlo en `EmailService.enviar` (design, Decisión 3); verificar con `./mvnw test -Dtest='TipoMailTest,EmailServiceGreenMailTest,NotificacionTurnosEmailGreenMailTest,NotificacionTurnosEmailTest'` (ajustar las aserciones de asunto que comparaban con `asunto()`)
- [x] 2.5 REFACTOR y verificación del área: `./mvnw test -Dtest='ar.turnos.odontologia.notificacion.**'` en verde; `TipoMailTest.cadaTipoTieneAsuntoYPlantillaPropios` sigue verde

## 3. Umbrales configurables en `EstadoInicialTurno` (área de Cande Salinas, C-07 — avisar en el PR, regla 16)

- [x] 3.1 RED: en `turno/EstadoInicialTurnoTest.java`, nuevos casos con umbrales explícitos (confirmación 12 h, recordatorio 72 h): un turno a 20 h queda `PENDIENTE` con flags en `false`; a 11 h 59 min queda `CONFIRMADO`; a 60 h está en la franja de recordatorio y no supera el umbral de 72 h; bordes exactos de 12 h y 72 h; los tests existentes (24/48) no se tocan
- [x] 3.2 GREEN: introducir los umbrales como valor construido desde `AppProperties` (`alerta().pendienteHoras()`, `recordatorio().horasAntes()`); las firmas de dos argumentos delegan en 24/48 por defecto (design, Decisión 4); verificar con `./mvnw test -Dtest=EstadoInicialTurnoTest`
- [x] 3.3 RED → GREEN: en `turno/TurnoServiceTest.java`, con `AppProperties` de alerta 12 h, una reserva a 20 h nace `PENDIENTE` y pide el recordatorio; con recordatorio 72 h, una reserva a 60 h pide el recordatorio y no la confirmación de reserva; `TurnoService` usa los umbrales de `AppProperties` en alta y reprogramación; verificar con `./mvnw test -Dtest=TurnoServiceTest`
- [x] 3.4 Verificar que los tests existentes del área siguen verdes sin modificarse: `./mvnw test -Dtest='ar.turnos.odontologia.turno.**'`

## 4. Texto del resumen de la reserva (área de Milagros, C-17 — avisar en el PR, regla 16)

- [x] 4.1 RED: en `frontend/src/public/reservar.test.ts`, con el ejemplo 201 (`PENDIENTE`) el resumen menciona los links para gestionarlo; con la variante `estado: CONFIRMADO` pide revisar el email por los datos del turno, no contiene "links" y mantiene "comunicate con el consultorio"
- [x] 4.2 GREEN: en `frontend/src/public/reservar.ts` (`mostrarResumen`) elegir el texto por `resumen.estado` (opcionalmente extraído a una función pura de `reservar.logica.ts` con su test en `reservar.logica.test.ts`); verificar con `npx vitest run src/public/reservar` y `npm run typecheck`

## 5. Verificación automatizada completa

- [x] 5.1 `cd backend && ./mvnw verify` en verde; registrar el total de tests contra el baseline de 1.1
- [x] 5.2 `cd frontend && npm test && npm run lint && npm run typecheck && npm run build` en verde; registrar el total contra el baseline de 1.2
- [x] 5.3 `cd docs/api/scripts && npm run verificar` en verde (el contrato no cambió) y `git diff --stat main -- docs/api` vacío

## 6. Arranque desde cero y recorrido de los 6 flujos (agente en esta máquina, datos ficticios)

- [x] 6.1 `docker compose down -v && docker compose up --build -d`; esperar `http://localhost:8081/actuator/health` en `UP`; verificar el seed con `GET /api/public/prestaciones` (4 prestaciones activas) y disponibilidad en un día con horario de atención
- [x] 6.2 Flujo 2 (reserva pública): en el navegador integrado reservar en `http://localhost:8080/reservar` con datos ficticios (DNI `99999999`, email `paciente@ejemplo.com` con Mailpit o `<cuenta-demo>+ana@gmail.com` con Gmail) tres turnos: a más de 48 h, entre 24 y 48 h y a menos de 24 h; verificar 201, el texto del resumen según el estado (grupo 4) y los tres mails correctos (Confirmación con solo Cancelar, Recordatorio con Confirmar y Cancelar, Aviso "Tu turno quedó reservado" sin botones)
- [x] 6.3 Carrera: dos `POST /api/public/turnos` casi simultáneos (`curl` en paralelo) al mismo horario libre; verificar un 201 y un 409 y un solo turno activo
- [x] 6.4 Flujo 3 (token): abrir los links de los mails; abrir el link no cambia el estado; confirmar a más de 48 h → 422 con mensaje; confirmar entre 24 y 48 h → Confirmado; cancelar un `PENDIENTE` a 24 h o más → Cancelado; cancelar un `CONFIRMADO` → mensaje de contactar al consultorio; link con token inválido → "Link inválido"
- [x] 6.5 Flujo 4 (recordatorio y alerta): con `APP_RECORDATORIO_INTERVALO_MIN=1` en `.env` y `docker compose up -d backend`, verificar que un turno `PENDIENTE` sin recordatorio dentro de la ventana lo recibe y que un `PENDIENTE` a menos del umbral dispara "Turno sin confirmar a menos de 24 h" al email del odontólogo (alias `+odontologo` con Gmail); volver el intervalo a su valor por defecto al terminar
- [x] 6.6 Flujo 1 (login) — requiere persona: la persona inicia sesión en `http://localhost:8080/admin/` con la contraseña del seed (solo está en `.env`; el agente no la lee ni la escribe); verificar también que una contraseña incorrecta da el mensaje genérico
- [x] 6.7 Flujos 5 y 6 (bloqueo y agenda), con la sesión iniciada por la persona: crear un bloqueo sobre un turno existente, ver la vista previa de afectados, confirmar y verificar el mail de cancelación por bloqueo; en la agenda, ver día y semana, cargar un turno manual, reprogramar (a 24 h o más y a menos de 24 h), confirmar, cancelar con mail y marcar asistencia/ausencia de un turno pasado
- [x] 6.8 Cada defecto encontrado en 6.1–6.7 se agrega como tarea nueva en este archivo con RED (test que lo reproduce) → GREEN, y se avisa al dueño del área si no es Agus (regla 16); si no hay defectos, dejar constancia en el resumen del apply
- [x] 6.8a Defecto hallado en 6.3: con tres o más altas simultáneas del mismo horario PostgreSQL aborta alguna por deadlock (SQLSTATE 40P01) y la API respondía 500 `ERROR_INTERNO`; RED en `common/ViolacionDeSolapamientoTest` (deadlock = carrera perdida) → GREEN en `common/ViolacionDeSolapamiento.java` (409 `HORARIO_OCUPADO`); verificado con 5 `POST` paralelos: un 201 y cuatro 409 (archivo de Int 1, regla 16)
- [x] 6.8b Defecto hallado en 8.1: el log de "Error inesperado" imprimía la ruta completa, con el token de `/api/public/turnos/{token}/...`; RED en `common/GlobalExceptionHandlerTest` → GREEN: `GlobalExceptionHandler` registra la ruta con `{token}` (regla 8)
- [x] 8.1a Hallazgo Ley 25.326: dos reservas simultáneas del mismo DNI nuevo violan la unicidad de `paciente.dni` y el detalle de PostgreSQL (`Key (dni)=(...)`) llegaba a los logs (Hibernate `SqlExceptionHelper` y el stack del handler); RED en `GlobalExceptionHandlerTest` (log sin el valor, con el SQLSTATE) y `config/LoggingSinDatosPersonalesTest` → GREEN: el handler loguea solo clase y SQLSTATE de las fallas de base y `application.yml` apaga `SqlExceptionHelper`

## 7. README final y checklist de aceptación

- [x] 7.1 Reescribir `README.md`: requisitos, comando único desde cero (`cp .env.example .env`, `docker compose down -v`, `docker compose up --build`), tabla de URLs, credenciales del seed (email ficticio por defecto `odontologo@ejemplo.com`; contraseña: "la de `SEED_ODONTOLOGO_PASSWORD` en tu `.env`"), tests, desarrollo fuera de Docker; verificar que no contiene secretos ni emails reales (`grep -nE "@gmail.com|PASSWORD=" README.md` solo devuelve marcadores)
- [x] 7.2 Sección "Demo con Gmail": variables a cambiar en `.env`, cuenta de demo como `<cuenta-demo>@gmail.com`, alias `+` para pacientes y `SEED_ODONTOLOGO_EMAIL=<cuenta-demo>+odontologo@gmail.com`, necesidad de `docker compose down -v` tras cambiar el seed, `APP_FRONTEND_BASE_URL=http://localhost:8080` y que los links solo abren en la notebook de la demo
- [x] 7.3 Sección "Guion de demo": recorrido paso a paso de los 6 flujos de KB 07 en el orden de presentación, con los datos ficticios a usar y el tip de `APP_RECORDATORIO_INTERVALO_MIN` bajo para mostrar recordatorio y alerta
- [x] 7.4 Sección "Resolución de problemas": SMTP inalcanzable → cada acción que manda mail tarda ~5 s (timeout) y el turno se crea igual, revisar `SPRING_MAIL_*`; con Gmail la reserva tarda unos segundos (envío sincrónico); revisar spam y el límite diario; puertos ocupados → variables `*_PORT`; seed que no cambia → `down -v`
- [x] 7.5 Sección "Checklist de aceptación del MVP": US-001 a US-016, US-021 y US-022 marcadas `[x]` con una línea de evidencia cada una (flujo o test que la cubre) a partir del grupo 6; una US sin evidencia queda `[ ]` y se reporta como bloqueante

## 8. Pasada de Ley 25.326

- [x] 8.1 Logs: `grep -rnE "log\.(trace|debug|info|warn|error)" backend/src/main/java` y revisar que ninguno incluya DNI, email, teléfono ni token; `docker compose logs backend` después del grupo 6 sin DNI `99999999`, emails de prueba, teléfonos ni tokens completos; cada hallazgo se corrige con su test
- [x] 8.2 Secretos: `git ls-files | grep -i "\.env"` devuelve solo `.env.example`; `git check-ignore .env` lo confirma; `.env.example` sin contraseñas reales ni la cuenta de demo
- [x] 8.3 Respuestas públicas: revisar con `curl` `GET /api/public/prestaciones`, `GET /api/public/disponibilidad`, `POST /api/public/turnos` (201) y `GET /api/public/turnos/{token}`: ninguna trae DNI, apellido, email, teléfono ni token, coherente con `docs/api/openapi.yaml`

## 9. Tareas solo humanas

- [ ] 9.1 Arranque desde cero (`docker compose down -v && docker compose up --build`) en la máquina de un segundo integrante distinto, siguiendo solo el README; anotar quién y el resultado
- [x] 9.2 Completar `.env` de la notebook de la demo con la cuenta de Gmail de demo y su contraseña de aplicación (nunca en git), hacer una reserva de prueba y marcar el remitente como "no es spam"
- [ ] 9.3 Ensayo completo de la demo con el guion del README en la notebook de la presentación, con Gmail; medir tiempos y tener Mailpit como plan B
- [ ] 9.4 Revisión del PR por Cande Salinas (cambios en `turno/EstadoInicialTurno.java` y `TurnoService.java`) y por Milagros (cambio en `public/reservar.ts`), según la regla 16

## Workflow follow-up

- Abrir el PR de `agus/c-21-integracion-e2e-demo` a `main` mencionando los archivos de otros integrantes tocados.
- Archivar el change con `/opsx:archive c-21-integracion-e2e-demo` y marcar C-21 como `[x]` en `CHANGES.md`.
