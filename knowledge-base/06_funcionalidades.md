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
- [ ] Se rechazan con 422 los rangos con `inicio >= fin` o que se solapan en el mismo día.
- [ ] Los cambios se reflejan de inmediato en la disponibilidad pública.

**Reglas relacionadas**: RN-HOR-01, RN-HOR-02, RN-DIS-01

### US-004 — Gestionar bloqueos con aviso de turnos afectados [MVP]
**Como** odontólogo
**Quiero** bloquear un rango de fecha y hora y ver qué turnos se ven afectados
**Para** ausentarme sin dejar pacientes sin aviso

**Criterios de aceptación**:
- [ ] Alta de bloqueo con inicio, fin y motivo opcional; baja de bloqueo.
- [ ] La vista previa lista los turnos activos dentro del rango sin modificar nada.
- [ ] Al confirmar, el bloqueo se crea y los turnos afectados pasan a `CANCELADO` con `cancelado_por = ODONTOLOGO`, en una transacción.
- [ ] Cada turno afectado envía un email a su email de contacto invitando a reservar de nuevo.
- [ ] Sin turnos afectados, el bloqueo se crea sin paso intermedio.

**Reglas relacionadas**: RN-BLO-01..05, RN-CAN-04

## Épica 3: Reserva pública

### US-005 — Ver horarios libres [MVP]
**Como** paciente
**Quiero** elegir una prestación y ver los horarios libres por fecha
**Para** reservar sin llamar

**Criterios de aceptación**:
- [ ] Solo se listan prestaciones activas.
- [ ] Los horarios cumplen: dentro de atención, fuera de bloqueos, sin turnos activos, turno completo dentro del rango, paso de grilla de 15 min, no en el pasado, con al menos 2 h de anticipación y hasta 60 días hacia adelante (valores configurables).
- [ ] Los horarios libres se calculan al vuelo, no se guardan (no son una entidad).
- [ ] Si no hay horarios en el día, la respuesta es una lista vacía (no un error).
- [ ] No requiere cuenta ni login.

**Reglas relacionadas**: RN-DIS-01..06, RN-PRE-01

### US-006 — Reservar un turno [MVP]
**Como** paciente
**Quiero** reservar un horario ingresando mis datos mínimos
**Para** asegurar mi turno sin crear una cuenta

**Criterios de aceptación**:
- [ ] Datos requeridos: nombre, apellido, DNI, email, teléfono, prestación y horario; se validan formatos (400 si el formato es inválido).
- [ ] El formulario exige aceptar el tratamiento de datos personales (casilla obligatoria con texto breve de finalidad); sin aceptar no se crea el turno (422).
- [ ] El turno se crea `PENDIENTE` si faltan 24 h o más, o `CONFIRMADO` si faltan menos de 24 h (RN-TUR-11), con `consentimiento_at` = instante de la aceptación, y el horario deja de ofrecerse.
- [ ] El email y el teléfono ingresados se guardan en el turno (`email_contacto`, `telefono_contacto`), no en el paciente.
- [ ] Si el DNI ya existe, el turno se asocia al paciente existente sin duplicarlo y sin modificar los datos guardados del paciente.
- [ ] Si el DNI no existe, se crea el paciente con nombre, apellido y DNI.
- [ ] Si el horario se ocupó en el medio, responde 409 "ese horario se acaba de ocupar, elegí otro" y el front vuelve a mostrar horarios.
- [ ] Si el horario es inválido (fuera de atención, sobre un bloqueo, en el pasado o fuera de la ventana de 2 h a 60 días), responde 422.
- [ ] Con más de 48 h: se envía "Confirmación de reserva" al email del turno con los datos del turno y **solo el botón Cancelar**.
- [ ] Con menos de 24 h (turno `CONFIRMADO`): se envía el "Aviso" con los datos del turno **sin ningún botón**; el paciente no puede cancelar por link y se comunica con el consultorio.
- [ ] Entre 24 h y 48 h: el turno nace `PENDIENTE` y se envía **un solo mail**, el "Recordatorio" (Confirmar y Cancelar), en el momento de crear el turno; luego se marca `recordatorio_enviado = true`. Si el envío falla queda en `false` y el job lo reintenta (RN-NOT-04, RN-NOT-05).
- [ ] Los turnos que nacen `CONFIRMADO` (menos de 24 h) se crean con `recordatorio_enviado = true` y no reciben recordatorio (RN-NOT-04).
- [ ] Un turno que nace `CONFIRMADO` no entra en la alerta al odontólogo (RN-NOT-06).
- [ ] La respuesta muestra un resumen del turno (sin exponer datos de otros).

**Reglas relacionadas**: RN-TUR-02, RN-TUR-03, RN-TUR-04, RN-PAC-01..04, RN-TOK-01, RN-TUR-11, RN-NOT-01, RN-NOT-04, RN-DAT-02, RN-DIS-05, RN-DIS-06

### US-007 — Confirmar el turno por link [MVP]
**Como** paciente
**Quiero** confirmar mi asistencia con un clic desde el recordatorio
**Para** avisar al odontólogo que voy a ir

**Criterios de aceptación**:
- [ ] `/turno/confirmar?token=...` muestra el resumen del turno y un botón "Confirmar asistencia"; al apretarlo el turno pasa a `CONFIRMADO`. Abrir el link sin apretar el botón no cambia el estado (algunos clientes de mail abren los links solos; queda igual que cancelar, US-008).
- [ ] Confirmar un turno ya confirmado responde éxito (idempotente).
- [ ] Si faltan más de 48 h para el inicio, `POST /api/public/turnos/{token}/confirmar` responde 422 (RN-NOT-08). El link de Confirmar solo viaja en el Recordatorio, que sale 48 h antes (job) o al crear el turno si se creó entre 24 h y 48 h antes.
- [ ] Confirmar un turno cancelado o pasado informa que no es posible (422).
- [ ] Token inexistente (404): página de "link inválido".

**Reglas relacionadas**: RN-TUR-04, RN-TOK-02, RN-TOK-04, RN-TOK-05, RN-NOT-08

### US-008 — Cancelar el turno por link [MVP]
**Como** paciente
**Quiero** cancelar mi turno desde el email
**Para** liberar el horario si no puedo ir

**Criterios de aceptación**:
- [ ] Solo un turno `PENDIENTE` y hasta 24 h antes del inicio: pasa a `CANCELADO` con `cancelado_por = PACIENTE` y el horario se libera.
- [ ] Con menos de 24 h: se rechaza con 422 `CANCELACION_FUERA_DE_PLAZO` y se indica que se comunique con el consultorio.
- [ ] Un turno ya `CONFIRMADO` no se puede cancelar por link (confirmar es definitivo para el paciente): 422 `TRANSICION_INVALIDA` con "Tu turno ya está confirmado. Para cancelarlo comunicate con el consultorio."; la pantalla oculta el botón de cancelar. Lo cancela el odontólogo.
- [ ] Cancelar un turno ya cancelado es idempotente; sobre uno terminal (asistió/ausente) informa que no aplica (422).
- [ ] Se pide confirmación en pantalla antes de cancelar (evita cancelar por un clic accidental o precarga de links por clientes de mail).

**Reglas relacionadas**: RN-CAN-01, RN-CAN-02, RN-TOK-02

## Épica 4: Agenda del odontólogo

### US-009 — Ver agenda diaria y semanal [MVP]
**Como** odontólogo
**Quiero** ver mis turnos por día y por semana
**Para** organizar mi jornada

**Criterios de aceptación**:
- [ ] Vista diaria y semanal con paciente, prestación, hora inicio/fin, estado y el teléfono de contacto del turno (para poder llamar al número con el que se reservó).
- [ ] Los turnos `PENDIENTE` se marcan "sin confirmar".
- [ ] Los turnos `PENDIENTE` que empiezan en menos de 24 h se resaltan, con el teléfono de contacto y botones Confirmar (US-021) y Cancelar (US-012).
- [ ] Muestra bloqueos y horarios fuera de atención de forma distinguible.
- [ ] Los turnos cancelados no ocupan lugar visual en la grilla (se pueden ocultar o mostrar atenuados).
- [ ] Horas en `America/Argentina/Buenos_Aires`.

**Reglas relacionadas**: RN-TUR-05, RN-NOT-06, RN-GLO-03

### US-010 — Cargar un turno manualmente [MVP]
**Como** odontólogo
**Quiero** crear un turno para un paciente (nuevo o existente)
**Para** registrar los que coordino por teléfono o WhatsApp

**Criterios de aceptación**:
- [ ] Búsqueda de paciente por DNI/nombre o alta rápida (nombre, apellido, DNI).
- [ ] El odontólogo ingresa el email y el teléfono de contacto del turno; si el paciente ya tuvo turnos, se precargan desde su último turno y los puede editar.
- [ ] Solo permite horarios libres; ante choque con otro turno responde 409.
- [ ] No permite cargar fuera del horario de atención, sobre un bloqueo ni en el pasado (422).
- [ ] Se envía el mail correspondiente al email del turno según la anticipación (RN-NOT-01): más de 48 h, "Confirmación de reserva" con solo Cancelar; entre 24 h y 48 h, "Recordatorio" en el momento de crear.
- [ ] Si el turno empieza en menos de 24 h, nace `CONFIRMADO` y se envía el "Aviso" sin botones (RN-TUR-11, RN-TUR-09). El odontólogo no está sujeto al mínimo de 2 h; solo no puede cargar en el pasado.

**Reglas relacionadas**: RN-TUR-02, RN-TUR-09, RN-TUR-11, RN-PAC-01, RN-DIS-05, RN-NOT-01, RN-NOT-04

### US-011 — Reprogramar un turno [MVP]
**Como** odontólogo
**Quiero** mover un turno a otro horario libre
**Para** resolver cambios sin cancelar y recargar

**Criterios de aceptación**:
- [ ] Solo turnos activos; el nuevo horario respeta las reglas de disponibilidad (422 si es inválido) y la restricción de exclusión (409 si choca).
- [ ] Recalcula `fin` con la duración de la prestación.
- [ ] El estado se recalcula con la misma regla que al crear (RN-TUR-11): con el nuevo inicio a 24 h o más el turno queda (o vuelve a) `PENDIENTE`, con `recordatorio_enviado = false` y `alerta_pendiente_enviada = false`; con menos de 24 h queda `CONFIRMADO` y ambos flags quedan en `true`.
- [ ] Si queda `PENDIENTE`, el recordatorio sale al entrar en la ventana de 48 h (o en el siguiente ciclo si ya está dentro).
- [ ] Se envía el Mail 3 al email del turno con el nuevo horario: solo el link de cancelar si queda `PENDIENTE`; aviso de turno confirmado sin links si queda `CONFIRMADO`.

**Reglas relacionadas**: RN-TUR-08, RN-TUR-02, RN-TUR-11, RN-NOT-03, RN-NOT-04, RN-NOT-07

### US-012 — Cancelar un turno desde la agenda [MVP]
**Como** odontólogo
**Quiero** cancelar un turno en cualquier momento
**Para** manejar imprevistos

**Criterios de aceptación**:
- [ ] Pasa a `CANCELADO` con `cancelado_por = ODONTOLOGO` y libera el horario.
- [ ] Se envía email de aviso al email del turno.

**Reglas relacionadas**: RN-CAN-03, RN-CAN-04, RN-CAN-05

### US-013 — Marcar asistencia o ausencia [MVP]
**Como** odontólogo
**Quiero** marcar un turno como "asistió" o "ausente"
**Para** llevar el registro de asistencia

**Criterios de aceptación**:
- [ ] Solo desde `PENDIENTE` o `CONFIRMADO`.
- [ ] Solo se puede marcar desde la hora de inicio del turno; antes de eso el sistema lo rechaza (422), porque el estado es terminal.
- [ ] El estado queda terminal y visible en la agenda.

**Reglas relacionadas**: RN-TUR-01, RN-TUR-07

### US-021 — Confirmar un turno desde la agenda [MVP]
**Como** odontólogo
**Quiero** confirmar un turno pendiente desde la agenda
**Para** dejarlo confirmado cuando hablé con el paciente por mis medios

**Criterios de aceptación**:
- [ ] `POST /api/turnos/{id}/confirmar` (autenticado) pasa un turno `PENDIENTE` a `CONFIRMADO`.
- [ ] Sobre un turno ya `CONFIRMADO` responde éxito (idempotente); sobre uno cancelado, pasado o terminal responde 422.
- [ ] Turno inexistente: 404.
- [ ] Está disponible como botón Confirmar en la agenda, en especial en los `PENDIENTE` resaltados a menos de 24 h (US-009, US-022).

**Reglas relacionadas**: RN-TUR-10, RN-TUR-04, RN-NOT-06

## Épica 5: Pacientes

### US-014 — ABM de pacientes [MVP]
**Como** odontólogo
**Quiero** ver, buscar, crear y editar pacientes
**Para** mantener los datos de identificación de sus pacientes al día

**Criterios de aceptación**:
- [ ] Listado con búsqueda por DNI o apellido.
- [ ] Alta y edición con nombre, apellido y DNI; DNI único (422 `DNI_DUPLICADO` si se duplica). El paciente no tiene email ni teléfono: esos datos viven en cada turno.
- [ ] Los datos del paciente solo se editan acá (la reserva pública nunca los modifica, RN-PAC-04).
- [ ] El detalle del paciente incluye `ultimoContacto` (email y teléfono de su último turno, solo lectura) para precargar la carga manual (US-010).
- [ ] Un paciente con turnos no se borra (decisión de baja en [10](10_preguntas_abiertas.md)).

**Reglas relacionadas**: RN-PAC-01..04, RN-DAT-01

## Épica 6: Recordatorios y emails

### US-015 — Recordatorio automático 48 h antes o al crear [MVP]
**Como** paciente
**Quiero** recibir un recordatorio por email con botones de confirmar y cancelar cuando mi turno sigue sin confirmar
**Para** no olvidarme y poder avisar si no voy

**Criterios de aceptación**:
- [ ] Un job `@Scheduled` corre cada 15 minutos (configurable con `APP_RECORDATORIO_INTERVALO_MIN`) y busca turnos `PENDIENTE` (no `CONFIRMADO`) con inicio a 48 h o menos y `recordatorio_enviado = false`; les envía el Recordatorio.
- [ ] El recordatorio es el único email con el link de Confirmar (RN-NOT-02, RN-NOT-08).
- [ ] El recordatorio, como los demás avisos del turno, va al email del turno (`email_contacto`).
- [ ] Tras enviar (por el job o al crear el turno, RN-NOT-04), marca `recordatorio_enviado = true`; no se reenvía.
- [ ] No se envía recordatorio a los turnos que nacen `CONFIRMADO` (menos de 24 h, `recordatorio_enviado = true`). Un turno creado entre 24 h y 48 h recibe el Recordatorio en el momento de crearse, no por el job (salvo reintento si el envío falló).
- [ ] Al reprogramar a 24 h o más se reinicia el indicador y se envía un nuevo recordatorio para el horario actualizado al entrar en la ventana; si el nuevo inicio queda a menos de 24 h el turno pasa a `CONFIRMADO` y no hay recordatorio.
- [ ] Un error de SMTP no frena el resto del lote ni se marca como enviado (en la creación sincrónica el turno igual queda creado y el job reintenta).
- [ ] Funciona con Mailpit y con Gmail SMTP solo cambiando variables de entorno.

**Reglas relacionadas**: RN-NOT-02, RN-NOT-03, RN-NOT-04, RN-NOT-05

### US-022 — Alerta al odontólogo por turnos pendientes a menos de 24 h [MVP]
**Como** odontólogo
**Quiero** que me avisen de los turnos que siguen sin confirmar a menos de 24 h
**Para** comunicarme con el paciente por mis medios y decidir si confirmo o cancelo

**Criterios de aceptación**:
- [ ] El mismo job `@Scheduled` de US-015 busca turnos `PENDIENTE` que empiezan en menos de 24 h con `alerta_pendiente_enviada = false`.
- [ ] Por cada uno envía **un** email "Turno no confirmado" al odontólogo (su email de login) con paciente, prestación, horario y teléfono de contacto del turno, para que llame al paciente, y marca `alerta_pendiente_enviada = true`; no se repite.
- [ ] Un turno creado con menos de 24 h nace `CONFIRMADO` y no entra en la alerta; solo entran los que siguen `PENDIENTE` al llegar a menos de 24 h.
- [ ] Al reprogramar el turno se reinicia `alerta_pendiente_enviada = false`.
- [ ] Un error de SMTP no frena el resto del lote ni se marca como enviado.
- [ ] En la agenda esos turnos aparecen resaltados con el teléfono y botones Confirmar (US-021) y Cancelar (US-012).

**Reglas relacionadas**: RN-NOT-06, RN-NOT-05, RN-TUR-10

## Épica 7: Infraestructura

### US-016 — Correr todo con Docker Compose [MVP]
**Como** integrante del equipo o evaluador
**Quiero** levantar el sistema con un solo comando
**Para** desarrollar y demostrar sin configurar nada a mano

**Criterios de aceptación**:
- [ ] `docker compose up --build` levanta `db`, `backend`, `frontend` y `mailpit`.
- [ ] Hay `.env.example` versionado y `.env` ignorado por git.
- [ ] Tras el arranque existen el odontólogo seed, las prestaciones y los horarios de ejemplo.
- [ ] El odontólogo seed usa datos ficticios (por ejemplo `odontologo@ejemplo.com`), nunca un email o nombre real. Ese email recibe las alertas de turnos pendientes (US-022); en desarrollo llegan a Mailpit.
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
