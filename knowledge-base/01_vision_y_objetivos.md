# Visión y Objetivos

## Propósito del sistema

Agenda de turnos online para un odontólogo independiente: el profesional gestiona su agenda y el paciente reserva 24/7 sin cuenta, con confirmación, recordatorio y aviso por email.

Hoy el odontólogo coordina los turnos a mano, por teléfono o WhatsApp. Eso provoca superposición de turnos, pacientes que faltan sin avisar y mucho tiempo dedicado a coordinar. Además, el paciente no puede reservar fuera del horario de atención. Es un proyecto académico de un equipo de 6 personas; la entrega es el MVP funcionando el **2026-11-25**, con prioridad en la velocidad de entrega y sin presupuesto.

## Objetivos por actor

| Actor | Objetivo principal | Objetivos secundarios |
|-------|--------------------|-----------------------|
| Odontólogo | Tener una agenda propia sin solapamientos | Ver agenda diaria y semanal; configurar horarios, prestaciones y bloqueos; cargar, reprogramar, confirmar y cancelar turnos; reducir ausencias con recordatorios; enterarse de los turnos sin confirmar a menos de 24 h |
| Paciente | Reservar un turno sin llamar, a cualquier hora | Cancelar (turno `PENDIENTE`, hasta 24 h antes) desde el email y confirmar desde el recordatorio (48 h antes); no crear cuenta |

## Alcance v1 (MVP al 2026-11-25)

- Login del odontólogo (contraseña hasheada).
- ABM de pacientes con datos mínimos de identificación (nombre, apellido, DNI). El email y el teléfono de contacto se piden en cada turno y viven en el turno.
- Configuración de horarios de atención, prestaciones con duración propia y bloqueos.
- Agenda diaria y semanal.
- Alta, reprogramación y cancelación de turnos, con prevención de solapamientos garantizada en la base de datos.
- Estados del turno: pendiente, confirmado, cancelado, asistió, ausente.
- Página pública de reserva por link, sin cuenta.
- Emails al paciente (siempre al email del turno): confirmación de la reserva (solo con link de cancelar), recordatorio 48 h antes a los turnos `PENDIENTE` (único email con links de confirmar y cancelar), reprogramación (solo cancelar) y aviso de cancelación por el odontólogo. Un turno creado con menos de 24 h de anticipación nace `CONFIRMADO`, recibe un aviso sin links y no tiene recordatorio. El paciente solo puede cancelar hasta 24 h antes del turno y solo puede confirmar dentro de las 48 h previas. Ver la tabla "Mails del sistema" en [05](05_reglas_de_negocio.md).
- Alerta por email al odontólogo por cada turno `PENDIENTE` que empieza en menos de 24 h; además la agenda lo resalta con el teléfono de contacto y botones Confirmar / Cancelar.
- Todo corriendo con Docker Compose.

**Opcionales (solo si sobra tiempo, no se planifican en el MVP)**: lista de espera simple, link `wa.me` precargado, archivo `.ics`, exportación CSV a pacientes y turnos.

## Fuera de alcance

- Ficha clínica y odontograma.
- Facturación ARCA, obras sociales, Mercado Pago y señas.
- WhatsApp Business API y SMS.
- Multiprofesional y multisillón, sucursales.
- Secretaria u otros roles con login.
- App móvil y cuenta de paciente.

## Referentes de UX (contexto de `docs/discovery/informe-discovery.md`)

| Referente | Patrón a tomar |
|-----------|----------------|
| Open Dental (Web Sched) | Varias entradas a la reserva; flujo de reserva guiado |
| Doctoralia Pro | Acciones (confirmar/cancelar) directamente desde el mensaje recibido |
| Turnito | Reserva sin cuenta |

Vacíos del mercado que el sistema cubre en parte: prevención de solapamientos explícita y garantizada en base de datos, y reserva autogestionada demostrable. La lista de espera es un vacío del mercado pero queda como opcional.

## Métricas de éxito

Orientadas a la demo académica, no a producción:

- El flujo completo funciona de punta a punta con `docker compose up`: reservar, recibir el email, confirmar/cancelar, ver el turno en la agenda.
- Dos reservas simultáneas del mismo horario nunca producen dos turnos activos (la segunda recibe 409).
- Solo se ofrecen horarios realmente disponibles.
- Los emails llegan en Mailpit (dev) y en Gmail (demo).
