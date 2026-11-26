# Etapa 5: decisión sobre las skills recomendadas por `find-skill`

`find-skill` solo recomienda; la decisión de qué instalar fue del equipo. Abajo, una línea de justificación por cada recomendación. El resultado quedó indexado en [`.atl/skill-registry.md`](../../.atl/skill-registry.md).

Fecha de la decisión: 2026-11-23.

## Instaladas en esta etapa

| Skill | Decisión | Justificación |
|---|---|---|
| `openapi-spec-generation` | Instalada | El contrato `docs/api/openapi.yaml` (C-01) es el gate de todo el equipo: hacía falta escribirlo design-first y validarlo. |
| `typescript-best-practices` | Instalada | El front es TypeScript vanilla con `strict: true` y sin `any`, sin un framework que imponga estructura. |
| `accessibility` | Instalada | La reserva pública la usa cualquier paciente sin cuenta; la regla dura 12 exige labels, foco visible y teclado. |
| `accessibility-review` | Instalada | Complementa a la anterior: sirve para auditar cada pantalla (WCAG 2.1 AA) antes de cerrar su change. |
| `docker-compose-patterns` | Instalada | La entrega se levanta con un único `docker compose up` de cuatro servicios con health checks (C-02). |
| `nginx-configuration` | Instalada | nginx sirve el build del front y hace proxy de `/api` al backend. |

## Ya disponibles, se mantienen

| Skill | Decisión | Justificación |
|---|---|---|
| `springboot-patterns` | Se usa | Capas controller → service → repository y DTOs (regla dura 6). |
| `jpa-patterns` | Se usa | Entidades, relaciones y transacciones del modelo de turnos. |
| `spring-boot-security-jwt` | Se usa | Login del odontólogo con JWT y BCrypt (C-04). |
| `database-migrations` | Se usa | `schema-extra.sql` con la constraint `EXCLUDE USING gist` contra solapamientos. |
| `multi-stage-dockerfile` | Se usa | Imágenes de build y runtime separadas para backend y front. |
| `tdd` | Se usa | Strict TDD habilitado en todo el ciclo OPSX. |
| `springboot-verification` | Se usa | Verificación previa a cada PR del backend (build + tests con Testcontainers). |
| `vitest` | Se usa | Tests del front. |
| `zod` | Se usa | Validación de datos en el borde del front (respuestas de la API, formularios). |
| `responsive-design` | Se usa | El paciente reserva desde el celular. |

## Descartadas

| Skill | Decisión | Justificación |
|---|---|---|
| `react-testing` | No se usa | Es para React y el front es TypeScript vanilla (regla dura 9). |
| `tanstack-query-best-practices` | No se usa | Depende de React; el front hace `fetch` directo contra el contrato. |
| `vercel-react-best-practices` | No se usa | Es de React/Next.js, fuera del stack. |
| Angular, Redis, MySQL, Supabase, Upstash | No se usan | Tecnologías que no están en el stack de la knowledge-base. |
