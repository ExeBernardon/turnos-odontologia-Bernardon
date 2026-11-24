# Turnos Odontología (Bernardon) — Instrucciones para Agentes

> Este archivo (y su copia `CLAUDE.md`) es lo PRIMERO que todo agente lee al entrar al repo.
> Generado a partir de `knowledge-base/` y `CHANGES.md`. No editar a mano sin re-sincronizar ambos archivos.

Agenda online para un odontólogo independiente (un odontólogo, un sillón): el profesional gestiona agenda, prestaciones, horarios y bloqueos con login JWT; el paciente reserva sin cuenta desde un link público y cancela o confirma por links con token enviados por email. TP de Metodología I, equipo de 6, entrega del MVP el 2026-11-25.

> `docs/discovery/` es el Discovery del proyecto: referencia de lectura, **no se edita** dentro de los changes.

---

## Stack Tecnológico

| Capa | Tecnología | Versión |
|------|------------|---------|
| Frontend | TypeScript vanilla (sin framework), Vite (build y dev server) | TypeScript 5.x, Vite 5+ |
| Servidor web (Docker) | nginx: sirve el build y hace proxy de `/api` al backend | nginx estable |
| Backend | Java, Spring Boot, Maven, Spring Web, Spring Data JPA + Hibernate (`ddl-auto=update` + `schema-extra.sql`), Spring Security, Spring Mail, `@Scheduled` | Java 21, Spring Boot 3.5 |
| Base de datos | PostgreSQL (extensión `btree_gist`) | 16 |
| Auth | JWT (header `Authorization: Bearer`), BCrypt | — |
| Email | SMTP: Mailpit (dev), Gmail SMTP con contraseña de aplicación (demo) | Mailpit latest |
| Infra | Docker Compose: `db`, `backend`, `frontend`, `mailpit` | Compose v2 |

Detalle completo: [knowledge-base/02_descripcion_general.md](knowledge-base/02_descripcion_general.md)

---

## Base de Conocimiento

La fuente de verdad del dominio vive en `knowledge-base/` (índice en [README](knowledge-base/README.md)). **Leé el archivo relevante ANTES de implementar.**

| Archivo | Cuándo leerlo |
|---------|---------------|
| [01_vision_y_objetivos.md](knowledge-base/01_vision_y_objetivos.md) | Entender propósito, alcance del MVP y fuera de alcance |
| [03_actores_y_roles.md](knowledge-base/03_actores_y_roles.md) | Auth, RBAC, rutas públicas y privadas |
| [04_modelo_de_datos.md](knowledge-base/04_modelo_de_datos.md) | Entidades, ERD, máquina de estados, zona horaria, seed |
| [05_reglas_de_negocio.md](knowledge-base/05_reglas_de_negocio.md) | Reglas codificadas (RN-XX-NN) y tabla de mails |
| [06_funcionalidades.md](knowledge-base/06_funcionalidades.md) | Historias de usuario (US-001 a US-022) y criterios de aceptación |
| [07_flujos_principales.md](knowledge-base/07_flujos_principales.md) | Flujos E2E (login, reserva, token, recordatorio, bloqueo, agenda) |
| [08_arquitectura_propuesta.md](knowledge-base/08_arquitectura_propuesta.md) | Directorios, disponibilidad, seguridad, env vars, tests, borrador de endpoints |
| [09_decisiones_y_supuestos.md](knowledge-base/09_decisiones_y_supuestos.md) | Decisiones DD-xx y supuestos SU-xx |
| [10_preguntas_abiertas.md](knowledge-base/10_preguntas_abiertas.md) | Inconsistencias y preguntas a resolver ANTES de codear |
| [11_docker_y_entornos.md](knowledge-base/11_docker_y_entornos.md) | Docker Compose, puertos, `.env`, Mailpit vs Gmail |

> ⚠️ Resolver (o confirmar con el equipo) las preguntas de prioridad **Alta** de `10_preguntas_abiertas.md` antes de arrancar el primer change:
> - Criterios de evaluación de la entrega (tests, documentación, demo en vivo).
> - Presentación local con Docker o despliegue (define `APP_FRONTEND_BASE_URL` y los links de los emails).
> - Cuenta de Gmail para la demo y quién genera la contraseña de aplicación.
> - Reparto de las 6 personas (ya propuesto en `CHANGES.md`; confirmarlo).
> - Formato y ubicación del contrato de API (OpenAPI en `docs/api/`, ya asumido por C-01; confirmarlo).

---

## Skills Disponibles

<!-- Fuente de verdad: .atl/skill-registry.md (generado por skill-registry). -->

| Agente | Rol | Skills que carga |
|--------|-----|------------------|
| **Backend** | Spring Boot / JPA / seguridad / PostgreSQL | `springboot-patterns`, `jpa-patterns`, `spring-boot-security-jwt`, `springboot-security`, `postgres-patterns`, `database-migrations`, `java-coding-standards` |
| **Frontend** | TypeScript vanilla, Vite, accesibilidad, responsive | `typescript-best-practices`, `zod`, `accessibility`, `accessibility-review`, `responsive-design` |
| **Infra** | Docker Compose, Dockerfiles, nginx | `docker-compose-patterns`, `multi-stage-dockerfile`, `nginx-configuration`, `docker-patterns` |
| **Contrato / API** | OpenAPI 3.1 design-first, tipos y ejemplos | `openapi-spec-generation` |
| **Testing** | TDD estricto, Testcontainers, Vitest, verificación pre-PR | `tdd`, `springboot-tdd`, `spring-boot-test-patterns`, `vitest`, `springboot-verification`, `systematic-debugging` |
| **Orquestación** | OPSX / foundation / docs | `active-orchestrator`, `kb-creator`, `roadmap-generator`, `agent-instruction`, `skill-registry`, `skill-creator`, `security-and-hardening` (revisión previa a la demo) |

Cargá la skill correspondiente al contexto ANTES de escribir código.

> **No aplican** `react-testing`, `tanstack-query-best-practices` ni `vercel-react-best-practices`: son de React y este proyecto es TypeScript vanilla.

> Los compact rules de cada skill los resuelve el orquestador desde `.atl/skill-registry.md` (generado por `skill-registry`; no versionado — no está en el repo).

---

## Roadmap de Changes

El plan de implementación completo está en [CHANGES.md](CHANGES.md). Resumen:

- **Total**: 25 changes en 5 fases (0 gates de coordinación, 1 backend por dominio, 2 frontend por pantalla, 3 integración, 4 opcionales). C-01 a C-21 son el MVP; C-22 a C-25 son opcionales y **recortables** si no se llega.
- **Camino crítico** (8): `C-01 → C-02 → C-03 → C-06 → C-07 → C-08 → C-20 → C-21`. Cadena casi crítica: `C-03 → C-11 → C-12 → C-21`.
- **Gates**: nadie se ramifica antes de que C-01 (contrato) y C-02 (docker-scaffold) estén en `main`; C-03 (entidades y seed) es el mini-gate que desbloquea todo el backend; C-21 cierra el MVP.
- **Primer change**: `C-01` (`contrato-api`) → `/opsx:propose contrato-api`.
- **Reparto** (detalle y carpetas en `CHANGES.md`): Int 1 contrato, entidades base y auth (C-01, C-03, C-04, C-13); Int 2 Docker y configuración del consultorio (C-02, C-05, C-10, C-14, C-15); Int 3 disponibilidad y área pública (C-06, C-17, C-18); Int 4 turnos backend (C-07, C-08); Int 5 agenda front (C-19, C-20); Int 6 notificaciones, pacientes e integración (C-11, C-12, C-09, C-16, C-21).

**Antes de cualquier `/opsx:propose`**: leé [CHANGES.md](CHANGES.md), identificá las dependencias del change y los archivos de "Leer antes".

---

## Reglas Duras (específicas del proyecto)

> Reglas globales ya definidas en `~/.claude/CLAUDE.md` (orquestador, governance, TDD, engram): el proyecto las hereda. Acá viven solo las reglas **específicas de este proyecto** + las universales que el global no cubra.

Son contrato; romperlas es un defecto.

**Dominio y datos**

1. NUNCA usar datos reales de pacientes ni de personas (seed, tests, fixtures, capturas, demos) → siempre datos ficticios (ej. `odontologo@ejemplo.com`, DNI `99999999`).
2. NUNCA commitear `.env`, la contraseña de aplicación de Gmail ni otros secretos → solo `.env.example` con valores de ejemplo.
3. NUNCA confiar solo en la validación Java para evitar turnos superpuestos → la constraint `EXCLUDE USING gist` de Postgres (`schema-extra.sql`) es la garantía; su violación se traduce a 409.
4. NUNCA usar `LocalDateTime` para instantes → `timestamptz` + `OffsetDateTime`/`Instant`; zona `America/Argentina/Buenos_Aires` para mostrar y calcular ventanas.

**Backend y contrato**

5. NUNCA cambiar un endpoint, DTO o código de error por fuera de `docs/api/openapi.yaml` → si el contrato cambia, se cambia primero ahí (en un change/PR propio) y se avisa al equipo.
6. NUNCA devolver entidades JPA desde los controllers → siempre DTOs; capas controller → service → repository.
7. NUNCA usar códigos HTTP fuera de la tabla de RN-GLO-04 (400, 401, 404, 409, 422) → con el formato de error del contrato.
8. NUNCA mandar avisos al paciente a otro email que el `email_contacto` del turno, ni loguear tokens completos.

**Front y tests**

9. NUNCA agregar React u otro framework de UI → TypeScript vanilla con `strict: true` y prohibido `any`.
10. NUNCA usar H2 para tests de integración → Testcontainers con PostgreSQL 16 (la constraint EXCLUDE no existe en H2).
11. El front consume los tipos del contrato (`docs/api`) y usa los ejemplos JSON del contrato como mocks hasta que el backend esté listo.
12. Formularios accesibles: `<label>` asociado, foco visible y navegación completa con teclado (la reserva pública la usa cualquiera).

**Git y equipo**

13. Cada change de `CHANGES.md` va en su propia rama (ej. `feat/c-07-turnos-alta`) y entra a `main` por PR → nadie pushea directo a `main` después de mergear C-01.
14. Conventional commits en español (`feat(turnos): ...`, `fix(...)`, `docs(...)`).
15. Cada integrante commitea con su propio usuario de Git lo que le toca (lo exige la consigna) → no commitear por otro.
16. NUNCA modificar archivos de un change asignado a otro integrante sin avisar → coordinar en el PR.

---

## Flujo de Trabajo

```
1. Leer la KB relevante (knowledge-base/)        → entender el dominio
2. Identificar el change en CHANGES.md           → respetar dependencias y gates
3. /opsx:propose C-NN-nombre                     → proposal + design + specs + tasks
4. /opsx:apply                                   → implementar las tasks (cargando skills, TDD)
5. /opsx:archive C-NN-nombre + marcar [x]        → cerrar el change (PR a main)
```

Aplicar TODAS las reglas duras en cada paso. Ante conflicto entre la KB y este archivo, las reglas duras prevalecen.
