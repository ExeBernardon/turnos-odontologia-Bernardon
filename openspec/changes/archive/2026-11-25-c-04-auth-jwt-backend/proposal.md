# Proposal

## Why

C-03 ya entregó las entidades, el repositorio `Odontologo`, el seed y las reglas de acceso, pero hoy no existe forma de autenticarse: las rutas privadas responden 401 a todos y el `UserDetailsService` es un placeholder vacío (`backend/.../config/SecurityConfig.java:47`). C-04 cierra el mini-gate que desbloquea C-13, C-08, C-09 y C-10.

- **Pertenece al MVP**: está en la Fase 1 de `CHANGES.md` y habilita el login (C-13) y las rutas privadas de C-08, C-09 y C-10.
- **Reglas verificables**: credenciales indistinguibles, vencimiento del JWT, rutas públicas/privadas y email normalizado se expresan como escenarios Dado/Cuando/Entonces con aserciones concretas.
- **Lo bastante chico**: un solo usuario, dos endpoints, un filtro y un ajuste puntual del seed; sin cambios de esquema ni de contrato.

## What Changes

- `POST /api/auth/login`: valida `email` y `password` contra el odontólogo sembrado (BCrypt) y devuelve 200 con `token`, `tipo: "Bearer"`, `expiraEn` e `nombre`, según `docs/api/openapi.yaml:1498-1524`.
- `GET /api/auth/me`: devuelve `id`, `nombre` y `email` del odontólogo dueño del JWT.
- Generación y validación de JWT HS256 con `JWT_SECRET` y `JWT_EXPIRATION_MINUTES` ya configurados; filtro de autenticación stateless en `SecurityFilterChain`, manteniendo `permitAll` en login y `/api/public/**`.
- 401 `CREDENCIALES_INVALIDAS` con el mismo mensaje para email inexistente y contraseña incorrecta; 401 `NO_AUTENTICADO` uniforme para token ausente, inválido o vencido; 400 `VALIDACION` / `SOLICITUD_MAL_FORMADA` por body inválido. Sin exponer hash, contraseña ni token en logs ni respuestas.
- Normalización del email de login: `trim` + minúsculas antes de buscarlo. El seed hoy guarda el email del entorno sin normalizar (`DataSeeder.java:65`), por lo que C-04 incluye el ajuste mínimo para persistirlo con el mismo criterio y su test; se avisa en el PR porque toca un archivo de C-03 (ya mergeado, de otro integrante).
- Tests: login correcto, contraseña incorrecta, email inexistente con mensaje idéntico, email con espacios/mayúsculas, token vencido/inválido, ruta privada sin token, ruta pública sin token y hash nunca expuesto.
- Fuera de alcance: cambios en `docs/api/`, front (C-13), refresh token, revocación, rate limit, roles, recuperación de contraseña, tokens de turno, envío de emails y endpoints de otros dominios.

## Capabilities

### New Capabilities
- `autenticacion-odontologo`: login del odontólogo, emisión y validación del JWT, consulta del usuario autenticado y normalización consistente del email entre seed y login.

### Modified Capabilities
- Ninguna. `api-contrato`, `api-errores` y `plataforma-backend` ya describen las rutas, los códigos y el formato de error; C-04 los implementa sin cambiar los requisitos.

## Impact

- Código: `backend/src/main/java/ar/turnos/odontologia/{auth,config,security}/` y el ajuste de email en `backend/src/main/java/ar/turnos/odontologia/seed/DataSeeder.java`; tests en `backend/src/test/java/ar/turnos/odontologia/`.
- API: sin cambios; se respeta `docs/api/openapi.yaml` y `docs/api/errores.md` como fuente única de verdad.
- Dependencias: ninguna nueva (Spring Security y `jjwt` ya están en `backend/pom.xml` desde C-02).
- Datos: sin cambios de esquema. Las bases de desarrollo con un email sembrado sin normalizar requieren reset (`docker compose down -v`) o normalizar ese valor; se documenta en `design.md`.
- Desbloquea: C-13 (login front y guard) y da soporte a las rutas privadas de C-08, C-09 y C-10.
