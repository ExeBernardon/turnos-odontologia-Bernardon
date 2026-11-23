# Decisiones y Supuestos

## Decisiones documentadas

### DD-01 — Front en TypeScript vanilla con Vite y nginx
**Decisión**: TypeScript sin framework; Vite como build y dev server; en Docker nginx sirve el build y hace proxy de `/api`. Dos áreas: pública (reserva, confirmar, cancelar) y privada (login, agenda, configuración, pacientes), como dos entry points HTML.
**Contexto**: stack obligatorio del proyecto académico; plazo de 3 días.
**Alternativas consideradas**: React/Vue (descartadas por consigna); Webpack (más lento de configurar); servir el front desde Spring.
**Justificación**: Vite da TS + HMR sin configuración; nginx con proxy deja el mismo origen y elimina CORS.
**Trade-offs aceptados**: sin framework la UI cuesta más y puede quedar inconsistente entre integrantes; mitigación: carpeta `shared/` con helpers de DOM y un router mínimo acordado desde el scaffold.

### DD-02 — Backend Java 21 + Spring Boot 3.5 + Maven en capas
**Decisión**: API REST con Spring Data JPA/Hibernate sobre PostgreSQL; capas controller, service, repository, DTOs y entidades; errores con `@ControllerAdvice` y JSON uniforme; organización por feature.
**Contexto**: stack obligatorio; equipo conoce Spring.
**Alternativas**: hexagonal/DDD (más ceremonia que valor en 3 días); Gradle.
**Justificación**: velocidad de entrega y familiaridad.
**Trade-offs aceptados**: lógica de dominio en services anémicos; aceptable para el tamaño.

### DD-03 — Auth con JWT, único usuario odontólogo
**Decisión**: JWT stateless en `Authorization: Bearer`, BCrypt, login con email y contraseña. Rutas públicas sin JWT. Odontólogo creado por seed vía variables de entorno.
**Contexto**: un solo usuario con login; sin secretaria.
**Alternativas**: sesión con cookie (necesita estado y CSRF); Spring Security OAuth/Keycloak (excesivo).
**Justificación**: simple, sin estado, adecuado a SPA.
**Trade-offs aceptados**: sin refresh token ni revocación; token en `localStorage` (expuesto a XSS, mitigado por no renderizar HTML no escapado); sin rate limit de login (SU-06).

### DD-04 — Esquema con Hibernate `ddl-auto=update` + `schema-extra.sql`, sin Flyway
**Decisión (del usuario)**: Hibernate genera y actualiza las tablas. Lo que Hibernate no genera (extensión `btree_gist` y la restricción EXCLUDE parcial) se agrega con un `schema-extra.sql` idempotente ejecutado después de Hibernate (`spring.jpa.defer-datasource-initialization=true`, `spring.sql.init.mode=always`).
**Contexto**: plazo de 3 días y 6 personas; nadie quiere escribir ni coordinar migraciones.
**Alternativas consideradas**: Flyway con migraciones versionadas (esquema explícito y reproducible).
**Justificación**: cero fricción para evolucionar entidades en paralelo; velocidad.
**Trade-offs aceptados contra Flyway**:
- `update` nunca borra ni renombra columnas ni cambia tipos de forma segura: un rename deja una columna huérfana; los cambios destructivos se hacen a mano o reseteando el volumen de Docker.
- El esquema no está versionado ni es auditable; dos ramas con entidades distintas pueden dejar bases divergentes.
- El orden de creación dependiente de `schema-extra.sql` es frágil: debe ser idempotente y tolerar que la tabla ya exista; si el nombre de tabla o columna cambia, el script falla al arrancar.
- No apto para producción real; para la demo académica alcanza. Si el proyecto continuara, migrar a Flyway tomando `schema-extra.sql` como `V2`.

### DD-05 — No solapamiento garantizado por restricción de exclusión
**Decisión**: `EXCLUDE USING gist (tstzrange(inicio, fin) WITH &&) WHERE (estado IN ('PENDIENTE','CONFIRMADO'))`. El backend captura `DataIntegrityViolationException` / SQLSTATE `23P01` y responde 409 "ese horario se acaba de ocupar". No se incluye `odontologo_id` (un solo odontólogo), aunque se puede agregar.
**Contexto**: riesgo de concurrencia en la reserva pública.
**Alternativas**: validar solo en el service (carrera); bloqueo pesimista `SELECT ... FOR UPDATE` (no cubre filas inexistentes); `SERIALIZABLE`.
**Justificación**: es la única garantía real; el service solo da mensajes tempranos.
**Trade-offs aceptados**: depende de `btree_gist` y de una restricción fuera del mapeo JPA; las pruebas necesitan PostgreSQL real (Testcontainers), no H2.

### DD-06 — Recordatorios con `@Scheduled` y flag
**Decisión**: job cada X minutos (por defecto 15) que selecciona turnos `PENDIENTE`/`CONFIRMADO` que empiezan en las próximas 48 h y `recordatorio_enviado = false`; envía y marca.
**Contexto**: sin presupuesto ni infraestructura de colas.
**Alternativas**: Quartz, cola (RabbitMQ), cron externo, scheduler por turno.
**Justificación**: mínimo código, estado en la base, sobrevive reinicios.
**Trade-offs aceptados**: precisión de +-X minutos; una sola instancia del backend (con dos duplicaría envíos); sin reintentos con backoff.

### DD-07 — Email con Spring Mail: Mailpit en dev, Gmail SMTP en demo
**Decisión**: SMTP configurable solo por variables de entorno; emails de confirmación, recordatorio y aviso de cancelación por el consultorio.
**Contexto**: sin presupuesto; WhatsApp API y SMS descartados.
**Alternativas**: SendGrid/Mailgun (cuenta y verificación de dominio); envío real en dev.
**Justificación**: Mailpit permite ver mails sin enviar; Gmail con contraseña de aplicación funciona gratis.
**Trade-offs aceptados**: límites diarios de Gmail y posible spam; probar con Gmail antes de la demo. El envío es síncrono o `@Async` simple; sin cola.

### DD-08 — Token de turno opaco
**Decisión**: token generado por el backend con `SecureRandom` (32 bytes o más, Base64 URL-safe) o UUID v4 como mínimo; columna única; los links apuntan al front (`/turno/confirmar?token=...`), que llama a la API. El GET solo lee; las acciones son POST con botón explícito.
**Contexto**: confirmar y cancelar sin login, sin que se pueda adivinar un turno ajeno.
**Alternativas**: ID secuencial (adivinable); JWT firmado por turno (más complejo, no revocable).
**Justificación**: simple, no adivinable, revocable al cambiar estado.
**Trade-offs aceptados**: quien tenga el link puede actuar sobre ese turno; el token no vence salvo por el estado del turno.

### DD-09 — Docker Compose con `db`, `backend`, `frontend`, `mailpit`
**Decisión**: ver [11](11_docker_y_entornos.md). Builds multi-stage; variables en `.env` con `.env.example` versionado.
**Contexto**: la entrega es "todo corriendo en Docker".
**Alternativas**: instalar todo local; PaaS (sin presupuesto).
**Justificación**: un comando levanta todo, mismo entorno para 6 personas.
**Trade-offs aceptados**: builds más lentos; el desarrollo diario puede hacerse con Vite y Spring fuera de Docker usando solo `db` y `mailpit`.

### DD-10 — Zona horaria única y `timestamptz`
**Decisión**: instantes en `timestamptz`; lógica y presentación en `America/Argentina/Buenos_Aires`; la API usa ISO-8601 con offset.
**Contexto**: horarios de atención son locales; Argentina no tiene horario de verano.
**Alternativas**: `timestamp` sin zona (ambiguo); UTC en pantalla.
**Justificación**: evita errores de desfase entre JVM, base y navegador.
**Trade-offs aceptados**: un solo huso soportado.

### DD-11 — Trabajo en paralelo: contrato de API primero
**Decisión**: el primer change del roadmap es el contrato de API (OpenAPI o markdown versionado); se mergea a `main` antes de ramificar. 6 personas, cada una con su agente, en ramas de GitHub.
**Contexto**: sin contrato, 6 agentes inventan endpoints distintos.
**Alternativas**: contrato emergente; front y back secuenciales.
**Justificación**: desbloquea el paralelismo front/back.
**Trade-offs aceptados**: consume las primeras horas del plazo; cambios posteriores al contrato requieren un change explícito.

## Supuestos inferidos

### SU-01 — Sin secretaria
**Suposición:** la v1 no tiene secretaria ni otros roles con login; el odontólogo hace todo.
**Origen**: `discovery.md` sección 2.
**Riesgo si es falso**: habría que agregar roles y RBAC.
**Cómo validar**: confirmar con el equipo/docente.

### SU-02 — Un odontólogo y un sillón
**Suposición:** hay un solo profesional y un solo sillón, por lo que dos turnos activos nunca pueden coexistir en el tiempo y la exclusión no necesita `odontologo_id`.
**Origen**: `discovery.md` sección 2.
**Riesgo si es falso**: la restricción de exclusión y el modelo deben incluir recurso/profesional.
**Cómo validar**: confirmar con el equipo.

### SU-03 — Presentación local con Docker
**Suposición:** la demo se hace corriendo en local con Docker Compose, sin despliegue en la nube.
**Origen**: pregunta abierta del discovery; supuesto operativo.
**Riesgo si es falso**: habría que desplegar con HTTPS y dominio, y los links de email apuntarían a otra URL.
**Cómo validar**: preguntar a la cátedra (ver [10](10_preguntas_abiertas.md)).

### SU-04 — Paso de grilla de 15 minutos
**Suposición:** los horarios se ofrecen cada 15 min y las duraciones de prestación son múltiplos de 15.
**Origen**: decisión técnica para el cálculo de disponibilidad.
**Riesgo si es falso**: huecos inutilizables o prestaciones que no encajan.
**Cómo validar**: confirmar con el odontólogo; el paso es configurable (`APP_AGENDA_PASO_MINUTOS`).

### SU-05 — Una sola instancia del backend
**Suposición:** corre un solo contenedor `backend`, por lo que el job de recordatorios no se duplica.
**Origen**: entorno de demo.
**Riesgo si es falso**: recordatorios duplicados.
**Cómo validar**: Compose define una sola réplica.

### SU-06 — Sin protección contra fuerza bruta ni abuso de la reserva pública
**Suposición:** en una demo académica no hace falta rate limit, captcha ni verificación de email del paciente.
**Origen**: prioridad velocidad de entrega.
**Riesgo si es falso**: spam de reservas ocupando la agenda.
**Cómo validar**: revisar tras la demo; el odontólogo puede cancelar turnos falsos.

### SU-07 — Los pacientes usan email
**Suposición:** el paciente prefiere reservar online y el email alcanza como canal de recordatorio, aunque el canal habitual en Argentina es WhatsApp.
**Origen**: riesgos del `discovery.md` (supuestos sin probar).
**Riesgo si es falso**: baja efectividad contra las ausencias.
**Cómo validar**: fuera del alcance de la entrega; medir tras la demo.
