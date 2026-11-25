# Design

## Context

Ver `proposal.md` para la motivación. Estado actual: C-03 ya mergeó la entidad `Odontologo`, `OdontologoRepository.findByEmail`, el hash BCrypt del seed y las reglas de acceso en `SecurityConfig`; sin embargo `SecurityConfig.java:39` dejó el hook del filtro JWT pendiente y `SecurityConfig.java:50` aún usa un `InMemoryUserDetailsManager` vacío. `AppProperties.Jwt` y las dependencias `jjwt` ya existen (`pom.xml:22,57-73`), al igual que `ApiAuthenticationEntryPoint` y `GlobalExceptionHandler` con el formato uniforme. El seed guarda `datos.email()` sin normalizar (`DataSeeder.java:65`), mientras que la decisión de C-04 es buscar por email con `trim` + minúsculas; sin ajuste, una configuración con mayúsculas o espacios deja al odontólogo imposible de autenticar. Restricciones: contrato OpenAPI inmutable, Java 21, Spring Boot 3.5, PostgreSQL con Testcontainers (nunca H2), `strict: true` en el front (sin impacto aquí) y sin datos reales de pacientes.

## Goals / Non-Goals

**Goals:**
- Hacer efectivas las reglas de acceso del contrato con un JWT real y stateless.
- Unificar el criterio del email entre seed y login para que variantes de espacios/mayúsculas funcionen.
- Reutilizar el formato de error uniforme sin ensuciar el `GlobalExceptionHandler` con excepciones de seguridad.

**Non-Goals:**
- Cambiar `docs/api/openapi.yaml`, `docs/api/errores.md` ni los mensajes del catálogo.
- Agregar roles, refresh token, revocación, bloqueo por intentos, recuperación de contraseña ni logout.
- Implementar el front de login (C-13) o los endpoints de dominio (C-08, C-09, C-10).
- Migrar emails ya persistidos en bases existentes mediante un script de datos.

## Decisions

1. **Normalización en un único punto, compartida por seed y login.** Se extrae una función pequeña que aplica `trim()` + `toLowerCase(Locale.ROOT)` y se usa tanto al persistir el odontólogo sembrado como al buscarlo en `login`. Así `findByEmail` sigue siendo un `equals` simple y el `uniqueConstraint` de `odontologo.email` no admite dos variantes del mismo email. Alternativas descartadas: (a) normalizar solo en la búsqueda con `lower(trim(email))` — deja el dato sembrado inconsistente y el email devuelto por `/me` con espacios; (b) `citext`/collation en PostgreSQL — sale del alcance del change y del esquema actual.

2. **Autenticación estándar de Spring Security sobre JPA.** Un `UserDetailsService` adapta `OdontologoRepository` y el `PasswordEncoder` BCrypt existente; `AuthenticationManager` valida la contraseña. El controller queda delgado: recibe un DTO con `@NotBlank` / `@Email`, delega y traduce el resultado. Alternativas descartadas: comparar contraseñas a mano en el controller (pierde la integración con el filtro) y Basic Auth (el contrato exige Bearer JWT).

3. **JWT firmado con los parámetros ya configurados.** `JwtService` usa `app.jwt().secret()` y `app.jwt().expirationMinutes()` para emitir y verificar el token; el `subject` es el email normalizado y el `expiraEn` se calcula con el `Clock` inyectable y se serializa como ISO-8601 con offset de `app.zonaHoraria()`. Alternativas descartadas: tokens opacos con tabla (nueva entidad y consulta por request) y sesiones con cookies (violan la regla stateless del contrato).

4. **Filtro antes de `UsernamePasswordAuthenticationFilter`.** Un `OncePerRequestFilter` extrae el header `Authorization: Bearer`, valida el token, pobla el `SecurityContext` y deja pasar; si falla, `ApiAuthenticationEntryPoint` ya existente responde 401 `NO_AUTENTICADO`. `GlobalExceptionHandler` no se toca: sus propios comentarios delegan 401/403 a los filtros (`GlobalExceptionHandler.java:88-90`). Se conservan los `permitAll` de login, `/api/public/**`, health y `/error`.

5. **`/me` lee del `SecurityContext`, no del body ni de un parámetro.** El controller devuelve `id`, `nombre` y `email` del `Odontologo` autenticado. Para evitar una segunda consulta innecesaria, el `UserDetails` expone el `id` junto con el email normalizado del `subject`. Alternativa descartada: volver a buscar por email en cada `/me`.

6. **Errores de negocio con un solo mensaje.** `AuthenticationManager` lanza `BadCredentialsException` tanto para email inexistente como para contraseña incorrecta; el servicio la traduce a `ApiException(401, CREDENCIALES_INVALIDAS, mensaje fijo)`. No se distingue "usuario no encontrado" en la respuesta ni en los logs. La validación del body y el JSON ilegible siguen siendo resueltos por los handlers existentes (`VALIDACION` / `SOLICITUD_MAL_FORMADA`).

7. **Pruebas en dos niveles.** Unitarias para emisión/verificación del token y normalización; integración con `@SpringBootTest` + Testcontainers y `MockMvc` para el flujo completo de login, `/me`, rutas privadas y públicas, token vencido/manipulado y ausencia de `Set-Cookie`. Se extendrá `DataSeederIT` para cubrir el email con espacios/mayúsculas; `SecurityConfigTest` deberá importar el nuevo filtro y sus dependencias para seguir compilando bajo `@WebMvcTest`.

## Risks / Trade-offs

- Tocar `DataSeeder.java` (archivo de C-03 ya mergeado) → cambio de una línea plus su test, de otro integrante; avisar explícitamente en el PR según la regla 16 de `AGENTS.md`.
- Bases de desarrollo ya levantadas con el email sin normalizar seguirán teniendo el valor viejo → documentar `docker compose down -v` (acepta DD-04) o un `UPDATE odontologo SET email = lower(trim(email))` manual; no se agrega migración de datos.
- `JWT_SECRET` con valor de ejemplo en el perfil local → `@NotBlank` ya falla temprano fuera de local/test; no loguear ni versionar el secret real, solo `.env.example` con valor ficticio.
- Un filtro demasiado amplio podría blindar rutas públicas → ejecutar los tests existentes de `SecurityConfigTest` y agregar uno específico para `/api/public/**` y health.
- Spring Security puede loguear la excepción con el token → configurar el filtro para no propagar el header ni el token a los logs; verificar en el test de "sin secretos en respuestas ni logs".
- Cambiar el `UserDetailsService` rompe el `@WebMvcTest` actual → actualizar sus `@Import` en la misma task, sin relajar las aserciones de 401 uniforme.

## Migration Plan

Sin cambios de esquema ni de contrato. Secuencia: aplicar los commits del change, ejecutar `./mvnw test` con Docker disponible y verificar en local con `docker compose up`. Si hay un volumen previo con email sin normalizar, reiniciar la base con `docker compose down -v` o normalizar ese único registro a mano antes de probar el login. Rollback: revertir los commits de C-04; el backend vuelve al `UserDetailsService` vacío y las rutas privadas siguen respondiendo 401, sin dejar datos nuevos.

## Open Questions

Ninguna: la normalización del email y el alcance del ajuste en el seed ya fueron decididos por el usuario.
