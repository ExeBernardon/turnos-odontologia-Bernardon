# entorno-ejecucion Specification

## Purpose

Define cómo se levanta y se configura el sistema completo (base, backend, front y servidor de correo de desarrollo) para que los 6 integrantes y la demo usen el mismo entorno con un solo comando y sin secretos versionados.

## Requirements

### Requirement: Un solo comando levanta el sistema
Con Docker y Compose v2 instalados, copiar `.env.example` a `.env` y ejecutar `docker compose up --build` en la raíz del repo SHALL levantar los servicios `db`, `backend`, `frontend` y `mailpit` sin pasos manuales adicionales.

#### Scenario: Arranque desde un clon limpio
- **WHEN** un integrante clona el repo, ejecuta `cp .env.example .env` y luego `docker compose up --build`
- **THEN** los cuatro servicios quedan corriendo y `db`, `backend` y `frontend` reportan estado healthy

#### Scenario: Sin archivo .env
- **WHEN** se ejecuta `docker compose up` sin haber creado `.env`
- **THEN** Compose falla al arrancar indicando que falta el archivo, en lugar de levantar el backend con configuración incompleta

### Requirement: Orden de arranque por salud
El backend SHALL arrancar solo cuando la base está lista para aceptar conexiones, y el front SHALL arrancar solo cuando el backend reporta estar sano.

#### Scenario: La base tarda en estar lista
- **WHEN** PostgreSQL todavía está inicializando su volumen
- **THEN** el backend no arranca hasta que la base responde como lista, y no falla por conexión rechazada

#### Scenario: El backend no está sano
- **WHEN** el backend no logra arrancar
- **THEN** el front no se inicia y `docker compose ps` muestra al backend como no healthy

### Requirement: Mismo origen con proxy de la API
El front SHALL servirse en un único origen que también responde `/api/**` reenviando al backend, tanto en Docker como en el servidor de desarrollo, de modo que el navegador nunca haga requests a otro origen y no se necesite CORS.

#### Scenario: API por el proxy en Docker
- **WHEN** con el sistema levantado se pide `GET http://localhost:8080/api/auth/me` sin header `Authorization`
- **THEN** responde 401 con el formato de error uniforme y `codigo` `NO_AUTENTICADO`, emitido por el backend

#### Scenario: API por el proxy en desarrollo
- **WHEN** el front corre con el servidor de desarrollo y el backend corre en el puerto de host del backend
- **THEN** `GET /api/auth/me` pedido al servidor de desarrollo responde lo mismo que en Docker

### Requirement: Áreas pública y privada con recarga directa
El front SHALL responder la página del área pública para `/` y sus rutas (`/reservar`, `/turno/confirmar`, `/turno/cancelar`) y la del área privada para `/admin/` y sus rutas, también cuando el navegador recarga o abre directamente una ruta profunda.

#### Scenario: Recarga de una ruta privada
- **WHEN** se abre directamente `http://localhost:8080/admin/agenda`
- **THEN** se recibe la página del área privada con estado 200, no un 404

#### Scenario: Link de email a una ruta pública
- **WHEN** se abre directamente `http://localhost:8080/turno/cancelar?token=abc`
- **THEN** se recibe la página del área pública con estado 200

### Requirement: Configuración por variables de entorno sin secretos versionados
Toda la configuración sensible o dependiente del entorno SHALL venir de variables de entorno definidas en `.env`. El repo SHALL versionar solo `.env.example`, con todas las variables y valores de ejemplo, y SHALL ignorar `.env` y `.env.*`.

#### Scenario: .env.example completo
- **WHEN** se compara `.env.example` con la tabla de variables de KB 08 §Variables de entorno
- **THEN** están todas las variables de la tabla más los puertos de host, con valores de ejemplo y sin contraseñas ni secretos reales

#### Scenario: .env no se versiona
- **WHEN** un integrante crea `.env` o `.env.demo` y ejecuta `git status`
- **THEN** git no lista esos archivos, y sí lista cambios en `.env.example`

### Requirement: Puertos de host configurables
Los puertos publicados en el host (front, backend, base y Mailpit UI y SMTP) SHALL poder cambiarse desde `.env` sin editar `docker-compose.yml`, con valores por defecto `8080` (front), `8081` (backend), `5432` (base), `8025` (Mailpit UI) y `1025` (Mailpit SMTP).

#### Scenario: Puerto ocupado en una máquina
- **WHEN** un integrante tiene el 8080 ocupado y define `FRONTEND_PORT=8090` en su `.env`
- **THEN** el front queda accesible en `http://localhost:8090` sin cambios en archivos versionados

### Requirement: Correo de desarrollo capturado y de demo por SMTP real
Con los valores por defecto de `.env.example`, todo email que envíe el backend SHALL quedar capturado en Mailpit (UI en el puerto de Mailpit) sin salir a internet. Cambiando solo las variables `SPRING_MAIL_*` y `APP_MAIL_FROM` SHALL poder enviarse por un SMTP real con STARTTLS y autenticación (Gmail en la demo).

#### Scenario: Mailpit en desarrollo
- **WHEN** el sistema corre con los valores de `.env.example`
- **THEN** la UI de Mailpit responde en `http://localhost:8025` y el backend apunta su SMTP a `mailpit:1025` sin autenticación

#### Scenario: Gmail en la demo
- **WHEN** se ponen en `.env` el host `smtp.gmail.com`, puerto `587`, usuario, contraseña de aplicación y `..._SMTP_AUTH` y `..._STARTTLS_ENABLE` en `true`
- **THEN** el backend usa ese SMTP sin cambios de código ni de archivos versionados

#### Scenario: SMTP caído no tumba el backend
- **WHEN** el servidor SMTP configurado no está disponible
- **THEN** el backend arranca y reporta estado sano igual

### Requirement: Persistencia y reinicio de la base
Los datos de PostgreSQL SHALL persistir entre reinicios del sistema en un volumen nombrado, y SHALL poder borrarse a propósito con `docker compose down -v`.

#### Scenario: Reinicio conserva datos
- **WHEN** se ejecuta `docker compose down` y luego `docker compose up`
- **THEN** los datos de la base siguen estando

#### Scenario: Reset intencional
- **WHEN** se ejecuta `docker compose down -v` y luego `docker compose up`
- **THEN** la base arranca vacía

### Requirement: Desarrollo diario fuera de Docker
Un integrante SHALL poder levantar solo `db` y `mailpit` con Compose y correr el backend con el Maven Wrapper y el front con el servidor de desarrollo de Vite en su máquina, contra esa base y ese Mailpit, siguiendo los pasos del `README.md`.

#### Scenario: Backend local contra la base de Compose
- **WHEN** con `docker compose up db mailpit` corriendo se ejecuta el backend con el perfil `local` mediante el Maven Wrapper
- **THEN** el backend arranca en el puerto `8081`, se conecta a la base en `localhost:5432` y envía correo a Mailpit en `localhost:1025`

#### Scenario: Front local
- **WHEN** se ejecuta `npm install` y `npm run dev` en `frontend/`
- **THEN** el front queda en `http://localhost:5173` y sus requests a `/api` llegan al backend del puerto `8081`

### Requirement: README de entrega con guion de demo
El `README.md` SHALL permitir que alguien sin contexto levante y muestre el MVP: requisitos, el comando único de arranque desde cero, las URLs, las credenciales del seed (solo ficticias o por referencia a variables de `.env`), cómo pasar a Gmail solo con variables de entorno y un guion de demo que recorra los 6 flujos principales. SHALL NOT contener secretos ni direcciones de email reales.

#### Scenario: Arranque desde cero siguiendo el README
- **WHEN** un integrante sigue el README en una máquina con Docker y una copia de `.env.example` como `.env`
- **THEN** con `docker compose down -v` y `docker compose up --build` el sistema queda accesible en las URLs indicadas, con el odontólogo, las prestaciones y los horarios del seed

#### Scenario: Sin secretos en el README
- **WHEN** se revisa el README versionado
- **THEN** no aparece ninguna contraseña real, contraseña de aplicación, `JWT_SECRET` ni dirección de email real; la cuenta de Gmail de demo figura solo como marcador

### Requirement: Resolución de problemas de correo en el README
El `README.md` SHALL documentar los síntomas conocidos del correo en la demo: si el SMTP configurado no responde, cada acción que envía mail tarda alrededor del timeout de SMTP (unos 5 s) y el turno se crea igual; con Gmail, el envío sincrónico demora la respuesta unos segundos; los mails pueden caer en spam; y el seed del odontólogo solo se aplica sobre una base vacía.

#### Scenario: SMTP inalcanzable
- **WHEN** quien presenta nota que reservar o cancelar tarda unos 5 s y no llegan mails
- **THEN** el README indica revisar la configuración `SPRING_MAIL_*` y que los turnos igual quedaron creados

#### Scenario: Cambio del email del odontólogo del seed
- **WHEN** se cambia `SEED_ODONTOLOGO_EMAIL` en `.env` sobre una base ya inicializada
- **THEN** el README indica que hace falta `docker compose down -v` para que el seed lo aplique
