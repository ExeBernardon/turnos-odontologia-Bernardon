# Preguntas Abiertas

## Inconsistencias detectadas

### IN-01 — "El recordatorio se envía 48 h antes" vs. reservas con menos de 48 h
**Documento A dice** (`discovery.md`): se envía un recordatorio 48 h antes de cada turno pendiente o confirmado.
**Documento B dice** (decisión técnica): el job busca turnos que empiezan "en las próximas 48 h" sin recordatorio enviado.
**Impacto**: un turno reservado para dentro de 3 h recibe confirmación y recordatorio casi juntos; puede parecer duplicado.
**Resolución propuesta**: aceptar el comportamiento en v1 (RN-NOT-04) o no enviar recordatorio si el turno se creó con menos de 48 h de anticipación (marcar `recordatorio_enviado = true` al crear).

### IN-02 — Cancelación del paciente (24 h) vs. recordatorio (24 h) — RESUELTA (2026-11-23): el recordatorio pasa a 48 h antes
**Documento A dice**: el paciente cancela hasta 24 h antes.
**Documento B dice**: el recordatorio con el link de cancelar sale justo 24 h antes (+- el intervalo del job).
**Impacto**: el paciente recibe el link de cancelar cuando la ventana está por cerrarse; si el job se demora, el link llega ya vencido y se rechaza.
**Resolución propuesta**: dejarlo así y hacer que el mensaje del rechazo y el email expliquen la regla; opcionalmente enviar el recordatorio 25-26 h antes (configurable con `APP_RECORDATORIO_HORAS_ANTES`).

### IN-03 — Emails mencionados en el discovery vs. email de aviso de cancelación
**Documento A dice** (`discovery.md` funcionalidades): emails de confirmación y recordatorio.
**Documento B dice** (reglas y decisiones confirmadas): además hay aviso de cancelación por el consultorio.
**Impacto**: ninguno funcional; la KB incluye los tres emails.
**Resolución propuesta**: tratar los tres como MVP.

## Preguntas abiertas (priorizadas)

| Prioridad | Pregunta | Bloquea | Decisor |
|-----------|----------|---------|---------|
| Alta | ¿Cuáles son los criterios de evaluación de la entrega (tests, documentación, demo en vivo)? | Alcance de tests y docs | Equipo / cátedra |
| Alta | ¿La presentación es local con Docker o hay que desplegar? (SU-03) | `APP_FRONTEND_BASE_URL`, links de email | Equipo / cátedra |
| Alta | ¿Qué cuenta de Gmail envía los mails de la demo y quién genera la contraseña de aplicación? | Prueba de email real antes de la demo | Equipo |
| Alta | ¿Cómo se reparten las 6 personas entre front, back e infraestructura? | Roadmap (`CHANGES.md`) | Roadmap / equipo |
| Alta | ¿Formato del contrato de API: OpenAPI (yaml) o markdown? ¿Dónde vive (`docs/api/`)? | Primer change | Equipo técnico |
| Media | Al reprogramar o cargar un turno manual, ¿se notifica por email al paciente y hay un email específico de reprogramación? | US-010, US-011 | Equipo / odontólogo |
| Media | ¿El odontólogo puede confirmar un turno desde la agenda (ej. confirmó por teléfono)? | Transiciones de estado | Equipo |
| Media | ¿Se puede corregir un `ASISTIO`/`AUSENTE` marcado por error? | Máquina de estados | Equipo |
| Media | Si el paciente reserva con un DNI existente pero otro email/teléfono, ¿se pisan los datos? (propuesto: sí, RN-PAC-04) | US-006 | Equipo |
| Media | ¿Hay anticipación mínima para reservar (ej. no menos de 2 h) y máxima (ej. hasta 60 días)? | Disponibilidad | Odontólogo |
| Media | ¿Qué dato de contacto del consultorio se muestra al rechazar cancelaciones tardías (teléfono/WhatsApp)? | RN-CAN-02, `APP_CONSULTORIO_CONTACTO` | Odontólogo |
| Media | ¿Un paciente puede tener varios turnos activos a la vez? (hoy sin límite) | Abuso de reservas | Equipo |
| Media | ¿Los turnos pasados `PENDIENTE` sin marcar asistencia se resaltan o pasan a algún estado? (hoy no cambian solos) | Agenda | Equipo |
| Baja | ¿Se puede eliminar un paciente con turnos? (propuesto: no) | US-014 | Equipo |
| Baja | ¿Se vuelven a sembrar prestaciones y horarios si las tablas quedan vacías? (propuesto: solo si vacías al arrancar) | Seed | Equipo |
| Baja | ¿Qué hacer con los textos legales (aviso de tratamiento de datos, Ley 25.326) en la página de reserva? | Cumplimiento | Equipo / cátedra |
| Baja | ¿Se incluye alguna opcional (lista de espera, `wa.me`, `.ics`, CSV) y cuál primero? | Sobrante de tiempo | Equipo |
| Baja | ¿Rate limit de login y de reserva pública? (SU-06) | Endurecimiento | Equipo |
