## 1. Safety net y base

- [x] 1.1 Baseline: `cd frontend && npm ci && npm test` (registrar cantidad de tests verdes)
- [x] 1.2 Helper común `errores.ts`: mapeo `codigo` a mensaje y `detalles` a campos (TDD, con ejemplos del contrato)

## 2. Prestaciones

- [x] 2.1 `prestaciones.logica.ts`: validador (nombre, duración 1..480) (TDD)
- [x] 2.2 `prestaciones.api.ts`: listar, crear, actualizar, eliminar con parsers; test con fetch mockeado con ejemplos
- [x] 2.3 `prestaciones.ts`: listado, alta/edición, activar/desactivar, baja con confirmación y manejo de 422 `PRESTACION_CON_TURNOS` ofreciendo desactivar (tests DOM)

## 3. Horarios

- [x] 3.1 `horarios.logica.ts`: validador `inicio < fin` y agrupación por día lunes a domingo con orden (TDD)
- [x] 3.2 `horarios.api.ts`: listar, crear, actualizar, eliminar con parsers; test con ejemplos
- [x] 3.3 `horarios.ts`: listado agrupado, alta/edición/baja, mensajes 422 `RANGO_HORARIO_INVALIDO` y `HORARIOS_SOLAPADOS` conservando valores (tests DOM)

## 4. Accesibilidad, estilos y cierre

- [x] 4.1 `configuracion.css` responsive con foco visible; asserts de labels, `aria-invalid` y regiones de estado en los tests
- [x] 4.2 Verificación: lint, typecheck, test, build; `openspec validate c-14-configuracion-prestaciones-horarios-front --strict`; docker compose y `curl /admin/configuracion` 200
