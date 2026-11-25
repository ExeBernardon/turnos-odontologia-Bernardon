# Turnos odontológicos

Sistema de turnos para un consultorio odontológico: reserva pública sin cuenta, confirmación y cancelación por link, y agenda privada para el odontólogo. Trabajo práctico académico (datos ficticios).

Más contexto en [`knowledge-base/`](knowledge-base/README.md); el contrato de la API está en [`docs/api/`](docs/api/openapi.yaml) y el plan de trabajo en [`CHANGES.md`](CHANGES.md).

## Requisitos

- Docker con Compose v2 (corriendo, también para los tests del backend).
- Java 21 (para el backend fuera de Docker y los tests).
- Node 22 o superior (para el front fuera de Docker; la imagen de build usa Node 24).

## Levantar todo desde cero (un solo comando)

```bash
cp .env.example .env
docker compose down -v
docker compose up --build
```

- `.env` no se versiona (solo `.env.example`). Si falta, Compose falla a propósito.
- `down -v` borra la base: sirve para empezar de cero y es necesario cada vez que se cambia el odontólogo del seed (el seed solo corre sobre una base vacía). Con `docker compose down` los datos se conservan.
- Esperá a que el backend esté sano: `http://localhost:8081/actuator/health` responde `{"status":"UP"}` (el primer arranque tarda un par de minutos por el build).

| Qué | URL |
|-----|-----|
| App pública (reserva) | http://localhost:8080/reservar |
| Área del odontólogo | http://localhost:8080/admin/ |
| Mailpit (bandeja de emails de desarrollo) | http://localhost:8025 |
| Salud del backend | http://localhost:8081/actuator/health |

Los puertos de host se cambian desde `.env` (`FRONTEND_PORT`, `BACKEND_PORT`, `DB_PORT`, `MAILPIT_UI_PORT`, `MAILPIT_SMTP_PORT`).

### Credenciales del seed

Con la base vacía el backend crea un odontólogo ficticio, 4 prestaciones y horarios de atención. Se configura en `.env`:

- Email: el valor de `SEED_ODONTOLOGO_EMAIL` (por defecto `odontologo@ejemplo.com`).
- Contraseña: la de `SEED_ODONTOLOGO_PASSWORD` en tu `.env` (no está en el repositorio).

Ese mismo email recibe la alerta de turnos pendientes a menos de 24 h.

## Desarrollo diario (fuera de Docker)

Solo la base y Mailpit en Docker; backend y front en tu máquina:

```bash
docker compose up -d db mailpit

# backend (perfil local: puerto 8081, base en localhost:5432, Mailpit en localhost:1025)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local     # en Windows: mvnw.cmd

# front (http://localhost:5173, proxy de /api a localhost:8081)
cd frontend
npm install
npm run dev
```

Si tu `.env` usa otra contraseña de base, exportala antes de arrancar el backend (`SPRING_DATASOURCE_PASSWORD=...`).

## Tests

```bash
# backend: JUnit 5 + Testcontainers (PostgreSQL 16 real, requiere Docker corriendo)
cd backend && ./mvnw verify

# frontend: Vitest, lint, tipos y build
cd frontend && npm test && npm run lint && npm run typecheck && npm run build

# contrato de la API
cd docs/api/scripts && npm install && npm run verificar
```

Los tipos del front (`frontend/src/api/types.ts`) son una copia exacta de `docs/api/tipos.ts`. Si el contrato cambia: `npm run tipos:sync` en `frontend/`.

## Demo con Gmail

La demo se hace en una sola notebook con Docker. Mailpit sigue siendo el plan B (basta volver a los valores de `.env.example` y `docker compose up -d backend`).

1. En `.env` cambiar solo estas variables (la contraseña es una "contraseña de aplicación" de la cuenta, nunca la contraseña de la cuenta, y no se versiona):

   ```
   SPRING_MAIL_HOST=smtp.gmail.com
   SPRING_MAIL_PORT=587
   SPRING_MAIL_USERNAME=<cuenta-demo>@gmail.com
   SPRING_MAIL_PASSWORD=<contraseña de aplicación>
   SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
   SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
   APP_MAIL_FROM=<cuenta-demo>@gmail.com
   APP_FRONTEND_BASE_URL=http://localhost:8080
   SEED_ODONTOLOGO_EMAIL=<cuenta-demo>+odontologo@gmail.com
   ```

2. Los pacientes de prueba usan alias `+` de la misma cuenta (`<cuenta-demo>+ana@gmail.com`, `<cuenta-demo>+luis@gmail.com`): todos los mails llegan a una bandeja controlada por el equipo. No usar emails de personas reales.
3. Como el seed solo crea el odontólogo sobre una base vacía, después de cambiar `SEED_ODONTOLOGO_EMAIL` hay que correr `docker compose down -v` y `docker compose up --build`.
4. `APP_FRONTEND_BASE_URL=http://localhost:8080`: los links de los emails (confirmar y cancelar) apuntan a `localhost`, así que solo abren en la notebook que corre el sistema. Abrir los mails desde esa misma máquina.
5. Antes de la demo: hacer una reserva de prueba y marcar el remitente como "no es spam".

## Guion de demo

Datos ficticios: DNI `99999999` (o `9999999x`), emails `<cuenta-demo>+ana@gmail.com` (con Mailpit, `paciente@ejemplo.com`). Para mostrar recordatorio y alerta sin esperar, poner `APP_RECORDATORIO_INTERVALO_MIN=1` en `.env` y `docker compose up -d backend` (volver a `15` al terminar).

1. **Flujo 1, login del odontólogo.** Abrir `/admin/`, probar una contraseña incorrecta (mensaje genérico, sin decir qué campo falló) y después entrar con las credenciales del seed.
2. **Flujo 2, reserva pública.** Abrir `/reservar`, elegir prestación y fecha, ver los horarios libres y reservar. Reservar tres turnos y mostrar cada mail:
   - a más de 48 h: mail "Confirmamos tu reserva de turno" con solo el botón Cancelar; el turno queda Pendiente;
   - entre 24 y 48 h: mail "Recordatorio de tu turno" con Confirmar y Cancelar;
   - a menos de 24 h: mail "Tu turno quedó reservado" sin botones; el turno queda Confirmado y el resumen dice que se envían los datos del turno, sin links.

   Si el SMTP es Gmail, esperar el resumen antes de seguir (el envío es sincrónico, tarda unos segundos).
3. **Flujo 3, confirmar o cancelar por link.** Abrir los links del mail: abrirlos no cambia nada hasta apretar el botón. Confirmar el turno a más de 48 h da el mensaje de que todavía no se puede; confirmar el de 24 a 48 h lo pasa a Confirmado; cancelar un turno Pendiente a 24 h o más lo cancela; cancelar uno Confirmado pide comunicarse con el consultorio; un link alterado muestra "El link no es válido".
4. **Flujo 4, recordatorio y alerta.** Con el intervalo en 1 minuto, un turno Pendiente sin recordatorio dentro de las 48 h lo recibe, y uno Pendiente a menos de 24 h dispara el mail "Turno sin confirmar a menos de 24 h" al odontólogo.
5. **Flujo 5, bloqueo.** En el área del odontólogo, crear un bloqueo sobre un turno existente, ver la vista previa de afectados, confirmar y mostrar el mail de cancelación por bloqueo.
6. **Flujo 6, agenda.** Ver día y semana, cargar un turno manual, reprogramar (a 24 h o más y a menos de 24 h), confirmar, cancelar con mail y marcar asistencia o ausencia de un turno pasado.

## Resolución de problemas

- **El SMTP no responde** (credenciales mal, sin internet, host equivocado): cada acción que manda mail tarda unos 5 s (timeout) y el turno se crea igual. Revisar las variables `SPRING_MAIL_*` y `docker compose logs backend`.
- **Con Gmail la reserva tarda unos segundos**: el envío es sincrónico; esperar el resumen.
- **El mail no llega con Gmail**: mirar la carpeta de spam y el límite diario de envíos de la cuenta; como plan B usar Mailpit.
- **Puerto ocupado**: cambiar las variables `*_PORT` de `.env` (y la URL, si cambia `FRONTEND_PORT`, en `APP_FRONTEND_BASE_URL`).
- **El seed no cambia** (otro email o contraseña del odontólogo): el seed solo corre sobre una base vacía, hacer `docker compose down -v`.
- **Los links de los emails no abren**: apuntan a `APP_FRONTEND_BASE_URL`; abrirlos desde la máquina que corre el sistema.

## Checklist de aceptación del MVP

Historias de [`knowledge-base/06_funcionalidades.md`](knowledge-base/06_funcionalidades.md). Una `[x]` indica la evidencia del recorrido manual en Docker con Mailpit (datos ficticios); una `[ ]` falta recorrerla a mano con el odontólogo logueado y no se da por aceptada.

- [x] US-001 Login del odontólogo. Recorrido: inicio de sesión con el odontólogo del seed; con un token viejo, `/admin/agenda` redirige a `/admin/login`.
- [x] US-002 Gestionar prestaciones. Recorrido: crear "Control" de 20 min, editarla a 25 min (aparece en `/api/public/prestaciones`) y eliminarla.
- [x] US-003 Configurar horarios de atención. Recorrido: agregar el sábado de 09:00 a 12:00 (la disponibilidad pública pasa a 11 horarios), eliminarlo con confirmación (vuelve a 0).
- [x] US-004 Bloqueos con aviso de turnos afectados. Recorrido: bloqueo del lunes 09:30 a 11:00 con vista previa del turno afectado, confirmación, turno cancelado y horarios fuera de la disponibilidad; el mail de cancelación por bloqueo salió sin error.
- [x] US-005 Ver horarios libres. Recorrido: `GET /api/public/disponibilidad` y la grilla de `/reservar` (días sin atención sin horarios, paso de 15 min).
- [x] US-006 Reservar un turno. Recorrido: tres reservas a más de 48 h, entre 24 y 48 h y a menos de 24 h con 201 y el mail correcto de cada una; carrera de dos y de cinco `POST` simultáneos al mismo horario: un solo 201 y el resto 409 (`TurnoAltaConcurrenciaIT`).
- [x] US-007 Confirmar el turno por link. Recorrido: abrir el link no cambia el estado; confirmar a más de 48 h da 422; entre 24 y 48 h pasa a Confirmado.
- [x] US-008 Cancelar el turno por link. Recorrido: cancelar un Pendiente a 24 h o más lo cancela; uno Confirmado pide contactar al consultorio; token inválido da "El link no es válido".
- [x] US-009 Agenda diaria y semanal. Recorrido: vista diaria y semanal con estado, prestación y teléfono de cada turno; navegación por días.
- [x] US-010 Cargar un turno manualmente. Recorrido: carga manual con paciente nuevo, prestación y horario libre: nace "pendiente de confirmación".
- [x] US-011 Reprogramar un turno. Recorrido: reprogramar a otro día con horario libre: "queda pendiente de confirmación".
- [x] US-012 Cancelar un turno desde la agenda. Recorrido: cancelar desde la agenda con el diálogo de confirmación (muestra a quién se avisa): "Turno cancelado".
- [x] US-013 Marcar asistencia o ausencia. Recorrido: "Ausente" en un turno ya iniciado, con diálogo "No se puede deshacer": queda Ausente; antes del inicio el botón está deshabilitado con el motivo.
- [x] US-014 ABM de pacientes. Recorrido: alta con DNI con puntos, búsqueda por DNI, detalle con el contacto del último turno y edición del nombre.
- [x] US-015 Recordatorio automático. Recorrido: con `APP_RECORDATORIO_INTERVALO_MIN=1`, un Pendiente sin recordatorio dentro de las 48 h lo recibe en el siguiente ciclo del job; reservar entre 24 y 48 h lo manda al crear.
- [x] US-016 Correr todo con Docker Compose. Recorrido: `docker compose down -v && docker compose up --build --wait` arranca desde cero, el backend responde `UP` y el seed deja las 4 prestaciones y los horarios.
- [x] US-021 Confirmar un turno desde la agenda. Recorrido: confirmar desde la agenda un pendiente resaltado: "Turno confirmado".
- [x] US-022 Alerta al odontólogo por turnos pendientes a menos de 24 h. Recorrido: el mail "Turno sin confirmar a menos de 24 h" salió en el job; la agenda resalta el turno ("Sin confirmar · menos de 24 h"), muestra el aviso arriba, el teléfono y los botones Confirmar / Cancelar.
