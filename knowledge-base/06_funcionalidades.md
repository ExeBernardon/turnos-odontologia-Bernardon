# Funcionalidades

Organizadas por épica y por historia de usuario (US-NNN). Cada una está marcada **MVP** u **Opcional** según `docs/discovery/discovery.md`. Las opcionales no se implementan hasta que el núcleo MVP esté terminado.

## Épica 1: Acceso del odontólogo

### US-001 — Login del odontólogo [MVP]
**Como** odontólogo
**Quiero** ingresar con email y contraseña
**Para** acceder a mi agenda y configuración

**Criterios de aceptación**:
- [ ] `POST /api/auth/login` con credenciales válidas devuelve un JWT y datos básicos del usuario.
- [ ] Credenciales inválidas devuelven 401 con mensaje genérico (no revela si falló email o contraseña).
- [ ] Las rutas privadas sin token o con token vencido devuelven 401; el front redirige a `/admin/login`.
- [ ] El odontólogo existe por seed (variables de entorno) y su contraseña está hasheada con BCrypt.

**Reglas relacionadas**: RN-AU-01, RN-AU-02, RN-AU-03, RN-AU-04

## Épica 2: Configuración del consultorio

### US-002 — Gestionar prestaciones [MVP]
**Como** odontólogo
**Quiero** crear, editar y desactivar prestaciones con su duración
**Para** que cada turno ocupe el tiempo real

**Criterios de aceptación**:
- [ ] Alta y edición con nombre y duración en minutos (> 0).
- [ ] Una prestación con turnos no se borra; se desactiva y deja de aparecer en la reserva pública.
- [ ] Cambiar la duración no modifica turnos ya creados.

**Reglas relacionadas**: RN-PRE-01, RN-PRE-02, RN-PRE-03

### US-003 — Configurar horarios de atención [MVP]
**Como** odontólogo
**Quiero** definir mis días y rangos horarios de atención (varios por día)
**Para** que solo se ofrezcan horarios reales

**Criterios de aceptación**:
- [ ] Alta, edición y baja de rangos por día de la semana.
- [ ] Se rechazan rangos con `inicio >= fin` o que se solapan en el mismo día (400/422).
- [ ] Los cambios se reflejan de inmediato en la disponibilidad pública.

**Reglas relacionadas**: RN-HOR-01, RN-HOR-02, RN-DIS-01

### US-004 — Gestionar bloqueos con aviso de turnos afectados [MVP]
**Como** odontólogo
**Quiero** bloquear un rango de fecha y hora y ver qué turnos se ven afectados
**Para** ausentarme sin dejar pacientes sin aviso

**Criterios de aceptación**:
- [ ] Alta de bloqueo con inicio, fin y motivo opcional; baja de bloqueo.
- [ ] La vista previa lista los turnos activos dentro del rango sin modificar nada.
- [ ] Al confirmar, el bloqueo se crea y los turnos afectados pasan a `CANCELADO` con `cancelado_por = CONSULTORIO`, en una transacción.
- [ ] Cada paciente afectado recibe un email invitándolo a reservar de nuevo.
- [ ] Sin turnos afectados, el bloqueo se crea sin paso intermedio.

**Reglas relacionadas**: RN-BLO-01..05, RN-CAN-04

## Épica 3: Reserva pública

### US-005 — Ver horarios libres [MVP]
**Como** paciente
**Quiero** elegir una prestación y ver los horarios libres por fecha
**Para** reservar sin llamar

**Criterios de aceptación**:
- [ ] Solo se listan prestaciones activas.
- [ ] Los horarios cumplen: dentro de atención, fuera de bloqueos, sin turnos activos, turno completo dentro del rango, paso de grilla de 15 min, no en el pasado.
- [ ] Si no hay horarios en el día, la respuesta es una lista vacía (no un error).
- [ ] No requiere cuenta ni login.

**Reglas relacionadas**: RN-DIS-01..05, RN-PRE-01

### US-006 — Reservar un turno [MVP]
**Como** paciente
**Quiero** reservar un horario ingresando mis datos mínimos
**Para** asegurar mi turno sin crear una cuenta

**Criterios de aceptación**:
- [ ] Datos requeridos: nombre, apellido, DNI, email, teléfono, prestación y horario; se validan formatos.
- [ ] El turno se crea `PENDIENTE` y el horario deja de ofrecerse.
- [ ] Si el DNI ya existe, se asocia al paciente existente sin duplicarlo.
- [ ] Si el horario se ocupó en el medio, responde 409 "ese horario se acaba de ocupar, elegí otro" y el front vuelve a mostrar horarios.
- [ ] Se envía el email de confirmación con links de confirmar y cancelar.
- [ ] La respuesta muestra un resumen del turno (sin exponer datos de otros).

**Reglas relacionadas**: RN-TUR-02, RN-TUR-03, RN-TUR-04, RN-PAC-01..04, RN-TOK-01, RN-NOT-01

### US-007 — Confirmar el turno por link [MVP]
**Como** paciente
**Quiero** confirmar mi asistencia con un clic desde el email
**Para** avisar al consultorio que voy a ir

**Criterios de aceptación**:
- [ ] `/turno/confirmar?token=...` muestra el resumen y confirma; el turno pasa a `CONFIRMADO`.
- [ ] Confirmar un turno ya confirmado responde éxito (idempotente).
- [ ] Confirmar un turno cancelado o pasado informa que no es posible.
- [ ] Token inexistente: página de "link inválido".

**Reglas relacionadas**: RN-TUR-04, RN-TOK-02, RN-TOK-04, RN-TOK-05

### US-008 — Cancelar el turno por link [MVP]
**Como** paciente
**Quiero** cancelar mi turno desde el email
**Para** liberar el horario si no puedo ir

**Criterios de aceptación**:
- [ ] Hasta 24 h antes del inicio: el turno pasa a `CANCELADO` con `cancelado_por = PACIENTE` y el horario se libera.
- [ ] Con menos de 24 h: se rechaza y se indica que se comunique con el consultorio.
- [ ] Cancelar un turno ya cancelado es idempotente; sobre uno terminal (asistió/ausente) informa que no aplica.
- [ ] Se pide confirmación en pantalla antes de cancelar (evita cancelar por un clic accidental o precarga de links por clientes de mail).

**Reglas relacionadas**: RN-CAN-01, RN-CAN-02, RN-TOK-02

## Épica 4: Agenda del odontólogo

### US-009 — Ver agenda diaria y semanal [MVP]
**Como** odontólogo
**Quiero** ver mis turnos por día y por semana
**Para** organizar mi jornada

**Criterios de aceptación**:
- [ ] Vista diaria y semanal con paciente, prestación, hora inicio/fin y estado.
- [ ] Los turnos `PENDIENTE` se marcan "sin confirmar".
- [ ] Muestra bloqueos y horarios fuera de atención de forma distinguible.
- [ ] Los turnos cancelados no ocupan lugar visual en la grilla (se pueden ocultar o mostrar atenuados).
- [ ] Horas en `America/Argentina/Buenos_Aires`.

**Reglas relacionadas**: RN-TUR-05, RN-GLO-03

### US-010 — Cargar un turno manualmente [MVP]
**Como** odontólogo
**Quiero** crear un turno para un paciente (nuevo o existente)
**Para** registrar los que coordino por teléfono o WhatsApp

**Criterios de aceptación**:
- [ ] Búsqueda de paciente por DNI/nombre o alta rápida.
- [ ] Solo permite horarios libres; ante choque responde 409.
- [ ] Se envía el email de confirmación al paciente.

**Reglas relacionadas**: RN-TUR-02, RN-TUR-09, RN-PAC-01

### US-011 — Reprogramar un turno [MVP]
**Como** odontólogo
**Quiero** mover un turno a otro horario libre
**Para** resolver cambios sin cancelar y recargar

**Criterios de aceptación**:
- [ ] Solo turnos activos; el nuevo horario respeta las reglas de disponibilidad y la restricción de exclusión (409 si choca).
- [ ] Recalcula `fin` con la duración de la prestación.
- [ ] El `recordatorio_enviado` se reinicia a `false` si el nuevo inicio queda a más de 48 h.
- [ ] Se notifica al paciente del cambio (ver pregunta abierta en [10](10_preguntas_abiertas.md)).

**Reglas relacionadas**: RN-TUR-08, RN-TUR-02

### US-012 — Cancelar un turno desde la agenda [MVP]
**Como** odontólogo
**Quiero** cancelar un turno en cualquier momento
**Para** manejar imprevistos

**Criterios de aceptación**:
- [ ] Pasa a `CANCELADO` con `cancelado_por = CONSULTORIO` y libera el horario.
- [ ] Se envía email de aviso al paciente.

**Reglas relacionadas**: RN-CAN-03, RN-CAN-04, RN-CAN-05

### US-013 — Marcar asistencia o ausencia [MVP]
**Como** odontólogo
**Quiero** marcar un turno como "asistió" o "ausente"
**Para** llevar el registro de asistencia

**Criterios de aceptación**:
- [ ] Solo desde `PENDIENTE` o `CONFIRMADO`.
- [ ] El estado queda terminal y visible en la agenda.

**Reglas relacionadas**: RN-TUR-01, RN-TUR-07

## Épica 5: Pacientes

### US-014 — ABM de pacientes [MVP]
**Como** odontólogo
**Quiero** ver, buscar, crear y editar pacientes
**Para** mantener sus datos de contacto al día

**Criterios de aceptación**:
- [ ] Listado con búsqueda por DNI o apellido.
- [ ] Alta y edición con nombre, apellido, DNI, email, teléfono; DNI único (409 si se duplica).
- [ ] Un paciente con turnos no se borra (decisión de baja en [10](10_preguntas_abiertas.md)).

**Reglas relacionadas**: RN-PAC-01..03, RN-DAT-01

## Épica 6: Recordatorios y emails

### US-015 — Recordatorio automático 48 h antes [MVP]
**Como** paciente
**Quiero** recibir un recordatorio por email con links de confirmar y cancelar
**Para** no olvidarme y poder avisar si no voy

**Criterios de aceptación**:
- [ ] Un job `@Scheduled` cada X minutos busca turnos `PENDIENTE`/`CONFIRMADO` que empiezan en las próximas 48 h y tienen `recordatorio_enviado = false`.
- [ ] Tras enviar, marca `recordatorio_enviado = true`; no se reenvía.
- [ ] Un error de SMTP no frena el resto del lote ni se marca como enviado.
- [ ] Funciona con Mailpit y con Gmail SMTP solo cambiando variables de entorno.

**Reglas relacionadas**: RN-NOT-02, RN-NOT-03, RN-NOT-04, RN-NOT-05

## Épica 7: Infraestructura

### US-016 — Correr todo con Docker Compose [MVP]
**Como** integrante del equipo o evaluador
**Quiero** levantar el sistema con un solo comando
**Para** desarrollar y demostrar sin configurar nada a mano

**Criterios de aceptación**:
- [ ] `docker compose up --build` levanta `db`, `backend`, `frontend` y `mailpit`.
- [ ] Hay `.env.example` versionado y `.env` ignorado por git.
- [ ] Tras el arranque existen el odontólogo seed, las prestaciones y los horarios de ejemplo.
- [ ] El front en `localhost` llama a `/api` por el mismo origen (sin CORS).

Detalle en [11](11_docker_y_entornos.md).

## Épica 8: Opcionales

Solo si sobra tiempo; no bloquean el MVP.

### US-017 — Lista de espera simple [Opcional]
Paciente se anota para un día completo; el odontólogo ve la lista al cancelar un turno. Sin notificación automática.

### US-018 — Link `wa.me` con mensaje precargado [Opcional]
Botón en la agenda que abre WhatsApp Web/app con el mensaje armado (envío manual, un clic). No usa API.

### US-019 — Archivo `.ics` del turno [Opcional]
El paciente descarga un `.ics` desde la confirmación o el email para agregar el turno a su calendario.

### US-020 — Exportación CSV [Opcional]
Exportar pacientes y turnos a CSV desde el área privada.
