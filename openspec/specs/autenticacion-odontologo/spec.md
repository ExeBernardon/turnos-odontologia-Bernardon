# autenticacion-odontologo Specification

## Purpose

Define el comportamiento observable del acceso del odontólogo: login con credenciales normalizadas, emisión de un JWT Bearer, validación en rutas privadas y consulta del usuario autenticado, sin sesiones ni exposición de secretos.

## Requirements

### Requirement: Login del odontólogo con credenciales válidas
El sistema SHALL aceptar `POST /api/auth/login` con `email` y `password`, verificar la contraseña contra el hash del único odontólogo y responder 200 con `token`, `tipo: "Bearer"`, `expiraEn` (ISO-8601 con offset) y `nombre`, sin incluir contraseña ni hash.

#### Scenario: Login exitoso
- **GIVEN** existe el odontólogo sembrado con credenciales válidas
- **WHEN** se envía `POST /api/auth/login` con ese email y contraseña
- **THEN** responde 200 con `token`, `tipo: "Bearer"`, `expiraEn` con offset y `nombre`, sin `password` ni hash

#### Scenario: Vencimiento informado en la respuesta
- **GIVEN** un login exitoso
- **WHEN** se lee `expiraEn` de la respuesta
- **THEN** es un instante ISO-8601 con offset posterior al momento del login

### Requirement: Credenciales inválidas indistinguibles
El sistema SHALL responder 401 con `codigo: "CREDENCIALES_INVALIDAS"` y el mismo mensaje cuando el email no existe o la contraseña es incorrecta, sin revelar cuál de los dos datos falló.

#### Scenario: Contraseña incorrecta
- **GIVEN** un email de odontólogo existente
- **WHEN** se envía ese email con una contraseña incorrecta
- **THEN** responde 401 con `codigo: "CREDENCIALES_INVALIDAS"` y un mensaje que no indica si el email existe

#### Scenario: Email inexistente
- **GIVEN** un email que no corresponde a ningún odontólogo
- **WHEN** se envía ese email con cualquier contraseña
- **THEN** responde 401 con el mismo `codigo` y el mismo `mensaje` que para una contraseña incorrecta

### Requirement: Normalización del email de acceso
El sistema SHALL normalizar el email recibido en login con `trim` y minúsculas antes de buscar al odontólogo, y SHALL almacenar el email sembrado con el mismo criterio para que ambas formas coincidan.

#### Scenario: Login con espacios y mayúsculas
- **GIVEN** un odontólogo con email almacenado `odontologo@ejemplo.com`
- **WHEN** se envía `email: "  Odontologo@Ejemplo.com  "` con la contraseña correcta
- **THEN** responde 200 con el token del odontólogo

#### Scenario: Seed con email sin normalizar en la configuración
- **GIVEN** `SEED_ODONTOLOGO_EMAIL` configurado como `"  Odontologo@Ejemplo.com  "`
- **WHEN** se inicializa la base con el seed y luego se hace login con `odontologo@ejemplo.com`
- **THEN** el odontólogo queda almacenado como `odontologo@ejemplo.com` y el login responde 200

### Requirement: Validación del body de login
El sistema SHALL responder 400 con `codigo: "VALIDACION"` y `detalles` por campo cuando faltan o están vacíos `email` o `password`, y 400 con `codigo: "SOLICITUD_MAL_FORMADA"` sin `detalles` cuando el JSON no se puede interpretar.

#### Scenario: Email vacío
- **GIVEN** un request a `POST /api/auth/login`
- **WHEN** el body tiene `email: ""` y una contraseña cualquiera
- **THEN** responde 400 con `codigo: "VALIDACION"` y un detalle para el campo `email`

#### Scenario: JSON ilegible
- **GIVEN** un request a `POST /api/auth/login`
- **WHEN** el body no es JSON válido
- **THEN** responde 400 con `codigo: "SOLICITUD_MAL_FORMADA"` y sin `detalles`

### Requirement: Uso del JWT en rutas privadas
El sistema SHALL aceptar `Authorization: Bearer <token>` emitido por el login para acceder a las rutas bajo `/api/**` distintas de `POST /api/auth/login` y `/api/public/**`, y SHALL mantener accesibles esas dos excepciones sin token.

#### Scenario: Ruta privada con token válido
- **GIVEN** un token JWT obtenido por login
- **WHEN** se llama `GET /api/turnos` con `Authorization: Bearer <token>`
- **THEN** la solicitud no es rechazada por autenticación

#### Scenario: Ruta pública sin token
- **GIVEN** ningún header de autorización
- **WHEN** se llama `GET /api/public/prestaciones`
- **THEN** la respuesta no es 401 `NO_AUTENTICADO`

### Requirement: Token ausente, inválido o vencido
El sistema SHALL responder 401 con `codigo: "NO_AUTENTICADO"` y el formato uniforme de error cuando falta el header de autorización, el token no se puede verificar o el token está vencido.

#### Scenario: Ruta privada sin token
- **GIVEN** ningún header de autorización
- **WHEN** se llama `GET /api/turnos`
- **THEN** responde 401 con `codigo: "NO_AUTENTICADO"` y `path: "/api/turnos"`

#### Scenario: Token vencido
- **GIVEN** un token JWT cuyo vencimiento ya pasó
- **WHEN** se usa en una ruta privada
- **THEN** responde 401 con `codigo: "NO_AUTENTICADO"`

#### Scenario: Token manipulado
- **GIVEN** un token JWT con su firma alterada
- **WHEN** se usa en una ruta privada
- **THEN** responde 401 con `codigo: "NO_AUTENTICADO"`

### Requirement: Consulta del usuario autenticado
`GET /api/auth/me` SHALL responder 200 con `id`, `nombre` y `email` del odontólogo asociado al JWT, sin devolver contraseña ni hash.

#### Scenario: Datos del odontólogo autenticado
- **GIVEN** un token JWT válido de un odontólogo sembrado
- **WHEN** se llama `GET /api/auth/me` con ese token
- **THEN** responde 200 con `id`, `nombre` y `email` del odontólogo, sin `password` ni hash

### Requirement: Sesión stateless y secretos no expuestos
El backend SHALL ser stateless: no SHALL emitir cookies de sesión ni exponer el hash de la contraseña, `JWT_SECRET`, la contraseña del seed ni tokens en respuestas, mensajes de error o logs.

#### Scenario: Sin cookie de sesión
- **GIVEN** cualquier request a `/api/**`
- **WHEN** se inspeccionan las cabeceras de la respuesta
- **THEN** no hay una cabecera `Set-Cookie` de sesión

#### Scenario: Sin secretos en respuestas ni logs
- **GIVEN** un login fallido y un login exitoso
- **WHEN** se revisan los cuerpos de respuesta y los logs del servidor
- **THEN** no aparece la contraseña en claro, el hash BCrypt, `JWT_SECRET` ni el token completo
