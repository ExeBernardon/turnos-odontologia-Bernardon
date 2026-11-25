# Design

## Context

`frontend/src/admin/agenda.ts` hoy es un placeholder conectado al router de C-13, que ya provee el shell con navegación y guard de sesión. C-08 cerró `GET /api/turnos?desde&hasta&estado` (con `pendienteMenos24h` y teléfono de contacto), C-10 `GET /api/bloqueos` y C-05 `GET /api/horarios-atencion`; los ejemplos JSON del contrato son los fixtures. Ver `proposal.md` para la motivación y `specs/agenda-vistas-front/spec.md` para el comportamiento requerido. Es un change de solo lectura: las acciones quedan para C-20.

## Goals / Non-Goals

**Goals:** reemplazar el placeholder por una grilla diaria/semanal accesible; separar la lógica pura de fecha/grilla del DOM; parsear defensivamente las respuestas; dejar enganches estables para C-20; cubrir con Vitest los casos límite.

**Non-Goals:** confirmar, cancelar, reprogramar ni cargar turnos; modificar el contrato, el backend, el cliente HTTP, el router o la sesión; mostrar DNI o emails de contacto en la agenda; añadir frameworks o dependencias.

## Decisions

1. **Capas bajo `src/admin/agenda/`.** `agenda.api.ts` consulta los tres endpoints y valida la forma de cada respuesta; `agenda.logica.ts` calcula rangos, posiciones y estados sin DOM; `agenda.ts` orquesta eventos y render. Alternativa descartada: todo en `agenda.ts`, porque C-20 necesitará reutilizar la grilla y los parseos.
2. **Rangos derivados de Buenos Aires.** El día es `YYYY-MM-DD` en `America/Argentina/Buenos_Aires`; la semana va de lunes a domingo (ISO, `diaSemana` 1..7). Un único cálculo de rango se reutiliza para turnos y bloqueos, que exigen `desde`/`hasta`; horarios no llevan parámetros. El estado de vista/periodo se mantiene en la pantalla y se vuelve a renderizar sin recargar; no se persiste en la URL en este change.
3. **Tres consultas independientes.** Se lanzan juntas; si alguna falla se muestra un banner con `role="alert"` y un botón "Reintentar", y se renderizan solo las secciones que llegaron, sin presentar la grilla como completa. Alternativa descartada: `Promise.all` estricto, que pierde información útil cuando falla solo horarios o bloqueos.
4. **Grilla a partir de horarios.** Para cada día se generan los intervalos de atención y se marcan como "sin atención" los huecos entre rangos y fuera de ellos; los bloqueos se superimponen como franjas diferenciadas y los turnos se ubican por `inicio`. Los cancelados se filtran del flujo principal y solo se agregan atenuados cuando se activa el control. Alternativa descartada: grilla de 24 h, más ruidosa y ajena al consultorio.
5. **Urgencia según el backend.** El resaltado usa `estado === 'PENDIENTE' && pendienteMenos24h === true`; no se recalcula con la hora del navegador, para no discrepar del `Clock` del servidor. Los enganches para C-20 son contenedores con atributos `data-turno-id` y `data-enganche="confirmar|cancelar"`; el mockup los representa con botones deshabilitados.
6. **Formato y parseo.** `Intl.DateTimeFormat('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' })` para todo instante; el día se compone con `formatToParts`. Cada item se valida antes de usarse y los elementos con campos inválidos se ignoran sin romper la pantalla. Alternativa descartada: `new Date(...).toLocaleString()` sin zona explícita, que depende del dispositivo.
7. **Reuso y estilos.** Se aprovechan `el`, `montar`, `pedir`, `ErrorApi`, la sesión y los tipos del contrato; se agrega `agenda.css` de forma aditiva al `admin.css` existente, con columnas en escritorio y apilado en móvil. Los tokens visuales son los de `design-system/tokens.css` revisión 1.
8. **TDD.** Primero tests puros de rangos, agrupamiento por día, ubicación de franjas y formato con instantes fijos; luego tests de API con los ejemplos del contrato y tests DOM de render, navegación, toggle de cancelados, error y reintento.

## Risks / Trade-offs

- Tres requests con rangos distintos pueden desincronizarse → se calcula un solo rango por render y se reutiliza en turnos y bloqueos; los bloqueos tienen límite de 31 días, holgura suficiente para una semana.
- `Intl` con `timeZone` puede variar entre entornos → Node con ICU completo y tests con instantes UTC fijos; si jsdom no soporta la zona, se aísla la función de formato y se prueba por separado.
- Argentina usa actualmente offset `-03:00`, pero no se hardcodea: `Intl` resuelve el desplazamiento.
- La grilla semanal puede ser densa → se muestran tarjetas compactas por día y se apilan en pantallas angostas; no se agrega scroll horizontal.
- C-20 dependerá de la estructura del DOM → los atributos `data-enganche` son un contrato interno estable y se cubren con tests.

## Migration Plan

Reemplazar el cuerpo del placeholder en `src/admin/agenda.ts`; no hay datos ni esquema que migrar. Rollback: restaurar el placeholder previo y retirar los módulos nuevos, sin tocar otras pantallas.

## UI Mockups

- Design system revision: 1 (`design-system/README.md`, `Approval: approved (2026-11-24, by user)`).
- Decision: yes (2026-11-25, política `system-and-mockups`; no se solicitó saltear el gate).
- Mockups: `openspec/changes/c-19-agenda-vistas-front/mockups/` (`agenda-diaria.html`, `agenda-semanal.html`). Fuentes: KB 06 US-009, KB 07 Flujo 6, KB 08 §Zona horaria, ejemplos del contrato y mockups de C-13.
- Approval: approved (2026-11-25, by user). El usuario revisó las capturas y autorizó la implementación de UI.
- Preview: rendered (2026-11-25, Edge headless; `mockups/previews/agenda-{diaria,semanal}-{mobile,desktop}.png`, 390×844 y 1440×900). Playwright MCP no pudo iniciar por falta de Chrome. La inspección visual por el modelo no estuvo disponible; la revisión humana fue realizada y aprobada por el usuario el 2026-11-25.
- Visual verify: manual (2026-11-25). Implementación capturada con Edge headless/CDP contra `http://localhost:5173/admin/agenda` (sesión ficticia y respuestas mock del contrato): `mockups/verify/agenda-{diaria,semanal}-{mobile,desktop}.png`, 390×844 y 1440×900. Sin errores de consola, requests fallidos ni respuestas HTTP ≥ 400. No se afirma coincidencia visual: el modelo no acepta imágenes y la comparación lado a lado con las capturas aprobadas queda para revisión humana. Checklist pendiente: encabezado/navegación, grilla diaria con urgente/cancelado/bloqueo/fuera de horario, grilla semanal de lunes a domingo, apilado en 390 px, foco visible y sin desbordamiento.
