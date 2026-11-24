# Design — C-01 contrato-api

## Context

- Ver `proposal.md` (Why). Los requisitos observables están en `specs/api-contrato/spec.md` y `specs/api-errores/spec.md`; acá van las decisiones de cómo armar el contrato.
- Hoy no existe `docs/api/` ni código: el único insumo es el borrador de KB 08 §Borrador de recursos y endpoints REST, más las reglas de KB 05 (RN-*), los criterios de KB 06 (US-001..022) y el modelo de KB 04.
- Restricciones duras (AGENTS.md): solo 400/401/404/409/422 (RN-GLO-04, DD-14); DTOs, nunca entidades; datos ficticios; instantes con offset; front TS vanilla con `strict` y sin `any`; contrato como única fuente de verdad (regla 5).
- Plazo: MVP el 2026-11-25. El contrato tiene que quedar revisable en horas, no perfecto: lo que no está en el MVP no entra.
- Skill aplicada: `openapi-spec-generation` (design-first, OpenAPI 3.1, `$ref` para reuso, errores documentados con schema compartido, Bearer explícito con `security: []` en públicas, nullabilidad explícita, ejemplos reales, sin URLs hardcodeadas).

## Goals / Non-Goals

**Goals:**
- Un `openapi.yaml` que el backend implementa tal cual y que el front consume vía `tipos.ts` y `ejemplos/`.
- Cerrar todas las ambigüedades del borrador para que ningún integrante tenga que decidir una interfaz.
- Artefactos verificables con comandos (`redocly lint`, `tsc --strict`).

**Non-Goals:**
- Generar código (servidor o cliente) desde el OpenAPI: el backend escribe sus DTOs a mano (KB 08: DTO + mapper manual) y el front usa `tipos.ts`.
- Paginación, versionado en URL, rate limit, HATEOAS, `ETag`.
- Endpoints de opcionales (lista de espera, `.ics`, CSV, `wa.me`): si se hacen, entran con su propio change de contrato.
- Borrado de pacientes (pregunta abierta de baja prioridad en KB 10; no hay endpoint).

## Decisions

### D1. Estructura de `docs/api/`
```
docs/api/
├── openapi.yaml          # único archivo OpenAPI (sin split en múltiples yaml)
├── errores.md            # formato, tabla HTTP, catálogo de códigos, matriz por endpoint
├── tipos.ts              # tipos TS escritos a mano, espejo de components/schemas
└── ejemplos/             # fixtures planos: <recurso>-<operacion>.<caso>.json
```
Nombres de ejemplo: `turnos-public-crear.request.json`, `turnos-public-crear.201.json`, `turnos-public-crear.409-horario-ocupado.json`, `error.400-validacion.json`, `error.401-no-autenticado.json`. Plano (sin subcarpetas) porque así lo dice CHANGES.md y se encuentra con un `ls`.
- *Alternativa*: un yaml por recurso con `$ref` externos. Descartada: 6 personas leyendo, un solo archivo es más fácil de revisar en un PR y redocly lo valida igual.

### D2. Rutas sin versión en la URL
Se mantiene el prefijo `/api` del borrador (KB 03, 08 y la config de seguridad `/api/public/**` dependen de él). La versión vive en `info.version` (semver, `1.0.0`). `servers: [{ url: / }]` relativo, porque front y API comparten origen (proxy nginx/Vite).
- *Alternativa*: `/api/v1`. Descartada: un único cliente propio, mismo origen; agregar `v1` obliga a tocar KB, nginx y seguridad sin beneficio en el MVP.

### D3. Disponibilidad: `desde`/`hasta`, no `fecha`
`GET /api/public/disponibilidad?prestacionId&desde&hasta` y su gemelo privado `GET /api/disponibilidad`. Fechas `YYYY-MM-DD` inclusivas en zona de Buenos Aires, `hasta >= desde`, máximo 31 días (si no, 400 `VALIDACION`). Un día se pide con `desde = hasta`. Respuesta:
```json
{ "prestacion": { "id": 1, "nombre": "Consulta", "duracionMinutos": 30 },
  "dias": [ { "fecha": "2026-12-01", "horarios": ["2026-12-01T09:00:00-03:00", "..."] },
            { "fecha": "2026-12-02", "horarios": [] } ] }
```
Todos los días del rango aparecen, aunque estén vacíos o fuera de la ventana pública: el front pinta el calendario sin inferir huecos. Mismo schema `Disponibilidad` para público y privado; cambia solo el filtro (ventana 2 h / 60 días solo en público, RN-DIS-06).
- *Por qué*: el rango cubre el caso del flujo (un día) y el de la vista semanal sin dos parámetros alternativos; un único shape de respuesta.
- *Alternativa*: `fecha` sola (KB 07). Descartada: obliga a N requests para una semana. *Alternativa*: aceptar `fecha` **o** `desde/hasta`. Descartada: dos formas para lo mismo duplican validaciones y tests.
- El diagrama de KB 07 se ajusta a `desde=hasta=fecha`.

### D4. Formato de error y catálogo
Schema `Error` = `{ timestamp, status, error, codigo, mensaje, path, detalles? }`; `detalles: [{campo, mensaje}]` solo en 400 `VALIDACION` (para `SOLICITUD_MAL_FORMADA` —JSON ilegible, parámetro no parseable— no hay campo fiable). `codigo` es un enum `CodigoError` en el OpenAPI y un union type en `tipos.ts`. Catálogo final (cada código pertenece a un único status):

| Status | `codigo` | Cuándo |
|---|---|---|
| 400 | `VALIDACION` | Campo faltante o con formato inválido (DNI, email, teléfono, instante sin offset, rango de fechas inválido o > 31 días, `pacienteId` y `paciente` a la vez) |
| 400 | `SOLICITUD_MAL_FORMADA` | JSON ilegible, tipo incorrecto, parámetro no parseable |
| 401 | `NO_AUTENTICADO` | Sin token, token inválido o vencido en ruta privada |
| 401 | `CREDENCIALES_INVALIDAS` | Login fallido (mensaje genérico) |
| 404 | `RECURSO_NO_ENCONTRADO` | Turno, paciente, prestación, horario o bloqueo inexistente |
| 404 | `TOKEN_INVALIDO` | Token de turno inexistente |
| 409 | `HORARIO_OCUPADO` | Solapamiento con otro turno activo (incluye `23P01`) |
| 409 | `BLOQUEO_CON_TURNOS_AFECTADOS` | Bloqueo con turnos activos y `cancelarTurnosAfectados=false`; trae `turnosAfectados` |
| 422 | `HORARIO_NO_DISPONIBLE` | Fuera de atención, sobre bloqueo, en el pasado, fuera de la grilla, fuera de la ventana 2 h / 60 d |
| 422 | `CONSENTIMIENTO_REQUERIDO` | Reserva pública con `consentimiento: false` |
| 422 | `CANCELACION_FUERA_DE_PLAZO` | Paciente cancela un `PENDIENTE` con menos de 24 h |
| 422 | `CONFIRMACION_FUERA_DE_PLAZO` | Paciente confirma con más de 48 h |
| 422 | `TRANSICION_INVALIDA` | Cambio de estado no permitido (paciente cancela un `CONFIRMADO`, terminal, cancelado, pasado, asistencia antes del inicio, reprogramar inactivo) |
| 422 | `PRESTACION_INACTIVA` | Reservar, cargar o consultar disponibilidad con prestación inactiva |
| 422 | `PRESTACION_CON_TURNOS` | `DELETE` de prestación con turnos |
| 422 | `DNI_DUPLICADO` | Alta/edición de paciente (o alta rápida en carga manual) con DNI existente |
| 422 | `RANGO_HORARIO_INVALIDO` | `inicio >= fin` en horario de atención o bloqueo |
| 422 | `HORARIOS_SOLAPADOS` | Rango de atención que se pisa con otro del mismo día |

- Se agregan sobre la lista de KB 08: `CONFIRMACION_FUERA_DE_PLAZO`, `PRESTACION_INACTIVA`, `PRESTACION_CON_TURNOS`, `RANGO_HORARIO_INVALIDO`, `HORARIOS_SOLAPADOS`, `BLOQUEO_CON_TURNOS_AFECTADOS` y los genéricos de 400/401/404.
- `codigo` obligatorio en **todos** los errores (no solo en 422): el front despacha por `codigo`, nunca por `status` + texto.
- *Alternativa*: separar `HORARIO_NO_DISPONIBLE` en `FUERA_DE_ATENCION`, `EN_BLOQUEO`, `EN_EL_PASADO`, `FUERA_DE_VENTANA`. Descartada: el front reacciona igual en todos ("elegí otro horario"); el detalle va en `mensaje`. Si un test necesita distinguirlos, afirma sobre el caso de entrada, no sobre el código.
- *Alternativa*: `ASISTENCIA_ANTES_DE_INICIO` propio. Descartada por KB 08, que lo incluye en `TRANSICION_INVALIDA`.

### D5. Orden de evaluación en confirmar/cancelar
Para que la idempotencia sea predecible (RN-TOK-04) y los tests afirmen un único código: (1) existencia → 404; (2) ya está en el estado destino → 200 sin efectos (sin mails); (3) transición no permitida → 422 `TRANSICION_INVALIDA`; (4) plazo → 422 `*_FUERA_DE_PLAZO`. Aplica al paciente por token, con una regla más (decisión del usuario, "confirmar es definitivo"): el paciente por token **solo cancela desde `PENDIENTE`**; cancelar un `CONFIRMADO` es 422 `TRANSICION_INVALIDA` (mensaje: "Tu turno ya está confirmado. Para cancelarlo comunicate con el consultorio.") en el paso (3), antes del plazo; cancelar un `CANCELADO` sigue siendo 200 idempotente (paso 2). El odontólogo conserva la cancelación de cualquier turno activo. Para el odontólogo: confirmar es idempotente sobre `CONFIRMADO` (US-021); cancelar sobre `CANCELADO` o terminal es 422 (RN-CAN-05), porque el odontólogo opera desde la agenda viendo el estado y no hay clientes de mail reintentando.

### D6. Resumen público con flags calculados
`TurnoResumenPublico = { prestacion: {nombre, duracionMinutos}, inicio, fin, estado, nombrePaciente, puedeConfirmar, puedeCancelar, contactoConsultorio }`. `puedeConfirmar` = `PENDIENTE` y faltan ≤ 48 h y > 0; `puedeCancelar` = `PENDIENTE` y faltan ≥ 24 h (siempre `false` si está `CONFIRMADO`, porque confirmar es definitivo para el paciente). `contactoConsultorio` = `APP_CONSULTORIO_CONTACTO`.
- *Por qué*: la regla de plazos vive en un solo lugar (backend, con `Clock`); el front solo muestra u oculta botones y el texto de contacto (RN-CAN-02). Sin DNI, apellido, email, teléfono, ids ni token (KB 08 §Seguridad).
- La reserva pública responde 201 con este mismo resumen (US-006 "resumen sin exponer datos de otros").

### D7. El token nunca sale por la API
Ni el 201 de la reserva ni `Turno` privado incluyen `token`. Solo viaja en los links de los mails (C-11). Reduce superficie de fuga (logs del front, devtools, capturas de demo).
- *Alternativa*: devolverlo en el 201 para redirigir al paciente. Descartada: no hay pantalla que lo necesite en el MVP.

### D8. DTOs principales (nombres de `components/schemas`)
- Auth: `LoginRequest {email, password}`, `LoginResponse {token, tipo: "Bearer", expiraEn, nombre}`, `Usuario {id, nombre, email}` (`/me`).
- Prestaciones: `Prestacion {id, nombre, duracionMinutos, activa}`, `PrestacionPublica {id, nombre, duracionMinutos}`, `PrestacionRequest {nombre, duracionMinutos (1..480), activa (default true)}`. `PUT` reemplaza completo.
- Horarios: `HorarioAtencion {id, diaSemana (1..7 ISO), horaInicio "HH:mm", horaFin "HH:mm"}`, `HorarioAtencionRequest` sin `id`. Lista ordenada por día y hora.
- Bloqueos: `Bloqueo {id, inicio, fin, motivo: string|null, creadoEn}`, `BloqueoRequest {inicio, fin, motivo?}` (previsualizar), `BloqueoCrearRequest = BloqueoRequest + cancelarTurnosAfectados (default false)`, `BloqueoPrevisualizacion {turnosAfectados: Turno[]}`, `BloqueoCreado {bloqueo, turnosCancelados: Turno[]}`, `ErrorBloqueoConflicto = Error + turnosAfectados`. `GET /api/bloqueos?desde&hasta` con fechas `YYYY-MM-DD` obligatorias.
- Pacientes: `Paciente {id, nombre, apellido, dni, ultimoContacto: Contacto|null}`, `Contacto {email, telefono}`, `PacienteRequest {nombre, apellido, dni}`. `GET /api/pacientes?q` sin paginación (un consultorio: cientos de pacientes); `q` opcional busca por prefijo de DNI o apellido.
- Turnos: `Turno {id, inicio, fin, estado, canceladoPor: "PACIENTE"|"ODONTOLOGO"|null, paciente: PacienteRef {id, nombre, apellido, dni}, prestacion: PrestacionRef {id, nombre, duracionMinutos}, emailContacto, telefonoContacto, consentimientoAt: string|null, pendienteMenos24h, creadoEn}`; mismo schema para agenda y detalle (un solo tipo en el front). `TurnoManualRequest {pacienteId?, paciente?: PacienteRequest, prestacionId, inicio, emailContacto, telefonoContacto}` con `oneOf` (exactamente uno de los dos). `ReprogramarRequest {inicio}`. `EstadoAsistenciaRequest {estado: "ASISTIO"|"AUSENTE"}`. `GET /api/turnos?desde&hasta&estado` con `estado` opcional y todos los estados por defecto (la agenda atenúa cancelados).
- Público: `ReservaPublicaRequest {nombre, apellido, dni, email, telefono, prestacionId, inicio, consentimiento}`.
- Enums: `EstadoTurno`, `CanceladoPor`, `CodigoError`.
- Formatos: `email` máx. 150; `telefono` `^[0-9+()\-\s]{6,30}$`; `dni` `^(\d{7,8}|\d{1,2}\.\d{3}\.\d{3})$` en entrada y `^\d{7,8}$` en salida; nombres 1..100; `motivo` máx. 200; instantes `format: date-time` con offset obligatorio.
- `inicio` debe coincidir con un horario de la grilla (paso 15 desde el inicio del rango de atención); si no, 422 `HORARIO_NO_DISPONIBLE`. Aplica a reserva, carga manual y reprogramación.

### D9. Tipos TS escritos a mano, no generados
`tipos.ts` se escribe a mano espejando `components/schemas` (mismos nombres), con `readonly` donde es respuesta, uniones literales para enums y `| null` explícito. Se verifica con `npx tsc --noEmit --strict docs/api/tipos.ts`.
- *Alternativa*: `openapi-typescript`. Descartada para C-01: agrega dependencia y genera tipos anidados (`components["schemas"]["Turno"]`) menos legibles para el equipo; con ~30 schemas el costo manual es bajo. Si el contrato crece, un change posterior puede adoptarlo.

### D10. Ejemplos como archivos y referenciados desde el OpenAPI
Los JSON de `ejemplos/` son la fuente; el OpenAPI los referencia con `externalValue` en `examples` de cada media type. Datos ficticios coherentes entre archivos (mismo paciente "Ana Ejemplo", DNI `99999999`, `ana@ejemplo.com`, prestación "Consulta" id 1, fechas en diciembre de 2026 con `-03:00`).
- **Resultado del spike (apply):** `redocly lint` no valida `externalValue` y, con `value: {$ref}`, trata el `$ref` como contenido literal; se eligió `externalValue` + validación de cada ejemplo contra su schema con un script Ajv (JSON Schema 2020-12) fuera del repo, y checklist manual en el PR como respaldo (anotado en `errores.md`).
- Verificación de que los ejemplos respetan el schema (plan original): en apply se prueba si `redocly lint` (regla `no-invalid-media-type-examples`) resuelve `externalValue`. Si no lo hace, se usa `value` con `$ref` al archivo (Redocly resuelve `$ref` externos al bundlear) y, si tampoco, se revisa a mano con una checklist en el PR. Ver Risks.

### D11. Seguridad en el OpenAPI
`components.securitySchemes.bearerAuth {type: http, scheme: bearer, bearerFormat: JWT}` como `security` global; `security: []` en `/api/auth/login` y `/api/public/**`. Respuesta reutilizable `components.responses.NoAutenticado` (401) en todas las privadas. El 401 de rutas privadas lo emite el entry point de Spring Security con el mismo formato `Error` (nota para C-02/C-04).

## Risks / Trade-offs

- [Redocly no valida ejemplos externos] → fallback de D10; en el peor caso los ejemplos se revisan a mano contra `tipos.ts` en el PR. Ningún bloqueo para mergear.
- [`tipos.ts` se desincroniza del OpenAPI en changes futuros] → regla 5 de AGENTS.md: un change de contrato actualiza los tres artefactos juntos; tarea de verificación cruzada en este change.
- [Un único `HORARIO_NO_DISPONIBLE` esconde la causa] → el `mensaje` la explica; los tests de C-06/C-07 afirman por caso de entrada.
- [Sin paginación en pacientes y agenda] → aceptado: un consultorio, rangos de agenda limitados a 31 días.
- [Decidir orden de evaluación (D5) contradice alguna lectura de la KB] → D5 sigue RN-TOK-04 y RN-CAN-05 literalmente; se documenta en `errores.md`.
- [El contrato tarda y frena a los 6] → alcance cerrado al MVP, sin opcionales; revisión de un integrante y merge el mismo día.

## Migration Plan

No hay código previo. Merge a `main` antes de ramificar (gate 1). Rollback = revertir el PR. Cambios posteriores: change propio (`/opsx:propose` de ajuste de contrato) que actualiza `openapi.yaml`, `errores.md`, `tipos.ts` y `ejemplos/` juntos y sube `info.version`.

## Open Questions

- Texto exacto de los `mensaje` de error: se fijan textos de referencia en `errores.md`; el wording final puede ajustarse en implementación sin cambiar `codigo` ni status.
