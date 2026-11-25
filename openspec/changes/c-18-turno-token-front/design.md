# Design

## Context

`frontend/src/public/confirmar.ts` y `cancelar.ts` son placeholders; las rutas `/turno/confirmar` y `/turno/cancelar` ya están registradas en `public/rutas.ts` y `rutas.test.ts` exige que cada pantalla tenga su `h1` ("Confirmar turno", "Cancelar turno") de forma **sincrónica** (llama a la pantalla sin `token` y sin esperar). El router pasa `ctx.query` (`URLSearchParams`) a la pantalla.

Backend y contrato cerrados (C-01, C-08): `GET /api/public/turnos/{token}` (solo lectura), `POST .../confirmar` y `POST .../cancelar`, todos con `TurnoResumenPublico` (sin DNI, apellido, email, teléfono, ids ni token; `puedeConfirmar` y `puedeCancelar` los calcula el backend). Orden de evaluación del POST: 404 `TOKEN_INVALIDO` → 200 si ya está en el estado destino → 422 `TRANSICION_INVALIDA` → 422 `*_FUERA_DE_PLAZO`. El parámetro `token` tiene `minLength: 16`, `maxLength: 128`. El backend busca por token exacto (`findByToken` → 404).

C-17 (misma dueña) dejó el patrón y piezas reutilizables:
- `reservar.api.ts`: `parsearResumen` (privado) que arma un `TurnoResumenPublico` campo a campo con `admin/configuracion/parseo.ts` (descarta campos extra).
- `reservar.logica.ts`: `formatearFechaTurno`, `formatearHora` (con `ZONA` de Buenos Aires), `textoEstado` (solo `PENDIENTE`/`CONFIRMADO`; el resto cae al código crudo) y el patrón `accionDeError` (texto fijo del front, nunca el `mensaje` del servidor).
- `reservar.ts` / `reservar.css`: `iniciar(contenedor, opciones)` que devuelve una promesa para los tests, `opciones.ahora` para fijar el reloj, `crearAvisos`/`crearBoton` de `admin/configuracion/formulario.ts`, foco programático al resultado.
- Tests: `stubServidor`, `respuesta`, `boton` (`servidor.testutil.ts`) y `ejemplo()` (`ejemplos.testutil.ts`), de Int 2, reutilizados sin modificar.

`api/cliente.ts#pedir` agrega `Authorization` si hay sesión del odontólogo; en endpoints `permitAll` es inocuo (ya analizado en C-17).

## Goals / Non-Goals

**Goals:** las dos páginas comparten una sola implementación parametrizada por acción; toda la decisión de "qué mostrar" vive en funciones puras testeables sin DOM; mismos patrones de capas, parseo, mensajes y tests que C-17.

**Non-Goals:** cambios de contrato, backend o `index.html`; reprogramar desde el link; mostrar o reconstruir links; mover helpers de `admin/` a `shared/` (otros integrantes, regla 16); diálogo modal (`<dialog>`) o dependencia del diálogo de C-20 (no está en `main`); quitar el token de la barra de direcciones.

## Decisions

1. **Archivos.** En `frontend/src/public/`:
   - `turno-token.api.ts`: `obtenerTurnoPorToken(token)`, `confirmarTurnoPorToken(token)`, `cancelarTurnoPorToken(token)` sobre `pedir()`, con la ruta `/api/public/turnos/${encodeURIComponent(token)}` (y `/confirmar`, `/cancelar`) y `parsearResumen` importado de `reservar.api.ts`.
   - `turno-token.logica.ts` (puro): `leerToken`, `vistaDeTurno`, `accionDeErrorToken` y los textos fijos.
   - `turno-token.ts` (DOM): `iniciarTurnoToken(contenedor, accion, ctx, opciones?)` que devuelve una promesa para los tests (`opciones.ahora` como en C-17).
   - `confirmar.ts` / `cancelar.ts`: quedan como envoltorios de dos líneas (`render` → `void iniciarTurnoToken(contenedor, 'confirmar' | 'cancelar', ctx)`), así `rutas.ts` no cambia.
   - `turno-token.css` acotado a `.turno-token`.
   - Tests al lado: `turno-token.logica.test.ts`, `turno-token.api.test.ts`, `confirmar.test.ts`, `cancelar.test.ts`.
   *Alternativas*: (a) dos pantallas independientes → duplican carga, resumen, errores y estilos casi idénticos; (b) carpeta `public/turno/` → obliga a tocar `rutas.ts` sin ganancia.

2. **Reuso de C-17 modificando lo mínimo.** Se **exporta** `parsearResumen` de `reservar.api.ts` (sin cambiar su lógica) y `textoEstado` de `reservar.logica.ts` pasa a cubrir los cinco estados ("Pendiente de confirmación", "Confirmado", "Cancelado", "Asistió", "No asistió") con chequeo exhaustivo `never`, más su test. Formateo de fecha/hora se importa tal cual. Son archivos de la misma dueña, así que no aplica la regla 16. *Alternativas*: (a) extraer ahora un `public/turno-publico.*` común y mover allí parser y formateo → reabre archivos recién mergeados y sus tests sin cambiar comportamiento; se deja como refactor opcional; (b) copiar parser y formateo → duplicación que diverge.

3. **Validación del token en el front (`leerToken(query)`).** Devuelve el token recortado si mide entre 16 y 128 caracteres (límites del contrato); si no, `null` → vista "link inválido" **sincrónica** y sin pedido. Esto mantiene `rutas.test.ts` verde sin tocarlo (renderiza sin token) y evita pedidos inútiles. El token se usa solo para armar la URL del pedido: nunca se escribe en el DOM, en `console` ni en mensajes de error. `encodeURIComponent` evita que un token manipulado cambie la ruta (`../`, `?`).

4. **Decisión de vista como unión discriminada pura.** `vistaDeTurno(resumen, accion, ahora): Vista` con `Vista` =
   - `{ tipo: 'accionable' }` → mostrar el botón (`puedeConfirmar` / `puedeCancelar` según la acción; **la habilitación la decide solo el backend**);
   - `{ tipo: 'informativa', mensaje, contacto: boolean, reservarOtro: boolean }`.
   Orden de reglas cuando el flag es `false`: `ASISTIO`/`AUSENTE`, o `PENDIENTE`/`CONFIRMADO` con `inicio <= ahora` → "Este turno ya pasó."; luego por acción y estado (tabla de la spec: confirmado/cancelado/pendiente). Para elegir entre "todavía no (> 48 h)" y "fuera de plazo (< 24 h)" el front solo compara `inicio` con `ahora`: un `PENDIENTE` futuro sin el flag ya implica el motivo, así que **no se replican las constantes de 48 h ni de 24 h**. Exhaustividad con `never` sobre `EstadoTurno`. *Alternativa*: calcular las ventanas en el front → duplica reglas del backend y puede contradecirlo con relojes desfasados.

5. **Mensajes fijos del front (`accionDeErrorToken(error, accion)`).** Devuelve `{ mensaje, efecto }` con `efecto` ∈ `'linkInvalido' | 'releer' | 'reintentar'`: 404 (cualquier código) y 400 → `linkInvalido`; 422 `CONFIRMACION_FUERA_DE_PLAZO` / `CANCELACION_FUERA_DE_PLAZO` / `TRANSICION_INVALIDA` → texto de la spec + `releer`; `ErrorConexion` → su mensaje + `reintentar`; 500, `ErrorApi` sin código o desconocido → `MENSAJE_GENERICO` + `reintentar`. Nunca se muestra `error.mensaje` del servidor (mismo criterio que C-17: tono controlado y sin detalles internos). Los textos se toman de los ejemplos del contrato donde existen para que front y back digan lo mismo.

6. **Flujo de la pantalla.** Render sincrónico: `section.turno-token` con `h1` + avisos (`crearAvisos`: `status` y `alert`) + zona de resultado. Luego `GET` con "Cargando tu turno…". Con el `200`: `<dl>` de resumen (Paciente, Prestación "Consulta (30 min)", Fecha, Horario "10:00 a 10:30", Estado, Contacto del consultorio) y, según `vistaDeTurno`, el botón o el mensaje (`p` dentro de la región `status`, con `contactoConsultorio` y/o link `<a href="/reservar">Reservar otro turno</a>` cuando corresponde).
   - **POST**: flag `enviando` + `disabled` + `aria-busy="true"` + texto "Confirmando…"/"Cancelando…" contra el doble envío. Con `200` se re-renderiza el resumen con la respuesta (el estado del POST es la verdad) y un mensaje de resultado `h2 tabindex="-1"` enfocado: "¡Listo, {nombre}! Tu turno quedó confirmado." / "Tu turno quedó cancelado. El horario quedó libre." (+ "Reservar otro turno"). Si el `200` llega con el estado destino porque otra pestaña ya lo había hecho, el resultado visible es el mismo (idempotente) y no hace falta distinguirlo.
   - **422 → `releer`**: alerta con el texto fijo y nuevo `GET`; el resumen y la vista se recalculan con los flags nuevos (el botón desaparece). Si el `GET` de relectura falla, se conserva la alerta y se oculta el botón para no reintentar una acción ya rechazada.
   - **Errores de `GET`**: `linkInvalido` → reemplaza todo por `h2` "El link no es válido" + "Revisá que hayas abierto el link completo del último email del consultorio."; `reintentar` → alerta + botón "Reintentar".

7. **Confirmación de cancelación inline, no modal.** "Cancelar turno" oculta su botón y muestra un `div role="group" aria-labelledby` con la pregunta `p tabindex="-1"` "¿Seguro que querés cancelar tu turno?" (recibe el foco), "Sí, cancelar turno" y "No, mantener turno"; "No" la oculta y devuelve el foco a "Cancelar turno". *Alternativas*: (a) `window.confirm` → no se puede estilizar ni testear bien y algunos navegadores móviles lo bloquean; (b) `<dialog>.showModal()` → jsdom no implementa `showModal` de forma fiable y exige manejo de foco/Esc; (c) el diálogo de C-20 → está en una rama sin mergear de otra integrante. La versión inline no tiene trampa de foco, funciona con teclado y lector de pantalla y es trivial de testear.

8. **Estilos.** `turno-token.css` mobile-first (una columna, `max-width: 40rem`), botones de al menos 44 px, foco visible heredado de `base.css` + `outline` en los `h2`/`p` enfocables, estado mostrado siempre como texto (no solo color), botón de cancelar con estilo de acción destructiva **además** de su texto explícito. Se reutilizan los estilos `cfg-*` de avisos acotados a `.turno-token` como hace `reservar.css`.

9. **Tests (Vitest + jsdom, test-first).**
   - `turno-token.logica.test.ts`: `leerToken` (ausente, vacío, 15/16/128/129 caracteres, espacios); tabla completa de `vistaDeTurno` por acción × estado × flag × pasado/futuro (incluido el borde `inicio == ahora`); `accionDeErrorToken` con cada código y tipo de error; `textoEstado` de los cinco estados (en `reservar.logica.test.ts`).
   - `turno-token.api.test.ts`: `stubServidor` + `ejemplo()` para los 200 y cada 404/422 del contrato; URL con `encodeURIComponent`; método correcto; respuesta mal formada rechazada; campos extra descartados.
   - `confirmar.test.ts` / `cancelar.test.ts`: flujo DOM con reloj fijo: sin token (sin `fetch`), un solo `GET` al abrir, resumen del ejemplo, cada vista informativa, éxito con foco, doble clic, 422 con relectura, 404, 500/conexión con reintento, confirmación inline (sí/no/foco), ausencia de DNI/email/teléfono/token en `textContent`, regiones `status`/`alert`.
   - `rutas.test.ts` y `reservar*.test.ts` siguen verdes sin cambios (salvo el caso nuevo de `textoEstado`).

## Risks / Trade-offs

- [El token queda en la barra de direcciones, el historial y los logs de acceso de nginx] → es inherente al link del email (RN-TOK-02, el contrato lo define así); la página no carga recursos de terceros, así que no se filtra por `Referer`. Agregar `referrer-policy` o limpiar la URL con `history.replaceState` toca `index.html`/`nginx.conf` (Int 2) o el router: se anota como mejora coordinada, no se hace acá.
- [El reloj del dispositivo puede estar desfasado y elegir el texto "ya pasó" vs "todavía no/fuera de plazo" equivocado] → solo afecta la redacción del mensaje; la habilitación del botón la decide el backend con su reloj y el POST se valida igual.
- [`TRANSICION_INVALIDA` por una carrera (el odontólogo canceló mientras la página estaba abierta)] → alerta + relectura: el paciente ve el estado real.
- [Importar `reservar.api.ts`/`reservar.logica.ts` desde las páginas de token arrastra al bundle código de la reserva] → ya se sirve en el mismo bundle público; impacto nulo.
- [Un `400` del backend por un token raro se muestra como "link inválido"] → es el resultado correcto para el paciente y no revela nada.

## Migration Plan

Solo front. Entra con el PR de la rama `milagros/c-18-turno-token-front`; rollback = revertir el PR (las rutas vuelven a los placeholders). Sin datos ni configuración nueva.
