# Tasks

## 1. Safety net

- [ ] 1.1 Baseline: `cd frontend && npm test` y `npm run typecheck`; registrar la cantidad de tests verdes (si alguno falla, reportarlo como falla preexistente y no seguir)

## 2. Reuso de C-17 (`public/reservar.api.ts`, `public/reservar.logica.ts`)

- [ ] 2.1 RED → GREEN: en `reservar.logica.test.ts`, `textoEstado` de los cinco estados (`PENDIENTE` → "Pendiente de confirmación", `CONFIRMADO` → "Confirmado", `CANCELADO` → "Cancelado", `ASISTIO` → "Asistió", `AUSENTE` → "No asistió"); implementar con `switch` exhaustivo (`never`); verificar con `npx vitest run src/public/reservar.logica.test.ts` y que `reservar.test.ts` siga verde
- [ ] 2.2 Exportar `parsearResumen` de `reservar.api.ts` sin cambiar su lógica; verificar con `npx vitest run src/public/reservar.api.test.ts` y `npm run typecheck`

## 3. Lógica pura (`public/turno-token.logica.ts`)

- [ ] 3.1 RED: `turno-token.logica.test.ts` con `leerToken(query)`: sin `token`, vacío, solo espacios, 15 y 129 caracteres → `null`; 16 (`TOKEN-DE-EJEMPLO`) y 128 caracteres → el token recortado; GREEN y triangular; verificar con `npx vitest run src/public/turno-token.logica.test.ts`
- [ ] 3.2 RED → GREEN: `vistaDeTurno(resumen, 'confirmar', ahora)`: `PENDIENTE` + `puedeConfirmar` → accionable; `CONFIRMADO` → "Tu turno ya estaba confirmado"; `CANCELADO` → mensaje + `reservarOtro`; `PENDIENTE` sin flag e inicio futuro → "Todavía no se puede confirmar…"; `ASISTIO`/`AUSENTE` y `PENDIENTE`/`CONFIRMADO` con `inicio <= ahora` (incluido el borde `inicio == ahora`) → "Este turno ya pasó."; verificar con el mismo test
- [ ] 3.3 RED → GREEN: `vistaDeTurno(resumen, 'cancelar', ahora)`: `PENDIENTE` + `puedeCancelar` → accionable; `CANCELADO` → "Tu turno ya estaba cancelado." + `reservarOtro`; `CONFIRMADO` → mensaje de "ya está confirmado" con `contacto: true`; `PENDIENTE` sin flag e inicio futuro → mensaje de fuera de plazo con `contacto: true`; pasados → "Este turno ya pasó."; el flag del backend manda (un `PENDIENTE` futuro con flag `false` nunca es accionable); verificar con el mismo test
- [ ] 3.4 RED → GREEN: `accionDeErrorToken(error, accion)` con los JSON del contrato: 404 `TOKEN_INVALIDO` (de los tres endpoints) y 400 `VALIDACION` → `linkInvalido`; 422 `CONFIRMACION_FUERA_DE_PLAZO`, `CANCELACION_FUERA_DE_PLAZO`, `TRANSICION_INVALIDA` (por acción) → texto de la spec + `releer`; `ErrorConexion` → su mensaje + `reintentar`; 500 `ERROR_INTERNO`, `ErrorApi` sin código y `Error` cualquiera → `MENSAJE_GENERICO` + `reintentar`; el mensaje nunca es el `mensaje` del servidor; verificar con el mismo test y `npm run typecheck`

## 4. Cliente del recurso (`public/turno-token.api.ts`)

- [ ] 4.1 RED: `turno-token.api.test.ts` con `stubServidor` y `ejemplo()`: `obtenerTurnoPorToken('TOKEN-DE-EJEMPLO')` hace `GET /api/public/turnos/TOKEN-DE-EJEMPLO` y parsea `turnos-publico-obtener.200-pendiente.json` y `.200-confirmado.json`; una respuesta con `dni`/`email`/`telefono` extra devuelve un objeto sin esos campos; respuesta sin `puedeConfirmar` → rechazo; GREEN reutilizando `parsearResumen`
- [ ] 4.2 RED → GREEN: `confirmarTurnoPorToken` y `cancelarTurnoPorToken` hacen `POST .../confirmar` y `POST .../cancelar` sin body, parsean `turnos-publico-confirmar.200.json` / `turnos-publico-cancelar.200.json` y propagan como `ErrorApi` con su código cada 404 y 422 del contrato; un token con `/`, `?` o `..` viaja codificado con `encodeURIComponent`; verificar con `npx vitest run src/public/turno-token.api.test.ts`

## 5. Pantalla de confirmar (`public/turno-token.ts`, `public/confirmar.ts`)

- [ ] 5.1 RED: `confirmar.test.ts` (DOM con `stubServidor` y reloj fijo vía `iniciarTurnoToken(contenedor, 'confirmar', ctx, { ahora })`): sin token o con `token=abc` → `h1` "Confirmar turno" + "El link no es válido" **sin ningún `fetch`**; GREEN reemplazando el placeholder (`confirmar.ts` queda como envoltorio); `rutas.test.ts` sigue verde sin modificarlo
- [ ] 5.2 RED → GREEN: con token válido el único pedido al abrir es `GET /api/public/turnos/TOKEN-DE-EJEMPLO`; con `.200-pendiente` se ven "Ana", "Consulta (30 min)", "jueves 3 de diciembre de 2026", "10:00 a 10:30", "Pendiente de confirmación", el contacto del consultorio y el botón "Confirmar asistencia"; `textContent` no contiene el token ni valores de `dni`/`email`/`telefono` agregados a la respuesta
- [ ] 5.3 RED → GREEN: "Confirmar asistencia" hace un único `POST .../confirmar` aunque se active dos veces (botón `disabled` + `aria-busy` + "Confirmando…"); con el 200 → "¡Listo, Ana! Tu turno quedó confirmado.", estado "Confirmado", sin botón y foco en el mensaje de resultado dentro de una región `status`
- [ ] 5.4 RED → GREEN: vistas informativas sin botón: `.200-confirmado` → "Tu turno ya estaba confirmado"; `CANCELADO` → mensaje + link "Reservar otro turno" a `/reservar`; `PENDIENTE` sin flag futuro → "Todavía no se puede confirmar…"; turno pasado → "Este turno ya pasó."
- [ ] 5.5 RED → GREEN: errores: 404 del `GET` y del `POST` → "El link no es válido" sin resumen ni botón; 422 `CONFIRMACION_FUERA_DE_PLAZO` y `TRANSICION_INVALIDA` → alerta con el texto fijo, segundo `GET` y la vista releída sin botón (si la relectura falla, alerta conservada y botón oculto); conexión caída en el `GET` → alerta + "Reintentar" que repite el `GET`; 500 en el `POST` → mensaje genérico y botón otra vez habilitado; verificar con `npx vitest run src/public/confirmar.test.ts`

## 6. Pantalla de cancelar (`public/turno-token.ts`, `public/cancelar.ts`)

- [ ] 6.1 RED: `cancelar.test.ts`: sin token → `h1` "Cancelar turno" + "El link no es válido" sin `fetch`; con `.200-pendiente` → resumen y botón "Cancelar turno", un solo `GET`; GREEN reemplazando el placeholder (`cancelar.ts` queda como envoltorio)
- [ ] 6.2 RED → GREEN: "Cancelar turno" no hace ningún `POST`: muestra "¿Seguro que querés cancelar tu turno?" (con foco) y los botones "Sí, cancelar turno" / "No, mantener turno" alcanzables con Tab; "No, mantener turno" oculta la pregunta, no hace `POST` y devuelve el foco a "Cancelar turno"
- [ ] 6.3 RED → GREEN: "Sí, cancelar turno" hace un único `POST .../cancelar` (doble activación → un pedido; "Cancelando…"); con el 200 → "Tu turno quedó cancelado. El horario quedó libre.", estado "Cancelado", link "Reservar otro turno" y foco en el resultado
- [ ] 6.4 RED → GREEN: vistas informativas: `CANCELADO` → "Tu turno ya estaba cancelado."; `.200-confirmado` → "Tu turno ya está confirmado. Para cancelarlo comunicate con el consultorio." + contacto; `PENDIENTE` sin flag futuro → "Ya no se puede cancelar desde el link (faltan menos de 24 h). Comunicate con el consultorio." + contacto; pasado → "Este turno ya pasó."
- [ ] 6.5 RED → GREEN: errores del `POST`: 422 `CANCELACION_FUERA_DE_PLAZO` → alerta con el contacto + relectura; 422 `TRANSICION_INVALIDA` → "El turno cambió y ya no se puede cancelar desde el link." + relectura con el mensaje del estado nuevo; 404 → link inválido; conexión/500 → mensaje genérico y botón habilitado; verificar con `npx vitest run src/public/cancelar.test.ts`

## 7. Estilos y accesibilidad

- [ ] 7.1 `public/turno-token.css` mobile-first (una columna, `max-width: 40rem`, botones ≥ 44 px, foco visible en los elementos enfocables por código, botón de cancelar marcado como destructivo además de su texto, `cfg-*` acotados a `.turno-token`) importado desde `turno-token.ts`; asserts en `confirmar.test.ts`/`cancelar.test.ts` de un solo `h1`, regiones `role="status"`/`role="alert"`, botones nativos `type="button"` y `<dl>` del resumen; verificar con `npx vitest run src/public/` y `npm run typecheck`

## 8. Verificación integral

- [ ] 8.1 `cd frontend && npm run lint && npm run typecheck && npm test && npm run build` en verde; sin `any`, sin `console.*`, sin dependencias nuevas; sin cambios en `src/api/`, `src/shared/`, `src/admin/`, `public/rutas.ts`, `rutas.test.ts` ni `docs/api/` (verificar con `git diff --stat main`)
- [ ] 8.2 `openspec validate c-18-turno-token-front --strict` sin errores
- [ ] 8.3 Prueba manual con el stack Docker (`http://localhost:8080`, Mailpit `http://localhost:8025`), solo datos ficticios: reservar un turno para mañana (entre 24 y 48 h) y abrir desde Mailpit el link de Confirmar del recordatorio → resumen sin cambio de estado hasta apretar el botón, luego "quedó confirmado"; reabrir el link → "ya estaba confirmado"; abrir el link de Cancelar de ese mismo turno → "ya está confirmado… comunicate"; reservar otro turno a más de 48 h y cancelar desde su link (probar "No, mantener turno" y luego "Sí") → "quedó cancelado" y el horario vuelve a aparecer en `/reservar`; link de confirmar de un turno a más de 48 h (alterar la ruta del link de cancelar) → "Todavía no se puede confirmar"; token alterado → "El link no es válido"; `/turno/confirmar` sin token → "El link no es válido"
- [ ] 8.4 Chequeo manual de accesibilidad y responsive: ambos flujos solo con teclado (Tab, Enter/Espacio) con foco visible y foco en el resultado; lectura de estados y alertas con lector de pantalla o el árbol de accesibilidad; a 320 px y 375 px sin scroll horizontal; el token no aparece en el texto de la página

## Workflow follow-up

- Abrir PR de `milagros/c-18-turno-token-front` a `main` con commits de la dueña del change (conventional commits en español).
- Avisar en el PR a los dueños de `admin/configuracion/` (C-14/C-15) que el área pública sigue importando `parseo`, `formulario` y las utilidades de test sin modificarlas.
- Archivar con `/opsx:archive c-18-turno-token-front` y marcar C-18 `[x]` en `CHANGES.md`.
