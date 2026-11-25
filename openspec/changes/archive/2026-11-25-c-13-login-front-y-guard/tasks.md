# Tasks: c-13-login-front-y-guard

## 1. Gate visual (política `system-and-mockups`, revisión 1)

- [x] 1.1 Obtener aprobación del usuario para `design-system/README.md` (Revision 1) y sus previews `design-system/previews/components-{mobile,desktop}.png`; verificar que `Approval: approved (<fecha>, by user)` quede registrada en el README y `visual_design.status: approved` en `.active-orchestrator-state.json` antes de crear mockups.
- [x] 1.2 Crear los mockups HTML/CSS de `/admin/login` (inicial, validación local, error 401) y del layout del área admin (escritorio y móvil) en `openspec/changes/c-13-login-front-y-guard/mockups/`, importando `design-system/tokens.css` y `brand.js`; verificar que el README del change enlace los archivos y, tras la aprobación de 1.4, marque `Approval: approved (2026-11-24, by user)`.
- [x] 1.3 Renderizar cada mockup a 390×844 y 1440×900, guardar las capturas en `mockups/previews/` y actualizar `Preview:` en `design.md`; verificar que las cuatro imágenes existen y que sus dimensiones son las esperadas.
- [x] 1.4 Presentar los mockups y capturas al usuario y obtener aprobación explícita; verificar que `Approval: approved (<fecha>, by user)` quede registrada en `design.md` y en `mockups/README.md` antes de tocar código de UI. No iniciar la implementación sin esta aprobación.

## 2. Sesión del odontólogo con vencimiento

- [x] 2.1 Escribir `frontend/src/shared/sesion.test.ts` (TDD) para guardado/lectura/borrado en `localStorage`, expiración por `expiraEn`, compatibilidad con token legacy sin expiración y shim `obtenerToken`/`guardarToken`/`borrarToken`; verificado: 6 tests fallaron por módulo incompleto antes de la implementación.
- [x] 2.2 Implementar la sesión con clave `turnos.sesion` y el shim de compatibilidad en `frontend/src/shared/sesion.ts`; verificado: 7/7 tests pasan y `tsc --noEmit` sin errores.

## 3. Cliente HTTP y 401 de sesión expirada

- [x] 3.1 Ampliar `frontend/src/api/cliente.test.ts` (TDD) para comprobar el header `Authorization: Bearer` existente, la notificación solo en 401 `NO_AUTENTICADO`, la entrega intacta del `ErrorApi` y la ausencia de notificación en 401 `CREDENCIALES_INVALIDAS` del login; verificado: falla por `registrarManejadorExpiracion is not a function` antes del cambio.
- [x] 3.2 Implementar el registrador/reset del handler de expiración y la notificación condicional en `cliente.ts` sin cambiar la firma de `pedir`; verificado: 28/28 tests pasan incluidos los de configuración de C-14/C-15.

## 4. Guard del router

- [x] 4.1 Agregar a `frontend/src/shared/router.test.ts` (TDD) escenarios de ruta privada sin sesión, sesión vencida, ruta `/login` pública, sesión válida y ausencia de bucle de redirección; verificado: 2 tests fallaron antes del cambio.
- [x] 4.2 Implementar la opción `redirigir` en `crearRouter` y pasarla desde `admin/main.ts` usando `sesionValida()`; verificado: 29/29 tests de router, rutas y sesión pasan y el área pública no cambió.

## 5. Formulario de login

- [x] 5.1 Crear `frontend/src/admin/login.test.ts` (TDD) para envío de `LoginRequest`, 200 con guardado de sesión y navegación a `/admin/agenda`, 401 `CREDENCIALES_INVALIDAS` con mensaje genérico y sin redirect, 400 `VALIDACION` con detalle por campo y validación local de campos vacíos/email inválido; verificado: 6 tests fallaron contra el placeholder.
- [x] 5.2 Implementar `frontend/src/admin/login.ts` con campos etiquetados, `autocomplete`, `aria-describedby`, estados de error y envío por `pedir`; verificado: 6/6 tests pasan, `tsc --noEmit` y `eslint src` limpios.

## 6. Layout del área admin

- [x] 6.1 Ampliar `frontend/src/rutas.test.ts` y crear la prueba del shell (TDD) para enlaces Agenda/Configuración/Pacientes con `aria-current`, cambio de sección sin recarga, control de cerrar sesión que borra la sesión y login sin navegación; verificado: 6 tests fallaron antes del wrapper.
- [x] 6.2 Implementar el wrapper del layout en `admin/rutas.ts`, el handler de logout y el registro del handler de expiración en `admin/main.ts`, más `admin/admin.css` con los tokens de la revisión 1; verificado: 16/16 tests de rutas pasan, configuración conserva sus `<h1>` y `/admin/login` no muestra el layout.

## 7. Verificación integrada

- [x] 7.1 Ejecutar `cd frontend && npm test && npm run lint && npm run build`; verificado: 17 archivos / 170 tests en verde, `eslint .` limpio y build con `tsc --noEmit` + Vite correcto.
- [x] 7.2 Comparar login y layout implementados contra los mockups aprobados en cada viewport, usando el dev server y capturas en `mockups/verify/`; verificado: 4 capturas 390×844/1440×900, cero errores de consola/excepciones/requests fallidos y DOM esperado; sin diferencias estructurales, comparación de píxeles manual registrada en `design.md`.
- [x] 7.3 Verificado con `git status --short`, `git diff --stat` y `git diff --name-only`: `docs/api/openapi.yaml`, `docs/api/tipos.ts` y los tests de C-14/C-15 no aparecen modificados; solo se tocaron archivos de C-13, la política visual y el state del orquestador.

## Workflow follow-up

- Abrir PR a `main` desde `c-13-login-front-y-guard`, con conventional commit en español y usuario propio, avisando en la descripción que el interceptor de 401 se registra solo en el bootstrap para no afectar C-14/C-15.
- Archivar el change con `/opsx:archive` y marcar `[x]` en `CHANGES.md` luego de mergeado.
