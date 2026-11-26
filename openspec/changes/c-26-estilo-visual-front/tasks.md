# Tasks — C-26 estilo-visual-front

## 1. Tokens revisión 2 (fuente única)

- [x] 1.1 RED: agregar `frontend/src/styles/tokens.sync.test.ts` que compare `src/styles/tokens.css` con `design-system/tokens.css` y que falle si alguna hoja de pantalla redeclara un token en `:root`; verificar que `npm test` falla
- [x] 1.2 GREEN: actualizar `design-system/tokens.css` a la paleta de la revisión 2 (design.md, decisión 4), copiarlo a `frontend/src/styles/tokens.css`, importarlo desde `base.css` y quitar los `:root` duplicados de `admin.css` y `agenda.css` (dejando `--radius-pill` exigido por su test); verificar `npm test` en verde
- [x] 1.3 Subir `Revision: 2` y marcar "pending approval" en `design-system/README.md`, con la lista de tokens nuevos; verificar que el README nombra los tokens agregados

## 2. Capa global (`src/styles/base.css`)

- [x] 2.1 RED: agregar `frontend/src/styles/base.css.test.ts` que exija foco visible con `var(--focus-color)`, bloque `prefers-reduced-motion`, `min-height: var(--control-min-height)` en botones y controles, `button[type='submit']` primario y estados `:disabled` y `[aria-invalid='true']`; verificar que falla
- [x] 2.2 GREEN: escribir la capa global (body, encabezados, links, botones primario/secundario/deshabilitado, inputs/selects/textarea, fieldset/legend, avisos `role="status"`/`role="alert"`) usando solo tokens; verificar `npm test` en verde
- [x] 2.3 Verificar contraste AA de cada par texto/fondo y borde/fondo de la paleta (script de cálculo WCAG) y anotar los valores en design.md

## 3. Retoques por pantalla (solo donde la capa global no llega)

- [x] 3.1 Admin (`admin.css`): cabecera, menú lateral con página actual destacada, login en tarjeta, ancho completo del shell; verificar en `/admin/login` y `/admin/agenda` a 375 px y escritorio
- [x] 3.2 Agenda y acciones (`agenda.css`, `acciones.css`): tarjetas de turno, badges con color + texto, destructivo con borde; verificar `agenda.css.test.ts` y `acciones.css.test.ts` en verde
- [x] 3.3 Configuración, pacientes y buscador: reemplazar hex por tokens y dar estilo de tarjeta a ítems; verificar visualmente `/admin/configuracion` y `/admin/pacientes`
- [x] 3.4 Públicas (`reservar.css`, `turno-token.css`): columna centrada, secciones como tarjetas, radios como chips, destructivo con borde; verificar `/reservar` eligiendo prestación y fecha, sin reservar

## 4. Verificación integral

- [x] 4.1 `npm run lint && npm run typecheck && npm test && npm run build` en verde, sin cambios en archivos `.ts` de pantallas
- [x] 4.2 Reconstruir el contenedor (`docker compose up -d --build --wait frontend`) y revisar todas las pantallas a 320/375 px y escritorio: sin scroll horizontal, foco visible con Tab, objetivos de 44 px
- [ ] 4.3 Avisar en el PR a los dueños de cada hoja de estilo tocada (regla 16) y pedir aprobación de la revisión 2 del design system

## Workflow follow-up

- Agregar C-26 a `CHANGES.md` cuando el equipo apruebe el change.
- Archivar con `/opsx:archive c-26-estilo-visual-front` tras la aprobación y el merge.
