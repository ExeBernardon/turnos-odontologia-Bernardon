# Flujos Principales

Cada flujo se documenta extremo a extremo. Los endpoints son los del borrador de [08](08_arquitectura_propuesta.md); los nombres exactos los fija el change "contrato de API".

## Flujo 1: Login del odontólogo

**Disparador**: el odontólogo abre `/admin/login` y envía el formulario.
**Actor**: Odontólogo.

```mermaid
sequenceDiagram
    actor O as Odontólogo
    participant F as Front
    participant A as API (Spring)
    participant DB as PostgreSQL
    O->>F: email + contraseña
    F->>A: POST /api/auth/login
    A->>DB: buscar odontólogo por email
    DB-->>A: odontólogo + password_hash
    A->>A: BCrypt.matches()
    alt credenciales válidas
        A-->>F: 200 { token JWT, expira, nombre }
        F->>F: guardar token, ir a /admin/agenda
        F->>A: GET /api/... con Authorization: Bearer
    else inválidas
        A-->>F: 401 { mensaje genérico }
    end
```

**Casos de error**:
- Credenciales inválidas → 401 genérico.
- Token vencido/ausente en ruta privada → 401; el front limpia el token y redirige al login.
- Body inválido → 400 con detalle por campo.

## Flujo 2: Reserva pública (incluida la carrera por el mismo horario)

**Disparador**: el paciente abre la página pública de reserva.
**Actor**: Paciente anónimo.

**Pasos**: elegir prestación → elegir fecha y horario libre → completar datos → reservar → ver confirmación → recibir email.

```mermaid
sequenceDiagram
    actor P as Paciente
    participant F as Front
    participant A as API
    participant DB as PostgreSQL
    participant M as SMTP
    P->>F: abre /reservar
    F->>A: GET /api/public/prestaciones
    A-->>F: prestaciones activas
    P->>F: elige prestación y fecha
    F->>A: GET /api/public/disponibilidad?prestacionId&fecha
    A->>DB: horarios de atención, bloqueos, turnos activos
    A->>A: calcular slots libres
    A-->>F: lista de inicios libres
    P->>F: elige horario, completa datos y acepta el tratamiento de datos
    F->>A: POST /api/public/turnos
    A->>A: validar formato, consentimiento, horario de atención, bloqueos, pasado y ventana 2 h a 60 días
    A->>DB: buscar paciente por DNI (si no existe, crearlo, si existe no se modifica)
    A->>DB: INSERT turno (PENDIENTE si faltan 24 h o más, CONFIRMADO si faltan menos de 24 h, token, fin, email y teléfono de contacto, consentimiento_at, recordatorio_enviado = false si queda PENDIENTE y true si nace CONFIRMADO)
    alt insert OK
        DB-->>A: turno
        alt PENDIENTE con más de 48 h
            A->>M: Confirmación de reserva al email del turno (solo botón Cancelar)
        else PENDIENTE entre 24 h y 48 h
            A->>M: Recordatorio al email del turno (Confirmar y Cancelar), un solo mail
            A->>DB: recordatorio_enviado = true (si el envío falla queda false y lo reintenta el job)
        else CONFIRMADO (menos de 24 h)
            A->>M: Aviso al email del turno (sin botones)
        end
        A-->>F: 201 resumen del turno
        F-->>P: "Reserva realizada, revisá tu email"
    else exclusión violada (23P01)
        DB-->>A: error de restricción
        A-->>F: 409 "ese horario se acaba de ocupar, elegí otro"
        F->>A: GET /api/public/disponibilidad (refrescar)
        F-->>P: muestra horarios actualizados
    end
```

**Carrera de dos pacientes**: P1 y P2 ven libre el mismo horario y envían `POST` casi a la vez. Ambos pasan la validación de disponibilidad del service (que es solo informativa). El email y el teléfono de cada uno quedan en su propio turno, no en el paciente. Postgres serializa los `INSERT`: el primero en commitear gana; el segundo viola `turno_sin_solapamiento` y el backend lo traduce a 409. Es imprescindible que la inserción se haga en una transacción que se confirme/falle en el momento (`saveAndFlush`) para capturar la excepción dentro del service y no al final del request.

**Casos de error**:
- Formato inválido (DNI, email, teléfono) o body mal formado → 400 con detalle por campo.
- Casilla de tratamiento de datos sin aceptar → 422 (RN-DAT-02).
- Prestación inexistente → 404. Prestación inactiva → 422.
- Horario fuera de atención, sobre un bloqueo, en el pasado o fuera de la ventana de 2 h a 60 días → 422 (la validación del service repite el cálculo de disponibilidad, RN-DIS-05).
- Horario ocupado por otro turno (incluida la violación de la constraint) → 409.
- Turno entre 24 h y 48 h de anticipación → nace `PENDIENTE` y recibe un solo mail, el Recordatorio, enviado en el momento de crear (RN-NOT-04). Con menos de 24 h nace `CONFIRMADO` (RN-TUR-11) y recibe el Aviso sin botones.
- Fallo del email → la reserva queda creada, se registra en log (RN-NOT-05).

## Flujo 3: Confirmar o cancelar por token (reglas de 48 h y 24 h)

**Disparador**: el paciente hace clic en un link del email.
**Actor**: Paciente.

```mermaid
sequenceDiagram
    actor P as Paciente
    participant F as Front (/turno/...)
    participant A as API
    participant DB as PostgreSQL
    P->>F: abre /turno/confirmar?token=T
    F->>A: GET /api/public/turnos/T
    A->>DB: buscar turno por token
    alt no existe
        A-->>F: 404
        F-->>P: "Link inválido"
    else existe
        A-->>F: 200 resumen (prestación, fecha, estado)
        P->>F: pulsa "Confirmar"
        F->>A: POST /api/public/turnos/T/confirmar
        A->>A: inicio - ahora > 48 h ?
        alt faltan más de 48 h
            A-->>F: 422 "Todavía no se puede confirmar"
        else faltan 48 h o menos
            A->>DB: PENDIENTE a CONFIRMADO
            A-->>F: 200 turno CONFIRMADO
        end
    end
    Note over P,F: Cancelar: /turno/cancelar?token=T
    P->>F: pulsa "Cancelar turno"
    F->>A: POST /api/public/turnos/T/cancelar
    A->>A: inicio - ahora >= 24 h ?
    alt faltan 24 h o más
        A->>DB: estado CANCELADO, cancelado_por PACIENTE
        A-->>F: 200
    else faltan menos de 24 h
        A-->>F: 422 "Comunicate con el consultorio"
    end
```

**Casos de error**:
- Token inexistente → 404.
- Confirmar un turno cancelado, pasado o terminal → 422 con mensaje.
- Confirmar con más de 48 h de anticipación → 422 (RN-NOT-08). El Recordatorio es el único mail con el link de Confirmar; el odontólogo puede confirmar desde la agenda sin esta restricción (RN-TUR-10).
- Cancelar con menos de 24 h → 422 con teléfono/contacto del consultorio (dato de configuración, ver [10](10_preguntas_abiertas.md)).
- Repetir la acción ya aplicada → 200 idempotente.

El front muestra siempre una pantalla con botón explícito (el GET solo lee y no cambia nada, el cambio de estado es POST) para que los clientes de correo y antivirus que abren los links solos no cancelen ni confirmen sin que el paciente haga nada (ver [03](03_actores_y_roles.md#por-qué-login-confirmar-y-cancelar-son-post)).

## Flujo 4: Recordatorio y alerta programados

**Disparador**: `@Scheduled(fixedDelay)` cada 15 min (configurable con `APP_RECORDATORIO_INTERVALO_MIN`).
**Actor**: Sistema.

Un mismo job hace dos tareas por ciclo: (1) Recordatorio al paciente, solo a turnos `PENDIENTE` con inicio a 48 h o menos y `recordatorio_enviado = false` (envía el de 48 h antes y reintenta los que fallaron al crear), y (2) mail "Turno no confirmado" al odontólogo por turnos `PENDIENTE` a menos de 24 h.

```mermaid
sequenceDiagram
    participant J as Job @Scheduled
    participant DB as PostgreSQL
    participant M as SMTP
    loop cada 15 min (configurable)
        J->>DB: 1) turnos PENDIENTE, inicio entre ahora y ahora+48h, recordatorio_enviado = false
        DB-->>J: lista de turnos
        loop por cada turno
            J->>M: Recordatorio al email del turno (links confirmar y cancelar)
            alt envío OK
                J->>DB: recordatorio_enviado = true
            else falla SMTP
                J->>J: log del error y sin marcar (reintenta en el próximo ciclo)
            end
        end
        J->>DB: 2) turnos PENDIENTE, inicio entre ahora y ahora+24h, alerta_pendiente_enviada = false
        DB-->>J: lista de turnos
        loop por cada turno
            J->>M: Turno no confirmado al odontólogo (paciente, prestación, horario y teléfono del turno)
            alt envío OK
                J->>DB: alerta_pendiente_enviada = true
            else falla SMTP
                J->>J: log del error y sin marcar (reintenta en el próximo ciclo)
            end
        end
    end
```

Cada ciclo ejecuta dos consultas baratas (con el índice `(estado, inicio)`); si no hay turnos que cumplan la condición no pasa nada más (ver DD-06).

**Qué pasa después de la alerta**: la agenda muestra esos turnos resaltados con el teléfono y botones Confirmar / Cancelar. El odontólogo se comunica por sus medios con el paciente y decide: `POST /api/turnos/{id}/confirmar` (US-021) o `POST /api/turnos/{id}/cancelar` (US-012, con email de aviso al paciente).

**Reglas de creación y reprogramación**:
- Un turno creado (reserva pública o carga manual) entre 24 h y 48 h recibe el Recordatorio sincrónicamente al crearse y queda con `recordatorio_enviado = true`; el job solo lo toma si ese envío falló (RN-NOT-04).
- Un turno creado con menos de 24 h nace `CONFIRMADO` (RN-TUR-11): no está pendiente, así que no entra en la alerta al odontólogo ni en el recordatorio. La alerta solo cubre turnos que siguen `PENDIENTE` al llegar a menos de 24 h (RN-NOT-06).
- Los turnos `CONFIRMADO` nunca reciben recordatorio (RN-NOT-02).
- Al reprogramar a 24 h o más el turno queda (o vuelve a) `PENDIENTE` y se reinician `recordatorio_enviado = false` y `alerta_pendiente_enviada = false`: el recordatorio sale al entrar en la ventana de 48 h, o en el siguiente ciclo si ya está dentro. El Mail 3 lleva solo el link de cancelar. Al reprogramar a menos de 24 h el turno queda `CONFIRMADO`, ambos flags en `true` y el Mail 3 es un aviso sin links (RN-TUR-08, RN-NOT-07).

**Casos de error**:
- SMTP caído → se reintenta en el próximo ciclo, no se detiene el resto del lote.
- Dos instancias del backend → no aplica en v1 (una sola instancia, SU-05).
- El paciente solo puede cancelar hasta 24 h antes: con menos, el link de cancelar rechaza con 422 (RN-CAN-02).

## Flujo 5: Bloqueo con turnos afectados

**Disparador**: el odontólogo crea un bloqueo en `/admin/configuracion`.
**Actor**: Odontólogo.

```mermaid
sequenceDiagram
    actor O as Odontólogo
    participant F as Front
    participant A as API
    participant DB as PostgreSQL
    participant M as SMTP
    O->>F: rango + motivo
    F->>A: POST /api/bloqueos/previsualizar
    A->>DB: turnos activos que solapan el rango
    A-->>F: lista de turnos afectados (puede ser vacía)
    alt lista vacía
        F->>A: POST /api/bloqueos (cancelarTurnosAfectados=false)
        A-->>F: 201 bloqueo
    else hay afectados
        F-->>O: muestra turnos y pide confirmar
        O->>F: confirma
        F->>A: POST /api/bloqueos (cancelarTurnosAfectados=true)
        A->>DB: TX: INSERT bloqueo + turnos a CANCELADO (cancelado_por ODONTOLOGO)
        DB-->>A: commit
        loop por cada paciente afectado
            A->>M: email al email del turno: cancelamos tu turno, reservá de nuevo
        end
        A-->>F: 201 bloqueo + turnos cancelados
    end
```

**Casos de error**:
- Si aparece un turno nuevo entre la vista previa y la confirmación, el `POST` recalcula los afectados dentro de la transacción y cancela todos los vigentes; la respuesta devuelve los realmente cancelados. Si `cancelarTurnosAfectados=false` y hay afectados → 409 con la lista.
- Rango inválido (`inicio >= fin`) → 422. Body mal formado → 400.
- Falla de un email → se registra; el bloqueo y las cancelaciones no se revierten.

## Flujo 6: Gestión de agenda por el odontólogo

**Disparador**: el odontólogo abre `/admin/agenda`.
**Actor**: Odontólogo.

```mermaid
sequenceDiagram
    actor O as Odontólogo
    participant F as Front
    participant A as API
    participant DB as PostgreSQL
    participant M as SMTP
    O->>F: abre agenda (día o semana)
    F->>A: GET /api/turnos?desde&hasta  (Bearer)
    A->>DB: turnos del rango + paciente + prestación
    A-->>F: turnos con teléfono de contacto (PENDIENTE marcado sin confirmar, resaltado si faltan menos de 24 h)
    opt cargar turno
        O->>F: elige paciente + prestación + horario libre
        F->>A: POST /api/turnos (con email y teléfono del turno, precargados del último turno del paciente)
        A->>A: validar horario de atención, bloqueos y pasado
        A->>DB: INSERT turno (PENDIENTE, o CONFIRMADO si faltan menos de 24 h)
        A->>M: email al email del turno (Confirmación de reserva con solo Cancelar a más de 48 h, Recordatorio entre 24 h y 48 h, Aviso sin botones si queda CONFIRMADO)
        A-->>F: 201 (o 409 si choca)
    end
    opt reprogramar
        F->>A: PATCH /api/turnos/{id}/reprogramar { inicio }
        A->>DB: UPDATE inicio y fin, estado según anticipación (PENDIENTE a 24 h o más, CONFIRMADO a menos de 24 h) y flags recordatorio_enviado y alerta_pendiente_enviada
        A->>M: Mail 3 de reprogramación al email del turno (nuevo horario, solo link cancelar si queda PENDIENTE, sin links si queda CONFIRMADO)
        A-->>F: 200 (o 409 si choca, 422 si el horario no es válido)
    end
    opt confirmar
        F->>A: POST /api/turnos/{id}/confirmar
        A->>DB: PENDIENTE a CONFIRMADO (sin restricción de 48 h)
        A-->>F: 200
    end
    opt cancelar
        F->>A: POST /api/turnos/{id}/cancelar
        A->>DB: CANCELADO, cancelado_por ODONTOLOGO
        A->>M: email de aviso de cancelación al email del turno
        A-->>F: 200
    end
    opt marcar asistencia
        F->>A: PATCH /api/turnos/{id}/estado { estado: ASISTIO o AUSENTE }
        A-->>F: 200 (o 422 si el turno todavía no empezó)
    end
```

**Casos de error**:
- 401 → login. 404 turno inexistente. 409 por solapamiento. 422 por transición de estado inválida (ej. cancelar un turno ya `ASISTIO`), por marcar asistencia antes de la hora de inicio o por horario inválido al cargar o reprogramar. 400 por body mal formado.
