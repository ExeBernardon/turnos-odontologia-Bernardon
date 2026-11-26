# Design system — Turnos odontológicos

Base visual portable del proyecto. Conserva las decisiones visuales ya presentes en
el front; no introduce una marca nueva.

Mode: system-and-mockups
Revision: 2
Approval: approved (2026-11-25, by user)
Previous: revision 1 approved (2026-11-24, by user)
Decision source: equipo (pedido de rediseño visual solo CSS, 2026-11-25)
Date: 2026-11-25

## Propósito

Un único lugar para los valores visuales compartidos por la pantalla pública,
el login del odontólogo y el área de administración. Los mockups de cada change
importan `tokens.css` y `brand.js`; el front puede copiar los valores a su CSS
cuando el change lo requiera, sin inventar estilos por pantalla.

## Revisión 2 (pendiente de aprobación)

Paleta teal serena, fondo de página blanco roto, tarjetas blancas con radio de
12–16 px y sombra suave. Contrastes WCAG medidos en
`openspec/changes/c-26-estilo-visual-front/design.md`.

- Tokens nuevos: `--color-page`, `--color-input-border`, `--color-primary-soft`,
  `--color-primary-strong`, `--color-danger-bg`, `--color-disabled-bg`,
  `--color-disabled-text`, `--color-overlay`, `--font-size-md`, `--font-size-xl`,
  `--font-weight-medium`, `--font-weight-bold`, `--line-height-tight`, `--space-5`,
  `--radius-lg`, `--shadow-raised`, `--public-max-width`.
- Valores cambiados: primario `#1a56db` → `#0f6e66`, texto, bordes, estados,
  radios (`--radius-sm` 0.5rem, `--radius-md` 0.75rem) y sombras.
- El front usa una copia exacta en `frontend/src/styles/tokens.css`; el test
  `frontend/src/styles/tokens.sync.test.ts` falla si difieren. Al cambiar este
  archivo, copiarlo también allí.

## Fuentes (hechos observados, revisión 1)

- `frontend/index.html:7` y `frontend/admin/index.html:8`: título "Turnos odontológicos" / "Turnos odontológicos - Administración".
- `frontend/src/styles/base.css:1-29`: `system-ui`, color de texto `#1a1a1a`, fondo blanco, ancho máximo `60rem`, foco `3px solid #1a56db` con offset `2px`.
- `frontend/src/admin/configuracion/configuracion.css:1-61`: bordes `#c7ccd4`, radio `0.5rem`, controles de `2.75rem` de alto, verde `#14532d`/`#dcfce7`, rojo `#991b1b`/`#b91c1c`, aviso ámbar `#fffbeb`/`#78350f`, texto secundario `#4b5563`.
- `knowledge-base/09_decisiones_y_supuestos.md` DD-01 y DD-03: dos áreas (pública y admin) en TypeScript vanilla, sin framework.
- `AGENTS.md` regla 12: formularios accesibles con `<label>`, foco visible y teclado completo.

## Decisiones abiertas (TBD)

- Nombre comercial definitivo: `(TBD)`. Se muestra "Turnos odontológicos" porque es el título actual del HTML; no es una marca aprobada.
- Logo definitivo: `(TBD)`. `assets/logo.svg` es un placeholder etiquetado como tal.
- Imágenes/fotografía: `(TBD)`. No se usan en esta revisión.
- Paleta de marca extendida, tipografía de marca y animaciones específicas: `(TBD)`. Se reutilizan los valores observados.

## Reemplazo en un solo lugar

1. Nombre mostrado: editar `BRAND_NAME` en `design-system/brand.js`. Los HTML con `[data-brand-name]` se actualizan solos.
2. Logo: reemplazar `design-system/assets/logo.svg` por el archivo definitivo, manteniendo la ruta.
3. Colores, tipografía, espaciado, radios, sombras, foco y movimiento: editar `design-system/tokens.css`.
4. Si cambia cualquier token o asset compartido: subir `Revision:` en este archivo, actualizar `visual_design.revision` en `.active-orchestrator-state.json`, rerenderizar `components.html` y los mockups afectados y pedir nueva revisión.

## Previews

- `design-system/previews/components-mobile.png` (390×844)
- `design-system/previews/components-desktop.png` (1440×900)
- Vista interactiva: `design-system/components.html` (abrir en navegador local).

## Uso

Los cambios visuales siguen la política `system-and-mockups`: con esta base
aprobada, cada change crea sus mockups en `openspec/changes/<nombre>/mockups/`,
los muestra para aprobación y recién después se implementa.
