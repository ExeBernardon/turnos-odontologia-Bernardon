# plataforma-backend Specification

## Purpose

Define la base transversal del backend que todos los dominios comparten: configuración por entorno, zona horaria, reglas de acceso del contrato, errores genéricos con el formato uniforme, healthcheck, inicialización SQL e infraestructura de tests contra PostgreSQL real.

## Requirements

### Requirement: Configuración tipada por entorno con fallo temprano
Todos los parámetros de negocio y de infraestructura de KB 08 §Variables de entorno SHALL leerse de sus variables de entorno con los nombres de esa tabla. Los no sensibles SHALL tener por defecto el valor de ejemplo de la tabla; los sensibles (contraseña de la base, `JWT_SECRET`, `SEED_ODONTOLOGO_PASSWORD`) no SHALL tener valor por defecto fuera del perfil `local` y de los tests.

#### Scenario: Parámetro de negocio desde el entorno
- **WHEN** el backend arranca con `APP_CANCELACION_HORAS_MINIMAS=12`
- **THEN** la configuración de la aplicación expone 12 como horas mínimas de cancelación

#### Scenario: Parámetro de negocio por defecto
- **WHEN** el backend arranca sin `APP_AGENDA_PASO_MINUTOS`
- **THEN** la configuración expone 15 como paso de la grilla

#### Scenario: Falta un secreto
- **WHEN** el backend arranca sin perfil `local` y sin `JWT_SECRET`
- **THEN** el arranque falla con un mensaje que nombra la propiedad faltante, en lugar de usar un valor inventado

### Requirement: Instantes en UTC independientes del huso del sistema
El backend SHALL operar la JVM y la conexión de Hibernate en UTC sin importar el huso del contenedor o de la máquina, y SHALL exponer la zona de negocio configurada (`America/Argentina/Buenos_Aires` por defecto) y un reloj inyectable para la lógica de dominio.

#### Scenario: Máquina con otro huso
- **WHEN** el backend corre en una máquina con huso `Europe/Madrid`
- **THEN** la zona por defecto de la JVM es UTC y la zona de negocio sigue siendo `America/Argentina/Buenos_Aires`

### Requirement: Acceso público y privado según el contrato
Sin token, `POST /api/auth/login` y `/api/public/**` SHALL ser accesibles; cualquier otra ruta bajo `/api/**` SHALL responder 401 con el formato de error uniforme y `codigo` `NO_AUTENTICADO`. El backend SHALL ser stateless: no SHALL crear sesión ni emitir cookie de sesión.

#### Scenario: Ruta privada sin token
- **WHEN** se llama `GET /api/turnos` sin header `Authorization`
- **THEN** responde 401 con `status` 401, `error` `UNAUTHORIZED`, `codigo` `NO_AUTENTICADO`, `mensaje`, `path` `/api/turnos` y `timestamp` con offset

#### Scenario: Ruta pública sin token
- **WHEN** se llama una ruta bajo `/api/public/` sin header `Authorization`
- **THEN** la respuesta no es 401

#### Scenario: Sin sesión
- **WHEN** se hace cualquier request a `/api/**`
- **THEN** la respuesta no incluye `Set-Cookie` de sesión

### Requirement: Errores genéricos con el formato uniforme
El backend SHALL responder los errores genéricos del contrato con el formato uniforme de `docs/api/errores.md`: 400 `VALIDACION` con `detalles` por campo cuando falla la validación del body; 400 `SOLICITUD_MAL_FORMADA` sin `detalles` cuando el JSON o un parámetro no se puede interpretar; 404 `RECURSO_NO_ENCONTRADO` cuando la ruta o el recurso no existe.

#### Scenario: Validación de body
- **WHEN** un endpoint recibe un body con un campo obligatorio vacío
- **THEN** responde 400 con `codigo` `VALIDACION` y una entrada en `detalles` con ese `campo`

#### Scenario: JSON ilegible
- **WHEN** un endpoint recibe un body que no es JSON válido
- **THEN** responde 400 con `codigo` `SOLICITUD_MAL_FORMADA` y sin `detalles`

#### Scenario: Recurso inexistente
- **WHEN** un dominio informa que el recurso pedido no existe
- **THEN** responde 404 con `codigo` `RECURSO_NO_ENCONTRADO`

### Requirement: Errores de negocio declarados por cada dominio
Cada dominio SHALL poder rechazar un request con un status y un `codigo` del catálogo de `docs/api/errores.md` y un mensaje, y el backend SHALL convertirlo en una respuesta con el formato uniforme sin que el dominio arme el JSON.

#### Scenario: Regla de negocio violada
- **WHEN** un dominio rechaza una operación con status 422 y `codigo` `TRANSICION_INVALIDA`
- **THEN** la respuesta es 422 con ese `codigo`, el mensaje del dominio, `path` y `timestamp`, sin `detalles`

### Requirement: Healthcheck fuera del contrato
El backend SHALL exponer su estado de salud sin autenticación en `/actuator/health`, fuera de `/api`, reportando sano cuando la base responde, y sin depender de que el servidor SMTP esté disponible.

#### Scenario: Backend sano
- **WHEN** el backend está conectado a la base
- **THEN** `GET /actuator/health` responde 200 con estado `UP`

#### Scenario: Health no es parte de la API
- **WHEN** se lista el inventario de rutas bajo `/api`
- **THEN** no hay ninguna ruta de salud agregada fuera de las del contrato

### Requirement: Script SQL complementario después de Hibernate
En cada arranque, después de que Hibernate cree o actualice las tablas, el backend SHALL ejecutar el script `schema-extra.sql` usando `^^^` como separador de sentencias, y el script SHALL ser idempotente.

#### Scenario: Extensión disponible tras el arranque
- **WHEN** el backend arranca contra una base nueva
- **THEN** la extensión `btree_gist` queda instalada en la base

#### Scenario: Reinicio
- **WHEN** el backend se reinicia contra la misma base
- **THEN** el script corre de nuevo sin errores y sin duplicar nada

### Requirement: Tests de integración contra PostgreSQL real
Los tests de integración del backend SHALL correr con `./mvnw test` contra un PostgreSQL 16 levantado por Testcontainers, sin requerir una base instalada ni usar H2.

#### Scenario: Test de humo
- **WHEN** se ejecuta `./mvnw test` con Docker disponible
- **THEN** arranca un contenedor PostgreSQL 16, el contexto de la aplicación levanta y los tests de humo pasan

#### Scenario: Sin H2
- **WHEN** se revisan las dependencias del backend
- **THEN** no está H2 ni otra base embebida
