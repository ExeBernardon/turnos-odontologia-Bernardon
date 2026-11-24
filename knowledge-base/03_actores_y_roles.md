# Actores y Roles

## Actores del sistema

| Actor | Descripción | Cómo interactúa |
|-------|-------------|-----------------|
| Odontólogo (autenticado) | Único usuario con login. Dueño del consultorio. | Área privada del front: agenda, configuración, pacientes. Envía JWT en `Authorization: Bearer`. |
| Paciente (anónimo) | Sin cuenta. Se identifica por DNI al reservar. | Área pública: página de reserva y links de cancelar (y de confirmar, solo en el recordatorio) recibidos por email (autorizados por token del turno). |
| Sistema (job programado) | Proceso interno, no es un usuario. | `@Scheduled`: envía recordatorios a los pacientes con turno `PENDIENTE` y alertas al odontólogo por turnos pendientes a menos de 24 h. No pasa por la API. |

En la v1 no hay secretaria, ni otros profesionales, ni cuenta de paciente. Hay un solo odontólogo y un solo sillón.

## RBAC — Matriz de permisos

Dos actores. C = crear, R = leer, U = actualizar, D = eliminar/desactivar.

| Recurso | Odontólogo autenticado | Paciente anónimo |
|---------|------------------------|------------------|
| Prestaciones | CRUD (D = desactivar) | R solo activas (para elegir al reservar) |
| Horarios de atención | CRUD | - (indirecto: ve horarios libres) |
| Bloqueos | CRUD (con vista previa de turnos afectados) | - |
| Horarios libres (disponibilidad, calculada al vuelo, no es una entidad) | R | R |
| Pacientes (nombre, apellido, DNI) | CRUD | C/reutilización indirecta al reservar (por DNI, sin modificar los datos existentes); sin lectura |
| Turnos (agenda) | CRUD, reprogramar, confirmar, cancelar, cambiar estado | C (reservar); sobre SU turno por token: R, confirmar, cancelar (solo `PENDIENTE` y hasta 24 h antes; confirmar es definitivo) |
| Usuario/odontólogo | Login | - |

Regla de autorización del paciente: el token del turno autoriza únicamente operaciones sobre ese turno (RN-TOK-02). No existe listado ni búsqueda de turnos para anónimos.

## Rutas públicas

Sin JWT (prefijos `/api/public/**` y `/api/auth/login`), más las páginas públicas del front:

API:
- `POST /api/auth/login`
- `GET /api/public/prestaciones`
- `GET /api/public/disponibilidad` (los "horarios libres" no son una entidad ni una tabla: se calculan al vuelo, ver [08](08_arquitectura_propuesta.md#cálculo-de-horarios-libres))
- `POST /api/public/turnos`
- `GET /api/public/turnos/{token}` (solo lectura: devuelve el resumen del turno)
- `POST /api/public/turnos/{token}/confirmar`
- `POST /api/public/turnos/{token}/cancelar`

Front: `/reservar` (o `/`), `/turno/confirmar?token=...`, `/turno/cancelar?token=...`.

### Por qué login, confirmar y cancelar son POST

- **Login**: envía credenciales. En un GET irían en la URL y quedarían en el historial del navegador, en los logs del servidor y del proxy. En un POST viajan en el body.
- **Confirmar y cancelar**: cambian el estado del turno. Un GET tiene que ser de solo lectura (seguro e idempotente). Además, los clientes de mail, los antivirus y los previsualizadores de links abren los links de forma automática: si el link del email ejecutara la acción con un GET, el turno se confirmaría o cancelaría sin que el paciente haga nada.
- Por eso el link del email abre una **página del front** (`/turno/confirmar?token=...`) que muestra el resumen del turno con un `GET /api/public/turnos/{token}` (solo lectura, no cambia nada) y recién cuando el paciente aprieta el botón el front hace el `POST .../confirmar` o `POST .../cancelar`.

## Rutas privadas (requieren JWT)

API: todo lo demás bajo `/api/**`: `/api/auth/me`, `/api/prestaciones`, `/api/horarios-atencion`, `/api/bloqueos`, `/api/pacientes`, `/api/turnos`, `/api/disponibilidad`.

Front: `/admin/login` (pública, pero es la entrada al área privada), `/admin/agenda`, `/admin/configuracion`, `/admin/pacientes`. Sin token válido, el front redirige a `/admin/login`; el backend responde 401.
