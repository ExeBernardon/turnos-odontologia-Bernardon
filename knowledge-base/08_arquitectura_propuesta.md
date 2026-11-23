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
| Job programado con flag | Recordatorios | Idempotencia sin cola de mensajes |
| Token opaco por recurso | Confirmar/cancelar | Autorización sin cuenta (RN-TOK) |
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
│       │   ├── notificacion/     # EmailService, plantillas, RecordatorioJob
│       │   └── seed/             # DataSeeder (CommandLineRunner)
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── schema-extra.sql  # btree_gist + EXCLUDE (idempotente)
│       │   └── templates/        # plantillas de email (texto/HTML simple)
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

Entrada: `prestacionId`, `fecha` (o rango de fechas), en zona `America/Argentina/Buenos_Aires`. Salida: lista de instantes de inicio.

1. Obtener la duración `d` de la prestación (activa).
2. Para cada día, tomar los `HorarioAtencion` del día de la semana → rangos `[a, b)` convertidos a instantes con la zona.
3. Restar los `Bloqueo` que intersecan cada rango → quedan sub-rangos libres.
4. Restar los turnos activos (`PENDIENTE`, `CONFIRMADO`) cuyo `[inicio, fin)` interseca → sub-rangos libres.
5. Discretizar: dentro de cada sub-rango libre `[x, y)`, generar candidatos `x, x+paso, x+2·paso…` tales que `candidato + d <= y`. Paso configurable `app.agenda.paso-minutos` (por defecto 15). Alinear los candidatos a la grilla del paso desde la hora de inicio del rango de atención (no desde el fin de un turno anterior) para que la grilla sea estable.
6. Descartar candidatos con `inicio <= ahora` (opcionalmente con una anticipación mínima configurable, ver [10](10_preguntas_abiertas.md)).

El mismo cálculo se invoca al reservar o reprogramar para validar (422 si el horario no es válido); la garantía final contra concurrencia es la restricción EXCLUDE (409). Se implementa como lógica pura sobre listas de intervalos, con `Clock` inyectado.

## Seguridad

- **Autenticación**: JWT firmado (HS256 con secreto de variable de entorno), expiración configurable (por defecto 8 h). Login con email y contraseña; BCrypt. Token en `Authorization: Bearer`. Sin refresh token en v1. Sin sesión en servidor (stateless).
- **Autorización**: `SecurityFilterChain`: `permitAll` para `/api/auth/login` y `/api/public/**`; `authenticated` para el resto de `/api/**`. Un único rol (odontólogo).
- **Token de turno**: `SecureRandom`, 32 bytes en Base64 URL-safe (o UUID v4 como mínimo). Columna única. Se compara solo por igualdad en la consulta; no se loguea completo.
- **Validación de input**: Bean Validation (`@Valid`) en DTOs; normalización de DNI; el backend recalcula siempre `fin` y la disponibilidad (nunca confía en el front).
- **Respuestas públicas mínimas**: la consulta por token devuelve solo prestación, fecha, estado y nombre de pila; nunca DNI, email ni teléfono.
- **Fuerza bruta de login**: sin rate limit en v1 (riesgo asumido, ver SU-06).
- **Secrets**: todo por variables de entorno (`.env` fuera de git; `.env.example` versionado). Contraseña de aplicación de Gmail nunca en el repo.
- **CORS**: no necesario (mismo origen) en Docker y en dev (proxy Vite).
- **Datos personales**: solo los mínimos (RN-DAT-01); no loguear DNI/email en claro.

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
- `schema-extra.sql` idempotente: `CREATE EXTENSION IF NOT EXISTS btree_gist` y la `EXCLUDE` dentro de un bloque `DO $$ ... IF NOT EXISTS` (ver [04](04_modelo_de_datos.md)). Debe poder ejecutarse en cada arranque sin error.
- Captura: `DataIntegrityViolationException` cuya causa raíz es `PSQLException` con `SQLState = 23P01` (y nombre de constraint `turno_sin_solapamiento`) se traduce a 409.
- El usuario de la base necesita permiso para `CREATE EXTENSION` (en la imagen `postgres:16` el usuario por defecto es superusuario; `btree_gist` es "trusted" desde PG 13).

## Formato de error uniforme

```json
{ "timestamp": "2026-11-25T10:00:00-03:00", "status": 409, "error": "CONFLICT",
  "codigo": "HORARIO_OCUPADO", "mensaje": "Ese horario se acaba de ocupar, elegí otro.",
  "path": "/api/public/turnos", "detalles": [ { "campo": "dni", "mensaje": "..." } ] }
```

`detalles` solo en 400. Códigos de negocio estables (`HORARIO_OCUPADO`, `CANCELACION_FUERA_DE_PLAZO`, `TOKEN_INVALIDO`, `TRANSICION_INVALIDA`, `DNI_DUPLICADO`, `CREDENCIALES_INVALIDAS`) para que el front reaccione sin parsear texto. El change de contrato fija la lista final.

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
| `SEED_ODONTOLOGO_NOMBRE` | Nombre del odontólogo seed | `Dr. Bernardon` | N |
| `SEED_ODONTOLOGO_EMAIL` | Email de login | `odontologo@example.com` | N |
| `SEED_ODONTOLOGO_PASSWORD` | Contraseña inicial | `cambiar-esto` | Y |
| `APP_FRONTEND_BASE_URL` | Base para links de los emails | `http://localhost:8080` | N |
| `APP_ZONA_HORARIA` | Zona del negocio | `America/Argentina/Buenos_Aires` | N |
| `APP_AGENDA_PASO_MINUTOS` | Paso de la grilla | `15` | N |
| `APP_RECORDATORIO_INTERVALO_MINUTOS` | Frecuencia del job | `15` | N |
| `APP_RECORDATORIO_HORAS_ANTES` | Ventana del recordatorio | `48` | N |
| `APP_CANCELACION_HORAS_MINIMAS` | Anticipación mínima del paciente | `24` | N |
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
| 1 | Regla de 24 h de cancelación del paciente (borde exacto) | Unit |
| 2 | Transiciones de estado válidas e inválidas | Unit |
| 2 | Bloqueo: vista previa y cancelación en transacción | Integración |
| 2 | Recordatorio: selección de turnos y flag | Integración/Unit |
| 3 | Seguridad: ruta privada sin token = 401, pública = 200 | `@SpringBootTest`/MockMvc |
| 3 | Front: funciones puras (formato de fechas, cliente API) | Vitest, si hay tiempo |

La prueba de la carrera (dos hilos reservando a la vez) es la prueba clave del anti-solapamiento.

## Borrador de recursos y endpoints REST

> **Esto es un borrador, no el contrato.** Sirve de base para el primer change "contrato de API" del roadmap, que fija rutas definitivas, JSON de request/response, códigos de estado y códigos de error. Hasta que ese change esté mergeado a `main`, nadie implementa contra estas rutas como si fueran finales.

Convenciones: prefijo `/api`; JSON; ids numéricos; instantes ISO-8601 con offset; errores con el formato uniforme.

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
| POST | `/api/public/turnos` | Pública | Reservar turno (crea o reutiliza paciente por DNI; 409 si se ocupó) |
| GET | `/api/public/turnos/{token}` | Token | Ver resumen del turno propio |
| POST | `/api/public/turnos/{token}/confirmar` | Token | Confirmar asistencia |
| POST | `/api/public/turnos/{token}/cancelar` | Token | Cancelar (rechaza si faltan menos de 24 h) |

### Configuración (odontólogo)
| Método | Ruta | Auth | Propósito |
|--------|------|------|-----------|
| GET | `/api/prestaciones` | JWT | Listar todas (activas e inactivas) |
| POST | `/api/prestaciones` | JWT | Crear |
| PUT | `/api/prestaciones/{id}` | JWT | Editar (nombre, duración, activa) |
| DELETE | `/api/prestaciones/{id}` | JWT | Borrar si no tiene turnos; si tiene, 409 (usar desactivar) |
| GET | `/api/horarios-atencion` | JWT | Listar rangos semanales |
| POST | `/api/horarios-atencion` | JWT | Crear rango |
| PUT | `/api/horarios-atencion/{id}` | JWT | Editar rango |
| DELETE | `/api/horarios-atencion/{id}` | JWT | Eliminar rango |
| GET | `/api/bloqueos?desde&hasta` | JWT | Listar bloqueos |
| POST | `/api/bloqueos/previsualizar` | JWT | Turnos activos afectados por un rango (no modifica nada) |
| POST | `/api/bloqueos` | JWT | Crear bloqueo; con `cancelarTurnosAfectados=true` cancela turnos y envía emails |
| DELETE | `/api/bloqueos/{id}` | JWT | Eliminar bloqueo |

### Pacientes
| Método | Ruta | Auth | Propósito |
|--------|------|------|-----------|
| GET | `/api/pacientes?q` | JWT | Listar/buscar por DNI o apellido |
| GET | `/api/pacientes/{id}` | JWT | Detalle |
| POST | `/api/pacientes` | JWT | Crear (DNI único) |
| PUT | `/api/pacientes/{id}` | JWT | Editar |

### Turnos / agenda
| Método | Ruta | Auth | Propósito |
|--------|------|------|-----------|
| GET | `/api/turnos?desde&hasta&estado` | JWT | Agenda por rango (día o semana) |
| GET | `/api/turnos/{id}` | JWT | Detalle |
| POST | `/api/turnos` | JWT | Cargar turno manual (paciente existente o nuevo) |
| PATCH | `/api/turnos/{id}/reprogramar` | JWT | Mover a otro horario libre |
| POST | `/api/turnos/{id}/cancelar` | JWT | Cancelar por el consultorio (envía email) |
| PATCH | `/api/turnos/{id}/estado` | JWT | Marcar `ASISTIO` o `AUSENTE` |
| GET | `/api/disponibilidad?prestacionId&desde&hasta` | JWT | Horarios libres para carga manual (mismo cálculo que el público) |

Opcionales (no incluidos): lista de espera, `.ics`, CSV.
