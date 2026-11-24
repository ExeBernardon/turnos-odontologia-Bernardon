# Docker y Entornos

Documento extra a los 10 canónicos. Describe cómo correr todo con un solo comando y qué cambia entre desarrollo y demo.

## Servicios de Docker Compose

| Servicio | Imagen / build | Puerto host:contenedor | Rol |
|----------|----------------|------------------------|-----|
| `db` | `postgres:16` | `5432:5432` (solo dev; se puede no publicar) | Base de datos; volumen `pgdata` |
| `backend` | build multi-stage `backend/Dockerfile` (Maven -> JRE 21) | `8081:8080` (opcional, para Swagger/depuración) | API Spring Boot |
| `frontend` | build multi-stage `frontend/Dockerfile` (Vite build -> nginx) | `8080:80` | Sirve el front y hace proxy de `/api` a `backend:8080` |
| `mailpit` | `axllent/mailpit` | `8025:8025` (UI), `1025:1025` (SMTP) | Captura emails en desarrollo |

Dependencias: `backend` depende de `db` (con `healthcheck` de `pg_isready`); `frontend` depende de `backend`. Mailpit es opcional en la demo con Gmail.

> **Desvíos aprobados en C-02**: (1) el healthcheck del backend es `GET /actuator/health` (Spring Boot Actuator, fuera de `/api`) y no `/api/health`, porque el contrato de C-01 fija el inventario exacto de rutas bajo `/api`; el proxy se verifica con `GET /api/auth/me` sin token → 401 `NO_AUTENTICADO`. (2) La imagen de build del front usa Node 24 LTS en lugar de Node 20.

Los puertos son una propuesta; deben ser configurables en `.env` si chocan en la máquina de alguien.

## Un solo comando

```bash
cp .env.example .env      # una vez; editar secretos
docker compose up --build
```

- App pública: `http://localhost:8080/`
- Área del odontólogo: `http://localhost:8080/admin/`
- Mailpit (bandeja de emails): `http://localhost:8025`
- Reiniciar la base desde cero: `docker compose down -v` (borra el volumen `pgdata`; útil porque `ddl-auto=update` no migra de forma destructiva, ver DD-04).

## Dockerfiles (lineamientos)

- **backend**: etapa 1 `maven:3-eclipse-temurin-21` (`mvn -q -DskipTests package`, con cache de `~/.m2` si se puede); etapa 2 `eclipse-temurin:21-jre` copiando solo el jar; usuario no root; `EXPOSE 8080`.
- **frontend**: etapa 1 `node:24-alpine` (`npm ci && npm run build`; Node 20 salió de soporte, desvío aprobado en C-02); etapa 2 `nginx:alpine` copiando `dist/` y `nginx.conf`.
- **nginx.conf**: `location /api/ { proxy_pass http://backend:8080; }` y fallback a los `index.html` de cada área (`/admin/` y `/`, y `/turno/*` al público). Cabeceras `X-Forwarded-*`. Mismo origen: sin CORS.

## Variables de entorno

La lista completa con descripción y sensibilidad está en [08](08_arquitectura_propuesta.md#variables-de-entorno). `.env.example` versionado, `.env` ignorado por git. Valores clave:

```dotenv
# Base
POSTGRES_DB=turnos
POSTGRES_USER=turnos
POSTGRES_PASSWORD=cambiar-esto

# Backend
JWT_SECRET=cambiar-por-una-cadena-larga-de-32-bytes-o-mas
# Datos ficticios, nunca nombre ni email reales. Este email recibe las alertas de turnos pendientes (en dev llegan a Mailpit)
SEED_ODONTOLOGO_NOMBRE=Dr. Ejemplo
SEED_ODONTOLOGO_EMAIL=odontologo@ejemplo.com
SEED_ODONTOLOGO_PASSWORD=cambiar-esto
APP_FRONTEND_BASE_URL=http://localhost:8080
APP_ZONA_HORARIA=America/Argentina/Buenos_Aires
APP_RECORDATORIO_INTERVALO_MIN=15
APP_RECORDATORIO_HORAS_ANTES=48
APP_ALERTA_PENDIENTE_HORAS=24
APP_CANCELACION_HORAS_MINIMAS=24
APP_TURNO_ANTICIPACION_MIN_HORAS=2
APP_TURNO_VENTANA_MAX_DIAS=60

# Mail (dev: Mailpit)
SPRING_MAIL_HOST=mailpit
SPRING_MAIL_PORT=1025
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=false
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false
APP_MAIL_FROM=turnos@consultorio.local
```

## Entornos

| Aspecto | Desarrollo | Demo |
|---------|-----------|------|
| SMTP | Mailpit (`mailpit:1025`, sin auth) | Gmail: `smtp.gmail.com:587`, STARTTLS, usuario = cuenta, contraseña de aplicación |
| Cómo cambiar | Valores por defecto de `.env.example` | Editar solo las variables `SPRING_MAIL_*` y `APP_MAIL_FROM` en `.env` |
| Links de email | `APP_FRONTEND_BASE_URL=http://localhost:8080` | La URL real desde la que se presenta (ver SU-03) |
| Front en dev | `npm run dev` de Vite en `localhost:5173` con proxy `/api` a `localhost:8081` | nginx en Docker |
| Backend en dev | `./mvnw spring-boot:run` apuntando a la `db` de Compose | Contenedor `backend` |

Flujo de trabajo recomendado para el desarrollo diario: `docker compose up db mailpit` y correr backend y front fuera de Docker; antes de abrir un PR, verificar que `docker compose up --build` completo funciona.

## Gmail para la demo

1. Cuenta de Gmail con verificación en dos pasos activada.
2. Crear una "contraseña de aplicación" y ponerla en `SPRING_MAIL_PASSWORD`.
3. `SPRING_MAIL_HOST=smtp.gmail.com`, `SPRING_MAIL_PORT=587`, `..._SMTP_AUTH=true`, `..._STARTTLS_ENABLE=true`.
4. Probar con anticipación: revisar spam y el límite diario de envíos.
5. Nunca versionar la contraseña ni el `.env` (el `.gitignore` ya ignora `.env` y `.env.*`, con excepción de `.env.example`).

## Persistencia y seed

- Datos de Postgres en el volumen `pgdata`.
- Al arrancar el backend: Hibernate crea o actualiza tablas, luego `schema-extra.sql` agrega `btree_gist` y la restricción de exclusión (idempotente: corre en cada arranque pero reiniciar no rompe ni duplica nada, ver [08](08_arquitectura_propuesta.md#esquema-de-base-de-datos)), luego el seeder crea el odontólogo y los datos de ejemplo si faltan (ver [04](04_modelo_de_datos.md)).

## Zona horaria

La lógica no depende del TZ del contenedor (JVM en UTC, negocio en `America/Argentina/Buenos_Aires`). Ver [08](08_arquitectura_propuesta.md#zona-horaria).
