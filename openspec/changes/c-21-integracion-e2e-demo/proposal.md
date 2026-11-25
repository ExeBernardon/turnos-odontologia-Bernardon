# Proposal

## Why

C-01 a C-20 ya están mergeados y archivados, pero nadie probó todavía el MVP entero de punta a punta desde una base vacía, con los mails reales de Gmail y en más de una máquina. La entrega es el 2026-11-25: C-21 cierra el camino crítico dejando el sistema demostrable, los defectos de integración corregidos (cada uno con su test), la documentación de entrega lista y una pasada de Ley 25.326.

## What Changes

- **Arranque desde cero verificado**: `docker compose down -v && docker compose up --build` en al menos dos máquinas de integrantes distintos, con el seed ficticio presente (odontólogo, prestaciones, horarios).
- **Recorrido manual de los 6 flujos de KB 07** (login, reserva pública con la carrera, confirmar/cancelar por token con las reglas de 48 h y 24 h, recordatorio y alerta con `APP_RECORDATORIO_INTERVALO_MIN` bajo, bloqueo con turnos afectados, agenda), verificando los mails en Mailpit o en Gmail.
- **Demo local con Gmail** configurada solo por variables de entorno (contraseña de aplicación y cuenta de demo únicamente en `.env`), con destinatarios de prueba como alias `+` de la misma cuenta.
- **Correcciones de integración detectadas**, cada una test-first:
  - Asunto del Aviso: "Tu turno quedo reservado" pasa a "Tu turno quedó reservado".
  - El mail "Turno no confirmado" deja de decir "24 h" fijo: asunto y cuerpo usan el umbral configurado en `APP_ALERTA_PENDIENTE_HORAS`.
  - El resumen de la reserva pública deja de prometer "links para gestionarlo" cuando el turno nace `CONFIRMADO` (ese mail no trae links).
  - `EstadoInicialTurno` deja de fijar 24 h y 48 h en el código y toma los umbrales de `AppProperties` (los mismos que usa el job de avisos), sin cambiar el comportamiento con los valores por defecto.
- **README final**: requisitos, comando único, URLs, credenciales del seed ficticio, configuración de Gmail para la demo, guion de demo y resolución de problemas (incluido el SMTP inalcanzable, que demora cada acción unos 5 s por el timeout).
- **Checklist de aceptación** de las US `[MVP]` (US-001 a US-016, US-021, US-022) marcado con evidencia.
- **Pasada de Ley 25.326**: logs sin DNI, email ni teléfono; `.env` fuera de git; respuestas públicas sin datos sensibles.
- `mvn verify` y `npm test` / `lint` / `typecheck` / `build` en verde.

Fuera de alcance: los opcionales C-22 a C-25, despliegue en un servidor público, envío asíncrono de mails.

## Capabilities

### New Capabilities
(ninguna)

### Modified Capabilities
- `notificaciones-turnos`: el contenido de los mails exige asuntos con la ortografía correcta y que el "Turno no confirmado" exprese el umbral configurado de la alerta, no "24 h" fijo.
- `alta-turnos`: el estado inicial y los avisos de creación toman los umbrales de la configuración (`APP_ALERTA_PENDIENTE_HORAS`, `APP_RECORDATORIO_HORAS_ANTES`) en lugar de valores fijos.
- `reserva-publica-front`: el aviso de "revisá tu email" del resumen depende del estado (con links de gestión solo si el turno queda `PENDIENTE`).
- `entorno-ejecucion`: el README de entrega documenta la demo (credenciales ficticias del seed, Gmail por variables de entorno, guion y resolución de problemas).

## Impact

- **Backend, notificaciones (C-11, Agus)**: `notificacion/TipoMail.java`, `ModeloMail.java`, `EmailService.java`, `templates/email/turno-no-confirmado.html` y sus tests (`TipoMailTest`, `PlantillasMailTest`, `EmailServiceGreenMailTest`).
- **Backend, turnos (C-07, Cande Salinas — se avisa en el PR, regla 16)**: `turno/EstadoInicialTurno.java`, `turno/TurnoService.java` y `EstadoInicialTurnoTest`; los tests existentes de alta y reprogramación deben seguir verdes.
- **Frontend, reserva pública (C-17, Milagros — se avisa en el PR, regla 16)**: `frontend/src/public/reservar.ts` y `reservar.test.ts`.
- **Documentación**: `README.md` (reescritura final); `CHANGES.md` (C-21 marcado al archivar).
- **Contrato de API**: sin cambios (`docs/api/openapi.yaml` no se toca).
- **Dependencias**: ninguna nueva.
- **Riesgo conocido de la demo**: el envío de mails es sincrónico dentro del request (una reserva con Gmail tarda ~6 s); se documenta, no se cambia.
