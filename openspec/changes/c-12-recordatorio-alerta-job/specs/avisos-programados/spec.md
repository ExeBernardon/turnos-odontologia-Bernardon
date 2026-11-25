# Spec Delta

## Purpose

Define los avisos que el sistema emite solo por el paso del tiempo, sin que nadie haga nada: el Recordatorio al paciente cuando un turno `PENDIENTE` entra en la ventana de 48 h y la alerta "Turno no confirmado" al odontólogo cuando sigue `PENDIENTE` a menos de 24 h, con reintento y sin repetir envíos.

## ADDED Requirements

### Requirement: Ciclo periódico con dos tareas
El sistema SHALL ejecutar un ciclo de avisos programados cada `APP_RECORDATORIO_INTERVALO_MIN` minutos (15 por defecto), contados desde el fin del ciclo anterior. Cada ciclo SHALL ejecutar primero la tarea de recordatorios y después la de alertas, tomando un único instante "ahora" para ambas. Un ciclo sin turnos elegibles SHALL NOT enviar ningún mail ni modificar turnos.

#### Scenario: Ciclo sin turnos elegibles
- **WHEN** corre el ciclo y no hay turnos `PENDIENTE` con inicio en las próximas 48 h
- **THEN** no se envía ningún mail y ningún turno cambia

#### Scenario: Las dos tareas en el mismo ciclo
- **WHEN** corre el ciclo y hay un turno `PENDIENTE` que empieza en 30 h sin recordatorio y otro `PENDIENTE` que empieza en 10 h con recordatorio enviado y sin alerta
- **THEN** en ese mismo ciclo el primero recibe el Recordatorio y por el segundo el odontólogo recibe el "Turno no confirmado"

### Requirement: Selección de turnos para el recordatorio
La tarea de recordatorios SHALL elegir solo los turnos en estado `PENDIENTE`, con `recordatorio_enviado = false` y con inicio posterior a ahora y a `APP_RECORDATORIO_HORAS_ANTES` horas (48 por defecto) o menos de ahora; el borde de 48 h SHALL entrar. Turnos en cualquier otro estado o ya empezados SHALL NOT recibir el Recordatorio.

#### Scenario: Borde exacto de 48 h
- **WHEN** corre el ciclo y un turno `PENDIENTE` sin recordatorio empieza exactamente dentro de 48 h
- **THEN** el turno recibe el Recordatorio

#### Scenario: Fuera de la ventana
- **WHEN** corre el ciclo y un turno `PENDIENTE` sin recordatorio empieza dentro de 48 h y 1 minuto
- **THEN** el turno no recibe el Recordatorio y su flag sigue en `false`

#### Scenario: Turno confirmado
- **WHEN** corre el ciclo y un turno `CONFIRMADO` empieza dentro de 30 h
- **THEN** el turno no recibe el Recordatorio

#### Scenario: Turno cancelado
- **WHEN** corre el ciclo y un turno `CANCELADO` con `recordatorio_enviado = false` empieza dentro de 30 h
- **THEN** el turno no recibe el Recordatorio

#### Scenario: Turno ya empezado
- **WHEN** corre el ciclo y un turno `PENDIENTE` sin recordatorio empezó hace 10 minutos
- **THEN** el turno no recibe el Recordatorio

### Requirement: Recordatorio al email del turno y marca tras enviar
El Recordatorio de un turno elegido SHALL enviarse al `email_contacto` del turno, con los links de Confirmar y Cancelar. Si el envío sale, el sistema SHALL marcar `recordatorio_enviado = true` y ese turno SHALL NOT volver a recibirlo mientras no cambie su horario. Si el envío falla, el flag SHALL quedar en `false` para que el ciclo siguiente lo reintente.

#### Scenario: Envío exitoso marca el flag
- **WHEN** corre el ciclo y el Recordatorio de un turno `PENDIENTE` que empieza en 40 h sale bien
- **THEN** el turno queda con `recordatorio_enviado = true`

#### Scenario: Idempotencia entre ciclos
- **WHEN** corre un segundo ciclo después de uno que envió el Recordatorio de un turno
- **THEN** ese turno no recibe un segundo Recordatorio

#### Scenario: Reintento tras un fallo
- **WHEN** el Recordatorio de un turno falla en un ciclo y en el ciclo siguiente el envío sale bien
- **THEN** el turno recibe el Recordatorio en el segundo ciclo y recién ahí queda con `recordatorio_enviado = true`

#### Scenario: Reintento del recordatorio fallido al crear
- **WHEN** corre el ciclo y un turno creado entre 24 h y 48 h quedó `PENDIENTE` con `recordatorio_enviado = false` porque el envío al crearlo falló
- **THEN** el turno recibe el Recordatorio en ese ciclo

### Requirement: Selección de turnos para la alerta al odontólogo
La tarea de alertas SHALL elegir solo los turnos en estado `PENDIENTE`, con `alerta_pendiente_enviada = false` y con inicio posterior a ahora y a menos de `APP_ALERTA_PENDIENTE_HORAS` horas (24 por defecto) de ahora; el borde de 24 h SHALL NOT entrar. Los turnos `CONFIRMADO`, incluidos los que nacieron así por crearse a menos de 24 h, SHALL NOT disparar la alerta.

#### Scenario: Borde exacto de 24 h
- **WHEN** corre el ciclo y un turno `PENDIENTE` sin alerta empieza exactamente dentro de 24 h
- **THEN** no se envía la alerta por ese turno

#### Scenario: Apenas dentro de las 24 h
- **WHEN** corre el ciclo y un turno `PENDIENTE` sin alerta empieza dentro de 23 h y 59 minutos
- **THEN** el odontólogo recibe el "Turno no confirmado" por ese turno

#### Scenario: Turno nacido confirmado
- **WHEN** corre el ciclo y un turno creado a menos de 24 h (nació `CONFIRMADO` con ambos flags en `true`) empieza dentro de 5 h
- **THEN** no se envía la alerta ni el Recordatorio por ese turno

#### Scenario: Turno confirmado después de la reserva
- **WHEN** corre el ciclo y un turno que el paciente confirmó empieza dentro de 10 h con `alerta_pendiente_enviada = false`
- **THEN** no se envía la alerta por ese turno

### Requirement: Alerta al odontólogo y marca tras enviar
La alerta de un turno elegido SHALL enviarse al email de login del odontólogo, nunca al paciente. Si el envío sale, el sistema SHALL marcar `alerta_pendiente_enviada = true` y la alerta SHALL NOT repetirse para ese turno mientras no cambie su horario. Si el envío falla, el flag SHALL quedar en `false` para reintentar en el ciclo siguiente.

#### Scenario: Envío exitoso marca el flag
- **WHEN** corre el ciclo y la alerta de un turno `PENDIENTE` que empieza en 12 h sale bien
- **THEN** el turno queda con `alerta_pendiente_enviada = true` y el mail lo recibe solo el email del odontólogo

#### Scenario: La alerta no se repite
- **WHEN** corre un segundo ciclo después de uno que envió la alerta de un turno que sigue `PENDIENTE`
- **THEN** no se envía una segunda alerta por ese turno

#### Scenario: Reintento de la alerta
- **WHEN** la alerta de un turno falla en un ciclo y sale bien en el siguiente
- **THEN** el turno queda con `alerta_pendiente_enviada = true` recién en el segundo ciclo

### Requirement: Un fallo no frena el lote
Un fallo al avisar por un turno (el servidor SMTP no responde, rechaza el mensaje o cualquier error inesperado al procesar ese turno) SHALL NOT impedir que el mismo ciclo procese los turnos restantes de ambas tareas, SHALL NOT deshacer las marcas ya hechas en ese ciclo y SHALL quedar registrado en el log con el id del turno, sin email, teléfono, DNI, nombre ni token.

#### Scenario: Lote que sigue después de un error
- **WHEN** corre el ciclo con tres turnos elegibles para el Recordatorio y el envío del segundo falla
- **THEN** el primero y el tercero quedan con `recordatorio_enviado = true` y el segundo queda en `false`

#### Scenario: Error de una tarea no frena la otra
- **WHEN** en un ciclo fallan todos los envíos de Recordatorio y hay un turno elegible para la alerta
- **THEN** la tarea de alertas igual se ejecuta y envía la alerta de ese turno

#### Scenario: Log del fallo sin datos personales
- **WHEN** falla el aviso de un turno con datos ficticios durante el ciclo
- **THEN** el log registra el id del turno y ninguna línea contiene su email, teléfono, DNI, nombre ni token

### Requirement: Cambios concurrentes del turno no se pisan
Si mientras se avisa un turno este se confirma, se cancela o se reprograma, el ciclo SHALL NOT revertir ese cambio: el estado, el horario y los flags que dejó la otra operación SHALL prevalecer, y el flag del aviso SHALL marcarse solo si el turno sigue `PENDIENTE` con el mismo inicio que se avisó.

#### Scenario: Reprogramado durante el envío
- **WHEN** el ciclo envía el Recordatorio de un turno y, antes de marcarlo, el odontólogo lo reprograma a un inicio dentro de 5 días
- **THEN** el turno queda con el nuevo inicio y `recordatorio_enviado = false`, y recibirá el Recordatorio del nuevo horario al entrar en la ventana

#### Scenario: Confirmado durante el envío
- **WHEN** el ciclo envía la alerta de un turno y, antes de marcarla, el paciente u odontólogo lo confirma
- **THEN** el turno queda `CONFIRMADO` y el ciclo no lo vuelve a `PENDIENTE`

### Requirement: La reprogramación reinicia los avisos programados
Un turno reprogramado a 24 h o más del nuevo inicio SHALL volver a ser elegible para el Recordatorio y la alerta según su nuevo horario, aunque los haya recibido para el horario anterior. Un turno reprogramado a menos de 24 h SHALL quedar fuera de ambos avisos.

#### Scenario: Reprogramado a más de 48 h
- **WHEN** un turno que ya recibió Recordatorio y alerta se reprograma a un inicio dentro de 72 h y corren ciclos hasta que faltan 47 h
- **THEN** recibe un único Recordatorio nuevo cuando el inicio queda dentro de las 48 h y ninguno antes

#### Scenario: Reprogramado a menos de 24 h
- **WHEN** un turno `PENDIENTE` se reprograma a un inicio dentro de 6 h y corre el ciclo
- **THEN** el turno queda `CONFIRMADO` y no recibe Recordatorio ni alerta

### Requirement: Umbrales configurables
La ventana del Recordatorio SHALL tomarse de `APP_RECORDATORIO_HORAS_ANTES`, el umbral de la alerta de `APP_ALERTA_PENDIENTE_HORAS` y la frecuencia de `APP_RECORDATORIO_INTERVALO_MIN`, sin cambiar código.

#### Scenario: Ventana del recordatorio configurada
- **WHEN** `APP_RECORDATORIO_HORAS_ANTES` vale 72 y corre el ciclo con un turno `PENDIENTE` sin recordatorio que empieza en 60 h
- **THEN** el turno recibe el Recordatorio
