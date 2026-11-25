## 1. Safety net

- [x] 1.1 Baseline: `cd frontend && npm test` y `npm run typecheck`; registrar la cantidad de tests verdes (si alguno falla, reportarlo como falla preexistente y no seguir)

## 2. Lógica pura (`public/reservar.logica.ts`)

- [x] 2.1 RED: `reservar.logica.test.ts` con `ventanaFechas` (2026-11-30 12:00 BA → min `2026-11-30`, max `2027-01-29`; 2026-11-30 23:30 BA = 02:30Z del 1/12 → min sigue `2026-11-30`) y `validarFecha` (vacía, `2026-13-01`, antes de min, después de max → mensaje; min y max → válidas); GREEN y triangular; verificar con `npx vitest run src/public/reservar.logica.test.ts`
- [x] 2.2 RED → GREEN: `formatearHora` (`2026-12-01T09:30:00-03:00` → `09:30`, `2026-12-01T12:30:00Z` → `09:30`, instante inválido → cadena vacía) y `formatearFechaTurno` (`2026-12-10T10:00:00-03:00` → `jueves 10 de diciembre de 2026`; un instante `Z` cerca de medianoche cae en el día BA); verificar con el mismo test
- [x] 2.3 RED → GREEN: `validarReserva` (datos del ejemplo `turnos-publico-reservar.request.json` → sin errores; nombre/apellido vacíos o de 101 caracteres, DNI `99-999-999` y de 6/9 dígitos, email `paciente@`, `@ejemplo.com` y de 151 caracteres, teléfono `abc`, de 5 y de 31 caracteres, consentimiento `false` → error en cada campo; teléfono de 6 y 30 caracteres → válido) reutilizando `validarPaciente` de `admin/pacientes.logica.ts` sin modificarlo; verificar con el mismo test
- [x] 2.4 RED → GREEN: `armarReserva` (recorta textos, conserva el DNI como se escribió, `inicio` idéntico al string recibido, `consentimiento: true`; con los valores del ejemplo devuelve exactamente `turnos-publico-reservar.request.json`) y `textoEstado` (`PENDIENTE`, `CONFIRMADO`, fallback); verificar con el mismo test
- [x] 2.5 RED → GREEN: `accionDeError` con la tabla completa del diseño (`VALIDACION`, `HORARIO_OCUPADO`, `HORARIO_NO_DISPONIBLE`, `CONSENTIMIENTO_REQUERIDO`, `PRESTACION_INACTIVA`, `RECURSO_NO_ENCONTRADO`, `SOLICITUD_MAL_FORMADA`, `ERROR_INTERNO`, `ErrorApi` sin código, `ErrorConexion`, `Error` cualquiera) usando los JSON de error del contrato; el mensaje nunca es el `mensaje` del servidor; verificar con el mismo test y `npm run typecheck`

## 3. Cliente del recurso (`public/reservar.api.ts`)

- [x] 3.1 RED: `reservar.api.test.ts` con `stubServidor` y `ejemplo()`: `listarPrestacionesPublicas()` llama `GET /api/public/prestaciones` y parsea `prestaciones-publicas-listar.200.json`; rechaza elementos sin `duracionMinutos`; GREEN
- [x] 3.2 RED → GREEN: `obtenerDisponibilidad(1, '2026-12-01')` llama `GET /api/public/disponibilidad?prestacionId=1&desde=2026-12-01&hasta=2026-12-01`, parsea `disponibilidad-publica.200.json` (incluido el día con `horarios: []`) y rechaza `horarios` que no sea lista de strings; 422 `disponibilidad-publica.422-prestacion-inactiva.json` llega como `ErrorApi` con su código
- [x] 3.3 RED → GREEN: `reservarTurno(body)` envía `POST /api/public/turnos` con el body del ejemplo, parsea `turnos-publico-reservar.201.json` como `TurnoResumenPublico` (rechaza sin `estado` o con `prestacion` mal formada) y propaga como `ErrorApi` el 409 y cada 422 del contrato y el 400 `error.400-validacion.json` con `detalles`; verificar con `npx vitest run src/public/reservar.api.test.ts`

## 4. Pantalla: prestación, fecha y horarios (`public/reservar.ts`)

- [x] 4.1 RED: `reservar.test.ts` (DOM con `stubServidor` y reloj fijo vía `iniciar(contenedor, { ahora })`): un solo `h1` "Reservar turno"; carga `GET /api/public/prestaciones`; radios "Consulta (30 min)" y "Limpieza (45 min)" dentro de un `fieldset` con leyenda; fecha, horarios y datos ocultos; GREEN reemplazando el placeholder; `rutas.test.ts` sigue verde sin modificarlo
- [x] 4.2 RED → GREEN: lista vacía → "Por ahora no hay prestaciones disponibles para reservar."; error de conexión → alerta con "Reintentar" que vuelve a pedir y muestra la lista
- [x] 4.3 RED → GREEN: al elegir prestación aparece el campo "Fecha" con `min`/`max` de la ventana; fecha fuera de ventana → error junto al campo sin request; fecha válida → pide disponibilidad con `desde = hasta` y muestra radios `09:00`, `09:30`, `10:00` en un `fieldset` con leyenda; día vacío → estado "No hay horarios disponibles ese día. Probá con otra fecha." sin formulario
- [x] 4.4 RED → GREEN: cambiar de prestación o de fecha descarta el horario elegido y oculta el formulario conservando lo escrito; respuesta de disponibilidad fuera de orden se descarta; 422 `PRESTACION_INACTIVA` / 404 al pedir horarios → aviso y recarga de prestaciones; verificar con `npx vitest run src/public/reservar.test.ts`

## 5. Pantalla: formulario, envío y errores

- [x] 5.1 RED → GREEN: elegido un horario aparece "Tus datos" con labels Nombre, Apellido, DNI, Email, Teléfono (atributos `autocomplete`, `inputmode`, `maxlength` del diseño, `aria-describedby` al error) y la casilla desmarcada con el texto de finalidad; el form tiene `novalidate`
- [x] 5.2 RED → GREEN: envío con datos inválidos (DNI `99-999-999`, email `paciente@`, teléfono `abc`) o sin casilla → errores por campo con `aria-invalid="true"`, foco al primero y ningún POST
- [x] 5.3 RED → GREEN: envío válido manda exactamente el body de `turnos-publico-reservar.request.json`; dos activaciones seguidas de "Reservar" generan un solo POST; botón `disabled` + "Reservando…" mientras espera
- [x] 5.4 RED → GREEN: 409 `HORARIO_OCUPADO` → alerta "Ese horario se acaba de ocupar, elegí otro.", nuevo GET de disponibilidad del día, horario descartado, datos y casilla conservados; 422 `HORARIO_NO_DISPONIBLE` → su mensaje y refresco
- [x] 5.5 RED → GREEN: 422 `CONSENTIMIENTO_REQUERIDO` → error junto a la casilla; 422 `PRESTACION_INACTIVA` → aviso y recarga de prestaciones; 400 `VALIDACION` con detalle `email` → junto al campo con foco (detalles sin campo propio → alerta general); 500 y conexión caída → mensaje genérico sin texto técnico, datos conservados; verificar con `npx vitest run src/public/reservar.test.ts`

## 6. Pantalla: resumen y estilos

- [x] 6.1 RED → GREEN: 201 del ejemplo → las secciones se reemplazan por el resumen con "Ana", "Consulta (30 min)", "jueves 10 de diciembre de 2026", "10:00", "Pendiente de confirmación", contacto del consultorio y "Revisá tu email (paciente@ejemplo.com)"; foco en el `h2` del resumen; no aparecen DNI, teléfono ni token
- [x] 6.2 RED → GREEN: respuesta con `estado: CONFIRMADO` → "Confirmado" + "Si necesitás cambiarlo, comunicate con el consultorio."; "Reservar otro turno" vuelve al paso 1 con formulario vacío y casilla desmarcada
- [x] 6.3 `public/reservar.css` mobile-first (una columna, horarios en grilla `auto-fill`, tarjetas de radio ≥ 44 px, elegido marcado por borde y peso además de color, foco visible, estilos de `cfg-*` acotados a `.reserva`, dos columnas de datos desde `40rem`) importado desde `reservar.ts`; asserts en `reservar.test.ts` de regiones `role="status"`/`role="alert"` y leyendas; verificar con `npx vitest run src/public/` y `npm run typecheck`

## 7. Verificación integral

- [x] 7.1 `cd frontend && npm run lint && npm run typecheck && npm test && npm run build` en verde; sin `any`, sin `console.log`, sin dependencias nuevas; sin cambios en `src/api/`, `src/shared/`, `src/admin/`, `public/rutas.ts`, `rutas.test.ts` ni `docs/api/` (verificar con `git diff --stat main`)
- [x] 7.2 `openspec validate c-17-reserva-publica-front --strict` sin errores
- [x] 7.3 Prueba manual con `docker compose up` (o `npm run dev` con backend): reservar con datos ficticios, ver el resumen y el email en Mailpit; día sin horarios; forzar 409 reservando el mismo horario desde dos pestañas (la segunda refresca horarios); turno a menos de 24 h nace `CONFIRMADO`
- [x] 7.4 Chequeo manual de accesibilidad y responsive: todo el flujo solo con teclado (Tab, flechas en radios, Espacio en la casilla, Enter para reservar) con foco visible; errores leídos junto a su campo; a 320 px y 375 px sin scroll horizontal

## Workflow follow-up

- Abrir PR de `milagros/c-17-reserva-publica-front` a `main` con commits de la dueña del change (conventional commits en español).
- Avisar en el PR a los dueños de `admin/configuracion/` (C-14/C-15) y `admin/pacientes.logica.ts` (C-16) que el área pública los importa sin modificarlos; proponer un change propio para moverlos a `shared/`.
- Archivar con `/opsx:archive c-17-reserva-publica-front` y marcar C-17 `[x]` en `CHANGES.md`.
