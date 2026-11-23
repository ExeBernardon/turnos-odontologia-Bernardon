# Discovery — Turnos Odontología

**Fecha**: 2026-11-22
**Contexto**: proyecto académico en equipo (6 integrantes), entrega de un MVP funcionando el **2026-11-25**.
**Fuentes investigadas**: 21 sistemas (8 de Argentina, 7 de Latinoamérica, 6 internacionales). Ver `docs/discovery/sources/` y el informe completo en `docs/discovery/informe-discovery.md`.

## 1. Problema que resuelve

El odontólogo independiente coordina sus turnos a mano, por teléfono o WhatsApp. Eso provoca superposición de turnos, pacientes que faltan sin avisar y mucho tiempo dedicado a coordinar. Además, el paciente no puede reservar fuera del horario de atención. El sistema le da al odontólogo una agenda propia, con reserva online 24/7 para el paciente y recordatorios automáticos por email.

## 2. Usuarios / roles

- **Odontólogo** (único usuario con login): gestiona su agenda, prestaciones, horarios de atención y bloqueos; carga, reprograma y cancela turnos.
- **Paciente** (sin cuenta): reserva un turno desde un link público y lo confirma o cancela desde los links que recibe por email.

En la v1 no hay secretaria. Hay un solo odontólogo y un solo sillón.

## 3. Casos de uso

1. Como paciente, quiero reservar un turno desde un link público para no tener que llamar al consultorio.
2. Como odontólogo, quiero ver mi agenda diaria y semanal para organizar mi jornada.
3. Como odontólogo, quiero cargar, reprogramar y cancelar turnos sin que se superpongan.
4. Como paciente, quiero recibir un recordatorio por email y confirmar o cancelar con un clic.
5. Como odontólogo, quiero configurar mis horarios, prestaciones y bloqueos para que la reserva pública solo ofrezca horarios reales.

## 4. Competidores / soluciones existentes

| Competidor | País | Qué resuelve | Pricing visible | Diferenciador |
|---|---|---|---|---|
| DentalSoft ★ | Argentina | Gestión odontológica con reserva online | 3 meses gratis (con limitaciones); Gestión ARS 30.000/mes y PRO ARS 60.000/mes, +ARS 10.000 por profesional agregado (máx. 10) | Verificado con demo: ARCA y facturación, Mercado Pago, liquidación de obras sociales, reserva online en 4 pasos |
| Dentalink ★ | Chile (con módulo AR) | Gestión odontológica integral | Esencial USD 35, Pro USD 50 y Titanium USD 75 por mes, +USD 10/12/15 por odontólogo; administrativos gratis (verificado con asesor) | Obras sociales (aranceles, copago, conciliación) y receta electrónica Decreto 345/2024; ARCA, Mercado Pago, ISO 27001 e IA no verificados |
| DentalCore | Argentina | Gestión integral odontológica | ARS 75.000/mes o USD 50 (inconsistente) | La cobertura declarada más amplia (ARCA, obras sociales, FHIR); sigue controles de ISO/IEC 27001, sin certificación |
| ClinIA / OdontoClinIA | Argentina | Gestión institucional | Cotización (demo solo para empresas) | ARCA, PUCO, RENAPER y receta electrónica FHIR; sin bloqueos de agenda en la landing |
| Open Dental | Internacional | Software dental integral | USD 89/mes por sede (fuera de EE.UU.) | Web Sched con 4 formas de entrar a la reserva |
| Doctoralia Pro | Internacional (opera en AR) | Directorio y agenda de profesionales | No publicado en ARS | Búsqueda por obra social; confirmar o cancelar por WhatsApp |
| AgendaPro ★ | No declarado (opera en AR) | Agenda y reservas multirubro | Desde ARS 19.000/mes, 7 días de prueba, sin plan gratis | Reserva multicanal y WhatsApp con IA; **sin** ficha clínica, odontograma, seña, Mercado Pago, caja, ARCA ni obras sociales |

★ = datos verificados por el equipo el 2026-11-23 (página, asesor o demo). Detalle en `docs/discovery/sources/` y en `informe-discovery.md`.

**Notas**:
- Puntaje ponderado (sobre 5), después de la verificación del equipo: DentalSoft 4,15; Dentalink 3,85; DentalCore 2,90; ClinIA 2,80; Open Dental 2,65. AgendaPro bajó del 1.º puesto (3,10) al 16.º (1,85) al confirmarse que no tiene clínica ni cobros. Lo no verificado tiene techo 3, así que el ranking favorece a los sistemas verificados.
- Referentes de UX: Open Dental (entradas de reserva), Doctoralia (acciones desde el mensaje) y DentalSoft (reserva en 4 pasos verificada con demo).
- **Vacíos del mercado**:
  - La lista de espera no figura en ningún sistema argentino ni latinoamericano (tampoco en Dentalink).
  - La reserva autogestionada solo está verificada en DentalSoft.
  - La normativa 25.326 y 26.529 casi no se declara.
  - La prevención de solapamientos rara vez se explica.

## 5. Funcionalidades necesarias (MVP al 25/11)

- Login del odontólogo, con contraseña hasheada.
- ABM de pacientes con datos mínimos (nombre, DNI, email, teléfono).
- Configuración de horarios de atención, prestaciones con duración propia y bloqueos.
- Agenda diaria y semanal.
- Alta, reprogramación y cancelación de turnos, con prevención de solapamientos garantizada en la base de datos.
- Estados del turno: pendiente, confirmado, cancelado, asistió y ausente.
- Página pública de reserva por link, sin cuenta.
- Email de confirmación de la reserva y recordatorio 48 h antes, ambos con links para confirmar o cancelar.
- Todo el sistema corriendo con Docker Compose.

## 6. Funcionalidades opcionales (si sobra tiempo)

- Lista de espera simple.
- Link `wa.me` con el mensaje precargado, de un clic y manual.
- Archivo `.ics` para agregar el turno al calendario del paciente.
- Exportación de pacientes y turnos a CSV.

Quedan **fuera** de la v1: ficha clínica y odontograma, facturación ARCA, obras sociales, Mercado Pago y señas, WhatsApp Business API, SMS, multiprofesional y multisillón, sucursales, app móvil y cuenta de paciente.

## 7. Reglas de negocio

- **Duración**: la define la prestación. Cada prestación (consulta, limpieza, extracción…) tiene su duración y el turno ocupa exactamente ese tiempo.
- **Sin solapamientos**: no puede haber dos turnos activos que se superpongan. La base de datos lo garantiza con una restricción de exclusión en PostgreSQL. Si dos pacientes reservan el mismo horario al mismo tiempo, gana el primero que se guarda. El segundo recibe "ese horario se acaba de ocupar, elegí otro".
- **Disponibilidad**: solo se ofrecen horarios dentro del horario de atención, fuera de los bloqueos y sin turnos activos encima.
- **La reserva ocupa el horario**: desde que el paciente reserva, el turno queda **pendiente** y ese horario deja de ofrecerse. Confirmar no reserva nada: solo informa que el paciente va a asistir.
- **Turno sin confirmar**: queda pendiente y se mantiene. En la agenda aparece marcado como "sin confirmar" para que el odontólogo decida si llama al paciente. No se libera automáticamente.
- **Cancelación por el paciente**: puede cancelar desde el link del email hasta 24 h antes del turno. Con menos anticipación, el sistema lo rechaza y le indica que se comunique con el consultorio. El odontólogo puede cancelar desde la agenda en cualquier momento.
- **Bloqueos sobre turnos existentes**: si el odontólogo crea un bloqueo en un rango con turnos, el sistema le muestra cuáles son. Si confirma, esos turnos se cancelan (cancelados por el consultorio) y se envía un email a cada paciente invitándolo a reservar de nuevo.
- **Identidad del paciente**: se identifica por DNI. Si un paciente vuelve a reservar con el mismo DNI, el turno nuevo se asocia al paciente existente, sin duplicarlo. Dos personas que comparten teléfono o email son pacientes distintos si tienen DNI distinto.
- **Links de confirmar y cancelar**: cada turno tiene un **token aleatorio único**. Lo genera el backend al crear el turno, se guarda junto al turno y va en los links del email. El token permite confirmar o cancelar **solo ese turno**, sin login. Como no es un ID secuencial, no se puede adivinar el turno de otra persona.
- **Recordatorio**: se envía un email 48 h antes de cada turno pendiente o confirmado, así el paciente todavía puede cancelar (el límite de cancelación es 24 h).

## 8. Integraciones

- **Servidor de email (SMTP)**, única integración externa:
  - En desarrollo se usa **Mailpit**, un servidor de prueba en Docker que captura los mails y los muestra en una bandeja web (`localhost:8025`) sin enviarlos.
  - En la demo se usa **Gmail SMTP** con contraseña de aplicación, para que los mails lleguen de verdad.
  - Se pasa de uno a otro solo con variables de entorno.
- Ninguna integración paga (sin WhatsApp API, SMS ni pasarelas de pago).

## 9. Restricciones

- **Plazo**: MVP funcionando el 2026-11-25, 3 días desde el inicio del Discovery.
- **Stack obligatorio**:
  - Front: TypeScript vanilla, sin framework.
  - Back: API REST con Spring Boot y Maven, Hibernate y PostgreSQL.
  - Todo corriendo en Docker.
- **Sin presupuesto**: solo herramientas y servicios gratuitos.
- **Equipo**: 6 personas trabajando en paralelo, cada una en su rama de GitHub y con su propio agente (Active Stack y OpenSpec).

## 10. Riesgos

- **Plazo (el riesgo principal)**: 3 días para una app completa. Cualquier funcionalidad opcional pone en riesgo el núcleo, así que el roadmap tiene que priorizar sin concesiones.
- **Trabajo en paralelo sin contrato**: 6 agentes en 6 sesiones no comparten contexto. Sin un contrato de API escrito y versionado en el repo, cada agente inventa sus propios endpoints y JSON, y la integración falla al final. Mitigación: el contrato de API es el **primer change del roadmap**, se mergea a `main` antes de que el resto ramifique.
  - Operaciones que debe cubrir:
    - login;
    - CRUD de prestaciones, horarios y bloqueos (con aviso de turnos afectados);
    - consulta de horarios libres;
    - reserva pública;
    - confirmar o cancelar por token;
    - agenda por día o semana;
    - alta, reprogramación, cancelación y cambio de estado de turnos;
    - ABM de pacientes.
- **Concurrencia en la reserva**: dos pacientes en el mismo horario. Mitigado con la restricción de exclusión en la base de datos.
- **Front en TypeScript vanilla**: sin framework, armar la UI lleva más tiempo y es más fácil que quede inconsistente entre integrantes.
- **Entrega de emails**: Gmail tiene límites diarios y los mails pueden caer en spam. Mailpit cubre el desarrollo; hay que probar Gmail antes de la demo.
- **Datos personales**: la Ley 25.326 aplica aunque sea un proyecto académico. Mínimo: datos indispensables, contraseñas hasheadas y tokens no adivinables.
- **Supuesto sin probar**: que el paciente prefiera reservar online en vez de escribir por WhatsApp. No está validado con usuarios reales.
- **Supuesto sin probar**: que los recordatorios por email reduzcan las ausencias, porque el canal habitual en Argentina es WhatsApp.

## 11. Preguntas abiertas

- ¿Cuáles son los criterios de evaluación de la entrega? Por ejemplo, si piden tests, documentación o una demo en vivo.
- ¿La app se presenta corriendo en local con Docker, o hay que desplegarla en algún lado?
- ¿Qué cuenta de Gmail se usa para enviar los mails de la demo?
- ¿Cómo se reparten los 6 integrantes entre front, back e infraestructura? Lo propone el roadmap.
