# Mockups — C-19 agenda-vistas-front

Design system revision: 1 (`design-system/README.md`, `Approval: approved (2026-11-24, by user)`).
Decision: yes (política `system-and-mockups`).
Approval: approved (2026-11-25, by user).
Preview: rendered (2026-11-25, Edge headless; `previews/agenda-diaria-{mobile,desktop}.png` y `previews/agenda-semanal-{mobile,desktop}.png`, 390×844 y 1440×900).

## Brief

- **Audiencia**: el odontólogo, único usuario con acceso a `/admin/agenda`.
- **Tarea**: revisar la jornada por día o por semana, detectar turnos `PENDIENTE` próximos y distinguir bloqueos y horas sin atención.
- **Pantallas y estados**: vista diaria con turnos (confirmado, sin confirmar, urgente con teléfono, cancelado atenuado), sin turnos y error de carga; vista semanal de lunes a domingo con los mismos estados.
- **Tamaños responsive**: 390×844 y 1440×900. La grilla semanal pasa de siete columnas a una sola columna; la diaria apila la hora sobre los datos del turno.
- **Tokens y assets aprobados**: `design-system/tokens.css` revisión 1 y `design-system/assets/logo.svg` (placeholder `(TBD)`). El nombre visible se enlaza con `design-system/brand.js`.
- **Restricciones**: TypeScript vanilla sin framework; foco visible y navegación por teclado; texto con `textContent`; datos ficticios del contrato; sin acciones sobre turnos (C-20); sin endpoints nuevos.
- **Fuentes**: `knowledge-base/06_funcionalidades.md` US-009, `knowledge-base/07_flujos_principales.md` Flujo 6, `knowledge-base/08_arquitectura_propuesta.md` §Zona horaria, `docs/api/ejemplos/turnos-listar.200.json`, `bloqueos-listar.200.json`, `horarios-listar.200.json`, `CHANGES.md` C-19.

## Archivos

- `agenda-diaria.html` — día con turnos, bloqueo y horarios fuera de atención; día sin turnos; error de carga.
- `agenda-semanal.html` — semana completa con siete columnas y estados por turno.
- `previews/` — capturas de cada archivo en móvil y escritorio.
- `verify/` — capturas de la implementación real en `/admin/agenda`: `agenda-diaria-{mobile,desktop}.png` y `agenda-semanal-{mobile,desktop}.png`.

## Cómo previsualizar

Abrir los HTML directamente en el navegador (funcionan con `file://`, sin servidor ni dependencias remotas):

- Los mockups importan `../../../../design-system/tokens.css` y `../../../../design-system/brand.js`.
- Para ver el foco, tabular por los controles; el outline usa el token `--focus-color`.

## Inspección

Las capturas se generaron con Edge headless y su DOM se verificó por texto. La inspección visual automatizada no estuvo disponible en esta sesión (el modelo no acepta imágenes); la revisión humana de las cuatro capturas fue realizada y aprobada por el usuario el 2026-11-25.

La verificación de la implementación se registró en `design.md` como `Visual verify: manual (2026-11-25)`: se capturó `/admin/agenda` con Edge headless/CDP en 390×844 y 1440×900, con sesión ficticia y respuestas mock del contrato. No hubo errores de consola, requests fallidos ni respuestas HTTP ≥ 400. La comparación visual lado a lado con los mockups aprobados sigue pendiente de revisión humana.

## Aprobación

Los mockups fueron mostrados al usuario con sus capturas y aprobados el 2026-11-25. La implementación de UI de C-19 queda habilitada con `Approval: approved (2026-11-25, by user)` en este archivo y en `design.md`.
