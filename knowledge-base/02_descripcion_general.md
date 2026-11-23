# Descripción General

## Stack tecnológico

| Capa | Tecnologías | Versión mínima |
|------|-------------|----------------|
| Front | TypeScript vanilla (sin framework), Vite (build y dev server) | TypeScript 5.x, Vite 5+ |
| Servidor web (Docker) | nginx: sirve el build y hace proxy de `/api` al backend | nginx estable |
| Back | Java, Spring Boot, Maven, Spring Web, Spring Data JPA + Hibernate, Spring Security, Spring Mail, `@Scheduled` | Java 21, Spring Boot 3.5 |
| Base de datos | PostgreSQL (extensión `btree_gist`) | 16 |
| Auth | JWT (header `Authorization: Bearer`), BCrypt | - |
| Email | SMTP: Mailpit (dev), Gmail SMTP con contraseña de aplicación (demo) | Mailpit latest |
| Infra | Docker Compose: `db`, `backend`, `frontend`, `mailpit` | Compose v2 |

Contexto: equipo de 6, entrega 2026-11-25, prioridad velocidad de entrega, sin presupuesto (solo servicios gratuitos). Detalle de contenedores, puertos y variables en [11_docker_y_entornos.md](11_docker_y_entornos.md).

## Arquitectura general

```
 Paciente / Odontólogo (navegador)
            |
            v
   +------------------+   /api/*    +--------------------+    JDBC    +--------------+
   | frontend (nginx) | ----------> | backend            | ---------> | db           |
   | build de Vite    |  proxy      | Spring Boot 3.5    |            | PostgreSQL 16|
   +------------------+ (mismo      | controller>service |            +--------------+
                         origen)    | >repository        |
                                    |  @Scheduled        | ---SMTP--> mailpit (dev) / Gmail (demo)
                                    +--------------------+
```

Decisiones de alto nivel (detalle en [09](09_decisiones_y_supuestos.md)):

- **Mismo origen, sin CORS en Docker**: nginx hace proxy de `/api` al backend. En desarrollo el proxy lo hace Vite.
- **Monolito en capas** en el backend: controller, service, repository, DTOs y entidades. Errores con `@ControllerAdvice` y JSON uniforme.
- **Anti-solapamiento en la base de datos**: restricción de exclusión de PostgreSQL, agregada por un `schema-extra.sql` idempotente porque Hibernate (`ddl-auto=update`) no la genera.
- **Dos áreas en el front**: pública (reserva, confirmar y cancelar por token) y privada del odontólogo (login, agenda, configuración, pacientes).
- **Zona horaria**: `America/Argentina/Buenos_Aires`. Los instantes se guardan como `timestamptz` (UTC internamente) y se muestran en hora de Buenos Aires. Ver [08](08_arquitectura_propuesta.md).

## Integraciones externas

| Servicio | Propósito | Tipo |
|----------|-----------|------|
| Mailpit | Capturar emails en desarrollo (UI `localhost:8025`, SMTP 1025) | SMTP |
| Gmail SMTP | Enviar emails reales en la demo (contraseña de aplicación) | SMTP |

Es la única integración. No hay WhatsApp API, SMS ni pasarelas de pago. El cambio entre Mailpit y Gmail es solo por variables de entorno.

## API REST

Prefijo `/api`, JSON, errores con formato uniforme. Resumen por recurso (borrador completo en [08](08_arquitectura_propuesta.md#borrador-de-recursos-y-endpoints-rest)):

| Recurso | Auth | Descripción |
|---------|------|-------------|
| `/api/auth` | Pública (login) | Login del odontólogo, devuelve JWT |
| `/api/public/*` | Pública | Prestaciones activas, horarios libres, reserva, confirmar/cancelar por token |
| `/api/prestaciones`, `/api/horarios-atencion`, `/api/bloqueos` | JWT | Configuración del consultorio |
| `/api/pacientes` | JWT | ABM de pacientes |
| `/api/turnos` | JWT | Agenda y gestión de turnos |

El contrato detallado (JSON, códigos de error) es el primer change del roadmap y se mergea a `main` antes de ramificar.

## Trabajo en paralelo

6 personas, cada una con su agente (Active Stack y OpenSpec), en ramas de GitHub. Sin contrato de API escrito y versionado, cada agente inventaría endpoints distintos; por eso el change "contrato de API" va primero.
