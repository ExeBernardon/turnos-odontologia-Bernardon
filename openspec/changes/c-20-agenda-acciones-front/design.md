# Design

## Context

C-19 dejó la agenda en `frontend/src/admin/agenda/`, de Julieta (Int 5). Se divide en tres archivos:

- `agenda.api.ts`: carga turnos, bloqueos y horarios con parseo defensivo. `parsearTurno` es privada.
- `agenda.logica.ts`: lógica pura con zona `America/Argentina/Buenos_Aires`.
- `agenda.ts`: render. `crearTarjeta` agrega a los urgentes dos botones `disabled` con `data-enganche="confirmar|cancelar"`, y `cargar()` vuelve a pedir el rango.

`agenda.test.ts` comprueba que esos botones estén deshabilitados y no hagan nada.

De C-16, de la misma dueña de este change, vienen `shared/buscador-pacientes.ts`, con la búsqueda inyectada y `alSeleccionar`, `admin/pacientes.api.ts` (`buscarPacientes`, que ya trae `ultimoContacto`) y `admin/pacientes.logica.ts` (`validarPaciente`, `armarRequest`, `NOMBRE_MAX`).

De Int 2 vienen `admin/configuracion/`, con `parseo`, `fechas` (`hoyBA`, `formatear`), `errores` (`mensajeDeError`, `detallesPorCampo`), `formulario` (`crearCampo`, `crearAvisos`, `mostrarErroresCampos`) y `prestaciones.api` (`listarPrestaciones`), además de los testutils `ejemplo()` y `stubServidor()`.

El selector de horarios de C-17 (`public/reservar*.ts`, de Milagros) apunta al endpoint público y no exporta su parser de disponibilidad.

El contrato ya cubre todo lo necesario (`docs/api/openapi.yaml` y `docs/api/ejemplos/turnos-*.json`, `disponibilidad-privada.*.json`). Este change no lo modifica. jsdom 31, el entorno de Vitest, no implementa `HTMLDialogElement.showModal`.

## Goals / Non-Goals

**Goals:**

- Concentrar toda la lógica nueva en un módulo propio y reducir lo que se toca en los archivos de C-19 a puntos de montaje explícitos.
- Separar las decisiones puras (qué acciones habilitar, cómo traducir errores, cómo armar los requests) del DOM, para probarlas con relojes fijos.
- Reutilizar los helpers existentes sin modificarlos.

**Non-Goals:**

- Edición del contacto o de la prestación de un turno existente: no hay endpoint.
- Mover a `shared/` los helpers de `admin/configuracion/`. Ya se propuso como change aparte en C-17.
- Rutas nuevas, deep links o persistencia en la URL de los formularios.
- Arrastrar y soltar en la grilla.
- Recalcular la urgencia con el reloj del navegador: se sigue usando `pendienteMenos24h` del backend.

## Decisions

1. **Módulo propio `frontend/src/admin/agenda-acciones/`** con los siguientes archivos:
   - `acciones.api.ts`: `confirmarTurno`, `cancelarTurno`, `marcarAsistencia`, `reprogramarTurno`, `crearTurnoManual`, `obtenerDisponibilidadAdmin(prestacionId, fecha)` y `listarPrestacionesActivas()`, que filtra `activa` sobre `listarPrestaciones`.
   - `acciones.logica.ts`: funciones puras con `ahora: Date` inyectado.
   - `dialogo.ts`: diálogo de confirmación.
   - `selector-horario.ts`: día y horarios libres, compartido por el alta y la reprogramación.
   - `alta-manual.ts`: formulario de carga manual.
   - `reprogramar.ts`: formulario de reprogramación.
   - `acciones.ts`: fachada que consume la agenda.
   - `acciones.css`.

   **Alternativa descartada:** agregar los archivos dentro de `admin/agenda/`. Mezcla la propiedad de dos integrantes en la misma carpeta y complica la revisión del PR (regla 16).

2. **Integración con C-19 por una fachada inyectada.** `acciones.ts` exporta `crearAccionesAgenda({ recargar, ahora? })`, que devuelve:
   - `barra`: botón "Nuevo turno".
   - `aviso`: región `role="status"` de las acciones.
   - `alerta`: región `role="alert"`.
   - `panel`: contenedor de los formularios.
   - `deTurno(turno, compacta)`: devuelve el bloque de acciones de una tarjeta, o `null`.

   Los cambios en `agenda/agenda.ts` son tres:
   - (a) Importar la fachada y crearla en `render`. `cargar` es una declaración de función, así que se puede pasar como `recargar`.
   - (b) Agregar `barra` a `.agenda-barra`, y `aviso`, `alerta` y `panel` a la raíz, después del aviso de pendientes.
   - (c) Pasar `deTurno` como parámetro nuevo de `pintarDia` → `crearTarjeta`, y reemplazar el bloque `if (urgente) { … disabled … }` por `const acciones = deTurno(turno, compacta); if (acciones !== null) datos.append(acciones);`.

   Los botones Confirmar y Cancelar conservan `data-enganche="confirmar|cancelar"` y `data-turno-id` queda en el `article`.

   **Alternativas descartadas:**
   - Decorar el DOM de la agenda desde afuera, con `MutationObserver` o `querySelectorAll('[data-enganche]')` después de cada render. Es frágil y acopla al orden interno de render.
   - Delegar eventos con `CustomEvent`. No resuelve cómo la acción obtiene el `Turno` completo.

3. **Exportar `parsearTurno` de `agenda/agenda.api.ts`.** Las respuestas 200 y 201 de las acciones devuelven un `Turno`. `acciones.api.ts` envuelve el parser y lanza `ErrorRespuestaAgenda('turno')` si devuelve `null`. El cambio en el archivo de Julieta es solo la palabra `export`.

   **Alternativa descartada:** duplicar el parser de unas 40 líneas. Las dos copias podrían divergir si el schema `Turno` cambia.

   Para la disponibilidad sí se escribe un parser propio de unas 20 líneas, sobre `parseo.ts`, porque el de C-17 es privado y no hace falta tocar el archivo de Milagros.

   **Alternativa descartada:** reutilizar `obtenerDisponibilidad` de C-17. Llama a `/api/public/disponibilidad`, que aplica la ventana de 2 h y 60 días y no corresponde al odontólogo.

4. **Matriz de acciones pura.** `accionesPermitidas(turno, ahora)` devuelve `{ confirmar, reprogramar, cancelar, asistencia: { habilitada, desde } }`:
   - Confirmar: solo `PENDIENTE` con `inicio > ahora`. El backend responde 422 si el turno es pasado.
   - Reprogramar y Cancelar: `PENDIENTE` o `CONFIRMADO`.
   - Asistencia: visible en turnos activos y habilitada si `inicio <= ahora`. `desde` es la hora `HH:mm` de Buenos Aires.
   - Terminales y cancelados: nada.

   El reloj del navegador solo habilita o deshabilita botones. La autoridad es el backend: un 422 se informa y se recarga la agenda.

   Los botones deshabilitados usan el atributo `disabled` y llevan un texto visible asociado con `aria-describedby` ("Se puede marcar desde las 16:00").

   **Alternativa descartada:** `aria-disabled` con guarda en el click. Es más difícil de probar y de entender para el resto del equipo, y el texto visible ya da el motivo.

5. **Diálogo de confirmación propio** (`role="alertdialog"`, `aria-modal="true"`, `aria-labelledby`, `aria-describedby`):
   - Se monta en `document.body`.
   - Al abrir pone el foco en "Volver", la opción segura.
   - Atrapa Tab y Shift+Tab entre sus botones y cierra con Escape o "Volver".
   - Al cerrar devuelve el foco al disparador.
   - Mientras está abierto marca `inert` y `aria-hidden` en el resto del contenido.
   - `confirmarAccion(opciones): Promise<boolean>` resuelve `true` solo al aceptar.

   Se usa para Cancelar, Asistió y Ausente. Confirmar no lo usa, porque no es terminal y el backend es idempotente. Reprogramar tampoco, porque el envío del formulario ya es la confirmación explícita: su botón dice "Reprogramar al martes 1/12 09:30".

   **Alternativas descartadas:**
   - `<dialog>.showModal()` nativo: jsdom 31 no lo implementa, así que los tests necesitarían mocks del propio comportamiento que se quiere verificar.
   - `window.confirm`: no se puede estilizar, no muestra datos estructurados y bloquea los tests.

6. **Formularios en un panel dentro de la página.** El alta y la reprogramación se abren en `panel` como `<section aria-labelledby>` con `h2`. El foco va al `h2` (`tabindex="-1"`). "Cerrar" o un envío exitoso cierran el panel y devuelven el foco al disparador. Hay un solo panel abierto a la vez: abrir otro reemplaza el anterior.

   **Alternativas descartadas:**
   - Ruta nueva `/admin/agenda/nuevo`: obliga a tocar `admin/rutas.ts`, que es de C-13, y pierde el contexto de la agenda.
   - Modal para el alta: un formulario largo en un modal es más difícil de usar en el celular y con lector de pantalla.

7. **Selector de horario compartido.** `crearSelectorHorario({ prefijo, prestacionId, ahora, alElegir })` incluye:
   - `input type="date"` con `min = hoyBA(ahora)` y sin máximo, porque el odontólogo no tiene la ventana de 60 días.
   - El botón "Ver horarios".
   - Un `fieldset` de radios con `legend` "Horarios libres", cuyo `value` es el instante devuelto por la API tal cual. Así no se recalculan offsets.
   - Una región `role="status"`.

   Expone `refrescar()` para los 409 y 422 de horario, y `cambiarPrestacion(id)`, que descarta la elección. Las respuestas fuera de orden se descartan con un contador, como en el buscador.

8. **Alta manual.** Primero, un `fieldset` "Paciente" con radios "Paciente existente" y "Paciente nuevo":
   - **Existente:** monta `crearBuscadorPacientes({ prefijo: 'alta-turno-pac', buscar: buscarPacientes, alSeleccionar, textoSeleccionar: 'Elegir' })`. Al elegir, muestra "Apellido, Nombre · DNI" con "Cambiar paciente" y precarga email y teléfono desde `ultimoContacto`.
   - **Nuevo:** nombre, apellido y DNI, validados con `validarPaciente`. El objeto `paciente` se arma con `armarRequest` y va en el mismo `POST /api/turnos`, con lo que el alta es atómica.

   Después vienen la prestación (`select` de activas), el selector de horario y el contacto (email `maxlength=150`; teléfono con el patrón del contrato `^[0-9+()\-\s]{6,30}$`).

   `armarTurnoManual(entrada)` devuelve el `TurnoManualRequest` con exactamente uno de `pacienteId` o `paciente`, que es el `oneOf` del contrato.

   **Alternativa descartada:** `POST /api/pacientes` y luego `POST /api/turnos`. Deja pacientes huérfanos si falla el turno.

9. **Traducción de errores por acción.** `accionDeErrorTurno(error, accion)` devuelve `{ mensaje, efecto }`, donde `efecto` es `'refrescarHorarios' | 'recargarPrestaciones' | 'campoDni' | 'campos' | 'recargarAgenda' | 'ninguno'`. Sigue el patrón de `accionDeError` de C-17 con mensajes propios y no muestra el `mensaje` del servidor:
   - 409 `HORARIO_OCUPADO` y 422 `HORARIO_NO_DISPONIBLE` → refrescar horarios.
   - 422 `PRESTACION_INACTIVA` → recargar prestaciones.
   - 422 `DNI_DUPLICADO` → marcar el DNI con `aria-invalid` y foco.
   - 400 `VALIDACION` → `detallesPorCampo`.
   - 422 `TRANSICION_INVALIDA`, con un mensaje distinto por acción, y 404 `RECURSO_NO_ENCONTRADO` → recargar la agenda.
   - Error de conexión y 500 → mensaje genérico, sin recargar.
   - 401 → sin mensaje, porque lo maneja `registrarManejadorExpiracion` en `admin/main.ts`.

10. **Resultado, recarga y foco.** Al terminar, cada acción hace lo siguiente:
    - (a) Escribe el anuncio en `aviso`, por ejemplo "Turno reprogramado al martes 1 de diciembre, 09:30: queda pendiente de confirmación". El estado sale de la respuesta y no de una predicción.
    - (b) Hace `await recargar()`.
    - (c) Enfoca `[data-turno-id="<id>"]`, con `tabindex="-1"` si sigue en el DOM, o el `aviso`, que también tiene `tabindex="-1"`.

    Mientras la solicitud está en curso, los botones de esa tarjeta quedan `disabled` y con `aria-busy` en el bloque, para evitar dobles envíos. El aviso de pendientes de C-19 no se modifica.

11. **Vistas diaria y semanal.** El bloque de acciones es el mismo en las dos. En la vista semanal (`compacta`), las acciones secundarias (Reprogramar y la asistencia) quedan dentro de un `<details>` con `summary` "Más acciones", para no saturar la columna. Confirmar y Cancelar quedan siempre a la vista. El orden es Confirmar → Cancelar → Reprogramar → Asistió / Ausente.

12. **Estilos.** `acciones.css` es aditivo y va acotado a `.agenda-acciones` y `.aa-*`. Usa los tokens de `design-system/tokens.css` revisión 1: botones de 44 px o más, foco visible, mobile-first, el diálogo de ancho completo en el celular y sin scroll horizontal a 320 px. Cancelar y Ausente se distinguen con texto y borde, no solo con color.

13. **TDD.** Los tests van en este orden:
    - `acciones.logica.test.ts`, con relojes fijos: matriz de acciones, hora "desde", requests y errores.
    - `acciones.api.test.ts`, con `stubServidor`, `ejemplo()` y todos los ejemplos `turnos-*` y `disponibilidad-privada.*`.
    - `dialogo.test.ts`: foco, trap, Escape y retorno.
    - `selector-horario.test.ts`.
    - `alta-manual.test.ts`.
    - `reprogramar.test.ts`.
    - `acciones.test.ts`: la fachada.
    - La adaptación de `agenda.test.ts`.

    Los tests DOM fijan `Date` con `vi.useFakeTimers({ toFake: ['Date'] })`, como en C-19.

## Risks / Trade-offs

- **Se modifican archivos de Julieta**: `agenda/agenda.ts`, `agenda/agenda.api.ts` y `agenda/agenda.test.ts` → diff mínimo y aislado, enumerado en la proposal, con aviso en el PR y pedido de revisión a Julieta. La firma de `render` no cambia. `agenda.test.ts` mockea `./agenda.api` con un factory sin `parsearTurno`: no afecta porque las acciones solo lo usan al recibir una respuesta, y se verifica en la tarea de adaptación.
- **Imports de módulos de Int 2 desde otra carpeta**: `admin/configuracion/*`, sin modificarlos → mismo patrón que C-16 y C-17. Si se mueven a `shared/` en un change futuro, solo cambian los imports.
- **Reloj del navegador desfasado del servidor** → solo afecta qué botones se habilitan. El 422 del backend se traduce a un mensaje claro y recarga la agenda.
- **Reprogramar a un horario que se superpone con el propio turno** (por ejemplo, correrlo 15 minutos): la disponibilidad podría no ofrecerlo porque el turno ocupa su propio lugar. Es una limitación aceptada para el MVP y no requiere cambiar el contrato. Si el equipo lo necesita, se discute en un change de backend.
- **Diálogo propio en lugar del nativo** → más código que mantener, a cambio de tests reales de foco y teclado. Está acotado a `dialogo.ts` y es reutilizable.
- **La vista semanal se vuelve densa** → `<details>` para las acciones secundarias.

## Migration Plan

No hay datos ni esquema que migrar. Se despliega con el build del front.

Rollback: revertir el commit. Se vuelve a los botones deshabilitados de C-19 y el módulo `agenda-acciones/` queda sin usar o se elimina, sin efectos sobre el resto de las pantallas.

## Open Questions

- Texto definitivo de los anuncios y de los diálogos: se puede ajustar en el PR sin cambiar los specs.
