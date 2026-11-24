# Proposal — C-02 docker-scaffold

## Why

C-01 dejó el contrato en `main`, pero todavía no existe código: si cada uno de los 6 integrantes arma su propio `pom.xml`, `application.yml`, router o cliente HTTP, los PRs de la Fase 1 y 2 van a chocar en los mismos archivos y cada máquina va a tener un entorno distinto. C-02 es el gate 2 del roadmap (camino crítico): deja un entorno idéntico que se levanta con un solo comando y los esqueletos de backend y front con **todos los archivos compartidos ya armados**, para que desde C-03 cada uno trabaje en su carpeta y con TDD estricto (Testcontainers y Vitest ya corriendo). La entrega del MVP es el 2026-11-25: el scaffold tiene que ser mínimo y rápido de mergear.

## What Changes

**Archivos raíz — governance ALTO, revisión humana antes del apply:**
- Nuevo `docker-compose.yml` (se mantiene este nombre, no `compose.yaml`, porque así lo nombran la KB y el roadmap) con `db` (`postgres:16`, healthcheck `pg_isready`, volumen `pgdata`), `backend`, `frontend` y `mailpit`; puertos de host configurables por `.env`; `backend` espera a `db` healthy y `frontend` espera a `backend` healthy.
- Nuevo `.env.example` con **todas** las variables de KB 08 §Variables de entorno más los puertos de host, valores de ejemplo y SMTP de Mailpit por defecto. Sin secretos reales. `.gitignore` ya ignora `.env` y `.env.*` (con excepción de `.env.example`); se le suman `target/` y `dist/`.
- Nuevo `backend/pom.xml` con **todas** las dependencias que necesita el roadmap (web, data-jpa, validation, security, mail, actuator, thymeleaf para plantillas de email, postgresql, jjwt, configuration-processor; test: starter-test, security-test, testcontainers PostgreSQL) para que nadie lo toque después.
- Nuevo `README.md` mínimo con el comando único, las URLs y el flujo de desarrollo diario.

**Backend (`backend/`, paquete `ar.turnos.odontologia`):**
- Proyecto Spring Boot 3.5 + Java 21 + Maven Wrapper, carpetas por feature vacías (`auth`, `prestacion`, `horario`, `bloqueo`, `paciente`, `turno`, `disponibilidad`, `notificacion`, `seed`, `common`, `config`, `security`).
- `application.yml` con todas las claves `app.*` mapeadas explícitamente a sus variables de entorno, JPA (`ddl-auto=update`, `open-in-view=false`, Hibernate en UTC), inicialización SQL diferida con separador `^^^`, mail por variables y perfil `local` para correr fuera de Docker.
- Piezas transversales compartidas: `AppProperties` (`@ConfigurationProperties`), `ClockConfig`, `SchedulingConfig`, `SecurityConfig` (rutas públicas y privadas del contrato, stateless, 401 con el formato uniforme), `ApiError` + `GlobalExceptionHandler` para los errores genéricos del contrato (`VALIDACION`, `SOLICITUD_MAL_FORMADA`, `RECURSO_NO_ENCONTRADO`) y una excepción base de negocio que extienden los dominios.
- `schema-extra.sql` inicial con solo `CREATE EXTENSION IF NOT EXISTS btree_gist`; C-03 le agrega la constraint de exclusión.
- Healthcheck por Actuator en `/actuator/health` (fuera de `/api`, para no agregar un endpoint al contrato cerrado de C-01).
- `backend/Dockerfile` multi-stage (Maven + JDK 21 → JRE 21, usuario no root, JVM en UTC).
- Tests de humo con JUnit 5 + Testcontainers (PostgreSQL 16, nunca H2).

**Frontend (`frontend/`):**
- Proyecto Vite + TypeScript vanilla, `strict`, sin `any` (ESLint con `no-explicit-any`), dos entry points (`index.html` público y `admin/index.html`).
- Router mínimo en `src/shared/` con **todas** las rutas ya registradas y pantallas placeholder en archivos separados por pantalla (configuración partida en prestaciones, horarios y bloqueos).
- Cliente `fetch` base en `src/api/` que trata la respuesta como `unknown` y traduce el error uniforme; `src/api/types.ts` como copia exacta de `docs/api/tipos.ts` con script de sincronización y test que detecta desvíos.
- `frontend/Dockerfile` multi-stage (Node → nginx) y `nginx.conf` con proxy `/api` → `backend:8080` y fallback SPA por área (mismo origen, sin CORS); proxy `/api` en `vite.config.ts` para dev.
- Vitest configurado con tests de humo del router, del cliente y de la sincronización de tipos.

**Fuera de alcance**: entidades, seed, JWT real (C-03/C-04), plantillas de email (C-11), pantallas reales (C-13 en adelante), CI.

## Capabilities

### New Capabilities
- `entorno-ejecucion`: cómo se levanta y configura el sistema completo: servicios de Compose y su orden de arranque, configuración por `.env` sin secretos versionados, puertos configurables, mismo origen con proxy `/api`, Mailpit vs Gmail por variables y flujo de desarrollo fuera de Docker.
- `plataforma-backend`: base transversal del backend: configuración tipada por entorno con fallo temprano, zona horaria UTC en JVM y Hibernate, reglas de acceso público/privado del contrato, errores genéricos con el formato uniforme, healthcheck, inicialización SQL posterior a Hibernate e infraestructura de tests con PostgreSQL real.
- `plataforma-frontend`: base transversal del front: dos áreas con su entry point, router con todas las rutas del MVP, cliente HTTP tipado que traduce el error uniforme, tipos del contrato sincronizados con `docs/api/tipos.ts`, TypeScript estricto e infraestructura de tests con Vitest.

### Modified Capabilities
<!-- Ninguna: el backend implementa el formato de api-errores y la autenticación por ruta de api-contrato sin cambiar sus requirements. -->

## Impact

- **Archivos raíz nuevos (ALTO, revisar antes del apply)**: `docker-compose.yml`, `.env.example`, `README.md`; ajuste de `.gitignore`.
- **Archivos compartidos que quedan "pre-armados"** (los demás changes solo agregan, no reestructuran): `backend/pom.xml`, `backend/src/main/resources/application.yml`, `config/AppProperties.java`, `config/SecurityConfig.java` (C-04 agrega el filtro JWT en el punto marcado), `common/GlobalExceptionHandler.java` (C-03 agrega la traducción de `23P01`), `frontend/package.json`, `frontend/src/shared/router.ts`, `frontend/src/api/cliente.ts`.
- **Desvío respecto de CHANGES.md**: el criterio de salida "`/api/health` responde por el proxy" se reemplaza por "`GET /api/auth/me` sin token responde 401 `NO_AUTENTICADO` por el proxy" y el health queda en `/actuator/health`, porque el spec `api-contrato` fija el inventario exacto de `/api`.
- **Node**: la imagen de build usa Node 24 LTS (no `node:20-alpine` como dice KB 11, porque Node 20 ya está fuera de soporte); localmente se exige Node 22 o superior.
- **Consumidores**: C-03 (entidades, `schema-extra.sql`, `23P01`), C-04 (`SecurityConfig`, `AppProperties.jwt`), C-05..C-12 (carpetas por feature y excepción base), C-13..C-20 (router, cliente, tipos y placeholders).
- **Governance**: ALTO (CHANGES.md). Revisión de al menos otro integrante (Int 1) antes del merge.
