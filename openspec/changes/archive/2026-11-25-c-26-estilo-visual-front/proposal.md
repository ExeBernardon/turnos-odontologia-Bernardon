# Proposal — C-26 estilo-visual-front

## Why

Con el MVP (C-01 a C-21) completo, el front funciona pero se ve plano: estilos del navegador casi sin tocar, botones grises, formularios sin jerarquía y cada pantalla con valores de color sueltos. El equipo quiere una presentación más cuidada para la entrega sin tocar comportamiento: es el momento porque ya no quedan cambios funcionales en curso sobre el front.

## What Changes

- Nueva paleta y escala visual (revisión 2 de `design-system/tokens.css`): primario teal/azul sereno, fondo de página blanco roto, tarjetas blancas con radio de 12–16 px y sombra suave, tipografía del sistema con jerarquía clara.
- El front consume los tokens desde una sola copia sincronizada con `design-system/tokens.css` (test de sincronización, igual que los tipos del contrato); las pantallas dejan de redefinir tokens en su propio `:root`.
- Capa global en `frontend/src/styles/base.css`: cuerpo, encabezados, links, botones (primario, secundario, destructivo, deshabilitado), inputs y selects con anillo de foco, fieldsets, avisos de estado y error.
- Retoques livianos por pantalla donde la capa global no llega: cabecera y menú lateral del admin, layout de tarjeta centrada en una columna para las páginas públicas, opciones de radio como "chips", badges de estado con color más texto.
- **Solo CSS**: sin cambios de lógica, de estructura HTML/TS, de contrato ni dependencias nuevas.

## Capabilities

### New Capabilities
- `estilo-visual-front`: apariencia compartida del front — tokens visuales como única fuente, estilos globales de controles y estados, y requisitos de accesibilidad visual (contraste, foco, objetivos táctiles, sin scroll horizontal, movimiento reducido).

### Modified Capabilities
<!-- Ninguna: no cambia el comportamiento de ninguna pantalla existente. -->

## Impact

- Archivos CSS del front: `src/styles/` (nuevo `tokens.css` + `base.css`), `admin/admin.css`, `admin/agenda/agenda.css`, `admin/agenda-acciones/acciones.css`, `admin/configuracion/configuracion.css`, `admin/pacientes.css`, `shared/buscador-pacientes.css`, `public/reservar.css`, `public/turno-token.css`. Varios pertenecen a changes de otros integrantes (regla 16): se coordina en el PR.
- Tests de CSS existentes (`agenda.css.test.ts`, `acciones.css.test.ts`) se actualizan solo si mencionan la revisión 1; se agrega un test de sincronización de tokens y de reglas globales.
- `design-system/tokens.css` y `design-system/README.md` pasan a revisión 2 (pendiente de aprobación).
- Sin impacto en backend, contrato (`docs/api/`), Docker ni dependencias.
