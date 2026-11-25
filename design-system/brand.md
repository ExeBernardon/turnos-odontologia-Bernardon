# Marca

## Nombre

"Turnos odontológicos" — tomado del título actual de `frontend/index.html:7` y
`frontend/admin/index.html:8`. Nombre comercial definitivo: `(TBD)`.
El nombre mostrado en previews se enlaza desde `brand.js`; no editar cada HTML.

## Logo

Ruta estable: `assets/logo.svg`.
Estado: **placeholder provisional**, etiquetado como "LOGO PROVISIONAL (TBD)".
Logo definitivo: `(TBD)`. Reemplazar el archivo sin cambiar la ruta.

## Imágenes

Imágenes/fotografía: `(TBD)`. Esta revisión no usa imágenes além del placeholder
del logo; los componentes se resuelven con tipografía, color y espacio.

## Tono visual

Derivado del contexto (agenda de un consultorio odontológico, uso por un
profesional y por pacientes, proyecto académico): sobrio, claro, funcional y
accesible. Sin ilustraciones decorativas. Superficies blancas, contraste alto,
estados de error y éxito explícitos en texto, no solo en color.

Fuentes: `frontend/src/styles/base.css`, `frontend/src/admin/configuracion/configuracion.css`,
`knowledge-base/01_vision_y_objetivos.md`, `knowledge-base/06_funcionalidades.md` US-001.

## Reglas de uso

- No presentar el nombre ni el logo como marca aprobada hasta que el usuario lo confirme.
- Mantener `data-brand-name` en los HTML de preview para que el nombre sea reemplazable.
- No agregar colores nuevos sin declararlos antes en `tokens.css`.
- Todo componente interactivo debe conservar el foco visible definido en tokens.
