## 1. Safety net

- [ ] 1.1 Baseline: `cd frontend && npm test` y `npm run typecheck`; registrar la cantidad de tests verdes (si alguno falla, reportarlo como falla preexistente y no seguir)

## 2. Lógica pura (`admin/pacientes.logica.ts`)

- [ ] 2.1 RED: `pacientes.logica.test.ts` con `validarPaciente` (nombre/apellido vacíos o solo espacios, 101 caracteres, DNI `99-999-997`, letras, 6 y 9 dígitos → error por campo; `9999999`, `99999997`, `99.999.997`, `9.999.999` → sin errores); GREEN y triangular hasta cubrir todos los casos; verificar con `npx vitest run src/admin/pacientes.logica.test.ts`
- [ ] 2.2 RED → GREEN: `armarRequest` (recorta nombre, apellido y DNI; conserva el DNI como se escribió) y `formatearDni` (`99999997` → `99.999.997`, `9999999` → `9.999.999`); verificar con el mismo test

## 3. Cliente del recurso (`admin/pacientes.api.ts`)

- [ ] 3.1 RED: `pacientes.api.test.ts` con `stubServidor` y `ejemplo()`: `buscarPacientes('')` llama `GET /api/pacientes` sin `q`; `buscarPacientes(' Muestra Prueba ')` llama `?q=Muestra%20Prueba`; parsea `pacientes-listar.200.json` (con y sin `ultimoContacto`)
- [ ] 3.2 RED → GREEN: `obtenerPaciente` (`pacientes-obtener.200.json`), `crearPaciente` (201 y 422 `DNI_DUPLICADO` como `ErrorApi`), `actualizarPaciente` (`PUT /api/pacientes/3`, 200 y 422); `parsearPaciente` rechaza respuestas sin `ultimoContacto` o con tipos incorrectos; verificar con `npx vitest run src/admin/pacientes.api.test.ts`

## 4. Buscador reutilizable (`shared/buscador-pacientes.ts`)

- [ ] 4.1 RED: `buscador-pacientes.test.ts` con una función `buscar` falsa: label visible asociado al campo, envío con Enter/botón, texto recortado, vacío → `buscar('')`; GREEN
- [ ] 4.2 RED → GREEN: resultados en el orden recibido con "Apellido, Nombre", DNI con puntos y botón con `aria-label` "Ver Apellido, Nombre"; activar el botón llama `alSeleccionar` con el paciente y no vuelve a llamar `buscar`; `textoSeleccionar` y `etiqueta` personalizables
- [ ] 4.3 RED → GREEN: estados en `role="status"` ("Buscando…", "N pacientes encontrados", sin coincidencias), error de conexión/API en `role="alert"` con "Reintentar" que repite la última búsqueda, respuesta fuera de orden descartada, dos instancias con ids distintos; sin imports desde `admin/` (verificar con `grep -n "admin/" src/shared/buscador-pacientes.ts` vacío)
- [ ] 4.4 Estilos `shared/buscador-pacientes.css` mobile-first (sin scroll horizontal a 320px, botones ≥ 44px, foco visible); verificar con `npx vitest run src/shared/buscador-pacientes.test.ts` y `npm run typecheck`

## 5. Pantalla (`admin/pacientes.ts`)

- [ ] 5.1 RED: `pacientes.test.ts` (DOM con `stubServidor`): un solo `h1` "Pacientes", carga inicial `GET /api/pacientes`, lista del ejemplo; GREEN reemplazando el placeholder con `render`/`iniciar` y el buscador montado con `buscarPacientes`
- [ ] 5.2 RED → GREEN: seleccionar un resultado pide `GET /api/pacientes/{id}`, muestra el detalle con email y teléfono de solo lectura y la nota de último turno; con `ultimoContacto: null` muestra "Sin turnos registrados: todavía no hay datos de contacto"; 404 avisa y recarga la búsqueda; el foco pasa al título del detalle; no hay botón de eliminar
- [ ] 5.3 RED → GREEN: alta con "Nuevo paciente" (`pacientes-crear.request.json` → POST, aviso "Paciente creado", detalle con `99999997`, lista refrescada); validación local sin llamar a la API; el formulario no tiene email ni teléfono
- [ ] 5.4 RED → GREEN: edición desde el detalle (formulario precargado, `PUT /api/pacientes/3` con `pacientes-actualizar.request.json`, aviso "Cambios guardados", detalle actualizado); cancelar no llama a la API y devuelve el foco; 404 al guardar avisa y recarga
- [ ] 5.5 RED → GREEN: 422 `DNI_DUPLICADO` (alta y edición, ejemplos del contrato) en el campo DNI con `aria-invalid="true"` y foco, valores conservados; 400 `VALIDACION` con detalle para `apellido` junto a su campo; otros errores en la alerta con mensaje genérico
- [ ] 5.6 `admin/pacientes.css` responsive (detalle y formulario apilados en celular, sin color como único indicador); asserts de labels, `aria-describedby` y regiones status/alert en `pacientes.test.ts`; verificar con `npx vitest run src/admin/pacientes.test.ts`

## 6. Verificación integral

- [ ] 6.1 `cd frontend && npm run lint && npm run typecheck && npm test && npm run build` en verde; sin `any`, sin `console.log`, sin dependencias nuevas en `package.json`; `types.sync.test.ts` sigue verde (no se tocó `types.ts`)
- [ ] 6.2 `openspec validate c-16-pacientes-front --strict` sin errores
- [ ] 6.3 Prueba manual con `docker compose up` (o `npm run dev` con backend): login, `/admin/pacientes` carga la lista, buscar por DNI con puntos y por apellido, alta, alta con DNI repetido (error en el campo), edición, detalle con y sin último contacto
- [ ] 6.4 Chequeo manual de accesibilidad solo con teclado: Tab/Shift+Tab recorre buscador, resultados, detalle y formulario con foco visible; Enter busca y envía; los errores se leen junto a su campo; a 375px de ancho no hay scroll horizontal

## Workflow follow-up

- Abrir PR de `casado/c-16-pacientes-front` a `main` con commits de Cande Casado (conventional commits en español).
- Tras el merge: `/opsx:archive c-16-pacientes-front` y marcar C-16 como hecho en `CHANGES.md`.
- Avisar a quien tome C-20 la API de `crearBuscadorPacientes` y `obtenerPaciente` para la carga manual.
