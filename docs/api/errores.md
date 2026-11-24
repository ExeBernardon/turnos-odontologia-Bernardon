# Errores de la API

Contrato de errores de `docs/api/openapi.yaml` (change C-01). El front reacciona por `codigo`, nunca por `status` + texto. Agregar, renombrar o mover de status un código requiere un change de contrato (AGENTS.md regla 5).

## Formato uniforme

Toda respuesta de error es JSON con este cuerpo (schema `Error`, tipo `ApiError` en `tipos.ts`):

```json
{
  "timestamp": "2026-11-30T10:15:00-03:00",
  "status": 422,
  "error": "Unprocessable Entity",
  "codigo": "HORARIO_NO_DISPONIBLE",
  "mensaje": "Ese horario no está disponible. Elegí otro.",
  "path": "/api/public/turnos"
}
```

| Campo | Tipo | Notas |
|---|---|---|
| `timestamp` | instante ISO-8601 con offset | Momento del error. |
| `status` | número | Uno de 400, 401, 404, 409, 422. |
| `error` | string | Nombre del estado HTTP. |
| `codigo` | `CodigoError` | Catálogo cerrado (abajo). Siempre presente. |
| `mensaje` | string | Español, apto para mostrar al usuario. El wording puede ajustarse sin cambiar `codigo` ni status. |
| `path` | string | Ruta del request. |
| `detalles` | `[{campo, mensaje}]` | **Solo** en 400 `VALIDACION`. Los `campo` van en camelCase. |

`409 BLOQUEO_CON_TURNOS_AFECTADOS` agrega el campo `turnosAfectados` (lista de `Turno`, schema `ErrorBloqueoConflicto`).

El 401 de rutas privadas lo emite el entry point de Spring Security con este mismo formato (nota para C-02 y C-04).

## Códigos HTTP (RN-GLO-04)

| Status | Uso |
|---|---|
| 200 / 201 / 204 | Éxito (lectura o acción / creación / eliminación sin contenido). |
| 400 | Request mal formado o con formato inválido. |
| 401 | No autenticado o credenciales inválidas. |
| 404 | Recurso o token inexistente. |
| 409 | Conflicto de horario (turno solapado o bloqueo con turnos afectados). |
| 422 | Violación de una regla de negocio. |

No se usan 403, 405, 500 ni otros códigos de error en el contrato. Cada tipo de problema tiene un único código HTTP: un DNI duplicado es 422, no 409.

## Catálogo de códigos de negocio

Idéntico al enum `CodigoError` del OpenAPI y a la unión de `tipos.ts`. Cada código pertenece a un único status.

| Status | `codigo` | Cuándo | `mensaje` de referencia |
|---|---|---|---|
| 400 | `VALIDACION` | Campo faltante o con formato inválido (DNI, email, teléfono, instante sin offset, fecha, rango inválido o de más de 31 días, `pacienteId` y `paciente` a la vez o ninguno). | Hay campos con errores. |
| 400 | `SOLICITUD_MAL_FORMADA` | JSON ilegible, tipo incorrecto o parámetro de ruta/consulta no parseable (sin `detalles`). | La solicitud no se pudo interpretar. Revisá el formato del cuerpo o de los parámetros. |
| 401 | `NO_AUTENTICADO` | Sin token, token inválido o vencido en una ruta privada. | Tenés que iniciar sesión para continuar. |
| 401 | `CREDENCIALES_INVALIDAS` | Login fallido; mismo mensaje para email inexistente y contraseña incorrecta. | Email o contraseña incorrectos. |
| 404 | `RECURSO_NO_ENCONTRADO` | Turno, paciente, prestación, horario o bloqueo inexistente (en la ruta o referenciado en el body). | No encontramos el recurso solicitado. |
| 404 | `TOKEN_INVALIDO` | Token de turno inexistente; no revela ningún dato. | El link no es válido. |
| 409 | `HORARIO_OCUPADO` | Solapamiento con otro turno activo (incluye la violación de la restricción `EXCLUDE` de PostgreSQL, SQLSTATE `23P01`). | Ese horario ya fue reservado. Elegí otro. |
| 409 | `BLOQUEO_CON_TURNOS_AFECTADOS` | Bloqueo con turnos activos en el rango y `cancelarTurnosAfectados=false`. Trae `turnosAfectados`; el bloqueo no se crea. | El rango tiene turnos activos. Confirmá la cancelación para crear el bloqueo. |
| 422 | `HORARIO_NO_DISPONIBLE` | Fuera de atención, sobre un bloqueo, en el pasado, fuera de la grilla o (solo reserva pública) fuera de la ventana de 2 h a 60 días. | Ese horario no está disponible. Elegí otro. |
| 422 | `CONSENTIMIENTO_REQUERIDO` | Reserva pública con `consentimiento: false`; no se crea el turno. | Tenés que aceptar el tratamiento de tus datos para reservar. |
| 422 | `CANCELACION_FUERA_DE_PLAZO` | El paciente cancela por token un turno `PENDIENTE` con menos de 24 h de anticipación. | Ya no se puede cancelar desde el link (faltan menos de 24 h). Comunicate con el consultorio. |
| 422 | `CONFIRMACION_FUERA_DE_PLAZO` | El paciente confirma por token con más de 48 h de anticipación. | Todavía no se puede confirmar el turno. Podés confirmarlo desde 48 h antes. |
| 422 | `TRANSICION_INVALIDA` | Cambio de estado no permitido: el paciente cancela un turno `CONFIRMADO` (confirmar es definitivo), confirmar un cancelado, cancelar o reprogramar uno terminal o cancelado, marcar asistencia antes del inicio. | Ese cambio de estado no está permitido para este turno. |
| 422 | `PRESTACION_INACTIVA` | Reservar, cargar manualmente o consultar disponibilidad con una prestación inactiva. | La prestación no está disponible para reservar. |
| 422 | `PRESTACION_CON_TURNOS` | `DELETE` de una prestación que tiene turnos (usar `activa: false`). | La prestación tiene turnos y no se puede eliminar. Desactivala. |
| 422 | `DNI_DUPLICADO` | Alta o edición de paciente (o alta rápida en la carga manual) con un DNI que ya existe. | Ya existe un paciente con ese DNI. |
| 422 | `RANGO_HORARIO_INVALIDO` | `inicio >= fin` en un horario de atención o en un bloqueo. | La hora de inicio debe ser anterior a la de fin. |
| 422 | `HORARIOS_SOLAPADOS` | Rango de atención que se pisa con otro del mismo día. | El rango se superpone con otro horario de atención del mismo día. |

Notas:
- `HORARIO_NO_DISPONIBLE` agrupa varias causas (fuera de atención, bloqueo, pasado, grilla, ventana). El front reacciona igual ("elegí otro horario"); la causa va en `mensaje`. El `inicio` debe caer en la grilla de 15 minutos desde el inicio del rango de atención.
- Mensajes por endpoint que difieren del de referencia (por ejemplo la cancelación por token de un turno confirmado) están en `ejemplos/`.

## Orden de evaluación al confirmar y cancelar

Para que la idempotencia sea predecible y los tests afirmen un único código, se evalúa en este orden y se corta en el primer caso que aplique:

1. **Existencia**: token o id inexistente, 404 (`TOKEN_INVALIDO` o `RECURSO_NO_ENCONTRADO`).
2. **Idempotencia**: el turno ya está en el estado destino, 200 sin efectos ni mails.
3. **Transición**: cambio no permitido, 422 `TRANSICION_INVALIDA`.
4. **Plazo**: 422 `CONFIRMACION_FUERA_DE_PLAZO` (paciente, faltan más de 48 h) o `CANCELACION_FUERA_DE_PLAZO` (paciente, faltan menos de 24 h).

Reglas por actor:

| Acción | Paciente por token | Odontólogo |
|---|---|---|
| Confirmar | Solo `PENDIENTE`, con 48 h o menos y antes del inicio. Sobre `CONFIRMADO`: 200 idempotente. Sobre `CANCELADO`: 422 `TRANSICION_INVALIDA`. | Cualquier `PENDIENTE`, sin restricción de 48 h. Sobre `CONFIRMADO`: 200 idempotente. Sobre cancelado, pasado o terminal: 422. |
| Cancelar | Solo `PENDIENTE` con 24 h o más. **Sobre `CONFIRMADO`: 422 `TRANSICION_INVALIDA`** (confirmar es definitivo para el paciente: "Tu turno ya está confirmado. Para cancelarlo comunicate con el consultorio."). Sobre `CANCELADO`: 200 idempotente, aunque falten menos de 24 h. Sobre terminal (`ASISTIO`/`AUSENTE`): 422. | Cualquier turno activo, en cualquier momento. Sobre terminal o ya cancelado: 422 `TRANSICION_INVALIDA` (RN-CAN-05). |

En el resumen público `puedeCancelar` es `false` si el turno está `CONFIRMADO`.

## Ejemplos y su verificación

- Los JSON de `docs/api/ejemplos/` son la fuente de los ejemplos y se referencian desde el OpenAPI con `externalValue`. Nombres: `<recurso>-<operacion>.request.json`, `.<status>.json` y `.<status>-<codigo>.json`; los errores genéricos son compartidos (`error.400-validacion.json`, `error.400-solicitud-mal-formada.json`, `error.401-no-autenticado.json`, `error.404-recurso-no-encontrado.json`).
- **Técnica elegida (spike de C-01)**: `redocly lint` NO valida los ejemplos referenciados con `externalValue` (un ejemplo inválido a propósito pasó el lint sin avisos) y, con `value: {$ref: ...}`, trata el `$ref` como contenido literal (falsos positivos). Por eso se usa `externalValue` y la conformidad de cada ejemplo contra el schema de su media type se verifica con un script de validación JSON Schema 2020-12 (Ajv) que recorre todas las operaciones, valida cada ejemplo referenciado contra su schema y avisa de archivos sin referenciar. Si no se puede correr el script, el respaldo es la revisión manual contra `tipos.ts` en el PR.
- Todos los datos son ficticios: `odontologo@ejemplo.com`, `paciente@ejemplo.com`, DNI `99999999` / `99999998` / `99999997`, teléfonos `+54 9 11 0000-0000`.

## Matriz por endpoint

Éxito y errores posibles de cada endpoint. Cada status listado está declarado en la operación del OpenAPI y viceversa. Los errores genéricos (`VALIDACION`, `SOLICITUD_MAL_FORMADA`, `NO_AUTENTICADO`, `RECURSO_NO_ENCONTRADO`) usan los ejemplos compartidos; los específicos tienen su archivo `<operacion>.<status>-<codigo>.json`.

| Método y ruta | Éxito | Errores (`status` `codigo`) |
|---|---|---|
| `POST /api/auth/login` | 200 | 400 `VALIDACION` / `SOLICITUD_MAL_FORMADA`; 401 `CREDENCIALES_INVALIDAS` |
| `GET /api/auth/me` | 200 | 401 `NO_AUTENTICADO` |
| `GET /api/public/prestaciones` | 200 | - |
| `GET /api/public/disponibilidad` | 200 | 400 `VALIDACION`; 404 `RECURSO_NO_ENCONTRADO`; 422 `PRESTACION_INACTIVA` |
| `POST /api/public/turnos` | 201 | 400 `VALIDACION` / `SOLICITUD_MAL_FORMADA`; 404 `RECURSO_NO_ENCONTRADO`; 409 `HORARIO_OCUPADO`; 422 `HORARIO_NO_DISPONIBLE` / `CONSENTIMIENTO_REQUERIDO` / `PRESTACION_INACTIVA` |
| `GET /api/public/turnos/{token}` | 200 | 404 `TOKEN_INVALIDO` |
| `POST /api/public/turnos/{token}/confirmar` | 200 | 404 `TOKEN_INVALIDO`; 422 `TRANSICION_INVALIDA` / `CONFIRMACION_FUERA_DE_PLAZO` |
| `POST /api/public/turnos/{token}/cancelar` | 200 | 404 `TOKEN_INVALIDO`; 422 `TRANSICION_INVALIDA` / `CANCELACION_FUERA_DE_PLAZO` |
| `GET /api/prestaciones` | 200 | 401 `NO_AUTENTICADO` |
| `POST /api/prestaciones` | 201 | 400 `VALIDACION` / `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO` |
| `PUT /api/prestaciones/{id}` | 200 | 400 `VALIDACION` / `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 404 `RECURSO_NO_ENCONTRADO` |
| `DELETE /api/prestaciones/{id}` | 204 | 400 `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 404 `RECURSO_NO_ENCONTRADO`; 422 `PRESTACION_CON_TURNOS` |
| `GET /api/horarios-atencion` | 200 | 401 `NO_AUTENTICADO` |
| `POST /api/horarios-atencion` | 201 | 400 `VALIDACION` / `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 422 `RANGO_HORARIO_INVALIDO` / `HORARIOS_SOLAPADOS` |
| `PUT /api/horarios-atencion/{id}` | 200 | 400 `VALIDACION` / `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 404 `RECURSO_NO_ENCONTRADO`; 422 `RANGO_HORARIO_INVALIDO` / `HORARIOS_SOLAPADOS` |
| `DELETE /api/horarios-atencion/{id}` | 204 | 400 `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 404 `RECURSO_NO_ENCONTRADO` |
| `GET /api/bloqueos` | 200 | 400 `VALIDACION`; 401 `NO_AUTENTICADO` |
| `POST /api/bloqueos/previsualizar` | 200 | 400 `VALIDACION` / `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 422 `RANGO_HORARIO_INVALIDO` |
| `POST /api/bloqueos` | 201 | 400 `VALIDACION` / `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 409 `BLOQUEO_CON_TURNOS_AFECTADOS`; 422 `RANGO_HORARIO_INVALIDO` |
| `DELETE /api/bloqueos/{id}` | 204 | 400 `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 404 `RECURSO_NO_ENCONTRADO` |
| `GET /api/pacientes` | 200 | 401 `NO_AUTENTICADO` |
| `GET /api/pacientes/{id}` | 200 | 400 `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 404 `RECURSO_NO_ENCONTRADO` |
| `POST /api/pacientes` | 201 | 400 `VALIDACION` / `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 422 `DNI_DUPLICADO` |
| `PUT /api/pacientes/{id}` | 200 | 400 `VALIDACION` / `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 404 `RECURSO_NO_ENCONTRADO`; 422 `DNI_DUPLICADO` |
| `GET /api/turnos` | 200 | 400 `VALIDACION`; 401 `NO_AUTENTICADO` |
| `GET /api/turnos/{id}` | 200 | 400 `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 404 `RECURSO_NO_ENCONTRADO` |
| `POST /api/turnos` | 201 | 400 `VALIDACION` / `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 404 `RECURSO_NO_ENCONTRADO`; 409 `HORARIO_OCUPADO`; 422 `HORARIO_NO_DISPONIBLE` / `PRESTACION_INACTIVA` / `DNI_DUPLICADO` |
| `PATCH /api/turnos/{id}/reprogramar` | 200 | 400 `VALIDACION` / `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 404 `RECURSO_NO_ENCONTRADO`; 409 `HORARIO_OCUPADO`; 422 `HORARIO_NO_DISPONIBLE` / `TRANSICION_INVALIDA` |
| `POST /api/turnos/{id}/confirmar` | 200 | 400 `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 404 `RECURSO_NO_ENCONTRADO`; 422 `TRANSICION_INVALIDA` |
| `POST /api/turnos/{id}/cancelar` | 200 | 400 `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 404 `RECURSO_NO_ENCONTRADO`; 422 `TRANSICION_INVALIDA` |
| `PATCH /api/turnos/{id}/estado` | 200 | 400 `VALIDACION` / `SOLICITUD_MAL_FORMADA`; 401 `NO_AUTENTICADO`; 404 `RECURSO_NO_ENCONTRADO`; 422 `TRANSICION_INVALIDA` |
| `GET /api/disponibilidad` | 200 | 400 `VALIDACION`; 401 `NO_AUTENTICADO`; 404 `RECURSO_NO_ENCONTRADO`; 422 `PRESTACION_INACTIVA` |
