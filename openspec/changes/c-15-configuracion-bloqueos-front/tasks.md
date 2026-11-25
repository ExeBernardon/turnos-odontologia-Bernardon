## 1. Safety net

- [x] 1.1 Baseline: `cd frontend && npm test` (registrar cantidad de tests verdes)

## 2. Fechas (Buenos Aires)

- [x] 2.1 `fechas.ts` + test (RED primero): `aInstante`, `formatear`, `hoyBA`, `sumarDias`, límites de día y round-trip

## 3. Lógica y API

- [x] 3.1 `bloqueos.logica.ts` + test: validar formulario (inicio/fin/motivo máximo 200), validar rango de listado (máximo 31 días), armar requests
- [x] 3.2 `parseo.ts` (`leerTextoONulo`) y mensajes en `errores.ts` (+ tests)
- [x] 3.3 `bloqueos.api.ts` + test (RED primero) con ejemplos del contrato: listar, previsualizar, crear (201 y 409 con `turnosAfectados`), eliminar

## 4. Pantalla

- [x] 4.1 `bloqueos.ts` + test DOM: listado con fechas formateadas, vacío, error y reintento, cambio de rango
- [x] 4.2 Alta: sin afectados crea directo; con afectados confirma / cancela el flujo; carrera 409; 422 y 400 con detalles
- [x] 4.3 Baja con confirmación inline (confirma, cancela, 404)

## 5. Estilos, accesibilidad y cierre

- [x] 5.1 `configuracion.css` responsive para bloqueos; asserts de labels, `aria-invalid` y regiones status/alert en los tests
- [x] 5.2 Verificación: lint, typecheck, test, build; `openspec validate c-15-configuracion-bloqueos-front --strict`; docker compose healthy y `/admin/configuracion` 200
