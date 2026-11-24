# Arquitectura Propuesta

## Patrones aplicados

| Patrón | Dónde se usa | Por qué |
|--------|--------------|---------|
| Arquitectura en capas | Backend: controller, service, repository | Simple, conocida por todo el equipo, rápida de entregar |
| DTO + mapper manual | Frontera controller/service | No exponer entidades JPA ni el `token`/`password_hash` por error |
| Repository (Spring Data JPA) | Acceso a datos | Menos código; consultas por rango con `@Query` |
| `@ControllerAdvice` | Manejo global de errores | JSON de error uniforme (RN-GLO-01) |
| Restricción en base (EXCLUDE gist) | Anti-solapamiento | Única forma correcta ante concurrencia (RN-TUR-02) |
| Función pura de disponibilidad | `DisponibilidadService` | Núcleo testeable sin base ni reloj real (reloj inyectable `Clock`) |
| Job programado con flags | Recordatorios al paciente y alertas al odontólogo | Idempotencia sin cola de mensajes (`recordatorio_enviado`, `alerta_pendiente_enviada`) |
| Token opaco por recurso | Confirmar/cancelar | Autorización sin cuenta (RN-TOK), ver [Token opaco](#token-opaco) |
| Proxy mismo origen | nginx / Vite | Sin CORS |

## Estructura de directorios (monorepo)

```
turnos-odontologia-Bernardon/
├── docker-compose.yml
├── .env.example                  # versionado; .env va en .gitignore
├── backend/
│   ├── Dockerfile                # multi-stage: Maven -> JRE 21
│   ├── pom.xml
│   └── src/
│       ├── main/java/ar/turnos/odontologia/   # paquete base propuesto
│       │   ├── TurnosApplication.java
│       │   ├── config/           # SecurityConfig, ClockConfig, SchedulingConfig, propiedades (@ConfigurationProperties)
│       │   ├── security/         # JwtService, JwtAuthFilter, UserDetailsService
│       │   ├── common/           # ApiError, GlobalExceptionHandler, excepciones de dominio
│       │   ├── auth/             # AuthController, LoginRequest/Response
│       │   ├── prestacion/       # Controller, Service, Repository, Entity, DTOs
│       │   ├── horario/          # HorarioAtencion (idem)
│       │   ├── bloqueo/          # Bloqueo + vista previa y cancelación de afectados
│       │   ├── paciente/         # Paciente (idem)
│       │   ├── turno/            # Turno, EstadoTurno, TurnoService, TurnoController, PublicTurnoController
│       │   ├── disponibilidad/   # DisponibilidadService (cálculo de slots), controller público
│       │   ├── notificacion/     # EmailService, plantillas, RecordatorioJob (recordatorios y alertas)
│       │   └── seed/             # DataSeeder (CommandLineRunner)
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── schema-extra.sql  # btree_gist + EXCLUDE (idempotente)
│       │   └── templates/        # plantillas de email: Confirmación de reserva (solo cancelar), Aviso (sin botones), Recordatorio (confirmar y cancelar), reprogramación (solo cancelar), cancelación, bloqueo y Turno no confirmado (al odontólogo); ver "Mails del sistema" en 05
│       └── test/java/...         # tests de disponibilidad, solapamiento, reglas de cancelación
└── frontend/
    ├── Dockerfile                # multi-stage: Vite build -> nginx
    ├── nginx.conf                # sirve /, proxy /api -> backend:8080, fallback SPA
    ├── vite.config.ts            # proxy /api en dev
    ├── index.html                # área pública
    ├── admin/index.html          # área privada (segundo entry point)
    └── src/
        ├── api/                  # cliente fetch tipado (un archivo por recurso) + tipos del contrato
        ├── public/               # reservar.ts, confirmar.ts, cancelar.ts
        ├── admin/                # login.ts, agenda.ts, configuracion.ts, pacientes.ts
        ├── shared/               # router mínimo, componentes DOM, formato de fechas, estado de auth
        └── styles/
```

El paquete Java base es una propuesta; el change de scaffold lo fija. Organización **por feature** dentro del backend (cada carpeta contiene sus capas) para que 6 personas toquen carpetas distintas y minimicen conflictos de merge.

## Cálculo de horarios libres

Los "horarios libres" **no son una entidad ni una tabla**: no se guardan en ningún lado. Cada consulta a `GET /api/public/disponibilidad` (público) o `GET /api/disponibilidad` (odontólogo) los calcula al vuelo así: horarios de atención − bloqueos − turnos activos − pasado − ventana de reserva (mínimo 2 h y máximo 60 días). Por eso el "Read" de horarios libres de la matriz RBAC ([03](03_actores_y_roles.md)) es una consulta calculada, no un CRUD sobre una tabla.

Entrada: `prestacionId`, `fecha` (o rango de fechas), en zona `America/Argentina/Buenos_Aires`. Salida: lista de instantes de inicio.

1. Obtener la duración `d` de la prestación (activa).
2. Para cada día, tomar los `HorarioAtencion` del día de la semana → rangos `[a, b)` convertidos a instantes con la zona.
3. Restar los `Bloqueo` que intersecan cada rango → quedan sub-rangos libres.
4. Restar los turnos activos (`PENDIENTE`, `CONFIRMADO`) cuyo `[inicio, fin)` interseca → sub-rangos libres.
5. Discretizar: dentro de cada sub-rango libre `[x, y)`, generar candidatos `x, x+paso, x+2·paso…` tales que `candidato + d <= y`. Paso configurable `app.agenda.paso-minutos` (por defecto 15). Alinear los candidatos a la grilla del paso desde la hora de inicio del rango de atención (no desde el fin de un turno anterior) para que la grilla sea estable.
6. Descartar candidatos con `inicio <= ahora` y, en la reserva pública, los que no cumplen la ventana: al menos 2 h de anticipación (`app.turno.anticipacion-min-horas`, por defecto 2) y hasta 60 días hacia adelante (`app.turno.ventana-max-dias`, por defecto 60). Cuando consulta el odontólogo rige solo "no en el pasado" (RN-DIS-06).

El mismo cálculo se invoca al reservar, cargar o reprogramar para validar horario de atención, bloqueos, pasado y ventana (422 si el horario no es válido, RN-DIS-05). La restricción de la base solo cubre el solapamiento entre turnos: es la garantía final contra concurrencia (409). Se implementa como lógica pura sobre listas de intervalos, con `Clock` inyectado.

## Seguridad

- **Autenticación**: JWT firmado (HS256 con secreto de variable de entorno), expiración configurable (por defecto 8 h). Login con email y contraseña; BCrypt. Token en `Authorization: Bearer`. Sin refresh token en v1. Sin sesión en servidor (stateless).
- **Autorización**: `SecurityFilterChain`: `permitAll` para `/api/auth/login` y `/api/public/**`; `authenticated` para el resto de `/api/**`. Un único rol (odontólogo).
- **Token de turno**: opaco, `SecureRandom`, 32 bytes en Base64 URL-safe (o UUID v4 como mínimo). Columna única. Se compara solo por igualdad en la consulta; no se loguea completo. Ver [Token opaco](#token-opaco).
- **Validación de input**: Bean Validation (`@Valid`) en DTOs; normalización de DNI; el backend recalcula siempre `fin` y la disponibilidad (nunca confía en el front).
- **Respuestas públicas mínimas**: la consulta por token devuelve solo prestación, fecha, estado y nombre de pila; nunca DNI, email ni teléfono.
- **Fuerza bruta de login**: sin rate limit en v1 (riesgo asumido, ver SU-06).
- **Secrets**: todo por variables de entorno (`.env` fuera de git; `.env.example` versionado). Contraseña de aplicación de Gmail nunca en el repo.
- **CORS**: no necesario (mismo origen) en Docker y en dev (proxy Vite).
- **Datos personales**: solo los mínimos (RN-DAT-01); no loguear DNI/email/teléfono en claro. Consentimiento explícito en la reserva pública (RN-DAT-02).

### Token opaco

Un token **opaco** es un string aleatorio largo (por ejemplo un UUID v4 o 32 bytes de `SecureRandom` en Base64 URL-safe) que se genera al crear el turno y se guarda en la columna única `turno.token`. No contiene ningún dato (ni el id del turno, ni el DNI, ni la fecha) ni está firmado: no hay nada que decodificar. Cuando llega un request con el token, el servidor lo busca en la tabla y, si existe, opera sobre ese turno. Como es aleatorio y largo (2^128 o más posibilidades) no se puede adivinar ni derivar de otro turno, a diferencia de un id secuencial. Si el token no existe, 404. Es la alternativa simple a un JWT por turno: se revoca solo, porque la autorización depende del estado del turno en la base.

## Zona horaria

- Persistencia: `timestamptz` (UTC internamente); JVM y Hibernate con `UTC`.
- Negocio: `ZoneId` `America/Argentina/Buenos_Aires` (propiedad `app.zona-horaria`) para convertir horarios de atención (hora local) a instantes y para definir "día".
- API: ISO-8601 con offset (`2026-11-25T10:00:00-03:00`). Las fechas "día" (query de agenda) son `YYYY-MM-DD` interpretadas en Buenos Aires.
- Front: formatea con `Intl.DateTimeFormat('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' })`.
- Emails: se renderizan en hora de Buenos Aires.
- Contenedores: `TZ=America/Argentina/Buenos_Aires` solo informativo; la lógica no depende del TZ del sistema.

## Esquema de base de datos

- `spring.jpa.hibernate.ddl-auto=update`; sin Flyway (DD-04).
- `spring.jpa.defer-datasource-initialization=true` y `spring.sql.init.mode=always` para que `schema-extra.sql` corra después de que Hibernate cree las tablas.
- `spring.sql.init.separator=^^^`: Spring parte el script por `;`, lo que rompe el bloque `DO $$ ... $$` (que tiene `;` adentro). Con un separador propio, cada sentencia del script termina en `^^^` en vez de `;`.
- `schema-extra.sql` se ejecuta **en cada arranque**, pero es **idempotente**: reiniciar el sistema no rompe ni duplica nada.

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist
^^^
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'turno_sin_solapamiento') THEN
    ALTER TABLE turno ADD CONSTRAINT turno_sin_solapamiento
      EXCLUDE USING gist (tstzrange(inicio, fin) WITH &&)
      WHERE (estado IN ('PENDIENTE','CONFIRMADO'));
  END IF;
END $$
^^^
```

  - `CREATE EXTENSION IF NOT EXISTS`: si la extensión ya está, no hace nada.
  - La constraint se crea solo si no existe: el bloque `DO` consulta `pg_constraint` por nombre, y si ya está no ejecuta el `ALTER TABLE` (un `ADD CONSTRAINT` repetido fallaría). Primer arranque: crea todo. Arranques siguientes: no hace nada.
  - Los datos no se tocan: el volumen `pgdata` persiste y el seeder también es idempotente (solo crea lo que falta).

### Qué es `btree_gist`

Es una extensión de PostgreSQL que permite usar índices GiST sobre tipos que normalmente usan B-tree (enteros, texto, fechas) y, sobre todo, **combinar igualdad y rangos en una misma restricción** `EXCLUDE USING gist`. Sin ella, un `EXCLUDE` que mezcla un operador de igualdad (`=`) con uno de solapamiento de rangos (`&&`) no se puede crear. En este proyecto la restricción `EXCLUDE USING gist (tstzrange(inicio, fin) WITH &&)` es la que impide que existan dos turnos activos con rangos `[inicio, fin)` que se pisan, aunque dos pacientes reserven a la vez. Hoy esa restricción solo usa `&&` sobre un `tstzrange` (que PostgreSQL indexa con GiST de forma nativa), pero se activa `btree_gist` para poder sumar columnas con igualdad si se agregara más adelante `odontologo_id` o un sillón (por ejemplo `odontologo_id WITH =, tstzrange(...) WITH &&`).

- Captura: `DataIntegrityViolationException` cuya causa raíz es `PSQLException` con `SQLState = 23P01` (y nombre de constraint `turno_sin_solapamiento`) se traduce a 409.
- La constraint solo cubre solapamiento entre turnos. Horario de atención, bloqueos, pasado y ventana los valida el backend (RN-DIS-05).
- El usuario de la base necesita permiso para `CREATE EXTENSION` (en la imagen `postgres:16` el usuario por defecto es superusuario; `btree_gist` es "trusted" desde PG 13).

## Códigos HTTP unificados

Un único código por tipo de problema; no hay respuestas ambiguas del estilo "400/422" o "409/422" (RN-GLO-04). Los tests afirman un código concreto.

| Código | Cuándo | Ejemplos |
|--------|--------|----------|
| 400 | Request mal formado o validación de formato | JSON inválido, campo faltante, DNI/email/teléfono con formato inválido, parámetro de fecha ilegible |
| 401 | No autenticado | Sin token o token vencido en ruta privada, credenciales inválidas en el login |
| 404 | Recurso o token inexistente | Turno, paciente o prestación inexistente, token de turno inexistente |
| 409 | Conflicto de horario | Solapamiento con otro turno, incluida la violación de la constraint `EXCLUDE` (`23P01`), bloqueo sobre turnos activos sin `cancelarTurnosAfectados` |
| 422 | Violación de una regla de negocio | Rango horario inválido (`inicio >= fin`), horarios de atención solapados, fuera del horario de atención, sobre un bloqueo, en el pasado, fuera de la ventana de 2 h a 60 días, cancelación del paciente con menos de 24 h, transición de estado inválida (incluye marcar asistencia antes de la hora de inicio), sin consentimiento, prestación inactiva, DNI duplicado, borrar una prestación con turnos |

## Formato de error uniforme

```json
{ "timestamp": "2026-11-25T10:00:00-03:00", "status": 409, "error": "CONFLICT",
  "codigo": "HORARIO_OCUPADO", "mensaje": "Ese horario se acaba de ocupar, elegí otro.",
  "path": "/api/public/turnos", "detalles": [ { "campo": "dni", "mensaje": "..." } ] }
```

`detalles` solo en 400. Códigos de negocio estables (`HORARIO_OCUPADO`, `HORARIO_NO_DISPONIBLE`, `CANCELACION_FUERA_DE_PLAZO`, `CONSENTIMIENTO_REQUERIDO`, `TOKEN_INVALIDO`, `TRANSICION_INVALIDA`, `DNI_DUPLICADO`, `CREDENCIALES_INVALIDAS`) para que el front reaccione sin parsear texto. El change de contrato fija la lista final.

## Variables de entorno

| Variable | Descripción | Ejemplo | Sensible |
|----------|-------------|---------|----------|
| `POSTGRES_DB` | Nombre de la base | `turnos` | N |
| `POSTGRES_USER` | Usuario de la base | `turnos` | N |
| `POSTGRES_PASSWORD` | Contraseña de la base | `cambiar-esto` | Y |
| `SPRING_DATASOURCE_URL` | JDBC (en Compose apunta a `db`) | `jdbc:postgresql://db:5432/turnos` | N |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | Credenciales para el backend | `turnos` / `...` | Y (password) |
| `JWT_SECRET` | Secreto de firma (>= 32 bytes) | `cadena-larga-aleatoria` | Y |
| `JWT_EXPIRATION_MINUTES` | Vigencia del token | `480` | N |
| `SEED_ODONTOLOGO_NOMBRE` | Nombre del odontólogo seed (ficticio) | `Dr. Ejemplo` | N |
| `SEED_ODONTOLOGO_EMAIL` | Email de login y de las alertas de turnos pendientes (ficticio) | `odontologo@ejemplo.com` | N |
| `SEED_ODONTOLOGO_PASSWORD` | Contraseña inicial | `cambiar-esto` | Y |
| `APP_FRONTEND_BASE_URL` | Base para links de los emails | `http://localhost:8080` | N |
| `APP_ZONA_HORARIA` | Zona del negocio | `America/Argentina/Buenos_Aires` | N |
| `APP_AGENDA_PASO_MINUTOS` | Paso de la grilla | `15` | N |
| `APP_RECORDATORIO_INTERVALO_MIN` | Frecuencia del job (recordatorios y alertas), en minutos | `15` | N |
| `APP_RECORDATORIO_HORAS_ANTES` | Ventana del recordatorio | `48` | N |
| `APP_ALERTA_PENDIENTE_HORAS` | Umbral de la alerta al odontólogo por turnos `PENDIENTE` | `24` | N |
| `APP_CANCELACION_HORAS_MINIMAS` | Anticipación mínima para que el paciente cancele | `24` | N |
| `APP_TURNO_ANTICIPACION_MIN_HORAS` | Anticipación mínima para reservar (reserva pública) | `2` | N |
| `APP_TURNO_VENTANA_MAX_DIAS` | Máximo de días hacia adelante para reservar (reserva pública) | `60` | N |
| `APP_CONSULTORIO_CONTACTO` | Texto de contacto para mensajes y emails | `Tel. 11 5555-5555` | N |
| `SPRING_MAIL_HOST` | Servidor SMTP | `mailpit` / `smtp.gmail.com` | N |
| `SPRING_MAIL_PORT` | Puerto SMTP | `1025` / `587` | N |
| `SPRING_MAIL_USERNAME` | Usuario SMTP (vacío en Mailpit) | `cuenta@gmail.com` | N |
| `SPRING_MAIL_PASSWORD` | Contraseña de aplicación Gmail | `xxxx xxxx xxxx xxxx` | Y |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` / `..._STARTTLS_ENABLE` | `false` en Mailpit, `true` en Gmail | `true` | N |
| `APP_MAIL_FROM` | Remitente | `turnos@consultorio.local` | N |

Puertos y mapeo de Compose en [11](11_docker_y_entornos.md).

## Estrategia de tests mínimos

Prioridad al núcleo de agenda; el resto, lo que dé el tiempo. Convención TDD estricta del proyecto cuando aplique.

| Prioridad | Qué | Tipo |
|-----------|-----|------|
| 1 | Cálculo de disponibilidad: rangos múltiples por día, bloqueos, turnos activos, duración que no entra, paso 15, pasado | Unit (puro, `Clock` fijo) |
| 1 | Solapamiento en base: dos inserts que se pisan -> 409; adyacentes `[)` -> OK; turno cancelado no bloquea | Integración con Testcontainers (PostgreSQL real con `btree_gist`) |
| 1 | Regla de 24 h de cancelación del paciente (borde exacto) y ventana de reserva 2 h / 60 días | Unit |
| 2 | Transiciones de estado válidas e inválidas | Unit |
| 2 | Bloqueo: vista previa y cancelación en transacción | Integración |
| 2 | Recordatorio y alerta: selección de turnos y flags (recordatorio solo a `PENDIENTE`; recordatorio sincrónico al crear entre 24 h y 48 h y su reintento por el job, turno con menos de 24 h; reprogramación que cambia el estado) | Integración/Unit |
| 3 | Seguridad: ruta privada sin token = 401, pública = 200 | `@SpringBootTest`/MockMvc |
| 3 | Front: funciones puras (formato de fechas, cliente API) | Vitest, si hay tiempo |

La prueba de la carrera (dos hilos reservando a la vez) es la prueba clave del anti-solapamiento.

## Borrador de recursos y endpoints REST

> **Esto es un borrador, no el contrato.** Sirve de base para el primer change "contrato de API" del roadmap, que fija rutas definitivas, JSON de request/response, códigos de estado y códigos de error. Hasta que ese change esté mergeado a `main`, nadie implementa contra estas rutas como si fueran finales.

Convenciones: prefijo `/api`; JSON; ids numéricos; instantes ISO-8601 con offset; errores con el formato uniforme y los [códigos HTTP unificados](#códigos-http-unificados).

**Por qué login, confirmar y cancelar son POST**: cambian estado o envían credenciales. Un GET queda en el historial del navegador y en los logs, y los clientes de mail y antivirus abren los links solos, así que un GET que confirma o cancela lo haría sin intervención del paciente. Por eso el link del email abre una página del front que muestra el resumen con el GET de solo lectura `GET /api/public/turnos/{token}` y el botón dispara el POST (ver [03](03_actores_y_roles.md#por-qué-login-confirmar-y-cancelar-son-post)).

### Autenticación
| Método | Ruta | Auth | Propósito |
|--------|------|------|-----------|
| POST | `/api/auth/login` | Pública | Login con email y contraseña; devuelve JWT |
| GET | `/api/auth/me` | JWT | Datos del odontólogo autenticado (valida la sesión) |

### Públicos (paciente)
| Método | Ruta | Auth | Propósito |
|--------|------|------|-----------|
| GET | `/api/public/prestaciones` | Pública | Prestaciones activas con duración |
| GET | `/api/public/disponibilidad?prestacionId&desde&hasta` | Pública | Horarios libres por día para una prestación |
| POST | `/api/public/turnos` | Pública | Reservar turno. Body con nombre, apellido, DNI, email, teléfono, prestación, horario y `consentimiento: true`. Crea el paciente si no existe o reutiliza el existente sin modificarlo; email y teléfono van al turno. El turno nace `PENDIENTE` (24 h o más) o `CONFIRMADO` (menos de 24 h, RN-TUR-11). 400 formato, 422 sin consentimiento o horario inválido, 409 si se ocupó |
| GET | `/api/public/turnos/{token}` | Token | Ver resumen del turno propio. **Solo lectura**, no cambia estado: lo usa el front para mostrar la pantalla antes del botón |
| POST | `/api/public/turnos/{token}/confirmar` | Token | Confirmar asistencia (solo `PENDIENTE`, antes del inicio y con 48 h o menos de anticipación; 422 si no, RN-NOT-08) |
| POST | `/api/public/turnos/{token}/cancelar` | Token | Cancelar (422 si faltan menos de 24 h) |

### Configuración (odontólogo)
| Método | Ruta | Auth | Propósito |
|--------|------|------|-----------|
| GET | `/api/prestaciones` | JWT | Listar todas (activas e inactivas) |
| POST | `/api/prestaciones` | JWT | Crear |
| PUT | `/api/prestaciones/{id}` | JWT | Editar (nombre, duración, activa) |
| DELETE | `/api/prestaciones/{id}` | JWT | Borrar si no tiene turnos; si tiene, 422 (usar desactivar) |
| GET | `/api/horarios-atencion` | JWT | Listar rangos semanales |
| POST | `/api/horarios-atencion` | JWT | Crear rango |
| PUT | `/api/horarios-atencion/{id}` | JWT | Editar rango |
| DELETE | `/api/horarios-atencion/{id}` | JWT | Eliminar rango |
| GET | `/api/bloqueos?desde&hasta` | JWT | Listar bloqueos |
| POST | `/api/bloqueos/previsualizar` | JWT | Turnos activos afectados por un rango (no modifica nada) |
| POST | `/api/bloqueos` | JWT | Crear bloqueo; con `cancelarTurnosAfectados=true` cancela turnos y envía emails al email de cada turno. Con `false` y turnos afectados, 409 con la lista |
| DELETE | `/api/bloqueos/{id}` | JWT | Eliminar bloqueo |

### Pacientes
| Método | Ruta | Auth | Propósito |
|--------|------|------|-----------|
| GET | `/api/pacientes?q` | JWT | Listar/buscar por DNI o apellido. Cada ítem incluye `ultimoContacto` (email y teléfono de su último turno, o null) |
| GET | `/api/pacientes/{id}` | JWT | Detalle (id, nombre, apellido, DNI y `ultimoContacto` para precargar la carga manual). El paciente no tiene email ni teléfono propios |
| POST | `/api/pacientes` | JWT | Crear (nombre, apellido, DNI único, 422 si el DNI está duplicado) |
| PUT | `/api/pacientes/{id}` | JWT | Editar nombre, apellido y DNI (única vía para modificar datos del paciente) |

### Turnos / agenda
| Método | Ruta | Auth | Propósito |
|--------|------|------|-----------|
| GET | `/api/turnos?desde&hasta&estado` | JWT | Agenda por rango (día o semana). Incluye `telefonoContacto` y un indicador de pendiente a menos de 24 h para resaltarlo |
| GET | `/api/turnos/{id}` | JWT | Detalle |
| POST | `/api/turnos` | JWT | Cargar turno manual (paciente existente o nuevo) con `emailContacto` y `telefonoContacto`. 409 si choca, 422 si el horario no es válido |
| PATCH | `/api/turnos/{id}/reprogramar` | JWT | Mover a otro horario libre (409 si choca, 422 si no es válido). Recalcula el estado según la anticipación (PENDIENTE a 24 h o más, CONFIRMADO a menos de 24 h, RN-TUR-08), reinicia flags y envía email de reprogramación |
| POST | `/api/turnos/{id}/confirmar` | JWT | El odontólogo confirma un turno `PENDIENTE` (US-021), sin restricción de 48 h. 422 si no es `PENDIENTE` o activo |
| POST | `/api/turnos/{id}/cancelar` | JWT | Cancelar por el odontólogo (`cancelado_por = ODONTOLOGO`, envía email al email del turno) |
| PATCH | `/api/turnos/{id}/estado` | JWT | Marcar `ASISTIO` o `AUSENTE` (422 si todavía no llegó la hora de inicio) |
| GET | `/api/disponibilidad?prestacionId&desde&hasta` | JWT | Horarios libres para carga manual y reprogramación, calculados al vuelo (mismo cálculo que el público, sin la ventana de 2 h / 60 días) |

Opcionales (no incluidos): lista de espera, `.ics`, CSV.
