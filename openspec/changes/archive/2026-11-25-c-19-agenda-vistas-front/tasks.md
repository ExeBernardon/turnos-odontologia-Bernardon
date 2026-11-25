# Tasks

Gate de UI: no iniciar la implementación hasta que `design.md` y `mockups/README.md` registren `Approval: approved (<fecha>, by user)` para la revisión 1 aprobada. Ver `design.md` § UI Mockups.

## 1. Safety net y gate

- [x] 1.1 Ejecutar `cd frontend && npm test` y registrar la cantidad de tests verdes como baseline, verificando que la suite termina con código 0
- [x] 1.2 Verificar que la aprobación de los mockups está registrada en `design.md` y `mockups/README.md` antes de tocar código de la pantalla, confirmando `Approval: approved` en ambos archivos

## 2. Fechas, rangos y grilla (lógica pura, TDD)

- [x] 2.1 Escribir primero los tests de `agenda.logica.ts` para el rango diario y el semanal de lunes a domingo calculados en `America/Argentina/Buenos_Aires`, y verificar que fallan antes de implementarlos y pasan después
- [x] 2.2 Implementar y testear el formato con `Intl.DateTimeFormat('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' })`, incluido `YYYY-MM-DD`, verificando que un mismo instante se muestra igual aunque cambie la zona del navegador en el test
- [x] 2.3 Implementar y testear la grilla a partir de horarios de atención: intervalos, huecos "sin atención" y superposición de bloqueos, verificando casos de varios rangos por día y día sin horarios

## 3. Acceso a API y parseo defensivo

- [x] 3.1 Implementar `agenda.api.ts` con `pedir` para `GET /api/turnos?desde&hasta`, `GET /api/bloqueos?desde&hasta` y `GET /api/horarios-atencion`, verificando con tests que usa los ejemplos `docs/api/ejemplos/*.json` y adjunta el token de sesión
- [x] 3.2 Implementar el parseo defensivo de turnos, bloqueos y horarios, verificando con tests que los elementos con campos inválidos se ignoran sin romper la lista
- [x] 3.3 Cubrir con tests el manejo de 401 `NO_AUTENTICADO`, error de conexión y error con formato inesperado, verificando que el llamador recibe un error tipado y no una respuesta cruda

## 4. Pantalla de agenda

- [x] 4.1 Reemplazar el placeholder en `agenda.ts` por la vista diaria con tarjetas de turno (paciente, prestación, inicio, fin, estado y teléfono), "sin confirmar", resaltado de `pendienteMenos24h`, atenuado de cancelados y estado vacío, verificando con tests DOM cada estado
- [x] 4.2 Agregar la vista semanal y la navegación entre días y semanas con "Hoy"/"Esta semana", verificando con tests DOM que se consulta el rango correcto y se actualiza la etiqueta del periodo sin recargar
- [x] 4.3 Mostrar bloqueos y horas fuera de atención como franjas diferenciadas, banner de error con reintento y contenedores `data-turno-id` / `data-enganche="confirmar|cancelar"`, verificando con tests DOM que las acciones no ejecutan efectos en este change

## 5. Accesibilidad, estilos y cierre

- [x] 5.1 Agregar `agenda.css` de forma aditiva con tokens del design system y adaptación a 390 px, verificando con tests que los controles tienen nombre accesible, foco visible, `role="status"`/`role="alert"` y que no hay desbordamiento en la vista angosta
- [x] 5.2 Ejecutar `cd frontend && npm run lint`, `npm run typecheck`, `npm test` y `npm run build`, verificando que los cuatro comandos terminan con código 0
- [x] 5.3 Ejecutar `openspec validate c-19-agenda-vistas-front --strict`, verificando que no reporta errores de deltas, escenarios ni descripciones largas
- [x] 5.4 Iniciar el dev server de Vite, capturar `/admin/agenda` en móvil (390×844) y escritorio (1440×900) en `mockups/verify/`, revisar consola y requests fallidos, comparar con las capturas aprobadas y registrarlo en `design.md` como `Visual verify: passed | differences | manual` antes de detener el servidor

## Workflow follow-up

- Actualizar el checkbox de C-19 en `CHANGES.md` en el mismo PR del change.
- Archivar el change con `/opsx:archive c-19-agenda-vistas-front` después de la revisión.
