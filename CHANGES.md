# CHANGES — Secuencia de Implementación

> Índice canónico de todos los changes del proyecto **Turnos Odontología (Bernardon)**.
> Cada change es atómico: un agente puede implementarlo en una sesión (~4-6 horas; los de lógica pura o CRUD simple, bastante menos).
> **Leer este archivo antes de ejecutar cualquier `/opsx:propose`.**
>
> Entrega del MVP: **2026-11-25**. Hoy es 2026-11-23: el plazo manda. Los changes C-01 a C-21 son el MVP (sección D del discovery / US `[MVP]` de la KB 06). Los changes C-22 a C-25 son opcionales y están marcados **recortable si no llegamos**.

---

## Cómo usar este documento

1. Identificar el change a implementar y verificar que sus dependencias están en `openspec/changes/archive/` (o mergeadas a `main`). "Dependencias" = orden de merge/archive, no de arranque: los changes de front y los de lógica pura pueden **arrancar** apenas pasa C-02, trabajando contra los ejemplos del contrato (`docs/api/ejemplos/`), y se mergean cuando sus dependencias ya están en `main`.
2. Leer los docs de la knowledge-base indicados en "Leer antes".
3. Ejecutar `/opsx:propose <nombre-del-change>` (por ejemplo `/opsx:propose contrato-api`).
4. Al terminar el change, archivarlo con `/opsx:archive <nombre-del-change>`.
5. Marcar el checkbox `[x]` en este archivo (en el mismo PR del change, para que `main` siempre refleje el estado).

Reglas de equipo (6 integrantes, cada uno con commits propios):
- **Nadie se ramifica antes de que C-01 y C-02 estén en `main`.** Cualquier cambio posterior al contrato requiere un change explícito (`/opsx:propose` de ajuste de contrato), nunca un cambio silencioso (DD-11).
- Una rama por change, nombrada `feat/c-NN-nombre` (prefijo según el tipo: `feat/`, `fix/`, `docs/`; ej. `feat/c-07-turnos-alta`); PR chico a `main`; el autor del change es quien commitea.
- Estructura **por feature** (KB 08): cada integrante toca sus carpetas. Los archivos compartidos (`pom.xml`, `application.yml`, `docker-compose.yml`, router y cliente API del front, `SecurityConfig`) quedan **pre-armados en C-02** para evitar conflictos; si hace falta tocar uno, avisar en el grupo antes de mergear.
- Estrategia de tests: TDD estricto en lo que importa (KB 08 §Estrategia de tests mínimos): disponibilidad, solapamiento, regla 24 h / 48 h.

---

## Árbol de dependencias

```
C-01 contrato-api                                   ← PRIMERO. Mergeado a main antes de ramificar
  └── C-02 docker-scaffold                          ← SEGUNDO. Mismo entorno para los 6 desde el día uno
        └── C-03 entidades-base-y-seed              ← mini-gate (~1-2 h): entidades, repos, schema-extra.sql, seed
              │
              ├── C-04 auth-jwt-backend
              │     └── C-13 login-front-y-guard
              │
              ├── C-05 prestaciones-horarios-backend
              │     └── C-14 configuracion-prestaciones-horarios-front
              │           └── C-15 configuracion-bloqueos-front   (+ C-10)
              │
              ├── C-06 disponibilidad-calculo
              │     └── C-07 turnos-alta-reserva-backend
              │           ├── C-08 turnos-acciones-backend
              │           │     ├── C-18 turno-token-front
              │           │     └── C-19 agenda-vistas-front
              │           │           └── C-20 agenda-acciones-front   (+ C-07, C-09)
              │           └── C-17 reserva-publica-front   (+ C-06)
              │
              ├── C-09 pacientes-backend
              │     └── C-16 pacientes-front   (+ C-13)
              │
              ├── C-10 bloqueos-backend
              │
              └── C-11 notificaciones-email-backend
                    └── C-12 recordatorio-alerta-job

C-21 integracion-e2e-demo    ← cierra el MVP: depende de C-12, C-15, C-16, C-17, C-18, C-20

Opcionales (recortables si no llegamos):
C-22 wa-me-link-agenda (← C-19)  ·  C-23 ics-turno (← C-18)  ·  C-24 exportacion-csv (← C-09, C-08)  ·  C-25 lista-espera-simple (← C-08, C-20)
```

### Paralelismo por fase

> Cada "gate" es un punto de sincronización. Los changes dentro de un gate pueden ejecutarse en paralelo. `[Int N]` = Integrante N (ver plan con 6 agentes).

```
GATE 0: ninguna
  → C-01 contrato-api                              [Int 1]   ← el contrato es lo primero que entra a main
  → (en rama, sin mergear) preparar C-02            [Int 2]   ← compose + Dockerfiles se pueden ir armando mientras se revisa el contrato

GATE 1: C-01 ✓
  → C-02 docker-scaffold                           [Int 2]   ← el resto revisa el contrato y prepara su rama/agente

GATE 2: C-01 + C-02 ✓                              ← FORK de 6 (todos pueden empezar)
  → C-03 entidades-base-y-seed                     [Int 1]   ← mini-gate: mergear rápido, lo necesita todo el backend
  → C-14 configuracion-prestaciones-horarios-front [Int 2]   (contra ejemplos del contrato)
  → C-06 disponibilidad-calculo                    [Int 3]   (lógica pura, TDD; mergea después de C-03)
  → C-07 turnos-alta-reserva-backend               [Int 4]   (arranca con reglas de tiempo puras en TDD; mergea después de C-03 y C-06)
  → C-19 agenda-vistas-front                       [Int 5]   (contra ejemplos del contrato)
  → C-11 notificaciones-email-backend              [Int 6]   (EmailService y plantillas; mergea después de C-03)

GATE 3: C-03 ✓                                     ← se desbloquea todo el backend
  → C-04 auth-jwt-backend                          [Int 1]
  → C-05 prestaciones-horarios-backend             [Int 2]
  → C-06 disponibilidad-calculo (merge)            [Int 3]
  → C-07 turnos-alta-reserva-backend (continúa)    [Int 4 — si C-06 ✓]
  → C-12 recordatorio-alerta-job                   [Int 6 — si C-11 ✓]

GATE 4: C-04 + C-06 + C-07 ✓
  → C-13 login-front-y-guard                       [Int 1]
  → C-10 bloqueos-backend                          [Int 2]
  → C-17 reserva-publica-front                     [Int 3]
  → C-08 turnos-acciones-backend                   [Int 4]
  → C-09 pacientes-backend                         [Int 6]

GATE 5: C-08 + C-09 + C-13 ✓
  → C-18 turno-token-front                         [Int 3]
  → C-20 agenda-acciones-front                     [Int 5 — si C-19 ✓]
  → C-15 configuracion-bloqueos-front              [Int 2 — si C-10 + C-14 ✓]
  → C-16 pacientes-front                           [Int 6]

GATE 6: C-12 + C-15 + C-16 + C-17 + C-18 + C-20 ✓
  → C-21 integracion-e2e-demo                      [Int 6 lidera, todos prueban]

GATE 7: C-21 ✓  (o antes si alguien queda libre y el MVP ya está estable)
  → C-22 wa-me-link-agenda                         [Int 5]   RECORTABLE
  → C-23 ics-turno                                 [Int 3]   RECORTABLE
  → C-24 exportacion-csv                           [Int 1]   RECORTABLE
  → C-25 lista-espera-simple                       [Int 2]   RECORTABLE
```

### Camino crítico (8 changes — mínimo irreducible)

```
C-01 → C-02 → C-03 → C-06 → C-07 → C-08 → C-20 → C-21
```

Es la cadena de disponibilidad → alta de turno → acciones sobre el turno → pantalla de acciones de la agenda → integración. Si cualquiera se atrasa, se atrasa la entrega. Cadena casi crítica (misma longitud en gates): `C-03 → C-11 → C-12 → C-21` (notificaciones). Todo lo demás tiene holgura.

### Plan óptimo con 6 agentes

> Cada "paso" es un bloque de trabajo de ~medio día. `*` = arranca con fixtures del contrato o lógica pura y mergea cuando sus dependencias ya están en `main`. Después de C-01 + C-02 los seis trabajan en carpetas distintas: por dominio backend y su pantalla de front.

```
Paso │ Int 1 (Auth/Base)     │ Int 2 (Configuración)       │ Int 3 (Disp. + Público) │ Int 4 (Turnos back)   │ Int 5 (Agenda front)   │ Int 6 (Notif. + Pacientes)
─────┼───────────────────────┼─────────────────────────────┼─────────────────────────┼───────────────────────┼────────────────────────┼───────────────────────────
  1  │ C-01 contrato-api     │ (prepara C-02 en rama)      │ lee contrato            │ lee contrato          │ lee contrato           │ lee contrato
  2  │ revisa C-02           │ C-02 docker-scaffold        │ lee contrato            │ lee contrato          │ lee contrato           │ lee contrato
  3  │ C-03 entidades-seed   │ C-14 config front *         │ C-06 disponibilidad     │ C-07 alta turno *     │ C-19 agenda vistas *   │ C-11 email backend
  4  │ C-04 auth-jwt         │ C-05 prest.-horarios back   │ C-17 reserva pública *  │ C-07 (cierre)         │ C-19 (cierre)          │ C-12 job recordatorios
  5  │ C-13 login front      │ C-10 bloqueos backend       │ C-18 turno token front  │ C-08 acciones turno   │ C-20 agenda acciones * │ C-09 pacientes backend
  6  │ merge queue / tests   │ C-15 config bloqueos front  │ (holgura, apoya C-21)   │ C-08 (cierre)         │ C-20 (cierre)          │ C-16 pacientes front
  7  │ C-21 integración E2E (todos; Int 6 lidera)                                                                                            
  8  │ C-24 csv  RECORTABLE  │ C-25 lista espera RECORT.   │ C-23 ics  RECORTABLE    │ apoyo / bugs          │ C-22 wa.me RECORTABLE  │ README y guion de demo
```

Asignación por persona (cada una con commits propios):

| Integrante | Dominio | Changes MVP | Opcional (recortable) | Carpetas que toca |
|------------|---------|-------------|-----------------------|-------------------|
| Integrante 1 | Contrato, entidades base y autenticación | C-01, C-03, C-04, C-13 | C-24 | `docs/api/`, `backend/.../auth`, `security`, `seed`, entidades/repos base, `frontend/src/admin/login.ts` |
| Integrante 2 | Docker + configuración del consultorio (prestaciones, horarios, bloqueos) | C-02, C-05, C-10, C-14, C-15 | C-25 | raíz (`docker-compose.yml`, `.env.example`), `backend/.../prestacion`, `horario`, `bloqueo`, `frontend/src/admin/configuracion/` |
| Integrante 3 | Disponibilidad y área pública (reserva, confirmar, cancelar) | C-06, C-17, C-18 | C-23 | `backend/.../disponibilidad`, `frontend/src/public/` |
| Integrante 4 | Turnos backend (alta, estados, anti-solapamiento) | C-07, C-08 | — (apoya bugs) | `backend/.../turno` |
| Integrante 5 | Agenda del odontólogo (front) | C-19, C-20 | C-22 | `frontend/src/admin/agenda*.ts` y `agenda/` |
| Integrante 6 | Notificaciones por email, job y pacientes; lidera la integración | C-11, C-12, C-09, C-16, C-21 | — | `backend/.../notificacion`, `paciente`, `frontend/src/admin/pacientes*.ts`, `templates/` |

Notas de reparto:
- Int 1 e Int 2 cargan con los gates (C-01, C-02, C-03): son los que más rápido tienen que mergear. Int 1 además hace de **cola de merge** durante el paso 6.
- Int 4 tiene el trabajo más denso (concurrencia, estados, reglas de 24 h / 48 h): conviene que Int 3 (liviano en el paso 6) lo ayude con revisión y tests.
- Int 3 e Int 4 se coordinan en la interfaz `DisponibilidadService.validarHorario(...)` (la define C-06 en la primera hora y la mergea aunque la implementación completa venga después).
- La interfaz `NotificacionTurnos` (un método por mail de la tabla de KB 05 "Mails del sistema") la define C-03 con una implementación no-op; C-07, C-08 y C-10 la llaman sin esperar a C-11, y C-11 la reemplaza por la real.

### Tabla resumen

| ID | Change | Fase | Integrante | Dependencias | Governance | MVP |
|----|--------|------|-----------|--------------|------------|-----|
| C-01 | contrato-api | 0 | 1 | — | CRITICO | Sí |
| C-02 | docker-scaffold | 0 | 2 | C-01 | ALTO | Sí |
| C-03 | entidades-base-y-seed | 0 | 1 | C-02 | CRITICO | Sí |
| C-04 | auth-jwt-backend | 1 | 1 | C-03 | CRITICO | Sí |
| C-05 | prestaciones-horarios-backend | 1 | 2 | C-03 | BAJO | Sí |
| C-06 | disponibilidad-calculo | 1 | 3 | C-03 | ALTO | Sí |
| C-07 | turnos-alta-reserva-backend | 1 | 4 | C-03, C-06 | ALTO | Sí |
| C-08 | turnos-acciones-backend | 1 | 4 | C-04, C-07 | MEDIO | Sí |
| C-09 | pacientes-backend | 1 | 6 | C-03, C-04 | BAJO | Sí |
| C-10 | bloqueos-backend | 1 | 2 | C-03, C-04 | MEDIO | Sí |
| C-11 | notificaciones-email-backend | 1 | 6 | C-03 | ALTO | Sí |
| C-12 | recordatorio-alerta-job | 1 | 6 | C-11 | ALTO | Sí |
| C-13 | login-front-y-guard | 2 | 1 | C-02, C-04 | MEDIO | Sí |
| C-14 | configuracion-prestaciones-horarios-front | 2 | 2 | C-02, C-05, C-13 | BAJO | Sí |
| C-15 | configuracion-bloqueos-front | 2 | 2 | C-10, C-14 | BAJO | Sí |
| C-16 | pacientes-front | 2 | 6 | C-09, C-13 | BAJO | Sí |
| C-17 | reserva-publica-front | 2 | 3 | C-02, C-06, C-07 | MEDIO | Sí |
| C-18 | turno-token-front | 2 | 3 | C-08, C-17 | MEDIO | Sí |
| C-19 | agenda-vistas-front | 2 | 5 | C-08, C-13 | MEDIO | Sí |
| C-20 | agenda-acciones-front | 2 | 5 | C-07, C-08, C-09, C-19 | MEDIO | Sí |
| C-21 | integracion-e2e-demo | 3 | 6 (todos) | C-12, C-15, C-16, C-17, C-18, C-20 | MEDIO | Sí |
| C-22 | wa-me-link-agenda | 4 | 5 | C-19 | BAJO | No — recortable |
| C-23 | ics-turno | 4 | 3 | C-18 | BAJO | No — recortable |
| C-24 | exportacion-csv | 4 | 1 | C-08, C-09 | BAJO | No — recortable |
| C-25 | lista-espera-simple | 4 | 2 | C-08, C-20 | MEDIO | No — recortable |

Primer change recomendado: **C-01 `contrato-api`** (`/opsx:propose contrato-api`).

---

## FASE 0 — Gates de coordinación (contrato, entorno, base)

> Los tres changes de esta fase son secuenciales y bloquean el trabajo paralelo. Son cortos a propósito: mergear rápido, con revisión de al menos otro integrante.

### [C-01] `contrato-api`
- **Estado**: `[x]` hecho (archivado 2026-11-24, PR #1)
- **Scope**: Contrato de API versionado en el repo (DD-11); es lo primero que se mergea a `main`
  - `docs/api/openapi.yaml` (OpenAPI 3.1) con **todos** los endpoints del borrador de KB 08 §Borrador de recursos y endpoints REST: auth (`POST /api/auth/login`, `GET /api/auth/me`), públicos (`GET /api/public/prestaciones`, `GET /api/public/disponibilidad`, `POST /api/public/turnos`, `GET /api/public/turnos/{token}`, `POST /api/public/turnos/{token}/confirmar`, `POST /api/public/turnos/{token}/cancelar`), configuración (`/api/prestaciones`, `/api/horarios-atencion`, `/api/bloqueos`, `/api/bloqueos/previsualizar`), pacientes (`/api/pacientes`), turnos (`/api/turnos`, `PATCH .../reprogramar`, `POST .../confirmar`, `POST .../cancelar`, `PATCH .../estado`) y `GET /api/disponibilidad`
  - Schemas de request/response (DTOs) con tipos, formatos y validaciones: instantes ISO-8601 con offset, fechas `YYYY-MM-DD`, DNI normalizado, enum `EstadoTurno` (`PENDIENTE|CONFIRMADO|CANCELADO|ASISTIO|AUSENTE`), `cancelado_por`, `ultimoContacto`, indicador de "pendiente a menos de 24 h" en `GET /api/turnos`
  - Resolver y fijar las ambigüedades del borrador: parámetros de `GET /api/public/disponibilidad` y `GET /api/disponibilidad` (el flujo usa `fecha`, el borrador `desde`/`hasta`), forma de la respuesta de horarios libres (agrupados por día), respuesta del login (`token`, `expiraEn`, `nombre`), body de `POST /api/bloqueos` (`cancelarTurnosAfectados`) y del 409 con la lista de turnos afectados, resumen público del turno (sin DNI, email ni teléfono)
  - `docs/api/errores.md`: formato de error uniforme (`timestamp, status, error, codigo, mensaje, path, detalles`), tabla de códigos HTTP unificados (400/401/404/409/422) y **lista final de códigos de negocio** (`HORARIO_OCUPADO`, `HORARIO_NO_DISPONIBLE`, `CANCELACION_FUERA_DE_PLAZO`, `CONSENTIMIENTO_REQUERIDO`, `TOKEN_INVALIDO`, `TRANSICION_INVALIDA`, `DNI_DUPLICADO`, `CREDENCIALES_INVALIDAS`, y los que falten, por ejemplo confirmación fuera de ventana de 48 h y prestación inactiva)
  - `docs/api/tipos.ts`: tipos TypeScript del contrato (se copian a `frontend/src/api/types.ts` en C-02)
  - `docs/api/ejemplos/*.json`: un request y un response de ejemplo por endpoint, incluidos los de error; son los fixtures con los que el front trabaja antes de que exista el backend
  - Tabla de matriz de códigos por endpoint (qué código devuelve cada caso: éxito, 400, 401, 404, 409, 422)
  - Sin código de aplicación ni infraestructura; solo documentación revisable
  - Tests: validar el YAML con un linter de OpenAPI (por ejemplo `npx @redocly/cli lint docs/api/openapi.yaml`)
- **Dependencias**: ninguna
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/08_arquitectura_propuesta.md` §Borrador de recursos y endpoints REST, §Códigos HTTP unificados, §Formato de error uniforme
  - `knowledge-base/06_funcionalidades.md` (criterios de aceptación de las US: definen los códigos por caso)
  - `knowledge-base/03_actores_y_roles.md` §Rutas públicas, §Rutas privadas, §Por qué login, confirmar y cancelar son POST
  - `knowledge-base/09_decisiones_y_supuestos.md` DD-11, DD-12, DD-14
  - `knowledge-base/10_preguntas_abiertas.md` (decisiones pendientes que el contrato debe cerrar)

---

### [C-02] `docker-scaffold`
- **Estado**: `[x]` hecho (archivado 2026-11-24, PR #3)
- **Nota (desvíos aprobados al aplicar)**: el health check es `/actuator/health` (no `/api/health`, que no existe en el contrato de C-01) y la imagen de build del front usa Node 24 (no `node:20-alpine`). C-02 además agrega al contrato el error 500 `ERROR_INTERNO` (respuesta genérica a errores inesperados).
- **Scope**: Entorno idéntico para los 6 desde el día uno + esqueletos de proyecto (US-016, parte infraestructura)
  - `docker-compose.yml` con `db` (`postgres:16`, healthcheck `pg_isready`, volumen `pgdata`), `backend`, `frontend` y `mailpit` (`axllent/mailpit`, UI 8025, SMTP 1025); `backend` depende de `db` healthy, `frontend` depende de `backend`; puertos configurables por `.env`
  - `.env.example` versionado con **todas** las variables de KB 08 §Variables de entorno y valores de dev para Mailpit; `.env` en `.gitignore` (con excepción de `.env.example`)
  - `backend/`: proyecto Spring Boot 3.5 + Java 21 + Maven (wrapper incluido) con **todas** las dependencias del proyecto ya en el `pom.xml` (web, data-jpa, validation, security, mail, postgresql, jjwt, testcontainers, test) para que nadie toque el `pom.xml` después; paquete base `ar.turnos.odontologia` con las carpetas por feature vacías (auth, prestacion, horario, bloqueo, paciente, turno, disponibilidad, notificacion, seed, common, config, security)
  - `backend/src/main/resources/application.yml`: `ddl-auto=update`, `defer-datasource-initialization=true`, `sql.init.mode=always`, `sql.init.separator=^^^`, `jdbc.time_zone=UTC`, propiedades `app.*` por `@ConfigurationProperties`, mail por variables de entorno
  - Piezas transversales compartidas: `ApiError` + `GlobalExceptionHandler` (formato uniforme), `ClockConfig`, `SchedulingConfig`, `SecurityConfig` con `permitAll` en `/api/auth/login` y `/api/public/**` y `authenticated` en el resto, `GET /api/health` (o actuator)
  - `backend/Dockerfile` multi-stage (Maven → JRE 21, usuario no root)
  - `frontend/`: proyecto Vite + TypeScript vanilla con dos entry points (`index.html` público y `admin/index.html`), router mínimo en `src/shared/` con **todas las rutas ya registradas con pantallas placeholder** (`/reservar`, `/turno/confirmar`, `/turno/cancelar`, `/admin/login`, `/admin/agenda`, `/admin/configuracion`, `/admin/pacientes`), cliente `fetch` base en `src/api/` con manejo del error uniforme, `src/api/types.ts` (copia de `docs/api/tipos.ts`), página de configuración con tres secciones en archivos separados para que dos personas no choquen
  - `frontend/Dockerfile` multi-stage (node:20-alpine → nginx:alpine) y `nginx.conf` con proxy `/api` → `backend:8080` y fallback SPA a cada `index.html` (mismo origen, sin CORS); proxy `/api` en `vite.config.ts` para dev
  - Vitest configurado en el front y `mvn test` corriendo en el back (un test de humo en cada uno)
  - `README` mínimo con el comando único: `cp .env.example .env && docker compose up --build`, URLs de app, admin y Mailpit, y el flujo de dev diario (`docker compose up db mailpit` + backend y front fuera de Docker)
  - Criterio de salida: `docker compose up --build` levanta los 4 servicios en la máquina de cualquiera de los 6; el front responde en `:8080` y `/api/health` responde por el proxy
- **Dependencias**: C-01
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/11_docker_y_entornos.md`
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios, §Esquema de base de datos, §Variables de entorno, §Seguridad
  - `knowledge-base/09_decisiones_y_supuestos.md` DD-01, DD-02, DD-04, DD-07, DD-09
  - `knowledge-base/02_descripcion_general.md` §Stack

---

### [C-03] `entidades-base-y-seed`
- **Estado**: `[x]` hecho (archivado 2026-11-24, PR #5)
- **Scope**: Modelo de datos completo, sin lógica de negocio. Mini-gate: mergear rápido (~1-2 h) porque todo el backend lo necesita
  - Entidades JPA: `Odontologo`, `Paciente`, `Prestacion`, `HorarioAtencion`, `Bloqueo`, `Turno` con los tipos y constraints de KB 04 (`timestamptz`, DNI único, token único, `email_contacto`, `telefono_contacto`, `consentimiento_at`, `recordatorio_enviado`, `alerta_pendiente_enviada`, `cancelado_por`)
  - Enum `EstadoTurno` (`@Enumerated(STRING)`) y enum de `cancelado_por`
  - Repositorios Spring Data (solo los métodos CRUD y las consultas por rango de KB 04 que usan varios dominios: turnos activos por rango, bloqueos por rango, horarios por día)
  - `schema-extra.sql` idempotente con `btree_gist` y la constraint `turno_sin_solapamiento` (separador `^^^`)
  - Interfaz `NotificacionTurnos` (un método por mail de KB 05 "Mails del sistema": confirmación de reserva, aviso, recordatorio, reprogramación, reprogramación a menos de 24 h, cancelación por odontólogo, cancelación por bloqueo, turno no confirmado al odontólogo) con implementación no-op registrada como bean por defecto; C-11 la reemplaza
  - `DataSeeder` (`CommandLineRunner`, idempotente): odontólogo desde `SEED_ODONTOLOGO_*` con BCrypt, 4 prestaciones (Consulta 30, Limpieza 45, Extracción 45, Arreglo/caries 60) y horarios lunes a viernes 09-13 y 15-19, solo si las tablas están vacías; datos ficticios
  - Traducción de `23P01` (`turno_sin_solapamiento`) a 409 `HORARIO_OCUPADO` en el `GlobalExceptionHandler`
  - Tests: integración con Testcontainers (PostgreSQL real): dos inserts que se pisan → violación de exclusión; adyacentes `[)` → OK; turno cancelado no bloquea; seed idempotente al reiniciar
- **Dependencias**: C-02
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` (completo: entidades, ERD, máquina de estados, seed)
  - `knowledge-base/08_arquitectura_propuesta.md` §Esquema de base de datos, §Qué es `btree_gist`
  - `knowledge-base/05_reglas_de_negocio.md` §Mails del sistema, RN-TUR-02
  - `knowledge-base/09_decisiones_y_supuestos.md` DD-04, DD-05, DD-10, DD-12

---

## FASE 1 — Backend por dominio (en paralelo después de C-03)

> Cada change vive en su carpeta por feature. Los changes de seguridad de rutas privadas se prueban con `@WithMockUser` hasta que C-04 esté mergeado.

### [C-04] `auth-jwt-backend`
- **Estado**: `[x]` hecho (archivado 2026-11-25, PR #9)
- **Scope**: Login del odontólogo con JWT (US-001)
  - `POST /api/auth/login` — email + contraseña, BCrypt, devuelve JWT HS256 + expiración + nombre; 401 genérico `CREDENCIALES_INVALIDAS` sin revelar si falló email o contraseña
  - `GET /api/auth/me` — datos del odontólogo autenticado
  - `JwtService` (secreto de `JWT_SECRET`, expiración `JWT_EXPIRATION_MINUTES`), `JwtAuthFilter`, `UserDetailsService` sobre `Odontologo`
  - `SecurityFilterChain` final: `permitAll` en `/api/auth/login` y `/api/public/**`, `authenticated` en el resto de `/api/**`; stateless; 401 JSON uniforme para ausente/vencido/inválido
  - Tests: login correcto, contraseña incorrecta, email inexistente (mismo mensaje), token vencido → 401, ruta privada sin token → 401, ruta pública → 200, hash BCrypt nunca expuesto
- **Dependencias**: C-03
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/03_actores_y_roles.md` (RBAC, rutas públicas y privadas)
  - `knowledge-base/05_reglas_de_negocio.md` §Autenticación (RN-AU-01..04)
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad
  - `knowledge-base/07_flujos_principales.md` Flujo 1
  - `knowledge-base/09_decisiones_y_supuestos.md` DD-03, SU-06

---

### [C-05] `prestaciones-horarios-backend`
- **Estado**: `[x]` hecho (archivado 2026-11-24, PR #7)
- **Scope**: Configuración del consultorio, parte backend (US-002, US-003)
  - `GET/POST /api/prestaciones`, `PUT /api/prestaciones/{id}`, `DELETE /api/prestaciones/{id}` (422 si tiene turnos: se desactiva); `GET /api/public/prestaciones` (solo activas)
  - `GET/POST /api/horarios-atencion`, `PUT/DELETE /api/horarios-atencion/{id}`; 422 si `inicio >= fin` o si el rango se solapa con otro del mismo día
  - Validaciones Bean Validation (duración > 0); cambiar la duración no modifica turnos existentes
  - DTOs y mappers manuales; las entidades no se exponen
  - Tests: CRUD, prestación con turnos no se borra, rangos solapados → 422, rangos múltiples por día OK, pública solo devuelve activas
- **Dependencias**: C-03
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-002, US-003
  - `knowledge-base/05_reglas_de_negocio.md` RN-PRE, RN-HOR
  - `knowledge-base/04_modelo_de_datos.md` §Prestacion, §HorarioAtencion
  - `docs/api/openapi.yaml` (C-01)

---

### [C-06] `disponibilidad-calculo`
- **Estado**: `[x]` hecho (archivado 2026-11-25, PR #16)
- **Scope**: Cálculo de horarios libres, núcleo del sistema (US-005)
  - `DisponibilidadService` como **función pura** sobre listas de intervalos con `Clock` inyectado: horarios de atención − bloqueos − turnos activos − pasado − ventana; discretización con paso `app.agenda.paso-minutos` (15) alineada al inicio del rango de atención; el turno completo debe entrar en el rango
  - Ventana pública de 2 h a 60 días (configurable); consulta del odontólogo solo con "no en el pasado" (RN-DIS-06)
  - Método `validarHorario(prestacion, inicio, contexto)` reutilizable por reserva, carga manual y reprogramación (422 `HORARIO_NO_DISPONIBLE`); **publicar la interfaz en la primera hora** para que C-07 y C-08 la usen
  - `GET /api/public/disponibilidad` (pública) y `GET /api/disponibilidad` (JWT, sin ventana de 2 h / 60 días); lista vacía si no hay horarios (no es error); prestación inactiva → 422, inexistente → 404
  - Conversión de horas locales de atención a instantes con `America/Argentina/Buenos_Aires`
  - Tests (TDD estricto, prioridad 1): rangos múltiples por día, bloqueo parcial, turno activo que parte el rango, duración que no entra, paso de 15, bordes de 2 h y 60 días con `Clock` fijo, turno cancelado no ocupa, día sin atención
- **Dependencias**: C-03
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/08_arquitectura_propuesta.md` §Cálculo de horarios libres, §Estrategia de tests mínimos
  - `knowledge-base/05_reglas_de_negocio.md` RN-DIS-01..06
  - `knowledge-base/06_funcionalidades.md` US-005
  - `knowledge-base/04_modelo_de_datos.md` §Almacenamiento y zona horaria
  - `knowledge-base/09_decisiones_y_supuestos.md` SU-04

---

### [C-07] `turnos-alta-reserva-backend`
- **Estado**: `[ ]` pendiente
- **Scope**: Crear turnos: reserva pública y carga manual (US-006, parte backend de US-010)
  - `POST /api/public/turnos` — valida formato (400 con `detalles`), consentimiento obligatorio (422 `CONSENTIMIENTO_REQUERIDO`, guarda `consentimiento_at`), prestación activa, horario con `validarHorario` (C-06); resuelve paciente por DNI normalizado (crea o reutiliza **sin modificarlo**); email y teléfono van al turno; token opaco de 32 bytes `SecureRandom` Base64 URL-safe
  - `POST /api/turnos` (JWT) — carga manual con paciente existente o alta rápida, `emailContacto` y `telefonoContacto`; sin mínimo de 2 h, solo no en el pasado; `consentimiento_at` null
  - Estado inicial según anticipación (RN-TUR-11): 24 h o más → `PENDIENTE` con `recordatorio_enviado=false`; menos de 24 h → `CONFIRMADO` con `recordatorio_enviado=true` y `alerta_pendiente_enviada=true`; `fin` = `inicio` + duración persistida
  - Inserción con `saveAndFlush` dentro del service para capturar la violación de exclusión y devolver 409 `HORARIO_OCUPADO`
  - Mail por anticipación vía `NotificacionTurnos` (>48 h confirmación solo Cancelar; 24-48 h recordatorio en el momento y marca `recordatorio_enviado=true` si salió; <24 h aviso sin botones); un fallo de SMTP no revierte la reserva (RN-NOT-05)
  - `GET /api/public/turnos/{token}` — solo lectura, resumen mínimo sin DNI, email ni teléfono; 404 `TOKEN_INVALIDO` si no existe
  - Tests (prioridad 1 y 2): carrera de dos hilos reservando el mismo horario → uno 201 y otro 409 (Testcontainers), paciente existente no se modifica, estado inicial en los bordes de 24 h, sin consentimiento → 422, fallo de mail no revierte
- **Dependencias**: C-03, C-06
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-006, US-010
  - `knowledge-base/07_flujos_principales.md` Flujo 2 (incluye la carrera por el mismo horario)
  - `knowledge-base/05_reglas_de_negocio.md` RN-TUR, RN-PAC, RN-TOK, RN-NOT-01, RN-NOT-04, RN-DAT-02
  - `knowledge-base/04_modelo_de_datos.md` §Turno, §Máquina de estados del turno
  - `knowledge-base/08_arquitectura_propuesta.md` §Token opaco, §Códigos HTTP unificados

---

### [C-08] `turnos-acciones-backend`
- **Estado**: `[ ]` pendiente
- **Scope**: Ciclo de vida del turno y agenda (US-007, US-008, US-009 backend, US-011, US-012, US-013, US-021)
  - `GET /api/turnos?desde&hasta&estado` — agenda por rango con `telefonoContacto` y el indicador de "pendiente a menos de 24 h"; `GET /api/turnos/{id}`
  - `POST /api/public/turnos/{token}/confirmar` — solo `PENDIENTE`, solo con 48 h o menos de anticipación (422 si más, RN-NOT-08), idempotente si ya está confirmado, 422 si cancelado/pasado/terminal, 404 si el token no existe
  - `POST /api/public/turnos/{token}/cancelar` — hasta 24 h antes (`cancelado_por=PACIENTE`), 422 `CANCELACION_FUERA_DE_PLAZO` con menos de 24 h (con el contacto del consultorio `APP_CONSULTORIO_CONTACTO`), idempotente si ya está cancelado
  - `POST /api/turnos/{id}/confirmar` (odontólogo, sin restricción de 48 h), `POST /api/turnos/{id}/cancelar` (`cancelado_por=ODONTOLOGO`, siempre; email al paciente), `PATCH /api/turnos/{id}/estado` (`ASISTIO`/`AUSENTE`, solo desde `PENDIENTE`/`CONFIRMADO` y solo desde la hora de inicio; 422 antes)
  - `PATCH /api/turnos/{id}/reprogramar` — solo activos, `validarHorario` (422), exclusión (409), recalcula `fin` y **estado** según RN-TUR-11, reinicia flags (`recordatorio_enviado`, `alerta_pendiente_enviada`) según corresponda y manda el Mail 3 / 3b
  - Máquina de estados centralizada (transiciones válidas, 422 `TRANSICION_INVALIDA` en las demás)
  - Tests: cada transición válida e inválida, borde exacto de 24 h y de 48 h con `Clock` fijo, idempotencia, reprogramar cruzando el umbral de 24 h en ambos sentidos, marcar asistencia antes de la hora → 422, cancelado no ocupa el horario
- **Dependencias**: C-04, C-07
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-007, US-008, US-009, US-011, US-012, US-013, US-021
  - `knowledge-base/07_flujos_principales.md` Flujo 3, Flujo 6
  - `knowledge-base/05_reglas_de_negocio.md` RN-TUR, RN-CAN, RN-TOK, RN-NOT-07, RN-NOT-08
  - `knowledge-base/04_modelo_de_datos.md` §Máquina de estados del turno

---

### [C-09] `pacientes-backend`
- **Estado**: `[ ]` pendiente
- **Scope**: ABM de pacientes, parte backend (US-014)
  - `GET /api/pacientes?q` — búsqueda por DNI o apellido; cada ítem con `ultimoContacto` (email y teléfono del último turno o null)
  - `GET /api/pacientes/{id}` (con `ultimoContacto`), `POST /api/pacientes` (422 `DNI_DUPLICADO`), `PUT /api/pacientes/{id}` (única vía para modificar datos del paciente)
  - Normalización del DNI a solo dígitos; sin endpoint de borrado (un paciente con turnos no se borra)
  - Tests: búsqueda por DNI y por apellido, DNI duplicado → 422, `ultimoContacto` correcto con varios turnos, el paciente no tiene email ni teléfono propios
- **Dependencias**: C-03, C-04
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-014
  - `knowledge-base/05_reglas_de_negocio.md` RN-PAC-01..04, RN-DAT-01
  - `knowledge-base/04_modelo_de_datos.md` §Paciente
  - `knowledge-base/09_decisiones_y_supuestos.md` DD-12

---

### [C-10] `bloqueos-backend`
- **Estado**: `[x]` hecho (archivado 2026-11-25, PR #10)
- **Scope**: Bloqueos con aviso de turnos afectados (US-004, parte backend)
  - `GET /api/bloqueos?desde&hasta`, `DELETE /api/bloqueos/{id}`
  - `POST /api/bloqueos/previsualizar` — lista los turnos activos dentro del rango sin modificar nada
  - `POST /api/bloqueos` — con `cancelarTurnosAfectados=true` crea el bloqueo y cancela los turnos afectados (`CANCELADO`, `cancelado_por=ODONTOLOGO`) **en una sola transacción** y manda el Mail 5 a cada email de contacto; con `false` y turnos afectados responde 409 con la lista; sin turnos afectados crea directo
  - Validación `inicio < fin` (422); los bloqueos pueden solaparse entre sí
  - Tests: previsualizar no modifica, cancelación en transacción (si falla el mail el bloqueo y las cancelaciones igual quedan, RN-NOT-05), 409 sin confirmación, bloqueo sin afectados, el bloqueo reduce la disponibilidad de C-06
- **Dependencias**: C-03, C-04
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-004
  - `knowledge-base/07_flujos_principales.md` Flujo 5
  - `knowledge-base/05_reglas_de_negocio.md` RN-BLO-01..05, RN-CAN-04
  - `knowledge-base/04_modelo_de_datos.md` §Bloqueo

---

### [C-11] `notificaciones-email-backend`
- **Estado**: `[ ]` pendiente
- **Scope**: Envío real de emails (parte de US-006, US-011, US-012, US-004, base de US-015 y US-022)
  - `EmailService` sobre Spring Mail: remitente `APP_MAIL_FROM`, links construidos con `APP_FRONTEND_BASE_URL` (`/turno/confirmar?token=...`, `/turno/cancelar?token=...`), horas renderizadas en Buenos Aires
  - Plantillas en `resources/templates/` para los 8 mails de KB 05 §Mails del sistema: 1 Confirmación de reserva (solo Cancelar), 1b Aviso (sin botones), 2 Recordatorio (Confirmar y Cancelar), 3 Reprogramación (solo Cancelar), 3b Reprogramación a menos de 24 h (sin botones), 4 Cancelación por el odontólogo, 5 Cancelación por bloqueo (invita a reservar de nuevo), 6 Turno no confirmado (al odontólogo, con teléfono de contacto)
  - Implementación real de `NotificacionTurnos` (C-03) que reemplaza al no-op; un fallo de SMTP se loguea y no se propaga (RN-NOT-05); no se loguean DNI, email ni teléfono en claro
  - Funciona con Mailpit y con Gmail SMTP cambiando **solo** variables de entorno (`SPRING_MAIL_*`, `APP_MAIL_FROM`)
  - Tests: cada plantilla incluye exactamente los botones que corresponden (el Recordatorio es el único con Confirmar), destinatario correcto (email del turno vs. email de login del odontólogo), SMTP caído no lanza excepción hacia el caller (GreenMail o mock del `JavaMailSender`)
- **Dependencias**: C-03
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` RN-NOT-01..08, §Mails del sistema
  - `knowledge-base/06_funcionalidades.md` US-006, US-015, US-022
  - `knowledge-base/11_docker_y_entornos.md` §Entornos, §Gmail para la demo
  - `knowledge-base/09_decisiones_y_supuestos.md` DD-07, SU-07

---

### [C-12] `recordatorio-alerta-job`
- **Estado**: `[ ]` pendiente
- **Scope**: Job programado único con dos tareas (US-015, US-022)
  - `RecordatorioJob` con `@Scheduled(fixedDelay)` cada `APP_RECORDATORIO_INTERVALO_MIN` (15 min), con `Clock` inyectado para poder testear
  - Tarea 1: turnos `PENDIENTE` con inicio a `APP_RECORDATORIO_HORAS_ANTES` (48 h) o menos y `recordatorio_enviado=false` → Recordatorio al email del turno → `recordatorio_enviado=true` (también reintenta los que fallaron al crear)
  - Tarea 2: turnos `PENDIENTE` con inicio a menos de `APP_ALERTA_PENDIENTE_HORAS` (24 h) y `alerta_pendiente_enviada=false` → mail "Turno no confirmado" al odontólogo (email de login) → `alerta_pendiente_enviada=true`
  - Un error de SMTP no frena el lote ni marca el flag; nunca se envía recordatorio a `CONFIRMADO`; los que nacen `CONFIRMADO` no entran en la alerta
  - Consultas con el índice `(estado, inicio)`
  - Tests: selección por estado y por borde de 48 h / 24 h, flags idempotentes (no reenvía), reintento tras fallo, lote que sigue después de un error, reprogramado reinicia flags
- **Dependencias**: C-11
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/07_flujos_principales.md` Flujo 4
  - `knowledge-base/06_funcionalidades.md` US-015, US-022
  - `knowledge-base/05_reglas_de_negocio.md` RN-NOT-02..06
  - `knowledge-base/09_decisiones_y_supuestos.md` DD-06, DD-13

---

## FASE 2 — Frontend por pantalla (arrancan con fixtures del contrato, mergean contra el backend real)

> Todo TypeScript vanilla con Vite. Cada pantalla en su archivo/carpeta para no pisarse. Reusar el router, el cliente API y los componentes DOM de `src/shared/` y `src/api/` armados en C-02.

### [C-13] `login-front-y-guard`
- **Estado**: `[ ]` pendiente
- **Scope**: Acceso del odontólogo, parte front (US-001)
  - `src/admin/login.ts`: formulario email + contraseña contra `POST /api/auth/login`, manejo del 401 genérico
  - Estado de auth en `src/shared/` (token en `localStorage`, expiración), cliente API agrega `Authorization: Bearer`, guard del router: sin token válido redirige a `/admin/login`; ante 401 limpia el token y redirige
  - Layout del área admin con navegación (Agenda, Configuración, Pacientes) y botón cerrar sesión
  - Tests (Vitest): guard con y sin token, interceptor de 401, formato del header
- **Dependencias**: C-02, C-04
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-001
  - `knowledge-base/07_flujos_principales.md` Flujo 1
  - `knowledge-base/03_actores_y_roles.md` §Rutas privadas
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios (`frontend/src`)

---

### [C-14] `configuracion-prestaciones-horarios-front`
- **Estado**: `[x]` hecho (archivado 2026-11-25, PR #12)
- **Scope**: Pantalla `/admin/configuracion`, secciones de prestaciones y horarios (US-002, US-003)
  - `src/admin/configuracion/prestaciones.ts`: listado (activas e inactivas), alta, edición de nombre y duración, activar/desactivar; mensaje claro cuando el borrado devuelve 422
  - `src/admin/configuracion/horarios.ts`: rangos por día de la semana (varios por día), alta, edición, baja; muestra los 422 de rango inválido o solapado
  - Validación de formato en el front (duración > 0, `inicio < fin`) y manejo del error uniforme del contrato
- **Dependencias**: C-02, C-05, C-13
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-002, US-003
  - `knowledge-base/03_actores_y_roles.md` §Rutas privadas
  - `docs/api/openapi.yaml` y `docs/api/ejemplos/` (C-01)

---

### [C-15] `configuracion-bloqueos-front`
- **Estado**: `[x]` hecho (archivado 2026-11-25, PR #13)
- **Scope**: Sección de bloqueos de `/admin/configuracion` (US-004, parte front)
  - `src/admin/configuracion/bloqueos.ts`: listado, alta con inicio, fin y motivo opcional, baja
  - Flujo de dos pasos: previsualizar los turnos afectados (`POST /api/bloqueos/previsualizar`) → mostrar la lista con aviso → confirmar con `cancelarTurnosAfectados=true`; sin afectados crea directo
  - Fechas y horas mostradas en `America/Argentina/Buenos_Aires` (`Intl.DateTimeFormat('es-AR', …)`)
- **Dependencias**: C-10, C-14
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-004
  - `knowledge-base/07_flujos_principales.md` Flujo 5
  - `knowledge-base/08_arquitectura_propuesta.md` §Zona horaria

---

### [C-16] `pacientes-front`
- **Estado**: `[ ]` pendiente
- **Scope**: Pantalla `/admin/pacientes` (US-014)
  - `src/admin/pacientes.ts`: listado con búsqueda por DNI o apellido, alta y edición (nombre, apellido, DNI), error `DNI_DUPLICADO` mostrado en el campo
  - Detalle con `ultimoContacto` en solo lectura
  - Componente reutilizable de búsqueda de paciente en `src/shared/` (lo usa C-20 para la carga manual de turnos)
- **Dependencias**: C-09, C-13
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-014
  - `knowledge-base/05_reglas_de_negocio.md` RN-PAC
  - `docs/api/ejemplos/` (C-01)

---

### [C-17] `reserva-publica-front`
- **Estado**: `[ ]` pendiente
- **Scope**: Página pública de reserva `/reservar` (US-005, US-006, parte front)
  - `src/public/reservar.ts`: elegir prestación (`GET /api/public/prestaciones`) → elegir fecha y horario libre (`GET /api/public/disponibilidad`; lista vacía = "sin horarios ese día") → formulario con nombre, apellido, DNI, email, teléfono y casilla obligatoria de tratamiento de datos con texto de finalidad → `POST /api/public/turnos`
  - Validación de formato en el front (DNI, email, teléfono) con los `detalles` por campo del 400
  - 409 `HORARIO_OCUPADO`: mensaje "ese horario se acaba de ocupar, elegí otro" y refresco de horarios; 422: mensajes por código
  - Pantalla de resumen del turno (sin datos de otros) con aviso de "revisá tu email"
  - Responsive (el paciente reserva desde el celular)
  - Tests (Vitest): funciones puras de formato de fechas, validadores y cliente API
- **Dependencias**: C-02, C-06, C-07
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-005, US-006
  - `knowledge-base/07_flujos_principales.md` Flujo 2
  - `knowledge-base/08_arquitectura_propuesta.md` §Zona horaria, §Formato de error uniforme
  - `knowledge-base/05_reglas_de_negocio.md` RN-DAT-02

---

### [C-18] `turno-token-front`
- **Estado**: `[ ]` pendiente
- **Scope**: Páginas de confirmar y cancelar por link del email (US-007, US-008, parte front)
  - `src/public/confirmar.ts` (`/turno/confirmar?token=...`) y `src/public/cancelar.ts` (`/turno/cancelar?token=...`): primero `GET /api/public/turnos/{token}` (solo lectura) para mostrar el resumen; **recién al apretar el botón** se hace el `POST` (nunca cambia el estado al abrir el link)
  - Cancelar pide confirmación en pantalla antes del `POST`
  - Estados: éxito, idempotente ("ya estaba confirmado/cancelado"), 404 "link inválido", 422 con mensaje (todavía no se puede confirmar con más de 48 h, cancelación con menos de 24 h con el contacto del consultorio, turno terminal o cancelado)
  - No muestra DNI, email ni teléfono
- **Dependencias**: C-08, C-17
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-007, US-008
  - `knowledge-base/07_flujos_principales.md` Flujo 3
  - `knowledge-base/03_actores_y_roles.md` §Por qué login, confirmar y cancelar son POST

---

### [C-19] `agenda-vistas-front`
- **Estado**: `[ ]` pendiente
- **Scope**: Pantalla `/admin/agenda`, vistas de lectura (US-009)
  - `src/admin/agenda.ts` + `src/admin/agenda/`: vista diaria y semanal con paciente, prestación, hora de inicio/fin, estado y teléfono de contacto del turno; navegación entre días y semanas
  - `PENDIENTE` marcados como "sin confirmar"; los `PENDIENTE` a menos de 24 h resaltados (con teléfono visible); cancelados atenuados u ocultables
  - Bloqueos y horarios fuera de atención distinguibles (consume `/api/bloqueos` y `/api/horarios-atencion`)
  - Horas en `America/Argentina/Buenos_Aires`; funciones puras de grilla y formato testeadas con Vitest
  - Deja en el DOM los puntos de enganche (botones/slots) que completa C-20
- **Dependencias**: C-08, C-13
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-009
  - `knowledge-base/07_flujos_principales.md` Flujo 6
  - `knowledge-base/08_arquitectura_propuesta.md` §Zona horaria

---

### [C-20] `agenda-acciones-front`
- **Estado**: `[ ]` pendiente
- **Scope**: Acciones del odontólogo sobre la agenda (US-010, US-011, US-012, US-013, US-021, parte front)
  - Cargar turno manual: buscar paciente (componente de C-16) o alta rápida, elegir prestación y horario libre (`GET /api/disponibilidad`), email y teléfono de contacto precargados desde `ultimoContacto` y editables; 409 y 422 con mensajes claros
  - Reprogramar: elegir nuevo horario libre y mostrar el estado resultante (pendiente o confirmado según anticipación)
  - Botones Cancelar (`POST /api/turnos/{id}/cancelar`), Confirmar (`POST /api/turnos/{id}/confirmar`, en especial en los `PENDIENTE` resaltados) y Marcar asistió / ausente (deshabilitado antes de la hora de inicio; 422 si se intenta)
  - Confirmación en pantalla antes de cancelar y de marcar estados terminales
- **Dependencias**: C-07, C-08, C-09, C-19
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-010, US-011, US-012, US-013, US-021
  - `knowledge-base/07_flujos_principales.md` Flujo 6
  - `knowledge-base/05_reglas_de_negocio.md` RN-TUR-08, RN-TUR-10, RN-TUR-11

---

## FASE 3 — Integración y cierre del MVP

### [C-21] `integracion-e2e-demo`
- **Estado**: `[ ]` pendiente
- **Scope**: Dejar el MVP demostrable el 2026-11-25
  - `docker compose down -v && docker compose up --build` desde cero en la máquina de al menos dos integrantes distintos; seed presente (odontólogo ficticio, prestaciones, horarios)
  - Recorrido manual de los 6 flujos de KB 07: login, reserva pública (incluida la carrera), confirmar/cancelar por token con las reglas de 48 h y 24 h, recordatorio y alerta (con `APP_RECORDATORIO_INTERVALO_MIN` bajo para probar), bloqueo con turnos afectados, agenda; los mails se verifican en Mailpit
  - Cambio a Gmail SMTP solo con variables de entorno (contraseña de aplicación, STARTTLS), prueba de envío y revisión de spam; `APP_FRONTEND_BASE_URL` apuntando a la URL de la demo
  - Corrección de bugs de integración detectados (cada fix con su test); `mvn test` y `npm test` en verde
  - README final con requisitos, comando único, URLs, credenciales del seed ficticio y guion de demo; checklist de aceptación de las US `[MVP]` marcado
  - Pasada de ley 25.326: no se loguean DNI/email/teléfono, `.env` fuera de git, respuestas públicas sin datos sensibles
- **Dependencias**: C-12, C-15, C-16, C-17, C-18, C-20
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/07_flujos_principales.md` (todos los flujos)
  - `knowledge-base/11_docker_y_entornos.md`
  - `knowledge-base/01_vision_y_objetivos.md` (criterios de éxito)
  - `knowledge-base/10_preguntas_abiertas.md` (cuenta Gmail, forma de presentación)

---

## FASE 4 — Opcionales (RECORTABLES si no llegamos)

> Solo si C-21 ya está mergeado o si alguien queda libre y el MVP está estable. **No bloquean la entrega.** Orden sugerido de recorte (primero lo que se corta): C-25, C-24, C-23, C-22.

### [C-22] `wa-me-link-agenda` — RECORTABLE si no llegamos
- **Estado**: `[ ]` pendiente
- **Scope**: US-018 (opcional)
  - Botón en la agenda que abre `https://wa.me/<telefono>?text=<mensaje>` con el mensaje precargado (turno, fecha, hora); envío manual, sin API
  - Función pura que arma el link a partir del teléfono de contacto del turno (normalización a formato internacional argentino); test en Vitest
  - Solo front, sin cambios de backend
- **Dependencias**: C-19
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-018 (Épica 8)
  - `docs/discovery/discovery.md` §6 Funcionalidades opcionales

---

### [C-23] `ics-turno` — RECORTABLE si no llegamos
- **Estado**: `[ ]` pendiente
- **Scope**: US-019 (opcional)
  - `GET /api/public/turnos/{token}/ics` que devuelve `text/calendar` con el turno (zona `America/Argentina/Buenos_Aires`); botón "Agregar al calendario" en la pantalla de resumen de C-18
  - Sin datos de contacto en el archivo; 404 si el token no existe
  - Tests: contenido del `.ics` válido, zona horaria correcta
- **Dependencias**: C-18
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-019 (Épica 8)
  - `knowledge-base/08_arquitectura_propuesta.md` §Zona horaria

---

### [C-24] `exportacion-csv` — RECORTABLE si no llegamos
- **Estado**: `[ ]` pendiente
- **Scope**: US-020 (opcional)
  - `GET /api/pacientes/export.csv` y `GET /api/turnos/export.csv?desde&hasta` (JWT, `text/csv`, UTF-8 con BOM para Excel); botones de exportar en las pantallas de pacientes y agenda
  - Escapado correcto de comas y comillas; solo columnas necesarias (datos mínimos)
  - Tests: escapado, rango de fechas, 401 sin token
- **Dependencias**: C-08, C-09
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-020 (Épica 8)
  - `knowledge-base/05_reglas_de_negocio.md` RN-DAT-01

---

### [C-25] `lista-espera-simple` — RECORTABLE si no llegamos
- **Estado**: `[ ]` pendiente
- **Scope**: US-017 (opcional; el más grande de los opcionales, el primero que se corta)
  - Entidad `ListaEspera` (paciente/contacto, fecha deseada, prestación) con `POST /api/public/lista-espera` y `GET /api/lista-espera?fecha` (JWT) — requiere ajuste explícito del contrato (change de contrato aparte, DD-11)
  - La agenda muestra la lista del día cuando el odontólogo cancela un turno; sin notificación automática
  - Tests: alta, listado por fecha, aparece al cancelar
- **Dependencias**: C-08, C-20
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` US-017 (Épica 8)
  - `knowledge-base/04_modelo_de_datos.md` (para extender el modelo)
  - `knowledge-base/09_decisiones_y_supuestos.md` DD-04, DD-11
