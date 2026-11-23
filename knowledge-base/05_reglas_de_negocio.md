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
- **RN-DIS-05**: La disponibilidad es solo informativa; la garantía final de no solapamiento es la restricción de la base (RN-TUR-02).

## Dominio: Turnos (RN-TUR)

- **RN-TUR-01**: Los estados son `PENDIENTE`, `CONFIRMADO`, `CANCELADO`, `ASISTIO`, `AUSENTE` (transiciones en [04](04_modelo_de_datos.md)).
- **RN-TUR-02**: No puede haber dos turnos activos (pendiente o confirmado) que se superpongan. Lo garantiza una restricción de exclusión en PostgreSQL, no solo el código.
- **RN-TUR-03**: Si dos pacientes reservan el mismo horario a la vez, gana el primero que se guarda; el segundo recibe 409 Conflict: "ese horario se acaba de ocupar, elegí otro".
- **RN-TUR-04**: Reservar ocupa el horario: el turno nace `PENDIENTE` y ese horario deja de ofrecerse. Confirmar no reserva nada: solo informa que el paciente asistirá.
- **RN-TUR-05**: Un turno sin confirmar queda `PENDIENTE` y se mantiene; se marca "sin confirmar" en la agenda. No se libera automáticamente.
- **RN-TUR-06**: Un turno cancelado registra quién canceló (`PACIENTE` o `CONSULTORIO`) y deja de ocupar el horario.
- **RN-TUR-07**: Solo el odontólogo marca `ASISTIO` o `AUSENTE`, sobre turnos activos. **(derivada)**
- **RN-TUR-08**: El odontólogo puede reprogramar un turno activo a otro horario libre; se aplican las mismas validaciones y la restricción de exclusión. **(derivada)**
- **RN-TUR-09**: El odontólogo puede cargar turnos manualmente, para un paciente existente o nuevo, y nacen igual que los online (`PENDIENTE`). **(derivada)** La confirmación del odontólogo desde la agenda es una decisión abierta, ver [10](10_preguntas_abiertas.md).

## Dominio: Cancelación (RN-CAN)

- **RN-CAN-01**: El paciente puede cancelar desde el link del email hasta 24 h antes del inicio del turno.
- **RN-CAN-02**: Con menos de 24 h de anticipación, el sistema rechaza la cancelación del paciente (422) y le indica que se comunique con el consultorio.
- **RN-CAN-03**: El odontólogo puede cancelar desde la agenda en cualquier momento (`cancelado_por = CONSULTORIO`).
- **RN-CAN-04**: Cuando el consultorio cancela un turno (manual o por bloqueo), se envía un email de aviso al paciente. **(derivada, confirmado por el usuario)**
- **RN-CAN-05**: Solo se cancelan turnos activos; cancelar uno ya terminal es un error de estado (409/422). **(derivada)**

## Dominio: Bloqueos (RN-BLO)

- **RN-BLO-01**: El odontólogo puede crear bloqueos (rango de fecha/hora con motivo) en los que no se ofrecen horarios.
- **RN-BLO-02**: Si el rango del bloqueo contiene turnos activos, el sistema le muestra cuáles son (vista previa) antes de guardar.
- **RN-BLO-03**: Si el odontólogo confirma, esos turnos se cancelan (cancelados por el consultorio) y se envía un email a cada paciente invitándolo a reservar de nuevo.
- **RN-BLO-04**: Crear el bloqueo y cancelar los turnos afectados ocurre en una sola transacción; los emails se envían después del commit. **(derivada)**
- **RN-BLO-05**: Sin turnos afectados, el bloqueo se crea directo. **(derivada)**

## Dominio: Pacientes (RN-PAC)

- **RN-PAC-01**: El paciente se identifica por DNI. Si vuelve a reservar con el mismo DNI, el turno nuevo se asocia al paciente existente, sin duplicarlo.
- **RN-PAC-02**: Datos mínimos: nombre, apellido, DNI, email y teléfono. No se piden más (Ley 25.326: datos indispensables).
- **RN-PAC-03**: Dos personas que comparten teléfono o email son pacientes distintos si el DNI es distinto.
- **RN-PAC-04**: Si al reservar con un DNI existente los datos de contacto difieren, se actualizan con los nuevos. **(derivada, ver [10](10_preguntas_abiertas.md))**

## Dominio: Tokens (RN-TOK)

- **RN-TOK-01**: Cada turno tiene un token aleatorio único, generado por el backend al crear el turno (SecureRandom, 32 bytes o más, o UUID v4 como mínimo) y guardado con el turno.
- **RN-TOK-02**: El token permite confirmar o cancelar solo ese turno, sin login. No es un ID secuencial, así que no se puede adivinar el turno de otra persona.
- **RN-TOK-03**: Los links de los emails apuntan al front (ej. `/turno/confirmar?token=...`), que llama a la API.
- **RN-TOK-04**: Un token inexistente responde 404 sin revelar datos. Confirmar o cancelar es idempotente respecto del estado ya alcanzado. **(derivada)**
- **RN-TOK-05**: Confirmar solo aplica a `PENDIENTE` y antes del inicio del turno. **(derivada)**

## Dominio: Notificaciones (RN-NOT)

- **RN-NOT-01**: Al reservar se envía email de confirmación de la reserva, con links de confirmar y cancelar.
- **RN-NOT-02**: Se envía un recordatorio por email 48 h antes de cada turno `PENDIENTE` o `CONFIRMADO`, con los mismos links.
- **RN-NOT-03**: El recordatorio se envía una sola vez por turno (flag `recordatorio_enviado`); no se reenvía.
- **RN-NOT-04**: Si el turno se reserva con menos de 48 h de anticipación, el recordatorio igual se envía en el próximo ciclo del job (la ventana es "empieza en las próximas 48 h"); en la práctica el paciente recibe confirmación y recordatorio seguidos. **(derivada, ver [10](10_preguntas_abiertas.md))**
- **RN-NOT-05**: Un fallo de envío de email no revierte la reserva ni la cancelación: se registra en log y, para el recordatorio, el flag no se marca para reintentar en el próximo ciclo. **(derivada)**

## Dominio: Datos personales (RN-DAT)

- **RN-DAT-01**: Ley 25.326 aplica aunque sea académico: solo datos indispensables, contraseñas hasheadas, tokens no adivinables, y no loguear datos personales innecesarios.

## Dominio: Excepciones globales

- **RN-GLO-01**: Todos los errores de API usan un JSON uniforme (ver [08](08_arquitectura_propuesta.md)).
- **RN-GLO-02**: Violación de la restricción de exclusión (`SQLSTATE 23P01` / `DataIntegrityViolationException`) se traduce a 409 con "ese horario se acaba de ocupar, elegí otro".
- **RN-GLO-03**: Todos los instantes se manejan en `America/Argentina/Buenos_Aires` para presentación y cálculo de días/horarios; almacenamiento en `timestamptz`.
