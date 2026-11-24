# Turnos Odontología — Base de Conocimiento

Base de conocimiento generada a partir de `docs/discovery/discovery.md`, `docs/discovery/informe-discovery.md` y las decisiones técnicas confirmadas por el usuario. Es la fuente canónica para implementar.

## Índice de Archivos

| Archivo | Contenido |
|---------|-----------|
| [01_vision_y_objetivos.md](01_vision_y_objetivos.md) | Propósito, objetivos por actor, alcance del MVP, fuera de alcance, referentes de UX, métricas |
| [02_descripcion_general.md](02_descripcion_general.md) | Stack, arquitectura general, integraciones, resumen de la API |
| [03_actores_y_roles.md](03_actores_y_roles.md) | Odontólogo y paciente, matriz RBAC, rutas públicas y privadas |
| [04_modelo_de_datos.md](04_modelo_de_datos.md) | ERD, entidades, restricción de exclusión, máquina de estados, zona horaria, seed |
| [05_reglas_de_negocio.md](05_reglas_de_negocio.md) | Reglas `RN-XX-NN` por dominio (incluye consentimiento RN-DAT-02, sillón RN-REC-01, alerta RN-NOT-06, tabla de mails y códigos HTTP RN-GLO-04) |
| [06_funcionalidades.md](06_funcionalidades.md) | Épicas, historias de usuario (US-001 a US-022) y criterios de aceptación (MVP / Opcional) |
| [07_flujos_principales.md](07_flujos_principales.md) | Diagramas de secuencia: login, reserva y carrera, token y 24 h, recordatorio y alerta al odontólogo, bloqueo, agenda |
| [08_arquitectura_propuesta.md](08_arquitectura_propuesta.md) | Directorios, cálculo de horarios libres, seguridad, token opaco, `btree_gist` y script idempotente, códigos HTTP, env vars, tests, borrador de endpoints REST |
| [09_decisiones_y_supuestos.md](09_decisiones_y_supuestos.md) | Decisiones DD-01 a DD-14 (incluye trade-off vs Flyway) y supuestos SU-01 a SU-07 |
| [10_preguntas_abiertas.md](10_preguntas_abiertas.md) | Reglas y preguntas resueltas, inconsistencias, preguntas priorizadas, puntos del Discovery sin evidencia y supuestos sin validar |
| [11_docker_y_entornos.md](11_docker_y_entornos.md) | Docker Compose, puertos, `.env`, Mailpit vs Gmail, un solo comando |

## Mapa de navegación

- Entender el dominio: [01](01_vision_y_objetivos.md), [03](03_actores_y_roles.md).
- Entender los datos: [04](04_modelo_de_datos.md).
- Entender las reglas: [05](05_reglas_de_negocio.md).
- Entender la arquitectura: [02](02_descripcion_general.md), [08](08_arquitectura_propuesta.md), [11](11_docker_y_entornos.md).
- Implementar: [07](07_flujos_principales.md), [06](06_funcionalidades.md).
- Antes de codificar: [10](10_preguntas_abiertas.md) y [09](09_decisiones_y_supuestos.md).
- Contrato de API (primer change): sección "Borrador de recursos y endpoints REST" de [08](08_arquitectura_propuesta.md).

## Resumen Ejecutivo

Agenda online para un odontólogo independiente (un odontólogo, un sillón): el profesional gestiona agenda, prestaciones, horarios y bloqueos con login JWT, y el paciente reserva sin cuenta desde un link público y cancela (hasta 24 h antes) o confirma (solo desde el recordatorio, dentro de las 48 h previas) por links con token recibidos por email al email del turno (Confirmación de reserva, Recordatorio a los pendientes, Aviso sin botones si el turno nace confirmado, reprogramación y aviso de cancelación). Los turnos creados con menos de 24 h nacen `CONFIRMADO`. El odontólogo recibe una alerta por email de los turnos pendientes a menos de 24 h y los confirma o cancela desde la agenda. El no solapamiento lo garantiza una restricción de exclusión de PostgreSQL.

Stack: TypeScript vanilla + Vite + nginx; Java 21 + Spring Boot 3.5 + Maven + Hibernate (`ddl-auto=update`) + PostgreSQL 16; Spring Mail (Mailpit en dev, Gmail en la demo); todo con Docker Compose. Entrega del MVP: 2026-11-25, equipo de 6 en paralelo, con el contrato de API como primer change.
