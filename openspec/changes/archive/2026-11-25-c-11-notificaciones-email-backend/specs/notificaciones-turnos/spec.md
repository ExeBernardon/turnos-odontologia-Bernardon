# Spec Delta

## ADDED Requirements

### Requirement: Envío real de cada aviso por email
El backend SHALL enviar por SMTP un email por cada aviso que pide un dominio, uno por cada fila de la tabla "Mails del sistema", sin que cambien quienes lo invocan. Cada aviso SHALL informar a quien lo invoca si el email salió o no.

#### Scenario: Aviso enviado
- **WHEN** un dominio pide el aviso de confirmación de reserva de un turno y el servidor SMTP acepta el mensaje
- **THEN** el servidor SMTP recibe un email para ese turno y el aviso informa que salió

#### Scenario: Cobertura de los ocho mails
- **WHEN** se pide cada uno de los ocho avisos para un turno con datos ficticios
- **THEN** el servidor SMTP recibe ocho emails, cada uno con un asunto distinto que identifica el tipo de mail

### Requirement: Destinatario de cada mail
Los siete mails al paciente SHALL enviarse solo al `email_contacto` del turno. El mail "Turno no confirmado" SHALL enviarse solo al email de login del odontólogo y nunca al paciente.

#### Scenario: Mail al paciente con contacto distinto de otros turnos
- **WHEN** se pide el recordatorio de un turno cuyo `email_contacto` es `paciente.turno@ejemplo.com` y el mismo paciente tiene otro turno con otro email
- **THEN** el único destinatario es `paciente.turno@ejemplo.com`

#### Scenario: Turno no confirmado al odontólogo
- **WHEN** se pide el aviso "Turno no confirmado" de un turno y el odontólogo inicia sesión con `odontologo@ejemplo.com`
- **THEN** el único destinatario es `odontologo@ejemplo.com` y el `email_contacto` del turno no recibe nada

#### Scenario: Sin odontólogo registrado
- **WHEN** se pide el aviso "Turno no confirmado" y no hay ningún odontólogo en la base
- **THEN** no se envía ningún email, el aviso informa que no salió y no se lanza ninguna excepción

### Requirement: Botones de cada mail
Cada mail SHALL incluir exactamente los botones de la tabla "Mails del sistema": Confirmación de reserva y Reprogramación, solo Cancelar; Recordatorio, Confirmar y Cancelar; Aviso, Reprogramación a menos de 24 h, Cancelación por el odontólogo, Cancelación por bloqueo y Turno no confirmado, ninguno. El Recordatorio SHALL ser el único con Confirmar.

#### Scenario: Recordatorio con Confirmar y Cancelar
- **WHEN** se envía el recordatorio de un turno
- **THEN** el email contiene un link de confirmar y un link de cancelar con el token del turno

#### Scenario: Confirmación de reserva solo con Cancelar
- **WHEN** se envía la confirmación de reserva o la reprogramación de un turno
- **THEN** el email contiene el link de cancelar con el token y no contiene ningún link de confirmar

#### Scenario: Mails sin botones
- **WHEN** se envía el aviso, la reprogramación a menos de 24 h, la cancelación por el odontólogo, la cancelación por bloqueo o el turno no confirmado
- **THEN** el email no contiene ningún link de confirmar ni de cancelar ni el token del turno

### Requirement: Links al front con el token
Los links de confirmar y cancelar SHALL apuntar al front, construidos con la URL base configurada en `APP_FRONTEND_BASE_URL`, con las rutas `/turno/confirmar` y `/turno/cancelar` y el token del turno en el parámetro `token`.

#### Scenario: Link de cancelar
- **WHEN** `APP_FRONTEND_BASE_URL` es `http://localhost:8080` y se envía la confirmación de reserva de un turno con token `abc123`
- **THEN** el link de cancelar es `http://localhost:8080/turno/cancelar?token=abc123`

#### Scenario: URL base con barra final
- **WHEN** `APP_FRONTEND_BASE_URL` termina en `/` y se envía el recordatorio
- **THEN** el link de confirmar no tiene doble barra antes de `turno/confirmar`

### Requirement: Contenido de los mails
Cada mail SHALL incluir el nombre del paciente, la prestación y la fecha y hora de inicio del turno en `America/Argentina/Buenos_Aires`. El "Turno no confirmado" SHALL incluir además el teléfono de contacto del turno. Los mails sin botones al paciente SHALL indicar el contacto del consultorio. La Cancelación por bloqueo SHALL invitar a reservar un nuevo turno.

#### Scenario: Hora en Buenos Aires
- **WHEN** se envía la confirmación de reserva de un turno que empieza a las `2026-12-10T13:00:00Z`
- **THEN** el email muestra la hora `10:00` y la fecha del 10 de diciembre de 2026

#### Scenario: Teléfono en el turno no confirmado
- **WHEN** se envía el turno no confirmado de un turno con teléfono de contacto `1155555555`
- **THEN** el email muestra ese teléfono, el nombre del paciente, la prestación y el horario

#### Scenario: Cancelación por bloqueo invita a reservar
- **WHEN** se envía la cancelación por bloqueo de un turno
- **THEN** el email invita a reservar un nuevo turno desde la reserva pública y no trae links con token

#### Scenario: Datos del paciente escapados
- **WHEN** el nombre del paciente contiene caracteres HTML como `<b>`
- **THEN** el email los muestra como texto y no como marcado

### Requirement: Remitente configurable
Todos los mails SHALL salir con el remitente configurado en `APP_MAIL_FROM`.

#### Scenario: Remitente por defecto
- **WHEN** `APP_MAIL_FROM` vale `turnos@consultorio.local` y se envía cualquier aviso
- **THEN** el remitente del email es `turnos@consultorio.local`

### Requirement: Fallo de envío sin propagación
Si el servidor SMTP no responde, rechaza el mensaje o el mail no se puede armar, el aviso SHALL informar que no salió, SHALL NOT lanzar una excepción a quien lo invoca y SHALL registrar en el log el id del turno y el tipo de mail. Un servidor SMTP que no responde SHALL cortarse por timeout y no dejar colgada la operación.

#### Scenario: SMTP caído
- **WHEN** se pide el recordatorio de un turno y no hay servidor SMTP escuchando
- **THEN** el aviso informa que no salió, no se lanza ninguna excepción y el log registra el id del turno y el tipo de mail

#### Scenario: La reserva no se revierte
- **WHEN** se crea un turno con la reserva pública y el servidor SMTP está caído
- **THEN** la reserva responde éxito y el turno queda guardado

### Requirement: Logs sin datos personales ni token
Los registros de log de los avisos, tanto si el envío sale como si falla, SHALL NOT contener el email, el teléfono, el DNI, el nombre del paciente ni el token del turno.

#### Scenario: Log de un fallo
- **WHEN** falla el envío del recordatorio de un turno con datos ficticios
- **THEN** ninguna línea de log contiene el email, el teléfono, el DNI, el nombre ni el token de ese turno

## REMOVED Requirements

### Requirement: Implementación por defecto sin envío
**Reason**: C-11 agrega la implementación real que envía por email; la implementación sin envío deja de usarse y se elimina.
**Migration**: Los dominios siguen invocando los mismos avisos sin cambios; el comportamiento queda definido por "Envío real de cada aviso por email". Los tests que no deben enviar mails reemplazan los avisos con un mock, como ya hacen los IT de turnos y bloqueos.
