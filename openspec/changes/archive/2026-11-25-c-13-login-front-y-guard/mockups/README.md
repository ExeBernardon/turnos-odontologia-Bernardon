# Mockups — C-13 login-front-y-guard

Design system revision: 1 (`design-system/README.md`, `Approval: approved (2026-11-24, by user)`).
Approval: approved (2026-11-24, by user).
Preview: rendered (2026-11-24, Edge headless; `previews/login-{mobile,desktop}.png` y `previews/layout-admin-{mobile,desktop}.png`, 390×844 y 1440×900).

## Brief

- **Audiencia**: el odontólogo (único usuario con login) y, como referencia indirecta, el paciente que llega desde el link público.
- **Tarea**: ingresar con email y contraseña, recuperarse de credenciales inválidas y navegar por Agenda / Configuración / Pacientes con una salida clara.
- **Pantallas y estados**: `/admin/login` — inicial, validación local, credenciales inválidas (401); layout del área admin — Agenda activa y Configuración activa, con navegación y cierre de sesión.
- **Tamaños responsive**: 390×844 y 1440×900. Los estados del login se acomodan en una columna en móvil y en rejilla en escritorio; el shell envuelve la navegación sin duplicar el `<h1>` de la pantalla.
- **Tokens y assets aprobados**: `design-system/tokens.css` revisión 1 y `design-system/assets/logo.svg` (placeholder `(TBD)`). El nombre visible se enlaza con `design-system/brand.js`.
- **Restricciones**: TypeScript vanilla sin framework; `<label>` asociado, foco visible y navegación por teclado; texto insertado con `textContent`; sin datos reales de pacientes; sin endpoints ni contrato nuevos.
- **Fuentes**: `knowledge-base/06_funcionalidades.md` US-001, `knowledge-base/07_flujos_principales.md` Flujo 1, `knowledge-base/03_actores_y_roles.md` §Rutas privadas, `docs/api/ejemplos/auth-login.*`, `CHANGES.md` C-13.

## Archivos

- `login.html` — tres estados del formulario de acceso.
- `layout-admin.html` — navegación del área privada en dos estados activos.
- `previews/` — capturas de cada archivo en móvil y escritorio.
- `verify/` — capturas de la implementación final (`login-*` y `layout-admin-*`) y `resultados.json` del chequeo de consola/red.

## Cómo previsualizar

Abrir los HTML directamente en el navegador (funcionan con `file://`, sin servidor ni dependencias remotas):

- `frontend` no interviene: los mockups importan `../../../../design-system/tokens.css` y `../../../../design-system/brand.js`.
- Para ver el foco, tabular por los campos y enlaces; el outline usa el token `--focus-color`.

## Verificación de la implementación

Con el dev server de Vite y Edge headless/CDP se capturaron `/admin/login` y `/admin/agenda` a 390×844 y 1440×900 en `verify/`. `verify/resultados.json` registró cero errores de consola, excepciones y requests fallidos; el DOM mostró formulario sin nav en login y shell con `h1: Agenda` en el área privada. La comparación de píxeles contra `previews/` queda a revisión humana.

## Aprobación

Los mockups se muestran al usuario con sus capturas. Recién con `Approval: approved (<fecha>, by user)` en este archivo y en `design.md` se habilita la implementación de UI de C-13.
