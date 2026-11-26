# Design — C-26 estilo-visual-front

## Context

Ver `proposal.md` (Why). Estado observado del front:

- `src/styles/base.css` es casi vacío (fuente del sistema, foco azul, `main` de 60rem) y lo importan las dos entradas (`public/main.ts`, `admin/main.ts`).
- Los tokens de la revisión 1 están **duplicados**: `admin/admin.css` y `admin/agenda/agenda.css` los redeclaran en `:root`; el resto de las pantallas (`configuracion.css`, `pacientes.css`, `buscador-pacientes.css`, `reservar.css`, `turno-token.css`) usa hex sueltos.
- Los botones no tienen clase común: la mayoría sale de `crearBoton()` con `type="button"` o `type="submit"`; solo agenda-acciones usa `.aa-boton`, `.aa-boton--primario`, `.aa-boton--peligro`.
- El build de Docker usa `./frontend` como contexto: el front no puede importar archivos de `design-system/` en build.
- Hay tests que leen CSS: `agenda.css.test.ts` (exige `--radius-pill: 999px` y ciertos `var(...)`) y `acciones.css.test.ts` (selectores acotados a `.aa-*`/`.agenda-acciones`, sin hex).

## Goals / Non-Goals

**Goals:**
- Una sola fuente de tokens (rev. 2) y una capa global que cubra el 80 % del aspecto sin tocar pantallas.
- Retoques por pantalla mínimos, solo reemplazando hex por tokens y donde la capa global no alcanza.

**Non-Goals:**
- Cambiar estructura HTML, clases emitidas por el TS, textos o comportamiento.
- Logo, nombre comercial, fuentes externas, ilustraciones o modo oscuro.

## Decisions

1. **Copia sincronizada de tokens** en `frontend/src/styles/tokens.css`, importada al inicio de `base.css`, con un test que la compara con `design-system/tokens.css` (mismo patrón que `types.sync.test.ts`). *Alternativa descartada*: importar `../../design-system/tokens.css` — rompe el build de Docker (contexto `./frontend`).
2. **Jerarquía de botones por selector de atributo**: `button` = secundario; `button[type="submit"]` = primario; clases destructivas existentes (`.cfg-peligro`, `.turno-token-destructivo`, `.aa-boton--peligro`) = destructivo. Permite jerarquía sin tocar el TS. *Alternativa*: agregar clases en `crearBoton()` — descartado por la restricción de solo CSS.
3. **Semántica de tokens conservada**: `--color-bg` sigue siendo la superficie blanca (así lo usan las pantallas); se agrega `--color-page` para el fondo de página, `--color-input-border` (≥3:1) separado de `--color-border` decorativo, `--radius-lg`, `--shadow-raised`, `--font-size-md`/`--font-size-xl`, `--color-primary-soft`. Se reutilizan los nombres existentes para que las pantallas mejoren solas.
4. **Paleta (rev. 2)**, contrastes medidos con la fórmula WCAG:

   | Token | Valor | Contraste |
   |---|---|---|
   | `--color-text` | `#1f2d33` | 14.2:1 sobre blanco, 13.0:1 sobre página |
   | `--color-text-muted` | `#4f5f66` | 6.6:1 blanco, 6.1:1 página |
   | `--color-primary` | `#0f6e66` | 6.1:1 (texto blanco sobre primario y primario sobre blanco) |
   | `--color-primary-hover` | `#0b5650` | 8.5:1 con texto blanco |
   | `--color-primary-soft` / `--color-primary-strong` | `#e3f2ef` / `#0b5650` | 7.4:1 |
   | `--color-input-border` | `#7a8c93` | 3.5:1 sobre blanco |
   | `--color-page` | `#f3f6f6` | fondo |
   | éxito / peligro / aviso / neutro | `#166534` s/ `#dcfce7`, `#a3241a` s/ blanco, `#7a3e0a` s/ `#fff4e0`, `#3b4a52` s/ `#e6ecee` | 6.5 / 7.4 / 7.6 / 7.7 |

   Foco: `3px solid var(--color-primary)` + offset 2px (5.6:1 contra la página).
5. **Layout por `:has()`** para el área admin a ancho completo (`#app:has(> .admin-shell)`) sin tocar el HTML. Navegadores objetivo (evergreen) lo soportan; si no, se cae al layout actual de 60rem, sin romper nada.
6. **Tests de CSS** como red de seguridad: un test de la capa global verifica que `base.css` declara foco visible, `prefers-reduced-motion`, alto mínimo con `--control-min-height` y que ninguna hoja de pantalla redeclara tokens en `:root` (salvo `--radius-pill` que exige el test existente de agenda, con el mismo valor). La verificación visual se hace en la app real a 375 px y escritorio.

## Risks / Trade-offs

- [Archivos de otros integrantes (regla 16)] → Cambios mínimos (hex → tokens, sin reordenar reglas); se listan en el PR para que cada dueño revise.
- [El primario global para `type="submit"` puede destacar un submit secundario (p. ej. "Ver bloqueos")] → Aceptable: es la acción del formulario; se revisa por pantalla tras la aprobación.
- [Especificidad: estilos de pantalla pisan la capa global] → La capa global usa selectores de elemento (baja especificidad) y las pantallas solo fijan layout; se evita `!important`.
- [Revisión 2 sin aprobar] → `README.md` del design system queda "pending approval"; el change no se archiva hasta la aprobación.

## Migration Plan

Solo CSS: se despliega con `docker compose up -d --build frontend`. Rollback: revertir el commit (no hay datos ni contrato involucrados).

## Open Questions

- Pulido fino por pantalla (agenda semanal, configuración, pacientes) después de que el equipo apruebe la revisión 2.
