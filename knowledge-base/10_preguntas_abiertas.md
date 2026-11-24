# Preguntas Abiertas

## Reglas resueltas

### Estado inicial y mails según la anticipación al crear el turno
Regla definida por el usuario (RN-TUR-11, RN-NOT-01, RN-NOT-02, RN-NOT-04, RN-NOT-08): con más de 48 h el turno nace `PENDIENTE` y recibe la Confirmación de reserva (solo Cancelar), y el job manda el Recordatorio 48 h antes; entre 24 h y 48 h nace `PENDIENTE` y recibe un solo mail, el Recordatorio, en el momento de crear; con menos de 24 h nace `CONFIRMADO`, recibe el Aviso sin botones y no dispara alerta ni recordatorio. Si a las 24 h antes sigue `PENDIENTE`, el odontólogo recibe el mail "Turno no confirmado". El paciente solo confirma dentro de las 48 h previas (422 antes). La reprogramación y la carga manual siguen la regla de estado (RN-TUR-08, RN-TUR-09).

### Preguntas cerradas

| Pregunta original | Resolución | Dónde quedó |
|-------------------|------------|-------------|
| Si el paciente reserva con un DNI existente pero otro email/teléfono, ¿se pisan los datos? | No se pisan. Paciente ya no tiene email ni teléfono: el contacto se guarda en el turno (`email_contacto`, `telefono_contacto`) y los datos del paciente solo los edita el odontólogo desde el ABM. | RN-PAC-04, DD-12, US-006 |
| Reserva entre 24 h y 48 h: ¿cómo confirma el paciente si no recibe recordatorio? | Nace `PENDIENTE` y recibe un solo mail, el Recordatorio (Confirmar y Cancelar), en el momento de crear el turno; `recordatorio_enviado = true` tras el envío (si falla, lo reintenta el job). Con menos de 24 h el turno nace `CONFIRMADO`. | RN-NOT-01, RN-NOT-02, RN-NOT-04, US-006, US-015 |
| Reprogramación: ¿qué estado queda? | Se recalcula con la regla de alta (RN-TUR-11): a 24 h o más queda (o vuelve a) `PENDIENTE`, flags en `false`, Mail 3 con solo Cancelar y recordatorio al entrar en la ventana de 48 h; a menos de 24 h queda `CONFIRMADO`, flags en `true` y Mail 3 sin links. | RN-TUR-08, RN-NOT-03, RN-NOT-07, US-011 |
| Carga manual del odontólogo a menos de 24 h: ¿también nace `CONFIRMADO`? | Sí, igual que la reserva pública, con el mail de aviso sin links. El odontólogo no está sujeto al mínimo de 2 h, solo no puede cargar en el pasado. | RN-TUR-09, RN-TUR-11, US-010 |
| Al reprogramar o cargar un turno manual, ¿se notifica por email al paciente? | Sí. Al reprogramar se envía un email al email del turno con el nuevo horario y solo el link de cancelar. Al cargar manualmente se envía el email que corresponda a la anticipación (RN-NOT-01). | RN-NOT-07, US-010, US-011 |
| ¿El odontólogo puede confirmar un turno desde la agenda? | Sí, es la transición `PENDIENTE` a `CONFIRMADO` por el odontólogo. | RN-TUR-10, US-021 |
| ¿Hay anticipación mínima para reservar y máxima? | Sí: al menos 2 h y hasta 60 días hacia adelante, configurables (reserva pública). | RN-DIS-06, US-005 |
| El recordatorio de un turno reservado con menos de 48 h, ¿se envía igual? (IN-01) | Entre 24 h y 48 h sí: nace `PENDIENTE` y recibe el Recordatorio en el momento de crear (único mail). Con menos de 24 h no: nace `CONFIRMADO` (RN-TUR-11) con `recordatorio_enviado = true` y recibe el Aviso. | RN-NOT-04, RN-TUR-11 |
| El job de recordatorios, ¿cada cuánto corre? | Cada 15 minutos, configurable con `APP_RECORDATORIO_INTERVALO_MIN`. | US-015, DD-06 |
| ¿Se puede marcar asistió/ausente en cualquier momento? | Solo desde la hora de inicio del turno. | RN-TUR-07, US-013 |
| ¿Quién figura como `cancelado_por` cuando cancela el odontólogo? | `ODONTOLOGO` (valores `PACIENTE` u `ODONTOLOGO`). | RN-TUR-06 |
| ¿Qué código HTTP devuelve cada caso? | Códigos unificados: 400, 401, 404, 409, 422. | RN-GLO-04, DD-14 |
| El enunciado admite grupos de hasta 3 integrantes y somos 6: ¿se acepta? | Sí, la cátedra aprobó el grupo de 6 integrantes. | CHANGES.md (reparto en 6) |
| ¿Hay un único sillón? | Sí en la v1: un profesional y un sillón. Multisillón y multiprofesional quedan fuera. | RN-REC-01, SU-02 |
| ¿El paciente puede cancelar un turno ya confirmado? | No, confirmar es definitivo para el paciente; sobre un `CONFIRMADO` el link de cancelar responde 422 `TRANSICION_INVALIDA` y lo cancela el odontólogo. El Recordatorio sigue trayendo Confirmar y Cancelar (el paciente elige una). | RN-CAN-01, US-008, 04 (máquina de estados) |

## Inconsistencias detectadas

### IN-01 — "El recordatorio se envía 48 h antes" vs. reservas con menos de 48 h — RESUELTA (2026-11-23, ajustada)
**Documento A dice** (`discovery.md`): se envía un recordatorio 48 h antes de cada turno pendiente o confirmado (hoy el recordatorio va solo a los `PENDIENTE`, RN-NOT-02).
**Documento B dice** (decisión técnica): el job busca turnos que empiezan "en las próximas 48 h" sin recordatorio enviado.
**Impacto**: un turno reservado para dentro de 3 h recibía confirmación y recordatorio casi juntos; parecía duplicado.
**Resolución**: un turno creado entre 24 h y 48 h nace `PENDIENTE` y recibe el Recordatorio en el momento de crear; con menos de 24 h nace `CONFIRMADO` con `recordatorio_enviado = true` y no recibe recordatorio (RN-NOT-04, RN-TUR-11). Al reprogramar rige la regla de estado según el nuevo inicio (RN-TUR-08).

### IN-02 — Cancelación del paciente (24 h) vs. recordatorio — RESUELTA (2026-11-23): el recordatorio pasa a 48 h antes
**Documento A dice**: el paciente cancela hasta 24 h antes.
**Documento B decía**: el recordatorio con el link de cancelar salía justo 24 h antes, cuando la ventana estaba por cerrarse.
**Resolución**: el recordatorio sale 48 h antes, así el paciente tiene un día entero para cancelar con el link. La cancelación del paciente sigue limitada a 24 h antes (RN-CAN-01, RN-CAN-02).

### IN-03 — Emails mencionados en el discovery vs. email de aviso de cancelación
**Documento A dice** (`discovery.md` funcionalidades): emails de confirmación y recordatorio.
**Documento B dice** (reglas y decisiones confirmadas): además hay aviso de cancelación por el odontólogo, email de reprogramación y alerta al odontólogo.
**Impacto**: ninguno funcional; la KB incluye todos los emails.
**Resolución propuesta**: tratar todos como MVP.

## Preguntas abiertas (priorizadas)

| Prioridad | Pregunta | Bloquea | Decisor |
|-----------|----------|---------|---------|
| Alta | ¿Cuáles son los criterios de evaluación de la entrega (tests, documentación, demo en vivo)? | Alcance de tests y docs | Equipo / cátedra |
| Alta | ¿La presentación es local con Docker o hay que desplegar? (SU-03) | `APP_FRONTEND_BASE_URL`, links de email | Equipo / cátedra |
| Alta | ¿Qué cuenta de Gmail envía los mails de la demo y quién genera la contraseña de aplicación? | Prueba de email real antes de la demo | Equipo |
| Alta | ¿Cómo se reparten las 6 personas entre front, back e infraestructura? | Roadmap (`CHANGES.md`) | Roadmap / equipo |
| Alta | ¿Formato del contrato de API: OpenAPI (yaml) o markdown? ¿Dónde vive (`docs/api/`)? | Primer change | Equipo técnico |
| Media | ¿Se puede corregir un `ASISTIO`/`AUSENTE` marcado por error? | Máquina de estados | Equipo |
| Media | ¿Qué dato de contacto del consultorio se muestra al rechazar cancelaciones tardías (teléfono/WhatsApp)? | RN-CAN-02, `APP_CONSULTORIO_CONTACTO` | Odontólogo |
| Media | ¿Un paciente puede tener varios turnos activos a la vez? (hoy sin límite) | Abuso de reservas | Equipo |
| Media | ¿Los turnos pasados `PENDIENTE` sin marcar asistencia se resaltan o pasan a algún estado? (hoy no cambian solos) | Agenda | Equipo |
| Media | ¿El consultorio real trabaja con más de un sillón o box? La v1 asume uno (RN-REC-01); el Discovery marca "sillones o boxes como recurso de agenda" como vacío del mercado sin evidencia propia. | Alcance futuro (multisillón) | Odontólogo |
| Media | Cualquier persona que conozca el DNI de otra puede crear un turno a su nombre con otro email, porque no se verifica el email. **Fuera de alcance de la v1** (SU-06): el odontólogo puede cancelar turnos falsos y el paciente real no ve alterados sus datos (RN-PAC-04). | Abuso de la reserva pública | Equipo |
| Baja | ¿Se puede eliminar un paciente con turnos? (propuesto: no) | US-014 | Equipo |
| Baja | ¿Se vuelven a sembrar prestaciones y horarios si las tablas quedan vacías? (propuesto: solo si vacías al arrancar) | Seed | Equipo |
| Baja | ¿Cuál es el texto exacto de finalidad de la casilla de consentimiento (Ley 25.326) en la página de reserva? La casilla obligatoria ya es regla (RN-DAT-02). | Cumplimiento | Equipo / cátedra |
| Baja | ¿Se incluye alguna opcional (lista de espera, `wa.me`, `.ics`, CSV) y cuál primero? | Sobrante de tiempo | Equipo |
| Baja | ¿Rate limit de login y de reserva pública? (SU-06) | Endurecimiento | Equipo |

## Puntos del Discovery sin evidencia

Afirmaciones del informe de Discovery (`docs/discovery/informe-discovery.md`) que no tienen respaldo verificado y que **no se usan como base de ninguna regla** de la KB. Se registran para no tratarlas como hechos.

| Punto | Estado en el Discovery | Tratamiento en la KB |
|-------|------------------------|----------------------|
| Lista de espera | No evidenciada en ningún sistema argentino ni latinoamericano relevado; se la menciona como vacío del mercado | Opcional (US-017), no planificada en el MVP |
| ARCA, Mercado Pago | Solo DentalSoft tiene ARCA verificada; MP y ARCA no verificados en Dentalink | Fuera de alcance (facturación y pagos) |
| ISO 27001 | Dentalink la afirma sin verificar; DentalCore dice seguir controles, sin certificación | No aplica; no se toma como requisito |
| Leyes (25.326 y 26.529) de Dentalink | Afirmadas, no verificadas | Se aplica la Ley 25.326 por decisión propia (RN-DAT-01, RN-DAT-02), no por lo que dice Dentalink |
| Seguridad de DentalSoft | Sin evidencia de cifrado ni de leyes (puntaje 1) | No se usa como referencia de seguridad |
| Funciones de ClinIA / OdontoClinIA | Landing y demo inaccesible (exige ser empresa); funciones afirmadas, no probadas | No se toma ninguna función como patrón a replicar |
| Sillones o boxes | Vacío del mercado sin evidencia propia | Un solo sillón en la v1 (RN-REC-01); pregunta abierta arriba |

## Supuestos sin validar

- **El paciente prefiere reservar online antes que por WhatsApp.** El canal habitual en Argentina es WhatsApp y no hay datos propios que lo contradigan. Si es falso, baja el uso de la reserva pública. Se mide tras la demo (SU-07).
- **Los recordatorios por email reducen las ausencias.** La cifra "hasta 90% menos faltas" que publica un competidor es una afirmación comercial sin verificar. Si es falsa, el email por sí solo no alcanza y habría que sumar WhatsApp o `wa.me`. Se mide tras la demo (SU-07).
