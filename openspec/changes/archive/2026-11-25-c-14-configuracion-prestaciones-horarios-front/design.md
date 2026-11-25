## Context

Las secciones `prestaciones.ts` y `horarios.ts` son placeholders con `render(seccion: HTMLElement)` compuestos por `configuracion/index.ts`. El cliente `src/api/cliente.ts` ya agrega el header `Authorization` desde `sesion.ts` (C-13 completa login y guard) y lanza `ErrorApi`/`ErrorConexion`. El backend de C-05 es real: `GET/POST /api/prestaciones`, `PUT/DELETE /api/prestaciones/{id}` y lo análogo en `/api/horarios-atencion`. Frontend: TypeScript vanilla strict, sin `any`, Vitest + jsdom.

## Goals / Non-Goals

**Goals:** ambas secciones funcionales y accesibles, con lógica pura testeable separada del DOM.
**Non-Goals:** bloqueos (C-15), cambios en cliente HTTP/router/sesión, cambios de contrato, librerías nuevas.

## Decisions

- **Capas por sección**: `*.api.ts` (llamadas vía `pedir()` con parsers que validan `unknown` a tipos del contrato), `*.logica.ts` (validadores, agrupación/orden; funciones puras) y `prestaciones.ts`/`horarios.ts` (DOM). Pasar siempre por `cliente.ts` para heredar el header de C-13.
- **Mensajes por `codigo`** en un helper común `errores.ts` de la carpeta; `VALIDACION` mapea `detalles[].campo` a los campos del formulario; el resto usa mensajes propios en español, con fallback al mensaje genérico.
- **Estado simple**: tras cada mutación exitosa se recarga el listado desde la API (fuente de verdad). El formulario se reutiliza para alta y edición.
- **Accesibilidad**: `label for`, `aria-invalid`, `aria-describedby` hacia el error del campo, regiones `role="status"`/`role="alert"`, `<fieldset>`/`<legend>`; confirmación de borrado inline (botón "Confirmar" visible) en vez de `window.confirm`, para que sea testeable y accesible. Horarios con `<input type="time">`.
- **Tests**: unitarios de lógica pura y de DOM con jsdom mockeando `fetch` con los JSON de `docs/api/ejemplos/` (regla 11).
- **Estilos**: `configuracion.css` en la carpeta, importado desde los módulos de sección; layout fluido.
- **Trade-off**: `duracionMinutos` se valida 1..480 según el contrato aunque la KB solo pide > 0.

## Risks / Trade-offs

- C-13 puede cambiar el manejo de 401 en el cliente; las secciones solo muestran el error y no dependen de ello.
- Edición concurrente: se resuelve recargando; sin control de versiones (fuera de alcance MVP).
