# Tasks — C-02 docker-scaffold

> TDD estricto: en los grupos 3 a 6 cada pieza con lógica arranca con su test en rojo (ver design.md D18). Los archivos marcados **ALTO** se revisan con el usuario antes de escribirlos.

## 0. Contrato: error 500 `ERROR_INTERNO`

- [x] 0.1 Agregar `ERROR_INTERNO` (500) a `docs/api/openapi.yaml` (enum, `status` 500, `components.responses.ErrorInterno`), `docs/api/errores.md`, `docs/api/tipos.ts`, ejemplo `docs/api/ejemplos/error.500-error-interno.json`, regla 7 de `AGENTS.md`/`CLAUDE.md` y delta spec `api-errores`; verificar con `cd docs/api/scripts && npm run verificar` y `openspec validate c-02-docker-scaffold --strict` (design D19)

## 1. Raíz del repo (ALTO)

- [x] 1.1 Ajustar `.gitignore` (sumar `target/`, `dist/`, `*.log`) y crear `.gitattributes` (`mvnw`, `*.sh` con `eol=lf`); verificar con `git check-ignore .env .env.demo backend/target frontend/dist` (todos ignorados) y `git check-ignore .env.example` (no ignorado)
- [x] 1.2 Crear `.env.example` con todas las variables de KB 08 §Variables de entorno más `FRONTEND_PORT`, `BACKEND_PORT`, `DB_PORT`, `MAILPIT_UI_PORT`, `MAILPIT_SMTP_PORT`, valores de ejemplo de KB 11 y comentarios para Gmail (design D5); verificar cruzando contra la tabla de KB 08 que no falta ninguna y que no hay secretos reales
- [x] 1.3 Crear `docker-compose.yml` con `db`, `backend`, `frontend`, `mailpit`, healthchecks, `depends_on` por salud, volumen `pgdata`, puertos por variables y tags fijos (design D3, D5); verificar con `docker compose config` sin errores

## 2. Esqueleto del backend (ALTO en pom.xml)

- [x] 2.1 Crear `backend/pom.xml` con parent Spring Boot 3.5.x, Java 21 y las dependencias de design D11, y generar el Maven Wrapper (`mvn -N wrapper:wrapper`); verificar con `./mvnw -q dependency:resolve` y que no aparece H2 en `./mvnw dependency:tree`
- [x] 2.2 Crear `TurnosApplication` (con `TimeZone.setDefault(UTC)`), las carpetas por feature con `package-info.java` y `templates/.gitkeep` según el árbol de design D1; verificar con `./mvnw -q compile`
- [x] 2.3 Crear `TestcontainersConfig` (`@ServiceConnection` PostgreSQL 16) y `src/test/resources/application-test.yml` con secretos ficticios; verificar que un test vacío `@SpringBootTest` que importa la config arranca el contenedor (queda en rojo hasta el grupo 3 si falta configuración)

## 3. Configuración, zona horaria y SQL inicial del backend

- [x] 3.1 RED/GREEN `AppPropertiesTest`: binding de `APP_*` con guión, defaults no sensibles y fallo sin `JWT_SECRET`; implementar `AppProperties` + `application.yml` con todas las claves `app.*` mapeadas explícitamente (design D6); verificar con `./mvnw test -Dtest=AppPropertiesTest`
- [x] 3.2 Crear `application-local.yml` (puerto 8081, `localhost`, valores de ejemplo de `.env.example`), `ClockConfig` y `SchedulingConfig`; verificar con `docker compose up -d db mailpit` + `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` y `curl localhost:8081/actuator/health` → `UP`
- [x] 3.3 RED/GREEN `PlataformaSmokeIT`: contexto levanta, `/actuator/health` UP, `btree_gist` presente en `pg_extension`, zona default UTC; implementar `schema-extra.sql` (solo `CREATE EXTENSION IF NOT EXISTS btree_gist` + `^^^`), config de `spring.sql.init.*` y actuator con health de mail desactivado (design D4, D8); verificar con `./mvnw test -Dtest=PlataformaSmokeIT`

## 4. Seguridad y errores uniformes del backend

- [x] 4.1 RED/GREEN `GlobalExceptionHandlerTest` (`@WebMvcTest` con controller de prueba en `src/test`): 400 `VALIDACION` con `detalles`, 400 `SOLICITUD_MAL_FORMADA` sin `detalles`, 404 `RECURSO_NO_ENCONTRADO`, `ApiException` 422 `TRANSICION_INVALIDA`, y excepción inesperada → 500 `ERROR_INTERNO` sin filtrar mensaje ni stack; implementar `ApiError`, `DetalleError`, `ApiException`, `GlobalExceptionHandler` con los mensajes de `docs/api/errores.md` (design D10); verificar con `./mvnw test -Dtest=GlobalExceptionHandlerTest`
- [x] 4.2 RED/GREEN `SecurityConfigTest`: `GET /api/turnos` sin token → 401 con formato uniforme y `codigo` `NO_AUTENTICADO`; `/api/public/...` sin token ≠ 401; `POST /api/auth/login` no es 401; sin `Set-Cookie`; implementar `SecurityConfig`, `ApiAuthenticationEntryPoint`, `PasswordEncoder` y `UserDetailsService` vacío con el punto marcado para C-04 (design D9); verificar con `./mvnw test -Dtest=SecurityConfigTest`
- [x] 4.3 Crear `backend/Dockerfile` multi-stage y `backend/.dockerignore` (design D12); verificar con `docker build -t turnos-backend ./backend` y que `./mvnw test` completo pasa

## 5. Esqueleto y tooling del frontend

- [x] 5.1 Crear `frontend/package.json` (scripts `dev`, `build` = `tsc --noEmit && vite build`, `test` = `vitest run`, `lint`, `tipos:sync`; `engines.node >= 22`; dependencias de design D17), `tsconfig.json`, `eslint.config.js` y `npm install`; verificar que `package-lock.json` se genera y que no hay framework de UI en las dependencias
- [x] 5.2 RED/GREEN `types.sync.test.ts` (copia idéntica a `docs/api/tipos.ts`); implementar `scripts/sync-tipos.mjs` y generar `src/api/types.ts` (design D15); verificar con `npm test` y que `npx tsc --noEmit` compila `types.ts` con la config estricta
- [x] 5.3 Crear `vite.config.ts` (build multi-page, proxy `/api` a `VITE_API_PROXY ?? http://localhost:8081`, plugin de fallback `/admin/*`, Vitest con jsdom), `index.html`, `admin/index.html` y `src/styles/base.css` (design D14); verificar con `npm run build` (genera `dist/index.html` y `dist/admin/index.html`)

## 6. Router, pantallas placeholder y cliente HTTP del frontend

- [x] 6.1 RED/GREEN `router.test.ts`: rutas registradas renderizan su placeholder, raíz de cada área redirige, ruta desconocida muestra "no encontrada", navegación con `pushState` y `popstate`, `ContextoRuta` expone la query; implementar `src/shared/router.ts` y `src/shared/dom.ts`; verificar con `npm test`
- [x] 6.2 Crear `src/public/{main,rutas,reservar,confirmar,cancelar}.ts` y `src/admin/{main,rutas,login,agenda,pacientes}.ts` + `src/admin/configuracion/{index,prestaciones,horarios,bloqueos}.ts` con placeholders accesibles (encabezado por pantalla; configuración con sus tres secciones); verificar con un test de `rutas` que cada ruta del spec plataforma-frontend resuelve a su pantalla y con `npm run build` y `npm run lint` sin errores
- [x] 6.3 RED/GREEN `cliente.test.ts` (`fetch` mockeado en el borde): error uniforme → `ErrorApi` tipado, error sin formato → mensaje genérico, Bearer con sesión, falla de red → `ErrorConexion`; implementar `src/api/cliente.ts` y `src/shared/sesion.ts` (design D16); verificar con `npm test`
- [x] 6.4 Crear `frontend/nginx.conf`, `frontend/Dockerfile` multi-stage y `frontend/.dockerignore` (design D12, D13); verificar con `docker build -t turnos-frontend ./frontend`

## 7. Documentación e integración

- [x] 7.1 Escribir `README.md` mínimo: requisitos (Docker con Compose v2, Java 21, Node 22+), `cp .env.example .env && docker compose up --build`, URLs (app `:8080`, admin `:8080/admin/`, Mailpit `:8025`, health backend `:8081/actuator/health`), reset con `down -v`, flujo diario fuera de Docker (perfil `local`, `npm run dev`), cómo pasar a Gmail y cómo correr los tests; verificar siguiendo los comandos tal como están escritos
- [x] 7.2 Verificación integral: desde un clon limpio, `cp .env.example .env && docker compose up --build`; comprobar `docker compose ps` (db, backend, frontend healthy), `curl -i localhost:8080/api/auth/me` → 401 `NO_AUTENTICADO` uniforme, `curl -i localhost:8080/admin/agenda` y `/turno/cancelar?token=abc` → 200 HTML, Mailpit UI en `:8025`, y `down` + `up` conserva el volumen (verificado con `.env` real; además se corrigió el healthcheck del frontend a `127.0.0.1`, porque `localhost` resolvía a IPv6 y nginx escucha solo en IPv4)
- [x] 7.3 Correr `./mvnw test` y `npm test && npm run lint && npm run build` en verde y `openspec validate c-02-docker-scaffold --strict`; revisar el diff contra AGENTS.md (sin secretos, sin H2, sin `any`, sin framework de UI)

## Workflow follow-up

- Abrir PR de `feat/c-02-docker-scaffold` a `main` con revisión de Int 1 (governance ALTO).
- Archivar con `/opsx:archive c-02-docker-scaffold` tras el merge y marcar C-02 `[x]` en `CHANGES.md` (anotando el desvío del health a `/actuator/health`).
- Avisar al equipo que ya pueden ramificar la Fase 1 (C-03 primero).
