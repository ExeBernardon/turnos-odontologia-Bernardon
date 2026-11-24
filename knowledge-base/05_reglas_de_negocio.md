# Reglas de Negocio

Cada regla tiene un código único `RN-{DOMINIO}-{NN}`. Todas provienen de `docs/discovery/discovery.md` salvo las marcadas **(derivada)**, que son consecuencias necesarias de las acordadas o de decisiones técnicas confirmadas.

## Dominio: Autenticación (RN-AU)

- **RN-AU-01**: El odontólogo es el único usuario con login; ingresa con email y contraseña.
- **RN-AU-02**: La contraseña se guarda hasheada con BCrypt; nunca en claro ni en logs.
- **RN-AU-03**: La sesión es un JWT enviado en `Authorization: Bearer`. Las rutas públicas (ver [03](03_actores_y_roles.md)) no exigen JWT. **(derivada)**
- **RN-AU-04**: El odontólogo se crea por seed configurable por variables de entorno; no hay registro público. **(derivada)**

## Dominio: Prestaciones (RN-PRE)

- **RN-PRE-01**: Cada prestación tiene su propia duración en minutos (consulta, limpieza, extracción…).
- **RN-PRE-02**: La duración del turno es exactamente la de la prestación: `fin = inicio + duración`. El `fin` se persiste en el turno, así cambiar la duración de la prestación después no altera turnos existentes. **(derivada)**
- **RN-PRE-03**: Una prestación con turnos no se elimina: se desactiva. Las inactivas no se ofrecen en la reserva pública. **(derivada)**

## Dominio: Horarios de atención (RN-HOR)

- **RN-HOR-01**: El horario de atención se define por día de la semana con uno o más rangos (hora inicio/fin).
- **RN-HOR-02**: Los rangos de un mismo día no pueden solaparse y `inicio < fin`. **(derivada)**

## Dominio: Disponibilidad (RN-DIS)

- **RN-DIS-01**: Solo se ofrecen horarios dentro del horario de atención, fuera de los bloqueos y sin turnos activos encima.
- **RN-DIS-02**: Un horario ofrecido debe permitir que el turno entero (`inicio` a `inicio + duración`) quepa dentro de un mismo rango de atención. **(derivada)**
- **RN-DIS-03**: Los horarios se ofrecen discretizados con un paso configurable (por defecto 15 min). **(derivada, SU-04)**
- **RN-DIS-04**: No se ofrecen horarios en el pasado. **(derivada)**
- **RN-DIS-05**: La disponibilidad es solo informativa; la garantía final de no solapamiento entre turnos es la restricción de la base (RN-TUR-02). Esa restricción **solo** cubre solapamiento entre turnos: el backend vuelve a validar horario de atención, bloqueos, pasado y ventana de reserva (RN-DIS-06) al reservar, cargar o reprogramar un turno, y rechaza con 422 si alguna falla. **(derivada)**
- **RN-DIS-06**: En la reserva pública solo se ofrecen y se aceptan horarios con al menos 2 h de anticipación y hasta 60 días hacia adelante (valores configurables: `APP_TURNO_ANTICIPACION_MIN_HORAS`, `APP_TURNO_VENTANA_MAX_DIAS`). Fuera de esa ventana el backend responde 422. Cuando el odontólogo carga o reprograma, rige solo la regla de no pasado (RN-DIS-04). **(derivada, aprobada)**

## Dominio: Turnos (RN-TUR)

- **RN-TUR-01**: Los estados son `PENDIENTE`, `CONFIRMADO`, `CANCELADO`, `ASISTIO`, `AUSENTE` (transiciones en [04](04_modelo_de_datos.md)).
- **RN-TUR-02**: No puede haber dos turnos activos (pendiente o confirmado) que se superpongan. Lo garantiza una restricción de exclusión en PostgreSQL, no solo el código. Los rangos son **semiabiertos** `[inicio, fin)`: un turno que empieza justo cuando termina otro no se solapa con él (el de 09:00-09:30 y el de 09:30-10:00 son compatibles).
- **RN-TUR-03**: Si dos pacientes reservan el mismo horario a la vez, gana el primero que se guarda; el segundo recibe 409 Conflict: "ese horario se acaba de ocupar, elegí otro".
- **RN-TUR-04**: Reservar ocupa el horario: el turno nace `PENDIENTE` (o `CONFIRMADO` si faltan menos de 24 h, RN-TUR-11) y ese horario deja de ofrecerse. Confirmar no reserva nada: solo informa que el paciente asistirá.
- **RN-TUR-05**: Un turno sin confirmar queda `PENDIENTE` y se mantiene; se marca "sin confirmar" en la agenda. No se libera automáticamente.
- **RN-TUR-06**: Un turno cancelado registra quién canceló (`PACIENTE` u `ODONTOLOGO`) y deja de ocupar el horario.
- **RN-TUR-07**: Solo el odontólogo marca `ASISTIO` o `AUSENTE`, sobre turnos activos y solo desde la hora de inicio del turno; antes de eso el sistema lo rechaza con 422 (el estado es terminal). **(derivada, aprobada)**
- **RN-TUR-08**: El odontólogo puede reprogramar un turno activo a otro horario libre; se aplican las mismas validaciones y la restricción de exclusión. El estado se recalcula según la anticipación del nuevo inicio, con la misma regla que al crear (RN-TUR-11): a 24 h o más el turno queda (o vuelve a) `PENDIENTE`, se reinician `recordatorio_enviado` y `alerta_pendiente_enviada` a `false` y se envía el Mail 3 con solo el link de cancelar; a menos de 24 h queda `CONFIRMADO`, ambos flags quedan en `true` y se envía el aviso de turno confirmado con el nuevo horario, sin links (RN-NOT-03, RN-NOT-07). **(derivada, resuelta)**
- **RN-TUR-09**: El odontólogo puede cargar turnos manualmente, para un paciente existente o nuevo, y nacen con la misma regla de estado inicial que los online (RN-TUR-11): a menos de 24 h nacen `CONFIRMADO`, con el mail de aviso sin links. Ingresa el email y el teléfono de contacto del turno; si el paciente ya tiene turnos, se precargan desde su **último turno** y el odontólogo puede editarlos. No permite cargar fuera del horario de atención, sobre un bloqueo ni en el pasado (RN-DIS-05); no rige el mínimo de 2 h de la reserva pública. Sin `consentimiento_at`. **(derivada, resuelta)**
- **RN-TUR-10**: El odontólogo puede confirmar un turno `PENDIENTE` desde la agenda (`PENDIENTE` a `CONFIRMADO`), por ejemplo después de hablar con el paciente. Es la misma transición que la confirmación por token del paciente, pero sin la restricción de 48 h (RN-NOT-08). **(derivada, resuelta)**
- **RN-TUR-11**: Estado inicial según la anticipación al crear el turno (reserva pública o carga manual): con 24 h o más nace `PENDIENTE`; con menos de 24 h nace directamente `CONFIRMADO` (la reserva pública exige al menos 2 h, RN-DIS-06). Un turno que nace `CONFIRMADO` no dispara la alerta al odontólogo (RN-NOT-06) ni recordatorio (RN-NOT-04). **(nueva, definida por el usuario)**

## Dominio: Cancelación (RN-CAN)

- **RN-CAN-01**: El paciente puede cancelar desde el link del email solo un turno `PENDIENTE` y hasta 24 h antes del inicio. **Confirmar es definitivo para el paciente**: sobre un turno `CONFIRMADO` el link de cancelar responde 422 `TRANSICION_INVALIDA` ("Tu turno ya está confirmado. Para cancelarlo comunicate con el consultorio.") y la cancelación la hace el odontólogo (RN-CAN-03). Cancelar un turno ya `CANCELADO` sigue siendo idempotente (200).
- **RN-CAN-02**: Con menos de 24 h de anticipación, el sistema rechaza la cancelación del paciente de un turno `PENDIENTE` (422 `CANCELACION_FUERA_DE_PLAZO`) y le indica que se comunique con el consultorio. Por eso el mail de un turno que nace `CONFIRMADO` a menos de 24 h no trae link de cancelar (RN-NOT-01).
- **RN-CAN-03**: El odontólogo puede cancelar desde la agenda en cualquier momento (`cancelado_por = ODONTOLOGO`).
- **RN-CAN-04**: Cuando el odontólogo cancela un turno (manual o por bloqueo), se envía un email de aviso al email del turno. **(derivada, confirmado por el usuario)**
- **RN-CAN-05**: Solo se cancelan turnos activos; cancelar uno ya terminal es un error de estado (422). **(derivada)**

## Dominio: Bloqueos (RN-BLO)

- **RN-BLO-01**: El odontólogo puede crear bloqueos (rango de fecha/hora con motivo) en los que no se ofrecen horarios.
- **RN-BLO-02**: Si el rango del bloqueo contiene turnos activos, el sistema le muestra cuáles son (vista previa) antes de guardar.
- **RN-BLO-03**: Si el odontólogo confirma, esos turnos se cancelan (`cancelado_por = ODONTOLOGO`) y se envía un email al email de cada turno afectado invitando a reservar de nuevo.
- **RN-BLO-04**: Crear el bloqueo y cancelar los turnos afectados ocurre en una sola transacción; los emails se envían después del commit. **(derivada)**
- **RN-BLO-05**: Sin turnos afectados, el bloqueo se crea directo. **(derivada)**

## Dominio: Pacientes (RN-PAC)

- **RN-PAC-01**: El paciente se identifica por DNI (único). Si vuelve a reservar con el mismo DNI, el turno nuevo se asocia al paciente existente, sin duplicarlo. Reservar con un DNI existente no modifica los datos del paciente (ver RN-PAC-04).
- **RN-PAC-02**: Datos mínimos del paciente: nombre, apellido y DNI. El email y el teléfono se piden en cada turno y se guardan en el turno (`email_contacto`, `telefono_contacto`), no en el paciente. No se piden más datos (Ley 25.326: datos indispensables).
- **RN-PAC-03**: Dos personas que comparten teléfono o email son pacientes distintos si el DNI es distinto: el contacto no identifica al paciente, solo es el medio de aviso de cada turno.
- **RN-PAC-04**: Si al reservar con un DNI existente el nombre o apellido difieren, no se modifican los datos del paciente. El email y el teléfono ingresados se guardan en el turno y se usan para los avisos de ese turno. Los datos del paciente solo los edita el odontólogo desde el ABM. **(derivada, resuelta)**

## Dominio: Tokens (RN-TOK)

- **RN-TOK-01**: Cada turno tiene un token aleatorio único, generado por el backend al crear el turno (SecureRandom, 32 bytes o más, o UUID v4 como mínimo) y guardado con el turno.
- **RN-TOK-02**: El token permite confirmar o cancelar solo ese turno, sin login. No es un ID secuencial, así que no se puede adivinar el turno de otra persona.
- **RN-TOK-03**: Los links de los emails apuntan al front (ej. `/turno/confirmar?token=...`), que llama a la API.
- **RN-TOK-04**: Un token inexistente responde 404 sin revelar datos. Confirmar o cancelar es idempotente respecto del estado ya alcanzado (el paciente no puede cancelar un `CONFIRMADO`, RN-CAN-01). **(derivada)**
- **RN-TOK-05**: Confirmar por token solo aplica a `PENDIENTE`, antes del inicio y dentro de las 48 h previas (RN-NOT-08). **(derivada)**

## Dominio: Notificaciones (RN-NOT)

- **RN-NOT-01**: Al crear un turno (reserva pública o carga manual) se envía **un solo** email al email del turno, según la anticipación (RN-TUR-11): con **más de 48 h** (turno `PENDIENTE`), "Confirmación de reserva" con los datos del turno y **solo el botón Cancelar** (sin Confirmar: el paciente todavía no sabe si podrá ir); entre **24 h y 48 h** (turno `PENDIENTE`), directamente el "Recordatorio" con Confirmar y Cancelar, enviado en el momento de crear el turno (RN-NOT-04); con **menos de 24 h** (turno `CONFIRMADO`), "Aviso" con los datos del turno y **sin ningún botón** (ya no puede cancelar; si necesita hacerlo, se comunica con el consultorio).
- **RN-NOT-02**: El "Recordatorio" se envía solo a turnos **`PENDIENTE`** (nunca a los `CONFIRMADO`), al email del turno, y es el **único** email que trae el botón **Confirmar**, junto con Cancelar (el paciente elige una: si confirma, ya no puede cancelar, RN-CAN-01). Sale por dos vías: (a) el job, 48 h antes, para los turnos `PENDIENTE` con inicio a 48 h o menos y `recordatorio_enviado = false`; (b) en el momento de crear un turno entre 24 h y 48 h (RN-NOT-04).
- **RN-NOT-03**: El recordatorio se envía una sola vez por cada fecha y horario programados del turno (flag `recordatorio_enviado`). Al reprogramar, si el nuevo inicio está a 24 h o más se reinicia `recordatorio_enviado` a `false` y el recordatorio sale al entrar en la ventana de 48 h (o en el siguiente ciclo del job si ya está dentro); si queda a menos de 24 h el turno pasa a `CONFIRMADO` y el flag queda en `true` (RN-TUR-08).
- **RN-NOT-04**: Si el turno se crea (reserva pública o carga manual) entre 24 h y 48 h de anticipación, nace `PENDIENTE` y se envía el Recordatorio **en el momento de crear** (no la Confirmación de reserva ni se espera al job); tras el envío se marca `recordatorio_enviado = true`. Si el envío falla, queda en `false` y el job lo reintenta en el siguiente ciclo (RN-NOT-05). Los turnos que nacen `CONFIRMADO` (menos de 24 h) se crean con `recordatorio_enviado = true` y no reciben recordatorio. **(derivada, resuelta)**
- **RN-NOT-05**: Un fallo de envío de email no revierte la reserva, la reprogramación ni la cancelación: se registra en log y, para el recordatorio y la alerta al odontólogo, el flag no se marca para reintentar en el próximo ciclo. **(derivada)**
- **RN-NOT-06**: Alerta al odontólogo, mail "Turno no confirmado". Cada turno `PENDIENTE` cuyo inicio llega a menos de 24 h (a las 24 h antes sigue sin confirmar ni cancelar) dispara **un** email **al odontólogo** (a su email de login) con paciente, prestación, horario y teléfono de contacto del turno, para que llame al paciente; se marca `alerta_pendiente_enviada = true` y no se repite. El odontólogo decide confirmar (RN-TUR-10) o cancelar (RN-CAN-03). Además la agenda resalta esos turnos con el teléfono y botones Confirmar / Cancelar. Si se reprograma el turno, `alerta_pendiente_enviada` se reinicia (queda en `true` si pasa a `CONFIRMADO`). Un turno creado con menos de 24 h nace `CONFIRMADO` (RN-TUR-11) y **no** dispara la alerta; solo la disparan los que siguen `PENDIENTE` al llegar a menos de 24 h. **(derivada, resuelta)**
- **RN-NOT-07**: Al reprogramar un turno se envía un email al email del turno con el nuevo horario (Mail 3). Si el nuevo inicio está a 24 h o más (turno `PENDIENTE`), trae **solo el link de cancelar** (sin Confirmar); si está a menos de 24 h (turno `CONFIRMADO`), es un aviso de turno confirmado con el nuevo horario, **sin links**. **(derivada, aprobada)**
- **RN-NOT-08**: El paciente solo puede confirmar dentro de las 48 h previas al inicio: el endpoint público de confirmar por token responde 422 si faltan más de 48 h. Así el link de Confirmar solo existe en el recordatorio y funciona desde que ese mail sale. La confirmación del odontólogo desde la agenda (RN-TUR-10) no tiene esta restricción. **(nueva, definida por el usuario)**

### Mails del sistema

| # | Mail | Destinatario | Cuándo sale | Botones que incluye |
|---|------|--------------|-------------|---------------------|
| 1 | Confirmación de reserva | Paciente (email del turno) | Al crear el turno con más de 48 h de anticipación (turno `PENDIENTE`) | Solo Cancelar |
| 1b | Aviso | Paciente (email del turno) | Al crear el turno con menos de 24 h (turno `CONFIRMADO`), con los datos del turno | Ninguno (para cancelar, contactar al consultorio) |
| 2 | Recordatorio | Paciente (email del turno) | Solo a turnos `PENDIENTE`: en el momento de crear el turno si se crea entre 24 h y 48 h antes; o por el job 48 h antes si se creó con más de 48 h (el job también reintenta envíos fallidos) | Confirmar y Cancelar |
| 3 | Reprogramación | Paciente (email del turno) | Al reprogramar el odontólogo. A 24 h o más (turno `PENDIENTE`) | Solo Cancelar |
| 3b | Reprogramación a menos de 24 h | Paciente (email del turno) | Al reprogramar el odontólogo a menos de 24 h (turno `CONFIRMADO`): aviso con el nuevo horario | Ninguno (para cancelar, contactar al consultorio) |
| 4 | Cancelación por el odontólogo | Paciente (email del turno) | Al cancelar el odontólogo desde la agenda | Ninguno |
| 5 | Cancelación por bloqueo | Paciente (email del turno) | Al confirmar un bloqueo con turnos afectados | Ninguno (invita a reservar de nuevo) |
| 6 | Turno no confirmado | Odontólogo (su email de login) | Cuando a 24 h del inicio el turno sigue `PENDIENTE`; lleva paciente, prestación, horario y teléfono del turno para que llame al paciente | Ninguno |

Los mails 1b y 3b son avisos sin botones (RN-NOT-01, RN-NOT-07). El Recordatorio es el único mail con Confirmar (RN-NOT-02).

## Dominio: Datos personales (RN-DAT)

- **RN-DAT-01**: Ley 25.326 aplica aunque sea académico: solo datos indispensables, contraseñas hasheadas, tokens no adivinables, y no loguear datos personales innecesarios.
- **RN-DAT-02**: La reserva pública exige que el paciente acepte el tratamiento de sus datos personales (casilla obligatoria con texto breve de finalidad). Sin aceptación no se crea el turno (422). Se guarda el instante de aceptación en el turno (`consentimiento_at`). Los turnos cargados por el odontólogo no llevan `consentimiento_at` (null). Ley 25.326.

## Dominio: Recursos (RN-REC)

- **RN-REC-01**: En la v1 hay un único profesional y un único sillón. El sillón es el recurso de la agenda y no puede haber dos turnos activos simultáneos. Multisillón y multiprofesional quedan fuera de la v1.

## Dominio: Excepciones globales

- **RN-GLO-01**: Todos los errores de API usan un JSON uniforme (ver [08](08_arquitectura_propuesta.md)).
- **RN-GLO-02**: Violación de la restricción de exclusión (`SQLSTATE 23P01` / `DataIntegrityViolationException`) se traduce a 409 con "ese horario se acaba de ocupar, elegí otro".
- **RN-GLO-03**: Todos los instantes se manejan en `America/Argentina/Buenos_Aires` para presentación y cálculo de días/horarios; almacenamiento en `timestamptz`.
- **RN-GLO-04**: Códigos HTTP unificados: 400 request mal formado o validación de formato, 401 no autenticado, 404 recurso o token inexistente, 409 conflicto de horario (solapamiento, incluida la violación de la constraint EXCLUDE), 422 violación de regla de negocio. Cada caso lleva un único código, sin ambigüedades del tipo "400/422" o "409/422". Tabla completa en [08](08_arquitectura_propuesta.md#códigos-http-unificados). **(derivada)**
