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
- El orden de creación dependiente de `schema-extra.sql` es frágil: debe ser idempotente y tolerar que la tabla ya exista (corre en cada arranque); si el nombre de tabla o columna cambia, el script falla al arrancar. Como contiene un bloque `DO $$ ... $$`, hay que configurar `spring.sql.init.separator=^^^` (ver [08](08_arquitectura_propuesta.md#esquema-de-base-de-datos)).
- No apto para producción real; para la demo académica alcanza. Si el proyecto continuara, migrar a Flyway tomando `schema-extra.sql` como `V2`.

### DD-05 — No solapamiento garantizado por restricción de exclusión
**Decisión**: `EXCLUDE USING gist (tstzrange(inicio, fin) WITH &&) WHERE (estado IN ('PENDIENTE','CONFIRMADO'))`. El backend captura `DataIntegrityViolationException` / SQLSTATE `23P01` y responde 409 "ese horario se acaba de ocupar". No se incluye `odontologo_id` (un solo odontólogo), aunque se puede agregar.
**Contexto**: riesgo de concurrencia en la reserva pública.
**Alternativas**: validar solo en el service (carrera); bloqueo pesimista `SELECT ... FOR UPDATE` (no cubre filas inexistentes); `SERIALIZABLE`.
**Justificación**: es la única garantía real; el service solo da mensajes tempranos.
**Trade-offs aceptados**: depende de `btree_gist` y de una restricción fuera del mapeo JPA; las pruebas necesitan PostgreSQL real (Testcontainers), no H2.

### DD-06 — Recordatorios y alertas con `@Scheduled` y flags
**Decisión**: un job que corre cada 15 minutos (configurable con `APP_RECORDATORIO_INTERVALO_MIN`) y hace dos cosas: (1) selecciona turnos `PENDIENTE` (no `CONFIRMADO`) que empiezan en las próximas 48 h con `recordatorio_enviado = false`, envía el recordatorio al email del turno y marca; (2) selecciona turnos `PENDIENTE` que empiezan en menos de 24 h con `alerta_pendiente_enviada = false`, avisa por email al odontólogo y marca (DD-13). Condición simple del job: `PENDIENTE`, inicio a 48 h o menos y `recordatorio_enviado = false`. Un turno creado entre 24 h y 48 h recibe el Recordatorio sincrónicamente al crearse (RN-NOT-04) y queda con `recordatorio_enviado = true`; si ese envío falla, el job lo reintenta en el siguiente ciclo. Los creados con menos de 24 h nacen `CONFIRMADO` con `recordatorio_enviado = true` (RN-TUR-11) y no lo reciben. El link de Confirmar existe solo en este recordatorio y el endpoint público de confirmar rechaza con 422 si faltan más de 48 h (RN-NOT-08).
**Contexto**: sin presupuesto ni infraestructura de colas.
**Alternativas**: Quartz, cola (RabbitMQ), cron externo, scheduler por turno.
**Justificación**: mínimo código, estado en la base, sobrevive reinicios.
**¿Cada 15 minutos ejecuta código?** Sí: cada 15 minutos corren dos consultas baratas (los turnos que entran en la ventana de 48 h sin recordatorio, y los pendientes a menos de 24 h sin alerta), apoyadas en el índice `(estado, inicio)`. Si ninguna devuelve filas, el ciclo termina sin hacer nada más. Alcanza en v1 porque hay una sola instancia del backend (SU-05), pocos turnos y no se necesita precisión al minuto: un recordatorio de 48 h puede salir hasta 15 minutos tarde sin consecuencias.
**Trade-offs aceptados**: precisión de +-15 minutos (el intervalo configurado); una sola instancia del backend (con dos duplicaría envíos); sin reintentos con backoff (se reintenta en el siguiente ciclo).

### DD-07 — Email con Spring Mail: Mailpit en dev, Gmail SMTP en demo
**Decisión**: SMTP configurable solo por variables de entorno; emails al paciente (siempre al email del turno): Confirmación de reserva (solo Cancelar), Recordatorio (único con Confirmar), Aviso (sin botones, turnos que nacen `CONFIRMADO`), reprogramación (solo Cancelar) y cancelación por el odontólogo; más el mail "Turno no confirmado" al odontólogo por turnos pendientes a menos de 24 h.
**Contexto**: sin presupuesto; WhatsApp API y SMS descartados.
**Alternativas**: SendGrid/Mailgun (cuenta y verificación de dominio); envío real en dev.
**Justificación**: Mailpit permite ver mails sin enviar; Gmail con contraseña de aplicación funciona gratis.
**Trade-offs aceptados**: límites diarios de Gmail y posible spam; probar con Gmail antes de la demo. El envío es síncrono o `@Async` simple; sin cola.

### DD-08 — Token de turno opaco
**Decisión**: token generado por el backend con `SecureRandom` (32 bytes o más, Base64 URL-safe) o UUID v4 como mínimo; columna única; los links apuntan al front (`/turno/confirmar?token=...`), que llama a la API. El GET (`GET /api/public/turnos/{token}`) solo lee; las acciones son POST con botón explícito, porque los clientes de mail y antivirus abren los links solos y un GET que cambia estado confirmaría o cancelaría sin intervención del paciente. Qué es un token opaco: ver [08](08_arquitectura_propuesta.md#token-opaco).
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

### DD-12 — Email y teléfono en el turno, no en el paciente
**Decisión**: `Paciente` guarda solo nombre, apellido y DNI (único). `email_contacto`, `telefono_contacto` y `consentimiento_at` viven en `Turno`. Al reservar con un DNI existente no se modifica nada del paciente: el contacto ingresado queda en el turno y a ese email van los avisos del turno. En la carga manual el contacto se precarga desde el último turno del paciente y el odontólogo lo edita.
**Contexto**: la reserva es pública y sin verificación de email; si el contacto fuera del paciente, cualquiera con un DNI ajeno podría pisarlo.
**Alternativas**: contacto en el paciente actualizado en cada reserva (se pisan datos); contacto en ambos (duplicación).
**Justificación**: evita pisar datos de otra persona, los avisos van siempre a quien reservó ese turno y el consentimiento (Ley 25.326) queda asociado a cada reserva.
**Trade-offs aceptados**: el ABM de pacientes no muestra un contacto propio (solo el del último turno, como referencia); el contacto se repite entre turnos del mismo paciente.

### DD-13 — Alerta al odontólogo por pendientes a menos de 24 h
**Decisión**: los turnos `PENDIENTE` a menos de 24 h (a) se resaltan en la agenda con el teléfono del turno y botones Confirmar / Cancelar, y (b) el job de DD-06 le manda un email al odontólogo por turno (flag `alerta_pendiente_enviada`, se reinicia al reprogramar). El odontólogo se comunica por sus medios con el paciente y decide confirmar (`POST /api/turnos/{id}/confirmar`) o cancelar.
**Contexto**: a menos de 24 h el paciente ya no puede cancelar por el link y un turno sin confirmar es el que más probablemente termine en ausencia.
**Alternativas**: solo resaltar en la agenda (no avisa si el odontólogo no la mira); liberar automáticamente los pendientes (descartado, RN-TUR-05).
**Justificación**: el odontólogo decide con información, sin automatismos que liberen horarios.
**Trade-offs aceptados**: un email por turno (puede ser ruidoso si hay muchas reservas de último momento); el envío depende del mismo SMTP.

### DD-14 — Códigos HTTP unificados
**Decisión**: 400 formato, 401 no autenticado, 404 inexistente, 409 conflicto de horario (incluida la constraint EXCLUDE), 422 regla de negocio. Un solo código por caso; tabla en [08](08_arquitectura_propuesta.md#códigos-http-unificados).
**Contexto**: los criterios decían "400/422" y "409/422", imposibles de afirmar en un test.
**Alternativas**: dejar el código a criterio de cada implementador.
**Justificación**: los tests y el front reaccionan a un código concreto.
**Trade-offs aceptados**: algunos casos que suelen ser 409 en otras APIs (DNI duplicado, borrar una prestación con turnos) son 422 acá, porque 409 queda reservado para conflictos de horario.

## Supuestos inferidos

### SU-01 — Sin secretaria
**Suposición:** la v1 no tiene secretaria ni otros roles con login; el odontólogo hace todo.
**Origen**: `discovery.md` sección 2.
**Riesgo si es falso**: habría que agregar roles y RBAC.
**Cómo validar**: confirmar con el equipo/docente.

### SU-02 — Un odontólogo y un sillón
**Suposición:** hay un solo profesional y un solo sillón (RN-REC-01), por lo que dos turnos activos nunca pueden coexistir en el tiempo y la exclusión no necesita `odontologo_id`.
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
**Suposición:** el paciente prefiere reservar online antes que por WhatsApp, y el email alcanza como canal de recordatorio (se espera que reduzca las ausencias), aunque el canal habitual en Argentina es WhatsApp. Ninguna de las dos cosas está validada.
**Origen**: riesgos del `discovery.md` (supuestos sin probar).
**Riesgo si es falso**: baja efectividad contra las ausencias.
**Cómo validar**: fuera del alcance de la entrega; medir tras la demo.
