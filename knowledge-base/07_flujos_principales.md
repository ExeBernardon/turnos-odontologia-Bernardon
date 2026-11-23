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
    P->>F: elige horario + datos
    F->>A: POST /api/public/turnos
    A->>DB: upsert paciente por DNI
    A->>DB: INSERT turno (PENDIENTE, token, fin)
    alt insert OK
        DB-->>A: turno
        A->>M: email confirmación (links confirmar/cancelar)
        A-->>F: 201 resumen del turno
        F-->>P: "Reserva realizada, revisá tu email"
    else exclusión violada (23P01)
        DB-->>A: error de restricción
        A-->>F: 409 "ese horario se acaba de ocupar, elegí otro"
        F->>A: GET /api/public/disponibilidad (refrescar)
        F-->>P: muestra horarios actualizados
    end
```

**Carrera de dos pacientes**: P1 y P2 ven libre el mismo horario y envían `POST` casi a la vez. Ambos pasan la validación de disponibilidad del service (que es solo informativa). Postgres serializa los `INSERT`: el primero en commitear gana; el segundo viola `turno_sin_solapamiento` y el backend lo traduce a 409. Es imprescindible que la inserción se haga en una transacción que se confirme/falle en el momento (`saveAndFlush`) para capturar la excepción dentro del service y no al final del request.

**Casos de error**:
- Datos inválidos (DNI, email) → 400 con detalle por campo.
- Prestación inexistente o inactiva → 404/422.
- Horario fuera de atención, en bloqueo o en el pasado → 422 (la validación del service repite el cálculo de disponibilidad).
- Fallo del email → la reserva queda creada; se registra en log (RN-NOT-05).

## Flujo 3: Confirmar o cancelar por token (regla de 24 h)

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
        A->>DB: PENDIENTE -> CONFIRMADO
        A-->>F: 200 turno CONFIRMADO
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
- Confirmar un turno cancelado/pasado/terminal → 409/422 con mensaje.
- Cancelar con menos de 24 h → 422 con teléfono/contacto del consultorio (dato de configuración, ver [10](10_preguntas_abiertas.md)).
- Repetir la acción ya aplicada → 200 idempotente.

El front muestra siempre una pantalla con botón explícito (el GET solo lee; el cambio de estado es POST) para que los clientes de correo que precargan links no cancelen o confirmen solos.

## Flujo 4: Recordatorio programado

**Disparador**: `@Scheduled(fixedDelay = X minutos)` (por defecto 15 min, configurable).
**Actor**: Sistema.

```mermaid
sequenceDiagram
    participant J as Job @Scheduled
    participant DB as PostgreSQL
    participant M as SMTP
    loop cada X minutos
        J->>DB: turnos estado IN (PENDIENTE,CONFIRMADO), inicio entre ahora y ahora+48h, recordatorio_enviado=false
        DB-->>J: lista de turnos
        loop por cada turno
            J->>M: email recordatorio (links confirmar/cancelar)
            alt envío OK
                J->>DB: recordatorio_enviado = true
            else falla SMTP
                J->>J: log del error; sin marcar (reintenta en el próximo ciclo)
            end
        end
    end
```

**Casos de error**:
- SMTP caído → se reintenta en el próximo ciclo; no se detiene el resto del lote.
- Dos instancias del backend → no aplica en v1 (una sola instancia, SU-05).
- Turno reservado con menos de 48 h → recibe el recordatorio en el siguiente ciclo (RN-NOT-04).

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
        A->>DB: TX: INSERT bloqueo + turnos -> CANCELADO (CONSULTORIO)
        DB-->>A: commit
        loop por cada paciente afectado
            A->>M: email "cancelamos tu turno, reservá de nuevo"
        end
        A-->>F: 201 bloqueo + turnos cancelados
    end
```

**Casos de error**:
- Si aparece un turno nuevo entre la vista previa y la confirmación, el `POST` recalcula los afectados dentro de la transacción y cancela todos los vigentes; la respuesta devuelve los realmente cancelados. Si `cancelarTurnosAfectados=false` y hay afectados → 409 con la lista.
- Rango inválido (`inicio >= fin`) → 400.
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
    A-->>F: turnos (PENDIENTE marcado "sin confirmar")
    opt cargar turno
        O->>F: elige paciente + prestación + horario libre
        F->>A: POST /api/turnos
        A->>DB: INSERT turno PENDIENTE
        A->>M: email confirmación
        A-->>F: 201 (o 409 si choca)
    end
    opt reprogramar
        F->>A: PATCH /api/turnos/{id}/reprogramar { inicio }
        A->>DB: UPDATE inicio/fin (activo)
        A-->>F: 200 (o 409 si choca)
    end
    opt cancelar
        F->>A: POST /api/turnos/{id}/cancelar
        A->>DB: CANCELADO, CONSULTORIO
        A->>M: email aviso de cancelación
        A-->>F: 200
    end
    opt marcar asistencia
        F->>A: PATCH /api/turnos/{id}/estado { estado: ASISTIO | AUSENTE }
        A-->>F: 200
    end
```

**Casos de error**:
- 401 → login. 404 turno inexistente. 409 por solapamiento. 422 por transición de estado inválida (ej. cancelar un turno ya `ASISTIO`).
