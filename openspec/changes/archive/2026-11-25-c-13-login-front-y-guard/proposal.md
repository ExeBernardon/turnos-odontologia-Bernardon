# Proposal: c-13-login-front-y-guard

## Why

El backend de autenticación (C-04) y el scaffold del front (C-02) ya están mergeados, pero el área privada todavía muestra un login placeholder y no protege sus rutas: cualquiera puede abrir `/admin/agenda`, `/admin/configuracion` o `/admin/pacientes`. C-14 y C-15 ya implementaron configuración apoyándose en este cambio; hay que cerrar la pieza de acceso para que el MVP tenga un ingreso real y una salida segura.

## What Changes

- Reemplazar el placeholder de `/admin/login` por un formulario accesible de email y contraseña que llama a `POST /api/auth/login` con el contrato de `docs/api/openapi.yaml`.
- Persistir la sesión del odontólogo (token Bearer, `expiraEn` y nombre) con vencimiento local y limpieza al cerrar sesión.
- Agregar un guard del router: sin sesión válida, las rutas privadas redirigen a `/admin/login`; la propia ruta de login queda pública.
- Interceptar en el cliente HTTP los 401 `NO_AUTENTICADO` de rutas privadas: limpiar la sesión y volver al login, sin alterar el manejo de errores de cada pantalla.
- Mantener el `Authorization: Bearer <token>` existente y su test; el login con credenciales inválidas (`CREDENCIALES_INVALIDAS`) no dispara el redirect.
- Agregar el layout del área admin con navegación a Agenda, Configuración y Pacientes y un control de cerrar sesión, sin duplicar los `<h1>` de las pantallas ya implementadas.
- Cubrir con tests Vitest: guard con y sin sesión, interceptor de 401, formato del header, expiración de la sesión, envío del formulario y logout.
- Según la política visual aprobada para el proyecto (`system-and-mockups`), crear y hacer aprobar los mockups de login y layout antes de implementar.

**BREAKING**: el almacenamiento de sesión pasa de `sessionStorage` con clave `turnos.token` a `localStorage` con datos de sesión; se conserva un shim de compatibilidad para el helper `obtenerToken()` que ya usa el cliente HTTP.

## Capabilities

### New Capabilities

- `acceso-admin-front`: comportamiento observable del acceso al área privada desde el navegador: formulario de login, sesión persistida con vencimiento, guard de rutas, reacción ante 401 de sesión expirada y layout con navegación y cierre de sesión.

### Modified Capabilities

Ninguna. `autenticacion-odontologo` define el comportamiento del backend y no cambia; `plataforma-frontend` conserva sus requisitos de ruteo, cliente HTTP tipado y errores uniformes. El interceptor de 401 se especifica como parte de la nueva capacidad, sin reemplazar la entrega de errores tipados al llamador.

## Impact

- Código: `frontend/src/admin/login.ts`, `frontend/src/admin/main.ts`, `frontend/src/admin/rutas.ts`, `frontend/src/shared/sesion.ts`, `frontend/src/shared/router.ts`, `frontend/src/api/cliente.ts`, nuevos módulos de layout/sesión y sus tests; CSS del área admin.
- Contrato: sin cambios. Se consumen `POST /api/auth/login`, sus ejemplos y los tipos ya sincronizados (`LoginRequest`, `LoginResponse`, `Usuario`, `ApiError`).
- Dependencias ya mergeadas: C-02 (scaffold, router, cliente, Vitest) y C-04 (login JWT). C-14 y C-15 se integran sin reescribir sus secciones de configuración.
- Riesgo a coordinar en el PR: los tests de C-14/C-15 simulan un 401 para verificar alerta y reintento; el interceptor se registra desde el bootstrap del admin, de modo que esos tests siguen ejerciendo el error tipado sin navigación forzada.
- UI: política `system-and-mockups`, revisión 1 del design system; mockups y capturas bajo `openspec/changes/c-13-login-front-y-guard/mockups/`.
