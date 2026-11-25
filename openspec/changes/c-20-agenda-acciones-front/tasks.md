# Tasks

## 1. Safety net

- [ ] 1.1 Baseline: correr `cd frontend && npm test` y `npm run typecheck`, y registrar la cantidad de tests verdes. Correr también `npx vitest run src/admin/agenda/` y registrar ese número aparte. Si algún test falla, reportarlo como falla preexistente y no seguir.

## 2. Lógica pura (`admin/agenda-acciones/acciones.logica.ts`)

- [ ] 2.1 RED: escribir `acciones.logica.test.ts` con `accionesPermitidas(turno, ahora)` sobre los ejemplos de `turnos-listar.200.json` y relojes fijos. Casos:
  - `PENDIENTE` futuro → confirmar, reprogramar y cancelar, con asistencia deshabilitada y `desde` `16:00`.
  - `PENDIENTE` ya empezado → sin confirmar, con asistencia habilitada.
  - `CONFIRMADO` → sin confirmar.
  - `CANCELADO`, `ASISTIO` y `AUSENTE` → ninguna acción.
  - Navegador en `America/New_York` → la misma hora `desde`.

  Después GREEN y triangular. Verificar con `npx vitest run src/admin/agenda-acciones/acciones.logica.test.ts`.
- [ ] 2.2 RED → GREEN: `armarTurnoManual(entrada)` con un paciente existente devuelve solo `pacienteId`. Con uno nuevo devuelve solo `paciente`, armado con `armarRequest` de C-16 y con la forma de `turnos-crear.request-paciente-nuevo.json`. Ambos casos recortan email y teléfono. `validarContacto` cubre:
  - Email vacío, sin `@` o de 151 caracteres → error.
  - Teléfono vacío, con letras o de 5 caracteres → error.
  - `+54 9 11 0000-0002` → válido.

  Verificar con el mismo test.
- [ ] 2.3 RED → GREEN: `accionDeErrorTurno(error, accion)` con los ejemplos del contrato, como `ErrorApi`:
  - `turnos-crear.409-horario-ocupado` y `422-horario-no-disponible` → `refrescarHorarios`.
  - `422-prestacion-inactiva` → `recargarPrestaciones`.
  - `422-dni-duplicado` → `campoDni`.
  - `error.400-validacion` → `campos`.
  - `turnos-{confirmar,cancelar,estado,reprogramar}.422-transicion-invalida` → `recargarAgenda`, con un mensaje distinto por acción. En `estado` el mensaje es "Solo se puede marcar la asistencia desde la hora de inicio del turno.".
  - `error.404-recurso-no-encontrado` → `recargarAgenda`.
  - `ErrorConexion` y `error.500-error-interno` → `ninguno` con mensaje genérico.
  - 401 → `ninguno` sin mensaje.

  Ningún mensaje es el `mensaje` del servidor.
- [ ] 2.4 RED → GREEN: `textoResultado(turno, accion)` en hora de Buenos Aires:
  - Alta → "Turno cargado: pendiente de confirmación" o "Turno cargado: confirmado".
  - Reprogramar → "Turno reprogramado al martes 1 de diciembre, 18:00: queda pendiente de confirmación".
  - Confirmar, cancelar y asistencia → "Turno confirmado", "Turno cancelado", "Asistencia registrada" y "Ausencia registrada".

  Verificar con `npx vitest run src/admin/agenda-acciones/` y `npm run typecheck`.

## 3. Cliente de las acciones (`admin/agenda-acciones/acciones.api.ts`)

- [ ] 3.1 Agregar `export` a `parsearTurno` en `frontend/src/admin/agenda/agenda.api.ts`. Es el único cambio en ese archivo de Julieta (C-19, Int 5) y se avisa en el PR. Verificar que `npx vitest run src/admin/agenda/` mantiene el número de tests verdes registrado en 1.1.
- [ ] 3.2 RED: escribir `acciones.api.test.ts` con `stubServidor` y `ejemplo()`:
  - `confirmarTurno(102)` → `POST /api/turnos/102/confirmar` y parsea `turnos-confirmar.200.json`.
  - `cancelarTurno` → `POST …/cancelar` con `turnos-cancelar.200.json`.
  - `marcarAsistencia(id, 'ASISTIO')` → `PATCH …/estado` con el body de `turnos-estado.request.json`.
  - `reprogramarTurno(id, inicio)` → `PATCH …/reprogramar` con `turnos-reprogramar.request.json`.

  Una respuesta sin `paciente` lanza un error de respuesta inválida. GREEN.
- [ ] 3.3 RED → GREEN:
  - `crearTurnoManual` envía `POST /api/turnos` con los dos requests de ejemplo, devuelve el turno de `turnos-crear.201.json` y propaga como `ErrorApi` el 409 y los tres 422 del ejemplo.
  - `obtenerDisponibilidadAdmin(1, '2026-12-01')` llama a `GET /api/disponibilidad?prestacionId=1&desde=2026-12-01&hasta=2026-12-01`, no a `/api/public/...`, y parsea `disponibilidad-privada.200.json`. Propaga `422-prestacion-inactiva`.
  - `listarPrestacionesActivas` filtra `activa: false` sobre `prestaciones-listar.200.json`.

  Verificar con `npx vitest run src/admin/agenda-acciones/acciones.api.test.ts`.

## 4. Diálogo de confirmación (`admin/agenda-acciones/dialogo.ts`)

- [ ] 4.1 RED: escribir `dialogo.test.ts`. `confirmarAccion({ titulo, descripcion, aceptar, disparador })` monta un `role="alertdialog"` con `aria-modal="true"` y `aria-labelledby` y `aria-describedby` que apuntan al título y al texto, y pone el foco en "Volver". "Volver" y Escape resuelven `false` y devuelven el foco al disparador. El botón de aceptar resuelve `true`. GREEN.
- [ ] 4.2 RED → GREEN: Tab desde el último botón vuelve al primero, y Shift+Tab hace lo contrario. Mientras está abierto, el resto del contenido tiene `inert` y `aria-hidden="true"`, y se restaura al cerrar. Al cerrar, el diálogo desaparece del DOM. Abrir uno nuevo con otro abierto no deja dos diálogos. Verificar con `npx vitest run src/admin/agenda-acciones/dialogo.test.ts`.

## 5. Selector de horario (`admin/agenda-acciones/selector-horario.ts`)

- [ ] 5.1 RED: escribir `selector-horario.test.ts` con la API mockeada:
  - El label "Día" está asociado al `input type="date"` con `min` igual a hoy en Buenos Aires.
  - "Ver horarios" consulta `obtenerDisponibilidadAdmin` con la prestación y el día.
  - Los radios dentro del `fieldset`, con `legend` "Horarios libres", muestran 09:00 y 09:30, y su `value` es el instante exacto de la API.
  - Elegir uno llama a `alElegir(instante)`.

  GREEN.
- [ ] 5.2 RED → GREEN:
  - Un día sin horarios muestra "No hay horarios libres ese día" en `role="status"`.
  - `refrescar()` vuelve a pedir el mismo día y limpia la elección.
  - `cambiarPrestacion` descarta la elección y vuelve a pedir.
  - Una respuesta fuera de orden se descarta.
  - Un error de conexión muestra el mensaje genérico con "Reintentar".

  Verificar con `npx vitest run src/admin/agenda-acciones/selector-horario.test.ts`.

## 6. Alta manual (`admin/agenda-acciones/alta-manual.ts`)

- [ ] 6.1 RED: escribir `alta-manual.test.ts` con `stubServidor`:
  - El panel tiene un `h2` "Nuevo turno" que recibe el foco.
  - El `fieldset` "Paciente" tiene los radios existente y nuevo.
  - En "existente" se monta `crearBuscadorPacientes` con `textoSeleccionar` "Elegir". Elegir a Bruno precarga `bruno@ejemplo.com` y `+54 9 11 0000-0001`, que se pueden editar.
  - Un paciente con `ultimoContacto: null` deja los campos vacíos con la indicación correspondiente.
  - "Cambiar paciente" vuelve al buscador.

  GREEN.
- [ ] 6.2 RED → GREEN:
  - En "nuevo" aparecen nombre, apellido y DNI. Con datos inválidos el error se muestra junto al campo, sin llamar a la API.
  - Solo se ofrecen prestaciones activas, y cambiarla limpia el horario.
  - Sin horario elegido no se envía, y se avisa.
  - El envío exitoso, con el body igual a los ejemplos de request, anuncia según el estado de la respuesta (`PENDIENTE` o `CONFIRMADO`), llama a `alCrear(turno)` y cierra el panel.
  - El botón de enviar queda deshabilitado mientras se espera la respuesta.
- [ ] 6.3 RED → GREEN, con los ejemplos del contrato:
  - 409 `HORARIO_OCUPADO` y 422 `HORARIO_NO_DISPONIBLE` → mensaje y nueva consulta de horarios.
  - 422 `PRESTACION_INACTIVA` → mensaje y nueva consulta de prestaciones.
  - 422 `DNI_DUPLICADO` → DNI con `aria-invalid="true"`, foco y sugerencia de buscarlo.
  - 400 `VALIDACION` con `emailContacto` → error junto al email.
  - Conexión y 500 → mensaje genérico.

  En todos los casos los datos cargados se conservan. Verificar con `npx vitest run src/admin/agenda-acciones/alta-manual.test.ts`.

## 7. Reprogramar (`admin/agenda-acciones/reprogramar.ts`)

- [ ] 7.1 RED: escribir `reprogramar.test.ts`:
  - El panel tiene un `h2` "Reprogramar turno" con el paciente, la prestación y el horario actual en Buenos Aires, y el `h2` recibe el foco.
  - El selector de horario usa la `prestacion.id` del turno.
  - El botón de envío nombra el nuevo horario ("Reprogramar al …").
  - El 200 de `turnos-reprogramar.200.json` anuncia el nuevo horario y "queda pendiente de confirmación". Con `CONFIRMADO` anuncia "queda confirmado".

  GREEN.
- [ ] 7.2 RED → GREEN:
  - 409 y 422 `HORARIO_NO_DISPONIBLE` → mensaje y nueva consulta de horarios, con el panel abierto.
  - 422 `TRANSICION_INVALIDA` y 404 → mensaje, cierre del panel y `alCambiar` para recargar.
  - "Cerrar" no llama a la API y devuelve el foco al disparador.

  Verificar con `npx vitest run src/admin/agenda-acciones/reprogramar.test.ts`.

## 8. Fachada e integración con la agenda

- [ ] 8.1 RED: escribir `acciones.test.ts`. `crearAccionesAgenda({ recargar, ahora })` devuelve `barra` (botón "Nuevo turno"), `aviso` (`role="status"`, `tabindex="-1"`), `alerta` (`role="alert"`) y `panel`. `deTurno(turno, compacta)` cumple:
  - Para un urgente, devuelve primero Confirmar y después Cancelar, con `data-enganche` y nombres accesibles con paciente y hora.
  - Para un `CONFIRMADO`, no incluye Confirmar.
  - Para los terminales, devuelve `null`.
  - En la vista compacta, Reprogramar y la asistencia van en `<details>` "Más acciones".
  - La asistencia queda deshabilitada antes del inicio, con el texto "Se puede marcar desde las 16:00" vinculado.

  GREEN.
- [ ] 8.2 RED → GREEN, flujos sobre la API mockeada:
  - Confirmar sin diálogo → una sola solicitud ante un doble click, anuncio "Turno confirmado", `recargar()` y foco en `[data-turno-id]` si existe, o en `aviso` si no.
  - Cancelar → diálogo con paciente, prestación, horario y email. "Volver" no llama a la API. Aceptar envía, anuncia, recarga y deja el foco en `aviso` cuando el turno ya no está.
  - Asistió y Ausente → diálogo y `PATCH` con el estado correcto.
  - 422 `TRANSICION_INVALIDA` → `alerta` y recarga.
  - Error de conexión → mensaje genérico, sin recarga y con los botones habilitados de nuevo.
  - 401 → sin mensaje propio.
- [ ] 8.3 Integrar en `frontend/src/admin/agenda/agenda.ts`, archivo de Julieta, con un diff mínimo:
  - (a) Importar `crearAccionesAgenda` y crearlo en `render` con `recargar: cargar`.
  - (b) Agregar `barra` a `.agenda-barra`, y `aviso`, `alerta` y `panel` a la raíz después del aviso de pendientes.
  - (c) Pasar `deTurno` a `pintarDia` → `crearTarjeta`, y reemplazar el bloque de botones `disabled` "(C-20)" por las acciones.

  Adaptar en `agenda/agenda.test.ts` la aserción de enganches deshabilitados: ahora existen, están habilitados y Confirmar llama a `POST …/confirmar`. Hay que mockear `../agenda-acciones/acciones.api` o ampliar el factory de `./agenda.api`. Verificar que `npx vitest run src/admin/agenda/` sigue verde y que `git diff --stat main -- frontend/src/admin/agenda/` muestra solo `agenda.ts`, `agenda.api.ts` y `agenda.test.ts`.
- [ ] 8.4 Crear `admin/agenda-acciones/acciones.css`, importado desde `acciones.ts`. Debe ser mobile-first, ir acotado a `.agenda-acciones` y `.aa-*`, y usar los tokens de la revisión 1:
  - Botones de 44 px o más y foco visible.
  - Cancelar y Ausente distinguidos por texto y borde.
  - El diálogo a ancho completo en el celular.
  - Los paneles apilados, sin scroll horizontal a 320 px.

  Verificar con un test de CSS al estilo de `agenda.css.test.ts` (selectores acotados, sin reglas globales) y con `npm run typecheck`.

## 9. Verificación integral

- [ ] 9.1 Correr `cd frontend && npm run lint && npm run typecheck && npm test && npm run build`, todo en verde. Además:
  - Sin `any`, `console.log` ni dependencias nuevas.
  - Sin cambios en `src/api/`, `src/shared/`, `src/public/`, `admin/configuracion/`, `admin/rutas.ts`, `admin/pacientes*` ni `docs/api/`. Verificar con `git diff --stat main`: solo deben aparecer `admin/agenda-acciones/**`, los tres archivos de `admin/agenda/` y `openspec/changes/c-20-agenda-acciones-front/**`.
  - `types.sync.test.ts` sigue verde.
- [ ] 9.2 `openspec validate c-20-agenda-acciones-front --strict` sin errores.
- [ ] 9.3 Prueba manual en `http://localhost:8080/admin/agenda` con `docker compose up`. **El humano inicia sesión**: el agente no lee `.env` ni la contraseña del seed. Con datos ficticios:
  - Cargar un turno para un paciente existente (contacto precargado) y otro para un paciente nuevo.
  - Cargar uno a menos de 24 h: nace `CONFIRMADO`.
  - Forzar un 409 cargando el mismo horario desde dos pestañas.
  - Reprogramar a más de 24 h y a menos de 24 h.
  - Confirmar un `PENDIENTE`, cancelar con el diálogo y marcar asistencia de un turno ya iniciado.
  - Revisar los emails en Mailpit (`http://localhost:8025`).

  Si no hay un humano disponible, el agente registra que la verificación quedó cubierta solo por los tests con mocks del contrato.
- [ ] 9.4 Chequeo manual de accesibilidad y responsive:
  - Todo el flujo solo con teclado y foco visible: Tab, flechas en los radios, Enter y Escape en los diálogos.
  - El foco vuelve al disparador al cerrar diálogos y paneles.
  - Un lector de pantalla anuncia el título y la descripción del diálogo, y los anuncios de resultado.
  - A 320 px y 375 px no hay scroll horizontal.

## Workflow follow-up

- Abrir el PR de `casado/c-20-agenda-acciones-front` a `main` con commits de Cande Casado (conventional commits en español, por ejemplo `feat(agenda): acciones sobre turnos y carga manual (C-20)`).
- En el PR, pedir revisión a Julieta (C-19) de los cambios en `admin/agenda/agenda.ts`, `agenda.api.ts` y `agenda.test.ts`, y avisar a Int 2 que se importan `admin/configuracion/*` sin modificarlos.
- Después del merge, correr `/opsx:archive c-20-agenda-acciones-front` y marcar C-20 como `[x]` en `CHANGES.md`. Avisar a Int 6 (C-21) que el MVP front está completo para la integración E2E.
