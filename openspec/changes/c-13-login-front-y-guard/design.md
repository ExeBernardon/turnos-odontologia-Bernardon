# Design: c-13-login-front-y-guard

## Context

Ver `proposal.md` para el motivo. Estado observable del repo al planificar: C-02 dejó un router mínimo, un cliente HTTP tipado y `sesion.ts` con un token en `sessionStorage`; C-04 implementó `POST /api/auth/login` y el JWT. `login.ts` y el layout son placeholders. C-14 y C-15 ya mergearon las secciones de configuración, que manejan sus propios 401 con alerta y reintento y renderizan directamente en el contenedor que les da el router. El contrato fija `LoginRequest`, `LoginResponse` y el error uniforme; no se cambia.

## Goals / Non-Goals

**Goals:**
- Habilitar el flujo completo de login del Flujo 1 con el contrato existente.
- Hacer que las rutas privadas del front sean inaccesibles sin sesión válida.
- Proveer un área admin navegable con cierre de sesión, sin reescribir pantallas ya implementadas.
- Reaccionar a la expiración de la sesión ante un 401 `NO_AUTENTICADO`.

**Non-Goals:**
- No cambiar endpoints, DTOs ni códigos de error; no implementar refresh token, revocación ni logout en el backend (JWT stateless).
- No modificar la lógica de prestaciones, horarios ni bloqueos de C-14/C-15.
- No alterar el área pública ni el router cuando se usa sin guard.
- No agregar framework de UI ni dependencias nuevas de runtime.

## Decisions

### 1. Sesión en `localStorage` con expiración local y shim de compatibilidad

`CHANGES.md` (C-13) y DD-03 (`knowledge-base/09_decisiones_y_supuestos.md`) prescriben `localStorage` con expiración; el `sessionStorage` actual es un placeholder del scaffold. Se guarda un objeto `turnos.sesion` con `token`, `tipo`, `expiraEn` y `nombre`, y se conservan `obtenerToken()`, `guardarToken()` y `borrarToken()` como shim para no tocar el cliente HTTP ni sus tests. Si existe un token legacy sin `expiraEn`, se acepta mientras el token esté presente.

Alternativas: mantener `sessionStorage` (descartada: contradice el roadmap y pierde la sesión al cerrar la pestaña); guardar solo el token (descartada: no permite expiración local ni mostrar el nombre).

### 2. Guard como opción del router compartido, no como wrapper de cada pantalla

`crearRouter` acepta una opción `redirigir?: (ruta) => string | null`. El router la consulta antes de resolver; si devuelve una ruta, navega ahí y no renderiza la pantalla. El bootstrap del admin pasa una función que devuelve `/login` para las tres rutas privadas cuando no hay sesión válida y `null` para `/login`. El área pública no pasa la opción y conserva su comportamiento.

Alternativas: envolver cada `Pantalla` en `rutas.ts` (descartada: obligaría a coordinar cambios en archivos de C-14/C-15 o a duplicar la lógica de navegación); guard dentro de cada pantalla (descartada: olvidable y no testeable de forma centralizada).

### 3. Interceptor de 401 registrado desde el bootstrap

El cliente sigue lanzando `ErrorApi` con `status`, `codigo`, `mensaje` y `detalles` para que cada pantalla pueda mostrar su error. Además, si el status es 401 y el código es `NO_AUTENTICADO`, notifica un handler opcional. El handler se registra en `admin/main.ts` y hace `borrarSesion()` + `router.navegar('/login')`. Sin handler registrado (nets de C-14/C-15 y tests de unidades), la notificación es un no-op: las pantallas siguen mostrando alerta y reintento. `CREDENCIALES_INVALIDAS` del login nunca dispara la redirección.

Alternativas: redirección incondicional dentro de `pedir` (descartada: rompe los tests de reintento de configuración y acopla el cliente al router); lanzar un error especial (descartada: cambia la API del cliente para todas las pantallas).

### 4. Layout admin como wrapper en la tabla de rutas

`admin/rutas.ts` define un wrapper que, para las rutas privadas, renderiza la pantalla en un contenedor hijo y arma alrededor un encabezado con `<nav>` y el control de cerrar sesión. `/login` se registra sin wrapper. Los enlaces usan `data-link` y son interceptados por el listener existente del router (está en `document`), por lo que no hay que pasar `navegar` a cada pantalla. Cerrar sesión borra la sesión en el handler del enlace y deja que el router navegue a `/login`. El wrapper no agrega `<h1>`: solo navegación.

Alternativas: montar el shell en `main.ts` con un contenedor hijo (descartada: el router reemplaza todos los hijos en cada render y habría que coordinar el ciclo de vida); que cada pantalla se envuelva sola (descartada: modifica archivos ajenos ya mergeados).

### 5. Formulario de login local, con patrones accesibles ya usados

`admin/login.ts` implementa sus propios campos con `<label>` asociado, `autocomplete`, `aria-describedby`, `role="alert"`/`role="status"` y validación local. Se reutiliza el estilo visual de `configuracion.css`, pero no se importa `admin/configuracion/formulario.ts` para no acoplar el login a una carpeta de otro change. Todo el texto se inserta con `textContent`, nunca `innerHTML` con datos.

### 6. CSS del área admin espejo de los tokens

Se agrega `frontend/src/admin/admin.css` con variables locales que replican la revisión 1 de `design-system/tokens.css` (color de foco `#1a56db`, borde `#c7ccd4`, radio `0.5rem`, control mínimo `2.75rem`, ancho máximo `60rem`). No se importa el archivo del design system desde el build de Vite: eso exigiría cambiar `vite.config.ts` y cruces de raíz. Si cambia un token compartido, se actualiza este archivo y se sube la revisión.

### 7. Pruebas TDD por comportamiento

- `sesion.test.ts`: guardado, lectura, borrado, vencimiento y compatibilidad legacy.
- `login.test.ts`: envío del `LoginRequest`, 200 → sesión y navegación, 401 → mensaje genérico sin redirect, 400 → detalle por campo, validación local.
- `router.test.ts`: guard con y sin sesión, ruta pública permitida, sin bucle de redirección.
- `cliente.test.ts`: header `Bearer` existente, notificación solo para 401 `NO_AUTENTICADO`, error entregado igual que antes, login 401 sin notificación.
- `rutas.test.ts`: navegación del shell, enlace activo y logout sin duplicar `<h1>` de la pantalla.

## Risks / Trade-offs

- [JWT en `localStorage` expuesto a XSS] → Mitigación: DD-03 ya lo acepta; se mantiene la regla de insertar todo texto con `textContent` y no evaluar HTML del servidor.
- [Estado global del handler de 401 entre tests] → Mitigación: el módulo expone un reset usado en `beforeEach` de los tests; en producción se registra una sola vez en el bootstrap.
- [Redirect compite con la alerta de configuración] → Mitigación: el error se sigue lanzando y el redirect solo ocurre con el handler del bootstrap; se documenta en el PR que los tests de C-14/C-15 se dejaron intactos.
- [Wrapper del layout re-renderiza la navegación en cada cambio de ruta] → Mitigación: es DOM barato y evita tocar pantallas mergeadas.
- [Sesión legacy sin `expiraEn` queda válida indefinidamente] → Mitigación: compatibilidad temporal; el backend sigue siendo la autoridad y responderá 401 `NO_AUTENTICADO` cuando el token venza, disparando el borrado y redirect.

## Migration Plan

1. La primera carga con el nuevo código lee el token legacy y lo usa como sesión sin expiración; no se pierde el acceso.
2. El siguiente login guarda el objeto completo con `expiraEn`.
3. No hay cambios de API ni de esquema; rollback = revertir el commit del change.

## UI Mockups

- Design system revision: 1 (`design-system/README.md`, `Approval: approved (2026-11-24, by user)`).
- Decision: yes (2026-11-24), según la política `system-and-mockups` elegida por el usuario.
- Mockups: `openspec/changes/c-13-login-front-y-guard/mockups/` (`login.html` con tres estados; `layout-admin.html` con Agenda y Configuración activas).
- Approval: approved (2026-11-24, by user).
- Preview: rendered (2026-11-24, Edge headless; `mockups/previews/login-{mobile,desktop}.png` y `layout-admin-{mobile,desktop}.png`). Inspección visual automatizada no disponible: el modelo no acepta imágenes, el usuario revisa las capturas.
- Visual verify: manual (2026-11-24). Dev server + Edge headless/CDP: capturas en `mockups/verify/` a 390×844 y 1440×900, sin errores de consola, excepciones ni requests fallidos; comprobado por DOM que `/admin/login` muestra el formulario sin nav y `/admin/agenda` muestra el shell con `h1: Agenda`. Sin diferencias estructurales detectadas; comparación visual de píxeles no automatizada, pendiente de revisión humana.

La base visual debe aprobarse antes de crear los mockups; los mockups deben aprobarse antes de implementar cualquier cambio de UI de este change. Si la base se revisa, se sube la revisión y se rerenderizan los mockups afectados.

## Open Questions

Ninguna que bloquee el diseño. La política visual quedó resuelta: `system-and-mockups`, revisión 1 aprobada el 2026-11-24.
