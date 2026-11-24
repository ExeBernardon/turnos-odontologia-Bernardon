# Design — C-02 docker-scaffold

## Context

- Repo hoy: solo documentación (`knowledge-base/`, `CHANGES.md`, `AGENTS.md`, `docs/api/` de C-01, `openspec/`). No hay `backend/` ni `frontend/`. Ver proposal.md §Why.
- Restricciones duras que moldean el diseño (AGENTS.md): sin secretos en el repo, Testcontainers y nunca H2, TS `strict` sin `any`, sin framework de UI, DTOs y nunca entidades, `timestamptz` + UTC, datos ficticios.
- El spec `api-contrato` fija el inventario **exacto** de rutas bajo `/api`: no se puede agregar `/api/health` sin un change de contrato.
- Herramientas locales verificadas: Docker 29 (Compose v2), Java 21, Maven 3.9, Node 24.
- Plazo: MVP el 2026-11-25. Prioridad: que el scaffold sea chico, correcto y mergeable en horas.

## Goals / Non-Goals

**Goals:**
- Que ningún change posterior tenga que tocar la estructura de `pom.xml`, `application.yml`, `docker-compose.yml`, `.env.example`, `package.json`, `vite.config.ts`, router o cliente HTTP; solo agregar en puntos marcados.
- Que TDD estricto pueda correr desde C-03: `./mvnw test` (Testcontainers) y `npm test` (Vitest) en verde con un test de humo cada uno.

**Non-Goals:**
- CI (GitHub Actions), cobertura mínima, análisis estático del backend (SpotBugs/PMD/Checkstyle).
- `compose.override.yaml` o hot reload dentro de Docker: el desarrollo diario es fuera de Docker (KB 11).
- HTTPS, rate limiting y headers de seguridad avanzados (CSP): revisión `security-and-hardening` previa a la demo.

## Decisions

### D1 — Árbol de archivos que el change compromete

```
.
├── docker-compose.yml                # ALTO
├── .env.example                      # ALTO
├── .gitignore                        # + target/, dist/, *.log
├── .gitattributes                    # mvnw y *.sh con eol=lf
├── README.md                         # comando único, URLs, flujo diario
├── backend/
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── pom.xml                       # ALTO
│   ├── mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties
│   └── src/
│       ├── main/java/ar/turnos/odontologia/
│       │   ├── TurnosApplication.java
│       │   ├── config/      AppProperties.java, ClockConfig.java, SchedulingConfig.java, SecurityConfig.java
│       │   ├── security/    ApiAuthenticationEntryPoint.java          (C-04 suma JwtService, JwtAuthFilter, UserDetailsService)
│       │   ├── common/      ApiError.java, DetalleError.java, ApiException.java, GlobalExceptionHandler.java
│       │   └── auth/ prestacion/ horario/ bloqueo/ paciente/ turno/ disponibilidad/ notificacion/ seed/   (package-info.java en cada una)
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-local.yml
│       │   ├── schema-extra.sql      # solo CREATE EXTENSION btree_gist (C-03 agrega la EXCLUDE)
│       │   └── templates/.gitkeep    # plantillas de email (C-11)
│       └── test/
│           ├── java/ar/turnos/odontologia/
│           │   ├── TestcontainersConfig.java      # @ServiceConnection PostgreSQL 16, reutilizable por todos
│           │   ├── PlataformaSmokeIT.java          # contexto + health + btree_gist + UTC
│           │   ├── config/AppPropertiesTest.java
│           │   ├── config/SecurityConfigTest.java  # 401 uniforme, sin sesión, público ≠ 401
│           │   └── common/GlobalExceptionHandlerTest.java   # VALIDACION, SOLICITUD_MAL_FORMADA, 404, ApiException
│           └── resources/application-test.yml      # secretos ficticios solo para tests
└── frontend/
    ├── Dockerfile
    ├── .dockerignore
    ├── nginx.conf
    ├── package.json, package-lock.json
    ├── tsconfig.json
    ├── eslint.config.js
    ├── vite.config.ts                 # build multi-page, proxy /api, fallback /admin/*, config de Vitest
    ├── index.html                     # área pública
    ├── admin/index.html               # área privada
    ├── scripts/sync-tipos.mjs         # copia ../docs/api/tipos.ts → src/api/types.ts
    └── src/
        ├── api/        types.ts (copia), cliente.ts, cliente.test.ts, types.sync.test.ts
        ├── shared/     router.ts, router.test.ts, sesion.ts (get/set/clear token; C-13 la completa), dom.ts
        ├── public/     main.ts, rutas.ts, reservar.ts, confirmar.ts, cancelar.ts
        ├── admin/      main.ts, rutas.ts, login.ts, agenda.ts, pacientes.ts,
        │               configuracion/index.ts, configuracion/prestaciones.ts, configuracion/horarios.ts, configuracion/bloqueos.ts
        └── styles/     base.css
```

Cada pantalla exporta `render(contenedor: HTMLElement, ctx: ContextoRuta): void`. Los `rutas.ts` de cada área ya registran todas las pantallas, así que C-13..C-20 solo reemplazan el contenido de su archivo.

### D2 — Puertos

| Servicio | Host (variable, default) | Contenedor |
|----------|--------------------------|------------|
| frontend (nginx) | `FRONTEND_PORT` = 8080 | 80 |
| backend | `BACKEND_PORT` = 8081 | 8080 |
| db | `DB_PORT` = 5432 | 5432 |
| mailpit UI / SMTP | `MAILPIT_UI_PORT` = 8025 / `MAILPIT_SMTP_PORT` = 1025 | 8025 / 1025 |
| Vite dev | 5173 | — |

El backend local (perfil `local`) escucha en **8081**, el mismo puerto de host que el backend en Docker: el proxy de Vite apunta siempre a `http://localhost:8081` (sobrescribible con `VITE_API_PROXY`) y funciona igual con backend en Docker o local. Alternativa descartada: backend local en 8080 (choca con el front de Docker y obliga a dos configuraciones de proxy).

### D3 — Healthchecks y orden de arranque

- `db`: `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB` (interval 5s, timeout 3s, retries 10, start_period 10s).
- `backend`: `wget -qO- http://localhost:8080/actuator/health` (imagen alpine trae busybox `wget`), start_period 40s; `depends_on: db: condition: service_healthy`.
- `frontend`: `wget -qO- http://localhost/`; `depends_on: backend: condition: service_healthy` (además evita que nginx falle al resolver `backend` en el arranque).
- `mailpit`: sin dependencia. El backend **no** depende de Mailpit (en la demo con Gmail puede no estar) y se desactiva el health de mail (`management.health.mail.enabled=false`) para que un SMTP caído no deje al backend unhealthy.
- `restart: unless-stopped` en los cuatro; imágenes con tag fijo (nunca `latest`): `postgres:16`, `axllent/mailpit:<vX.Y concreto>` fijado en el apply.

### D4 — Health en `/actuator/health`, no en `/api/health`

Se usa Spring Boot Actuator exponiendo solo `health` (`management.endpoints.web.exposure.include=health`, sin detalles). Queda fuera de `/api`, así que no rompe el inventario exacto del contrato ni necesita change de contrato. nginx no lo publica (solo lo usa el healthcheck interno y el puerto 8081 en dev). El criterio de salida de CHANGES.md "`/api/health` por el proxy" se verifica en cambio con `GET /api/auth/me` sin token → 401 `NO_AUTENTICADO` por el proxy, que prueba a la vez proxy, seguridad y formato de error. Alternativa descartada: agregar `/api/health` al OpenAPI (change de contrato CRÍTICO en el camino crítico, por un endpoint de infraestructura).

### D5 — `docker-compose.yml` y variables

- Nombre `docker-compose.yml` (lo exigen KB y roadmap; Compose v2 lo lee igual). Desvío consciente de la regla de la skill que prefiere `compose.yaml`.
- `backend` usa `env_file: .env` (si falta `.env`, Compose falla: es lo deseado) y sobrescribe en `environment` lo que depende de la red de Compose: `SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/${POSTGRES_DB}`, `SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}`, `SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}`. `db` toma `POSTGRES_*` del mismo `.env`. Volumen nombrado `pgdata`.
- `.env.example`: todas las variables de KB 08 + `FRONTEND_PORT`, `BACKEND_PORT`, `DB_PORT`, `MAILPIT_UI_PORT`, `MAILPIT_SMTP_PORT`, con valores de ejemplo de KB 11 (SMTP a `mailpit:1025` sin auth) y comentarios de cómo pasar a Gmail. `SPRING_DATASOURCE_*` no van en `.env.example` porque Compose las arma desde `POSTGRES_*`.
- `TZ=America/Argentina/Buenos_Aires` en los contenedores es solo informativo (logs); la JVM va en UTC (D7).

### D6 — `application.yml` y perfiles

- **Gotcha de binding**: Spring mapea `APP_RECORDATORIO_INTERVALO_MIN` a `app.recordatorio.intervalo.min`, **no** a `app.recordatorio.intervalo-min`. Por eso cada clave `app.*` se mapea explícitamente en el YAML: `intervalo-min: ${APP_RECORDATORIO_INTERVALO_MIN:15}`. `SPRING_*` (datasource, mail, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_*`) sí bindean solas por relaxed binding.
- Claves pre-armadas en `AppProperties` (record con records anidados, `@Validated`): `zonaHoraria`, `frontendBaseUrl`, `agenda.pasoMinutos`, `recordatorio.intervaloMin`/`horasAntes`, `alerta.pendienteHoras`, `cancelacion.horasMinimas`, `turno.anticipacionMinHoras`/`ventanaMaxDias`, `consultorio.contacto`, `mail.from`, `jwt.secret`/`expirationMinutes`, `seed.odontologo.nombre`/`email`/`password`. Los sensibles (`jwt.secret`, `seed.odontologo.password`) se mapean sin default (`${JWT_SECRET}`): si faltan, el arranque falla (spec plataforma-backend). La contraseña de la base también: `spring.datasource.password` sin default en el perfil base.
- Resto: `spring.jpa.hibernate.ddl-auto=update`, `spring.jpa.open-in-view=false`, `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`, `spring.jpa.defer-datasource-initialization=true`, `spring.sql.init.mode=always`, `spring.sql.init.schema-locations=classpath:schema-extra.sql`, `spring.sql.init.separator=^^^`, `spring.sql.init.continue-on-error=false`, `server.port=8080`, `spring.jackson.serialization.write-dates-as-timestamps=false`, mail por `SPRING_MAIL_*`.
- **Perfiles**: base (= Docker, todo por entorno); `local` (`application-local.yml`: `server.port=8081`, datasource `localhost:${DB_PORT:5432}`, credenciales y secretos **de ejemplo idénticos a `.env.example`**, mail `localhost:1025`); `test` (`src/test/resources/application-test.yml`: secretos ficticios, mail a `localhost:1025` sin envío real). No hay perfil `demo`: dev vs demo se diferencia solo por variables `SPRING_MAIL_*`/`APP_MAIL_FROM`/`APP_FRONTEND_BASE_URL` (KB 11), sin cambiar código.
- **Backend sin Docker**: `docker compose up db mailpit` y luego `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (`mvnw.cmd` en Windows). Si el `.env` del integrante usa otra contraseña de base, la exporta (`SPRING_DATASOURCE_PASSWORD=...`), que gana sobre el YAML.

### D7 — Zona horaria

JVM en UTC con `-Duser.timezone=UTC` en el `ENTRYPOINT` del Dockerfile, `TurnosApplication.main` fija `TimeZone.setDefault(UTC)` antes de arrancar Spring (cubre `./mvnw spring-boot:run` y los tests en cualquier máquina), Hibernate con `jdbc.time_zone=UTC`. `ClockConfig` expone `Clock.systemUTC()`; la zona de negocio sale de `AppProperties.zonaHoraria` (`ZoneId`). Los tests usan `Clock.fixed`.

### D8 — `schema-extra.sql` y el separador `^^^`

Vive en `backend/src/main/resources/schema-extra.sql` (KB 08). C-02 lo crea con una sola sentencia:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist
^^^
```

Así C-02 ya ejercita en el test de humo el orden (Hibernate → script), el separador y la idempotencia (la extensión existe tras arrancar), sin depender de tablas que todavía no existen. C-03 agrega el bloque `DO $$ ... $$` con la `EXCLUDE` y es quien verifica con Testcontainers que `^^^` no parte el bloque. Alternativa descartada: `optional:classpath:` sin archivo (no prueba nada y C-03 descubriría el problema recién en su change).

### D9 — Seguridad base

`SecurityConfig`: `SecurityFilterChain` stateless (`SessionCreationPolicy.STATELESS`), CSRF, form login, HTTP Basic y logout desactivados; `permitAll` para `POST /api/auth/login`, `/api/public/**`, `/actuator/health` y `/error`; `authenticated` para `/api/**`; `denyAll` resto. `ApiAuthenticationEntryPoint` escribe el `ApiError` 401 `NO_AUTENTICADO` con Jackson (spec api-errores: el 401 lo emite el entry point). Comentario `// C-04: addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)` como único punto de cambio de C-04. Para que Boot no genere un usuario en memoria con contraseña logueada, se declara un `UserDetailsService` vacío (`InMemoryUserDetailsManager()` sin usuarios) que C-04 reemplaza. `PasswordEncoder` BCrypt ya declarado (lo usan C-03 seed y C-04).

### D10 — Errores genéricos

`ApiError` (record con `timestamp` `OffsetDateTime` en la zona de negocio, `status`, `error`, `codigo`, `mensaje`, `path`, `detalles` omitido si es null vía `@JsonInclude(NON_NULL)`). `GlobalExceptionHandler` (`@RestControllerAdvice`) cubre: `MethodArgumentNotValidException`/`HandlerMethodValidationException` → 400 `VALIDACION` con detalles; `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` → 400 `SOLICITUD_MAL_FORMADA`; `NoResourceFoundException` → 404 `RECURSO_NO_ENCONTRADO`; `ApiException(HttpStatus, String codigo, String mensaje)` → su status y código (base que extienden los dominios: `RecursoNoEncontradoException`, etc., los crean ellos). Los mensajes son los de la tabla de `docs/api/errores.md`. Un handler catch-all de `Exception` responde 500 `ERROR_INTERNO` (nuevo código de contrato agregado en este change, ver D19): registra la excepción en el log del servidor y devuelve un mensaje genérico, nunca el mensaje ni el stack trace. Spring resuelve el handler más específico, así que los demás siguen ganando; `AccessDeniedException`/`AuthenticationException` no se capturan acá (los maneja Spring Security). C-03 suma el handler de `DataIntegrityViolationException`/`23P01`. Los códigos se pasan como `String` en C-02; si alguien quiere un enum `CodigoError` lo agrega C-03 (archivo propio, no conflictivo).

### D11 — Dependencias del `pom.xml` (ALTO)

Parent `spring-boot-starter-parent` 3.5.x (último patch al aplicar), `java.version=21`. Compile: `spring-boot-starter-web`, `-data-jpa`, `-validation`, `-security`, `-mail`, `-actuator`, `-thymeleaf` (plantillas de email de C-11), `io.jsonwebtoken:jjwt-api:0.12.6`; runtime: `org.postgresql:postgresql`, `jjwt-impl`, `jjwt-jackson`; optional: `spring-boot-configuration-processor`; test: `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`, `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql` (versiones de Testcontainers por el BOM de Boot). Plugins: `spring-boot-maven-plugin`, `maven-surefire-plugin` (corre `*Test` y `*IT` en `mvn test`, para que un solo comando corra todo). Sin Lombok (records), sin H2, sin Flyway (DD-04).

### D12 — Dockerfiles

- **backend**: etapa `maven:3.9-eclipse-temurin-21` → copia `pom.xml`, `mvn -B dependency:go-offline`, copia `src`, `mvn -B -DskipTests package` (con `--mount=type=cache,target=/root/.m2`); etapa `eclipse-temurin:21-jre-alpine` → usuario no root `app`, copia solo el jar, `EXPOSE 8080`, `ENTRYPOINT ["java","-Duser.timezone=UTC","-jar","/app/app.jar"]`. Se usa Maven de la imagen y no el wrapper (evita problemas de fin de línea de `mvnw` en Windows).
- **frontend**: etapa `node:24-alpine` → `npm ci`, copia fuente, `npm run build` (`tsc --noEmit && vite build`); etapa `nginx:<1.x estable>-alpine` → copia `dist/` y `nginx.conf`. KB 11 decía `node:20-alpine`, pero Node 20 está fuera de soporte desde abril de 2026; se usa 24 LTS (igual que las máquinas del equipo) y `engines.node >= 22`.
- `.dockerignore` en ambos (`target/`, `node_modules/`, `dist/`, `.env*`).

### D13 — nginx

`location /api/ { proxy_pass http://backend:8080; }` (sin barra final: conserva `/api`), con `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`. `location /admin/ { try_files $uri $uri/ /admin/index.html; }`, `location / { try_files $uri $uri/ /index.html; }`. `/assets/` con cache largo (hash de Vite), `index.html` con `no-cache`, gzip, `X-Content-Type-Options nosniff`, `Referrer-Policy same-origin` (evita filtrar el token de la query a terceros), `X-Frame-Options DENY`. Sin CORS.

### D14 — Vite, router y fallback en dev

- Build multi-page: `rollupOptions.input = { main: 'index.html', admin: 'admin/index.html' }`.
- Dev: `server.proxy['/api'] = process.env.VITE_API_PROXY ?? 'http://localhost:8081'`. El fallback SPA de Vite manda todo a `/index.html`, así que un plugin mínimo en `vite.config.ts` reescribe `/admin/*` (sin extensión) a `/admin/index.html` para que la recarga de `/admin/agenda` funcione igual que en nginx.
- Router propio (`src/shared/router.ts`, ~60 líneas): tabla `Map<string, Pantalla>` por área, `base` (`''` o `/admin`), redirección de la raíz del área, intercepción de clicks en `<a data-link>`, `history.pushState` + `popstate`, `ContextoRuta` con `URLSearchParams`. Sin dependencias.

### D15 — Tipos del contrato: copia + sincronización, no alias

`frontend/src/api/types.ts` es **copia exacta** de `docs/api/tipos.ts` (lo que ya prevé su encabezado y CHANGES.md). `npm run tipos:sync` la regenera y `types.sync.test.ts` compara ambos archivos byte a byte. Alternativa descartada: alias de Vite/TS a `../docs/api/tipos.ts`: el build de Docker usa `frontend/` como contexto y no ve `docs/`; ampliar el contexto a la raíz complica el Dockerfile y el cache. La copia más el test da una sola fuente de verdad con detección automática de desvíos.

### D16 — Cliente HTTP

`cliente.ts` expone `pedir<T>(metodo, ruta, { body?, parsear }): Promise<T>` donde `parsear: (dato: unknown) => T` lo aporta cada recurso (sin casts `as` sobre datos externos). Errores como clase `ErrorApi` con `status`, `codigo: CodigoError | null`, `mensaje`, `detalles`, y `ErrorConexion` para fallas de red; un type guard `esApiError(dato: unknown): dato is ApiError` valida el cuerpo uniforme. Lee el token de `shared/sesion.ts`. Un archivo por recurso (`api/turnos.ts`, etc.) lo agregan los changes de front. Zod queda en `package.json` por si los dominios prefieren parsear con schemas (pre-armado para no tocar `package.json`), pero el cliente base no lo requiere.

### D17 — `tsconfig`, lint y tests del front

`strict`, `noUncheckedIndexedAccess`, `noImplicitOverride`, `noUnusedLocals`, `noUnusedParameters`, `noFallthroughCasesInSwitch`, `verbatimModuleSyntax`, `moduleResolution: bundler`, `noEmit`. `types.ts` tiene que compilar con esta config (se verifica en el apply; si alguna opción choca con el contrato, se quita la opción, no se toca la copia). ESLint flat config con `typescript-eslint` recomendado + `@typescript-eslint/no-explicit-any: error`. Vitest con `environment: 'jsdom'`, `npm test` = `vitest run`. Dependencias dev: `typescript`, `vite`, `vitest`, `jsdom`, `eslint`, `typescript-eslint`, `@types/node`; dependencia: `zod`. Versiones exactas quedan en `package-lock.json` (mayores estables al aplicar, compatibles con Node 22+).

### D18 — Estrategia de tests (TDD desde el scaffold)

| Test | Capa | Qué prueba |
|------|------|-----------|
| `PlataformaSmokeIT` | `@SpringBootTest` + Testcontainers | contexto levanta, `/actuator/health` UP, `btree_gist` instalada, zona default UTC |
| `SecurityConfigTest` | `@SpringBootTest` + MockMvc + Testcontainers | privada sin token → 401 uniforme; pública ≠ 401; sin `Set-Cookie` |
| `GlobalExceptionHandlerTest` | `@WebMvcTest` con controller de prueba en `src/test` | 400 `VALIDACION` con detalles, 400 `SOLICITUD_MAL_FORMADA`, 404, `ApiException` 422 |
| `AppPropertiesTest` | `ApplicationContextRunner` | binding de env vars con guión, defaults, falta de `JWT_SECRET` falla |
| `router.test.ts` | Vitest + jsdom | rutas registradas, raíz, 404, pushState/popstate, query |
| `cliente.test.ts` | Vitest, `fetch` mockeado en el borde | error uniforme, error no uniforme, Bearer, falla de red |
| `types.sync.test.ts` | Vitest | copia idéntica |

`TestcontainersConfig` (`@TestConfiguration` con `@ServiceConnection PostgreSQLContainer("postgres:16")`) queda como pieza reutilizable para C-03 en adelante. La verificación de Compose (`docker compose up --build` + curl por el proxy) es manual y se documenta en tasks.

### D19 — Error 500 `ERROR_INTERNO` en el contrato

Decisión del usuario al aplicar: se agrega 500 `ERROR_INTERNO` al contrato como respuesta genérica de errores inesperados. Se declara una sola vez en `components.responses.ErrorInterno` (con ejemplo `error.500-error-interno.json`), se suma al enum `CodigoError`, a `tipos.ts` (`CodigoError` y `ApiError.status`) y a `errores.md`, pero **no** se adjunta a cada operación ni a la matriz por endpoint: queda como respuesta compartida documentada. `cross.mjs` no cambia (la matriz sigue sin 500). `AGENTS.md`/`CLAUDE.md` regla 7 lo permite solo para errores inesperados. El delta `specs/api-errores/spec.md` modifica los requirements de status permitidos y catálogo, y agrega el requirement de error interno genérico.

### D20 — Ajustes descubiertos al aplicar

- **Binder y placeholders**: un `${JWT_SECRET}` sin resolver queda como texto literal y `@NotBlank` no falla. Los sensibles se mapean con default vacío (`${JWT_SECRET:}`) y `@NotBlank` hace fallar el arranque nombrando `app.jwt.secret` (D6).
- **`AppPropertiesConfig`** (`@EnableConfigurationProperties`) reemplaza a `@ConfigurationPropertiesScan`: los `@WebMvcTest` no procesan el scan, y la config se importa explícitamente.
- **Zona horaria en tests**: los tests no pasan por `TurnosApplication.main`; surefire corre con `-Duser.timezone=UTC` (con la zona de Windows `America/Buenos_Aires` el driver de PostgreSQL falla con `invalid value for parameter "TimeZone"`).
- **Docker Engine 29**: Testcontainers 1.21.3 (BOM de Boot 3.5.7) recibe HTTP 400 por la API mínima 1.44; el `pom.xml` fija `testcontainers.version=1.21.4`.
- **`SecurityConfigTest`** usa `@WebMvcTest` con `SecurityConfig` importada (sin base ni contenedor): prueba lo mismo y es más rápido que `@SpringBootTest`.
- **Handler**: `HttpRequestMethodNotSupportedException` → 404 `RECURSO_NO_ENCONTRADO` y `HttpMediaTypeException` → 400 `SOLICITUD_MAL_FORMADA` para que el catch-all no los convierta en 500; `AccessDeniedException`/`AuthenticationException` se relanzan.
- **Versiones**: Spring Boot 3.5.7, `axllent/mailpit:v1.21`, `nginx:1.28-alpine`, Node 24; el front instaló las últimas mayores (TypeScript 6, Vite 8, Vitest 5, ESLint 10).

## Risks / Trade-offs

- [Desvío de CHANGES.md en el health] → documentado en proposal y aquí (D4); el criterio de salida equivalente se prueba por el proxy con `/api/auth/me`.
- [Archivos compartidos igual se tocan] → puntos de extensión marcados con comentario `// C-0X:`; `AppProperties` y `application.yml` ya traen todas las claves de KB 08; si falta alguna, se agrega en un PR chico avisando al equipo.
- [`env_file` requerido rompe a quien no copió `.env`] → es intencional (fallo temprano); el README pone el `cp` primero.
- [Testcontainers en Windows necesita Docker Desktop corriendo] → README lo aclara; sin Docker los tests de integración fallan con mensaje claro (no se saltean en silencio).
- [`mvnw` con CRLF en Windows rompe en Linux] → `.gitattributes` con `mvnw text eol=lf`; el Dockerfile no usa el wrapper.
- [nginx corre su master como root] → imagen oficial, workers sin privilegios; aceptable para la demo académica (revisar en `security-and-hardening`).
- [Versiones "última estable al aplicar" (Boot 3.5.x, Vite, Mailpit, nginx)] → se fijan exactas en `pom.xml`, `package-lock.json` y tags de imagen durante el apply, nunca `latest`.
- [`ddl-auto=update` + script que corre siempre] → asumido en DD-04; reset con `docker compose down -v`.

## Migration Plan

No hay datos ni versión previa. Rollback: revertir el PR (todos los archivos son nuevos salvo `.gitignore`). Orden de merge: PR de C-02 revisado por Int 1 antes de que nadie ramifique la Fase 1.

## Open Questions

- Tag exacto de `axllent/mailpit` y de `nginx:*-alpine`: se fija el último estable al aplicar (no cambia specs ni tasks).
- Si conviene sumar un `CodigoError` enum en Java: lo decide C-03 (archivo nuevo, no conflictivo).
