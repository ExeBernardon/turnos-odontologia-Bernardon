## Context

`frontend/src/admin/pacientes.ts` es un placeholder y la ruta `/admin/pacientes` ya está registrada con el layout admin en `admin/rutas.ts` (C-13). El backend (C-09) y el contrato están cerrados (`openspec/specs/pacientes/spec.md`, `docs/api/openapi.yaml`, `docs/api/ejemplos/pacientes-*.json`) y los tipos `Paciente`, `PacienteRequest`, `Contacto` y `DniEntrada` ya existen en `src/api/types.ts` (copia sincronizada de `docs/api/tipos.ts`). C-14/C-15 dejaron en `admin/configuracion/` un patrón por capas (api / lógica pura / DOM) y helpers: `crearCampo`, `crearAvisos`, `mostrarErroresCampos`, `mostrarErrorServidor`, `crearBoton` (`formulario.ts`), `mensajeDeError` y `detallesPorCampo` (`errores.ts`), parsers de `unknown` (`parseo.ts`) y utilidades de test (`stubServidor`, `campoPorLabel`, `boton`, `ejemplo`). `zod` está instalado pero el front no lo usa: los parsers son manuales. El 401 `NO_AUTENTICADO` ya lo resuelve `cliente.ts` (borra sesión y redirige).

## Goals / Non-Goals

**Goals:** pantalla de pacientes completa y accesible; buscador reutilizable listo para C-20; mismo estilo de capas, parseo y tests que C-14/C-15.

**Non-Goals:** cambios de contrato o backend; borrado de pacientes (no existe en la API); mostrar el historial de turnos del paciente; estado en la URL (`?q=` o `/admin/pacientes/{id}`); búsqueda mientras se escribe; paginación; mover los helpers de `admin/configuracion/` a `shared/` (pertenecen a otro integrante, regla 16); agregar dependencias.

## Decisions

1. **Archivos.** `admin/pacientes.ts` (DOM, reemplaza el placeholder; `render` llama a `iniciar` como en C-14 para que los tests esperen la carga), `admin/pacientes.api.ts` (`buscarPacientes(q)`, `obtenerPaciente(id)`, `crearPaciente(body)`, `actualizarPaciente(id, body)` sobre `pedir()` con un `parsearPaciente` defensivo que valida las cinco claves y `ultimoContacto` objeto o `null`), `admin/pacientes.logica.ts` (puro: `validarPaciente`, `armarRequest`, `formatearDni`) y `admin/pacientes.css`. Se mantiene `admin/pacientes.ts` como archivo (y no una carpeta) porque `rutas.ts` ya lo importa así y CHANGES.md lo nombra. *Alternativa*: carpeta `admin/pacientes/` como `configuracion/`; se descarta porque obliga a tocar `rutas.ts` sin ganancia para tres módulos.

2. **Reuso sin modificar.** La pantalla importa `formulario.ts`, `errores.ts`, `parseo.ts`, `configuracion.css` y las utilidades de test desde `admin/configuracion/`. El código `DNI_DUPLICADO` no está en `MENSAJES` de `errores.ts`: se maneja en `pacientes.ts` antes de delegar a `mostrarErrorServidor`, con el texto fijo "Ya existe un paciente con ese DNI." en el campo DNI (mismo texto que el contrato, sin depender del `mensaje` del servidor). Así C-16 no toca archivos de C-14/C-15.

3. **Buscador en `shared/buscador-pacientes.ts`, con la búsqueda inyectada.** API pensada para C-20:
   ```ts
   export interface OpcionesBuscadorPacientes {
     readonly prefijo: string;                       // ids únicos por instancia
     readonly buscar: (q: string) => Promise<readonly Paciente[]>;
     readonly alSeleccionar: (paciente: Paciente) => void;
     readonly etiqueta?: string;                     // default "Buscar por DNI o apellido"
     readonly textoSeleccionar?: string;             // default "Ver" (C-20 puede usar "Elegir")
   }
   export interface BuscadorPacientes {
     readonly raiz: HTMLElement;
     buscar(q?: string): Promise<void>;              // repite la última búsqueda si no se pasa q
     enfocar(): void;
   }
   export function crearBuscadorPacientes(opciones: OpcionesBuscadorPacientes): BuscadorPacientes;
   ```
   El componente solo depende de `shared/dom.ts`, de los tipos de `api/types.ts` y de `ErrorConexion`/`MENSAJE_GENERICO` de `api/cliente.ts`; no importa nada de `admin/`. Recibir `buscar` por parámetro lo hace testeable con una función falsa y evita que `shared/` conozca la ruta HTTP; la pantalla y C-20 le pasan `buscarPacientes` de `pacientes.api.ts`. *Alternativa*: que el componente llame a la API directamente; se descarta por acoplar `shared/` al recurso y obligar a stubear `fetch` en cada test del componente.

4. **Búsqueda al enviar, no mientras se escribe.** `<form role="search">` con `<label>`, `input type="search"` y botón "Buscar"; Enter o el botón disparan la búsqueda. Es lo más simple y accesible (sin combobox ARIA ni anuncios en cada tecla) y no requiere debounce. `q` se recorta; vacío → sin parámetro; se codifica con `encodeURIComponent` (`/api/pacientes?q=Muestra%20Prueba`). Para respuestas fuera de orden se guarda un contador de búsqueda y se descarta cualquier respuesta que no sea la última.

5. **Resultados como lista, no tabla.** `<ul>` de tarjetas (`Apellido, Nombre` + "DNI 99.999.999" + botón "Ver" con `aria-label="Ver Muestra, Carla"`). Con tres datos por fila una lista se adapta mejor al celular que una tabla con scroll horizontal. El DNI se muestra con puntos (`formatearDni`, puro) para leerlo mejor; el valor guardado sigue siendo solo dígitos.

6. **Estados.** El buscador tiene su propia región `role="status"` ("Buscando…", "2 pacientes encontrados", "No hay pacientes que coincidan con la búsqueda.") y `role="alert"` con botón "Reintentar". La pantalla usa `crearAvisos()` para los avisos de alta/edición ("Paciente creado", "Cambios guardados", errores generales y 404).

7. **Detalle y formulario en la misma pantalla.** Estructura: `h1 Pacientes` → avisos → botón "Nuevo paciente" → buscador → panel de detalle (`section` con `h2` y `aria-live` implícito por foco) → formulario (`fieldset` con leyenda "Nuevo paciente" / "Editar paciente"). Seleccionar un resultado llama a `obtenerPaciente(id)` (datos frescos, y la misma función le sirve a C-20 para precargar el contacto) y mueve el foco al `h2` del detalle (`tabindex="-1"`). El detalle muestra los datos en una `<dl>`; el contacto lleva la nota "Contacto del último turno (solo lectura)" o, si es `null`, "Sin turnos registrados: todavía no hay datos de contacto". "Editar" abre el formulario precargado y mueve el foco a Nombre; "Cancelar" lo cierra y devuelve el foco al botón que lo abrió. Tras guardar se muestra el detalle devuelto por la API y se llama a `buscador.buscar()` para refrescar la lista con la búsqueda vigente.

8. **Validación local en `pacientes.logica.ts`.** `validarPaciente({nombre, apellido, dni})` devuelve un `Record<campo, mensaje>`: nombre y apellido recortados obligatorios y hasta 100 caracteres (`maxlength="100"` además en el input); DNI recortado contra `^(\d{7,8}|\d{1,2}\.\d{3}\.\d{3})$`, la misma regex de `DniEntrada`. `armarRequest` envía los valores recortados y el DNI tal cual se escribió (normaliza el backend). Input DNI con `inputmode="numeric"` pero `type="text"` (acepta puntos).

9. **Errores del servidor.** 422 `DNI_DUPLICADO` → campo DNI (decisión 2); 400 `VALIDACION` → `mostrarErrorServidor` (detalles por campo, huérfanos a la alerta); 404 `RECURSO_NO_ENCONTRADO` en detalle o edición → alerta con el mensaje de `errores.ts`, se cierra el formulario/detalle y se recarga la búsqueda; 401 lo maneja el cliente; resto → mensaje genérico. Los valores del formulario se conservan siempre que hay error.

10. **Tests (Vitest + jsdom, ya configurado).** `pacientes.logica.test.ts` (validación y formato, casos válidos e inválidos por campo), `pacientes.api.test.ts` (fetch stubeado con `stubServidor` y los ejemplos de `docs/api/ejemplos/` vía `ejemplo()`: listar con y sin `q`, obtener, crear 201 y 422, actualizar 200 y 422, respuesta mal formada rechazada), `shared/buscador-pacientes.test.ts` (con `buscar` falso: envío, recorte, selección por callback, estados, reintento, fuera de orden, dos instancias) y `pacientes.test.ts` (DOM completo con `stubServidor`: carga inicial, detalle con y sin contacto, alta, edición, cancelar, 422, 400, 404, labels y regiones). El buscador no importa `servidor.testutil.ts` (está en `admin/`); usa promesas controladas propias.

## Risks / Trade-offs

- [La búsqueda distingue acentos (backend): "perez" no encuentra "Pérez"] → se acepta para el MVP; se documenta como pregunta abierta, sin normalizar en el front para no divergir del backend.
- [Lista sin paginación: con muchos pacientes la carga inicial crece] → un consultorio individual tiene pocos cientos; si molesta, se cambia la carga inicial a vacía sin tocar el contrato.
- [Importar helpers desde `admin/configuracion/` acopla pantallas] → es reuso explícito y estable; mover a `shared/` queda para un change propio coordinado con su dueño.
- [jsdom no implementa `label.control` en todos los casos] → `campoPorLabel` ya se usa en C-14/C-15 con éxito.

## Open Questions

Ninguna. Resueltas por Cande Casado (dueña del change), todas con el default del diseño:

- Al entrar se cargan todos los pacientes.
- La búsqueda se dispara al enviar (Enter o botón "Buscar"), sin debounce.
- El DNI se muestra con puntos en listado y detalle (se sigue enviando tal como se escribe y el backend lo guarda solo con dígitos).
- El texto del botón de cada resultado lo recibe el componente: "Ver" en pacientes y "Elegir" en C-20.
- Búsqueda sensible a acentos: ya aceptada para el MVP en C-09; no se pide change de backend.
