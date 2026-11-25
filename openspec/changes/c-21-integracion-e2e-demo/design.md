# Design

## Context

C-01 a C-20 están en `main` y archivados (ver proposal.md, Why). Estado observado en el código antes de este change:

- `notificacion/TipoMail.java` guarda asunto, plantilla y botones de los 8 mails. `AVISO_TURNO_CONFIRMADO` tiene el asunto "Tu turno quedo reservado" (sin tilde) y `TURNO_NO_CONFIRMADO` "Turno sin confirmar a menos de 24 h". `EmailService.enviar` usa `tipo.asunto()` tal cual.
- `templates/email/turno-no-confirmado.html` dice "Falta menos de 24 horas" fijo, aunque el job (`AvisosProgramadosService`) elige los turnos con `app.alerta().pendienteHoras()` (`APP_ALERTA_PENDIENTE_HORAS`).
- `notificacion/ModeloMail.de(tipo, turno, links, formato, contacto)` arma las variables de las plantillas; lo llaman `NotificacionTurnosEmail` (que ya recibe `AppProperties`) y los tests de plantillas.
- `turno/EstadoInicialTurno.java` fija `Duration.ofHours(24)` y `Duration.ofHours(48)`; `TurnoService` lo usa en el alta (`construir`, `avisar`) y en la reprogramación, y ya recibe `AppProperties` en el constructor. Los tests construyen `AppProperties` con `AppPropertiesFixture` (24 y 48 por defecto).
- `turno/PlazosTurno.java` (plazos del paciente por link) también fija 48 h y 24 h, y existe `APP_CANCELACION_HORAS_MINIMAS` sin usar ahí.
- `frontend/src/public/reservar.ts` (`mostrarResumen`) muestra siempre "te enviamos los datos del turno y los links para gestionarlo", también para `CONFIRMADO`, cuyo mail (Aviso) no trae links.
- `application.yml` fija los timeouts SMTP en 5000 ms; el seed del odontólogo lee `SEED_ODONTOLOGO_*` y su contraseña no tiene valor por defecto (viene solo de `.env`).
- Los logs de mail ya registran solo `turnoId`, tipo y clase de la excepción (requirement "Logs sin datos personales ni token" de `notificaciones-turnos`).

Restricciones: reglas duras del `CLAUDE.md` (datos ficticios, `.env` fuera de git, contrato sin cambios, Testcontainers, TS estricto, regla 16 sobre archivos de otros integrantes) y TDD estricto.

## Goals / Non-Goals

**Goals:**
- Que el MVP arranque desde una base vacía y los 6 flujos de KB 07 funcionen con Mailpit y con Gmail.
- Corregir solo los defectos de integración ya detectados (cuatro de código, uno de documentación), cada uno con un test que falle antes del fix.
- Dejar README y checklist de aceptación listos para la entrega.

**Non-Goals:**
- No se cambia el contrato de API, el modelo de datos ni el esquema.
- No se hace asíncrono el envío de mails (se documenta su demora).
- No se parametrizan los plazos del paciente de `PlazosTurno` ni sus mensajes (ver Decisión 5).
- No se corrigen otros textos sin tilde que no se hayan detectado como defecto en la demo.

## Decisions

### 1. Demo local con Docker en una notebook (decidido por el equipo)
`APP_FRONTEND_BASE_URL=http://localhost:8080`. Los links de los emails solo abren en la máquina que corre el sistema; la demo se hace en esa notebook. Alternativa descartada: desplegar en un servidor público (más infraestructura, más superficie de datos, fuera del alcance del TP).

### 2. Gmail con una cuenta de demo dedicada y alias `+` (decidido por el equipo)
La dirección real de la cuenta (`<cuenta-demo>@gmail.com` en los artefactos) y su contraseña de aplicación viven solo en `.env`. Los destinatarios de prueba son alias `+` de esa misma cuenta (`<cuenta-demo>+ana@gmail.com`, etc.), así todos los mails llegan a una bandeja controlada por el equipo y no se usan emails de personas reales. `SEED_ODONTOLOGO_EMAIL=<cuenta-demo>+odontologo@gmail.com` para que la alerta "Turno no confirmado" también llegue; como el seed solo crea el odontólogo sobre una base vacía, hace falta `docker compose down -v` después de cambiarlo. El envío por Gmail ya se verificó (reserva con 201 sin error de envío). Alternativa descartada: una cuenta personal de un integrante (mezcla datos personales y no se puede compartir la contraseña de aplicación).

### 3. Asunto con el umbral de la alerta
`TipoMail` sigue siendo la única fuente de asuntos. El asunto de `TURNO_NO_CONFIRMADO` pasa a ser una plantilla con las horas (por ejemplo "Turno sin confirmar a menos de %d h") y `TipoMail` expone un método que devuelve el asunto final a partir del modelo del mail; `EmailService` usa ese método en lugar de `asunto()`. `ModeloMail.de` recibe las horas de la alerta y agrega la variable `horasAlerta` al modelo del `TURNO_NO_CONFIRMADO`; la plantilla HTML la usa con `th:text`. `NotificacionTurnosEmail` toma el valor de `AppProperties.alerta().pendienteHoras()`, que ya recibe. Los asuntos fijos no cambian de comportamiento.
Alternativas descartadas: armar el asunto en `NotificacionTurnosEmail` (dispersa la fuente de los asuntos); dejar el texto genérico sin número ("dentro de poco") (pierde información útil para el odontólogo).

### 4. `EstadoInicialTurno` con umbrales de la configuración
Se introducen los umbrales como valor (por ejemplo un record con `confirmacion` y `recordatorio` como `Duration`) construido desde `AppProperties` (`alerta().pendienteHoras()` y `recordatorio().horasAntes()`, los mismos que usa el job). Los métodos de `EstadoInicialTurno` aceptan esos umbrales; los de dos argumentos actuales delegan en los valores por defecto 24/48 para que `EstadoInicialTurnoTest` y sus bordes sigan intactos. `TurnoService` construye los umbrales una vez en el constructor y usa siempre las variantes con umbrales, en alta y en reprogramación. Con los valores por defecto el comportamiento es idéntico, así que los tests de alta y reprogramación existentes deben seguir verdes sin tocarse.
Alternativas descartadas: inyectar `AppProperties` dentro de `EstadoInicialTurno` (deja de ser un cálculo puro); quitar las firmas de dos argumentos (obliga a reescribir tests de otra integrante sin necesidad).

### 5. `PlazosTurno` queda fuera
Los plazos del paciente (confirmar desde 48 h, cancelar hasta 24 h) también están fijos, pero sus mensajes ("desde 48 h antes", "faltan menos de 24 h") están en el código y reflejados en los ejemplos del contrato, y el equipo no los detectó como defecto en la demo. Parametrizarlos implicaría tocar mensajes y potencialmente el contrato (regla 5). Se deja anotado como deuda para después de la entrega; con la configuración por defecto todo es coherente.

### 6. Texto del resumen según el estado
En `mostrarResumen`, el aviso de "Revisá tu email (…)" se elige por `resumen.estado`: `PENDIENTE` → "te enviamos los datos del turno y los links para gestionarlo"; `CONFIRMADO` → "te enviamos los datos del turno" (sin links). La nota "Si necesitás cambiarlo, comunicate con el consultorio" se mantiene para `CONFIRMADO`. Si conviene, el texto se extrae a una función pura en `reservar.logica.ts` para testearlo sin DOM; si no, se cubre en `reservar.test.ts` con el ejemplo 201 y una variante `CONFIRMADO`.

### 7. Verificación manual guiada y checklist
El recorrido de los 6 flujos se hace con datos ficticios. Un agente puede hacer el arranque desde cero, la reserva pública, la carrera (dos `POST` casi simultáneos con `curl`), los links de token y la verificación de mails en Mailpit o en la bandeja de alias. El login del odontólogo necesita a una persona: la contraseña del seed vive solo en `.env` y el agente no la lee ni la escribe. Los flujos de bloqueo y agenda quedan para la persona logueada o para el agente una vez que la persona inició sesión en el navegador integrado. Para probar recordatorio y alerta se usa `APP_RECORDATORIO_INTERVALO_MIN=1` en `.env` durante la prueba y se vuelve al valor por defecto. El checklist de aceptación de las US `[MVP]` va en el README (sección "Checklist de aceptación del MVP"), con cada US marcada y una línea de evidencia.

### 8. Pasada de Ley 25.326
Se verifica en tres frentes: (a) `grep` de los `log.*` del backend buscando DNI, email, teléfono o token, y los logs del contenedor después del recorrido; (b) `git ls-files` confirma que solo está `.env.example` y `git check-ignore .env`; (c) las respuestas públicas (`/api/public/**`) se revisan contra el contrato para que no traigan DNI, apellido, email, teléfono ni token. Si aparece un hallazgo, se corrige con su test como un fix más.

## Risks / Trade-offs

- [Envío sincrónico: con Gmail una reserva tarda ~6 s] → se documenta en el README y en el guion de demo (esperar el resumen antes de seguir); hacerlo asíncrono queda fuera del MVP.
- [SMTP inalcanzable: cada acción que manda mail tarda ~5 s por el timeout] → sección de resolución de problemas en el README; el turno se crea igual (RN-NOT-05).
- [Gmail manda a spam o alcanza el límite diario] → probar con anticipación, marcar como "no es spam" el remitente antes de la demo, usar Mailpit como plan B cambiando solo `.env`.
- [Los links solo funcionan en la notebook de la demo] → abrir los mails desde esa misma máquina.
- [Tocar archivos de otros integrantes (C-07 de Cande Salinas, C-17 de Milagros)] → cambios mínimos, tests existentes intactos y aviso explícito en el PR (regla 16).
- [Cambiar firmas de `ModeloMail`/`TipoMail` rompe tests del área de notificaciones] → es el área de Agus, mismo dueño; los tests se ajustan en el mismo ciclo TDD.

## Migration Plan

No hay migración de datos. Para la demo: completar `.env` con la cuenta de demo y `SEED_ODONTOLOGO_EMAIL` con el alias, `docker compose down -v`, `docker compose up --build`. Rollback a Mailpit: restaurar los valores `SPRING_MAIL_*` de `.env.example` y `docker compose up -d backend`.
