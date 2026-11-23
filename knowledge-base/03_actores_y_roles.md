# Actores y Roles

## Actores del sistema

| Actor | Descripción | Cómo interactúa |
|-------|-------------|-----------------|
| Odontólogo (autenticado) | Único usuario con login. Dueño del consultorio. | Área privada del front: agenda, configuración, pacientes. Envía JWT en `Authorization: Bearer`. |
| Paciente (anónimo) | Sin cuenta. Se identifica por DNI al reservar. | Área pública: página de reserva y links de confirmar/cancelar recibidos por email (autorizados por token del turno). |
| Sistema (job programado) | Proceso interno, no es un usuario. | `@Scheduled`: envía recordatorios. No pasa por la API. |

En la v1 no hay secretaria, ni otros profesionales, ni cuenta de paciente. Hay un solo odontólogo y un solo sillón.

## RBAC — Matriz de permisos

Dos actores. C = crear, R = leer, U = actualizar, D = eliminar/desactivar.

| Recurso | Odontólogo autenticado | Paciente anónimo |
|---------|------------------------|------------------|
| Prestaciones | CRUD (D = desactivar) | R solo activas (para elegir al reservar) |
| Horarios de atención | CRUD | - (indirecto: ve horarios libres) |
| Bloqueos | CRUD (con vista previa de turnos afectados) | - |
| Horarios libres (disponibilidad) | R | R |
| Pacientes | CRUD | C/reutilización indirecta al reservar (por DNI); sin lectura |
| Turnos (agenda) | CRUD, reprogramar, cancelar, cambiar estado | C (reservar); sobre SU turno por token: R, confirmar, cancelar (hasta 24 h antes) |
| Usuario/odontólogo | Login | - |

Regla de autorización del paciente: el token del turno autoriza únicamente operaciones sobre ese turno (RN-TOK-02). No existe listado ni búsqueda de turnos para anónimos.

## Rutas públicas

Sin JWT (prefijos `/api/public/**` y `/api/auth/login`), más las páginas públicas del front:

API:
- `POST /api/auth/login`
- `GET /api/public/prestaciones`
- `GET /api/public/disponibilidad`
- `POST /api/public/turnos`
- `GET /api/public/turnos/{token}`
- `POST /api/public/turnos/{token}/confirmar`
- `POST /api/public/turnos/{token}/cancelar`

Front: `/reservar` (o `/`), `/turno/confirmar?token=...`, `/turno/cancelar?token=...`.

## Rutas privadas (requieren JWT)

API: todo lo demás bajo `/api/**`: `/api/auth/me`, `/api/prestaciones`, `/api/horarios-atencion`, `/api/bloqueos`, `/api/pacientes`, `/api/turnos`.

Front: `/admin/login` (pública, pero es la entrada al área privada), `/admin/agenda`, `/admin/configuracion`, `/admin/pacientes`. Sin token válido, el front redirige a `/admin/login`; el backend responde 401.
