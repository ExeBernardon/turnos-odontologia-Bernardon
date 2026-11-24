# Turnos odontológicos

Sistema de turnos para un consultorio odontológico: reserva pública sin cuenta, confirmación y cancelación por link, y agenda privada para el odontólogo. Trabajo práctico académico (datos ficticios).

Más contexto en [`knowledge-base/`](knowledge-base/README.md); el contrato de la API está en [`docs/api/`](docs/api/openapi.yaml) y el plan de trabajo en [`CHANGES.md`](CHANGES.md).

## Requisitos

- Docker con Compose v2 (corriendo, también para los tests del backend).
- Java 21 (para el backend fuera de Docker y los tests).
- Node 22 o superior (para el front fuera de Docker; la imagen de build usa Node 24).

## Levantar todo con un comando

```bash
cp .env.example .env
docker compose up --build
```

`.env` no se versiona (solo `.env.example`). Si falta, Compose falla a propósito.

| Qué | URL |
|-----|-----|
| App pública | http://localhost:8080/ |
| Área del odontólogo | http://localhost:8080/admin/ |
| Mailpit (bandeja de emails de desarrollo) | http://localhost:8025 |
| Salud del backend | http://localhost:8081/actuator/health |

Los puertos de host se cambian desde `.env` (`FRONTEND_PORT`, `BACKEND_PORT`, `DB_PORT`, `MAILPIT_UI_PORT`, `MAILPIT_SMTP_PORT`).

Para empezar de cero (borra la base): `docker compose down -v`. Con `docker compose down` los datos se conservan.

## Desarrollo diario (fuera de Docker)

Solo la base y Mailpit en Docker; backend y front en tu máquina:

```bash
docker compose up -d db mailpit

# backend (perfil local: puerto 8081, base en localhost:5432, Mailpit en localhost:1025)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local     # en Windows: mvnw.cmd

# front (http://localhost:5173, proxy de /api a localhost:8081)
cd frontend
npm install
npm run dev
```

Si tu `.env` usa otra contraseña de base, exportala antes de arrancar el backend (`SPRING_DATASOURCE_PASSWORD=...`).

## Tests

```bash
# backend: JUnit 5 + Testcontainers (PostgreSQL 16 real, requiere Docker corriendo)
cd backend && ./mvnw test

# frontend: Vitest
cd frontend && npm test && npm run lint && npm run build

# contrato de la API
cd docs/api/scripts && npm install && npm run verificar
```

Los tipos del front (`frontend/src/api/types.ts`) son una copia exacta de `docs/api/tipos.ts`. Si el contrato cambia: `npm run tipos:sync` en `frontend/`.

## Pasar de Mailpit a Gmail (demo)

Editar solo estas variables de `.env` (ver los comentarios de `.env.example`): `SPRING_MAIL_HOST=smtp.gmail.com`, `SPRING_MAIL_PORT=587`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` (contraseña de aplicación), `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`, `APP_MAIL_FROM` y `APP_FRONTEND_BASE_URL`. Después `docker compose up -d backend`.

## Estructura

```
backend/    Spring Boot 3.5 + Java 21 + Maven (paquete ar.turnos.odontologia, carpetas por feature)
frontend/   Vite + TypeScript vanilla (área pública y área /admin) servido por nginx
docs/api/   Contrato de la API (OpenAPI, errores, tipos, ejemplos)
openspec/   Specs y changes
```
