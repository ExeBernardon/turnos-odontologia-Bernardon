# Tasks

## 1. Normalización del email y seed

- [ ] 1.1 Extraer la normalización de email (`trim` + minúsculas con `Locale.ROOT`) en un helper reutilizable y verificarlo con un test unitario que cubra espacios, mayúsculas y acentos (`./mvnw test -Dtest=EmailNormalizadorTest`).
- [ ] 1.2 Aplicar el helper al guardar el odontólogo en `DataSeeder.java:65` y ampliar `DataSeederIT` para sembrar con `SEED_ODONTOLOGO_EMAIL` con espacios/mayúsculas y afirmar que se persiste `odontologo@ejemplo.com`; verificar con `./mvnw test -Dtest=DataSeederIT`. Avisar en el PR que se toca un archivo de C-03 ya mergeado (regla 16 de `AGENTS.md`).

## 2. Dominio de autenticación

- [ ] 2.1 Crear los DTOs del contrato (`LoginRequest` con `@NotBlank`/`@Email`, `LoginResponse` con `token`, `tipo`, `expiraEn`, `nombre`, y `UsuarioResponse` con `id`, `nombre`, `email`) sin exponer password ni hash; verificar que `./mvnw test` compile con los nuevos tipos.
- [ ] 2.2 Reemplazar el `InMemoryUserDetailsManager` vacío por un `UserDetailsService` sobre `OdontologoRepository` que use el email normalizado y exponga `id`, email y hash; verificar con un test unitario que encuentre el odontólogo con variantes de espacios/mayúsculas.
- [ ] 2.3 Implementar el servicio de login sobre `AuthenticationManager` y el `PasswordEncoder` BCrypt existente, lanzando `ApiException(401, CREDENCIALES_INVALIDAS, mensaje fijo)` tanto para email inexistente como para contraseña incorrecta; verificar con tests unitarios que ambos casos tienen el mismo `codigo` y `mensaje` y que fallan antes de devolver cualquier token.

## 3. JWT y cadena de filtros

- [ ] 3.1 Implementar `JwtService` con `app.jwt().secret()`, `expirationMinutes` y el `Clock` inyectable: emitir token con subject = email normalizado y calcular `expiraEn` con offset de `app.zonaHoraria()`; verificar con tests unitarios de token válido, vencido y con firma alterada.
- [ ] 3.2 Implementar `JwtAuthFilter` (`OncePerRequestFilter`) que lea solo `Authorization: Bearer <token>`, poble el `SecurityContext` y no loguee el token ni el header; verificar con tests de filtro que un token válido autentica y que uno inválido/vencido delega al entry point sin escribir secretos en logs.
- [ ] 3.3 Registrar el filtro con `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)` en `SecurityConfig.java:39`, conservar los `permitAll` vigentes y actualizar los `@Import` de `SecurityConfigTest`; verificar que `./mvnw test -Dtest=SecurityConfigTest` mantiene las aserciones de 401 uniforme, ruta pública sin token y ausencia de `Set-Cookie`.

## 4. Endpoints de autenticación

- [ ] 4.1 Agregar `POST /api/auth/login` en `AuthController` con `@Valid @RequestBody`, delegando al servicio y devolviendo el `LoginResponse` del contrato; verificar con `MockMvc` los casos 200, 400 `VALIDACION` (email vacío), 400 `SOLICITUD_MAL_FORMADA` (JSON ilegible) y 401 `CREDENCIALES_INVALIDAS` con mensaje idéntico para email inexistente y contraseña incorrecta.
- [ ] 4.2 Agregar `GET /api/auth/me` leyendo el odontólogo del `SecurityContext` (sin consulta extra ni datos del body) y devolviendo `UsuarioResponse`; verificar con `MockMvc` que con token válido responde 200 con `id`/`nombre`/`email` y sin `password` ni hash, y que sin token responde 401 `NO_AUTENTICADO`.

## 5. Verificación de integración

- [ ] 5.1 Ejecutar un `AuthFlowIT` con PostgreSQL/Testcontainers que cubra el flujo completo: seed con email sin normalizar → login con variantes → uso del token en ruta privada → `/me`; verificar con `./mvnw test -Dtest=AuthFlowIT` que no se usa H2 y que el estado del turno/odontólogo no se altera.
- [ ] 5.2 Verificar regresión de seguridad con `MockMvc`: token vencido, token manipulado, ruta pública sin token y sin `Set-Cookie` ni secretos en respuesta/logs; verificar con `./mvnw test -Dtest=AuthFlowIT,SecurityConfigTest`.
- [ ] 5.3 Confirmar que no hay cambios en el contrato ni en el front con `git diff --exit-code -- docs/api frontend`; verificar que el comando termina sin diff y que `openspec validate c-04-auth-jwt-backend --strict` pasa.
- [ ] 5.4 Ejecutar la suite completa con `./mvnw test` (Docker disponible) y verificar que todos los tests existentes de C-01/C-02/C-03 siguen pasando.

## Workflow follow-up

- Abrir PR a `main` desde la rama del change, avisando en la descripción del PR el ajuste mínimo a `DataSeeder.java` heredado de C-03.
- Archivar el change con `/opsx:archive c-04-auth-jwt-backend` y marcar C-04 como `[x]` en `CHANGES.md` después de la revisión.
