# Skill Registry

**Delegator use only.** Any agent that launches sub-agents reads this registry to resolve compact rules, then injects them directly into sub-agent prompts. Sub-agents do NOT read this registry or individual SKILL.md files.

See `_shared/skill-resolver.md` for the full resolution protocol.

Project: turnos-odontologia-bernardon. Stack: TypeScript vanilla + Vite (nginx); Java 21 + Spring Boot 3.5 + Maven + JPA/Hibernate + PostgreSQL 16; JWT; Spring Mail; Docker Compose; OpenAPI contract; Strict TDD (JUnit 5, Vitest).

## User Skills

(C = C:/Users/exe45/.claude/skills, A = C:/Users/exe45/.agents/skills)

| Trigger | Skill | Path | Relevance |
|---------|-------|------|-----------|
| Creating/validating OpenAPI 3.1 contracts, design-first API docs | openapi-spec-generation | C/openapi-spec-generation/SKILL.md | HIGH (contract change) |
| WCAG 2.2 audits, keyboard nav, screen reader | accessibility | C/accessibility/SKILL.md | HIGH (public booking form) |
| WCAG 2.1 AA review of a page/design | accessibility-review | C/accessibility-review/SKILL.md | HIGH |
| Reading/editing .ts files | typescript-best-practices | C/typescript-best-practices/SKILL.md | HIGH (vanilla front) |
| Creating/debugging compose.yaml | docker-compose-patterns | C/docker-compose-patterns/SKILL.md | HIGH |
| Nginx reverse proxy / SPA serving | nginx-configuration | C/nginx-configuration/SKILL.md | HIGH |
| Dockerfiles, multi-stage builds | multi-stage-dockerfile | C/multi-stage-dockerfile/SKILL.md | HIGH |
| JPA entities, relationships, N+1, transactions | jpa-patterns | C/jpa-patterns/SKILL.md | HIGH |
| Spring Boot REST layers, validation, services | springboot-patterns | C/springboot-patterns/SKILL.md | HIGH |
| JWT auth with Spring Security 6 / JJWT | spring-boot-security-jwt | C/spring-boot-security-jwt/SKILL.md | HIGH |
| Pre-PR Spring Boot verification loop | springboot-verification | C/springboot-verification/SKILL.md | MEDIUM |
| Schema/data migrations, PostgreSQL | database-migrations | C/database-migrations/SKILL.md | MEDIUM |
| Test-first, red-green-refactor | tdd | C/tdd/SKILL.md | HIGH (Strict TDD) |
| Writing Vitest tests, mocks, coverage | vitest | C/vitest/SKILL.md | HIGH (front tests) |
| Schema validation, z.object, safeParse | zod | C/zod/SKILL.md | MEDIUM (front boundary validation) |
| Responsive layouts, fluid type, grid | responsive-design | C/responsive-design/SKILL.md | MEDIUM |
| Spring Boot tests (MockMvc, Testcontainers) | spring-boot-test-patterns | A/spring-boot-test-patterns/SKILL.md | HIGH |
| Spring Boot TDD (JUnit 5) | springboot-tdd | A/springboot-tdd/SKILL.md | HIGH |
| Spring Security hardening | springboot-security | A/springboot-security/SKILL.md | MEDIUM |
| PostgreSQL patterns, indexes, constraints | postgres-patterns | A/postgres-patterns/SKILL.md | MEDIUM |
| Java coding standards | java-coding-standards | A/java-coding-standards/SKILL.md | MEDIUM |
| Docker patterns | docker-patterns | A/docker-patterns/SKILL.md | MEDIUM |
| Debugging methodology | systematic-debugging | A/systematic-debugging/SKILL.md | MEDIUM |
| Security/hardening review | security-and-hardening | A/security-and-hardening/SKILL.md | MEDIUM |
| React component tests | react-testing | C/react-testing/SKILL.md | NOT APPLICABLE (React; project is vanilla TS) |
| TanStack Query (React) | tanstack-query-best-practices | C/tanstack-query-best-practices/SKILL.md | NOT APPLICABLE (React) |
| React/Next perf | vercel-react-best-practices | C/vercel-react-best-practices/SKILL.md | NOT APPLICABLE (React) |

Other installed skills not relevant (Angular, Redis, MySQL, Supabase, Upstash, xlsx, playwright-cli, webapp-testing, ui-styling, frontend-design, impeccable, spring-boot-event-driven-patterns, java-springboot, javascript-testing-patterns) are omitted. Foundation-flow skills (active-orchestrator, kb-creator, roadmap-generator, find-skill, agent-instruction, discovery-research, web-scraper, skill-creator) are workflow skills, not coding skills.

## Compact Rules

Pre-digested rules per skill. Delegators copy matching blocks into sub-agent prompts as `## Project Standards (auto-resolved)`.

### openapi-spec-generation
- Design-first: write OpenAPI 3.1 (`openapi: 3.1.0`) before code; the contract is the first change and blocks parallel work.
- Reuse with `$ref` (schemas, parameters, responses); keep naming style consistent.
- Document ALL error responses (400/401/403/404/409/422) with a shared error schema; add real examples.
- Define security schemes (bearer JWT) explicitly; public booking endpoints declare `security: []`.
- Be explicit about nullability (3.1: `type: [string, "null"]`); version the API (URL) and spec (semver).
- No generic descriptions; no hardcoded URLs (use server variables).

### accessibility
- Target WCAG 2.2 AA. Every input has a visible `<label>`; errors tied via aria-describedby and announced (role="alert"/aria-live).
- Fully keyboard operable, visible focus, logical tab order, no keyboard traps; use native button/a/form.
- Icon buttons need accessible names (aria-label / visually-hidden text); images need alt ("" if decorative).
- Contrast >= 4.5:1 text, 3:1 UI; do not convey state by color alone (appointment status needs text/icon).
- Landmarks (header/main/nav), one h1, heading order; `lang` on html; touch targets >= 24px (prefer 44px).
- Automated tools find only a subset: verify with keyboard and the accessibility tree.

### accessibility-review
- Audit against WCAG 2.1 AA: contrast, keyboard navigation, touch targets, screen reader behavior, form labels/errors, focus management.
- Report findings by severity with a concrete fix per item; check agenda grids and modals (focus trap, Esc, return focus).

### typescript-best-practices
- `unknown` over `any`; external data (fetch responses) is `unknown` and parsed at the boundary into a named domain type.
- No `as` casts except after validation; prefer `satisfies`; narrow via discriminant > `in` > typeof/instanceof > type guard.
- Discriminated unions (`kind`) for variants (e.g. appointment status); exhaustive `never` check in default arms.
- Use runtime schema (zod) + `z.infer` instead of hand-written property guards; brand primitives where mixups are likely (ids, DNI).
- Derive types with Pick/Omit/ReturnType before new interfaces; pass object args; no `console.log` in shipped code.
- Do not mock what you can run; mock only what cannot run locally.

### docker-compose-patterns
- Use `compose.yaml` (not docker-compose.yml); pin image tags (never `latest`), e.g. postgres:16, pinned mailpit.
- `depends_on` with `condition: service_healthy`; every such dependency needs a `healthcheck` (db: `pg_isready`; start with interval 5s, timeout 3s, retries 3, start_period 10s).
- `restart: unless-stopped` for long-running services; named volumes for Postgres data.
- Secrets/config via env vars / `.env` (not committed); `compose.override.yaml` for dev-only settings.
- Distroless images without shell: use a healthcheck sidecar sharing network_mode.

### nginx-configuration
- Serve the Vite build as SPA (`try_files $uri /index.html`); proxy `/api` to the backend service (proxy_set_header Host, X-Real-IP, X-Forwarded-*).
- Add security headers, gzip, long cache for hashed assets, no-cache for index.html.
- Keep error and access logs separate; rate-limit abusive endpoints (public booking) if needed.

### multi-stage-dockerfile
- Separate build and runtime stages (node build -> nginx; Maven/JDK 21 build -> JRE 21 runtime); copy only artifacts.
- Order layers for cache (dependency manifests first); run as non-root; pin base image versions; use .dockerignore.

### jpa-patterns
- Lazy by default; avoid EAGER on collections; fix N+1 with `JOIN FETCH` or DTO projections.
- Entities: `@Enumerated(EnumType.STRING)`, explicit column constraints, indexes via `@Table(indexes=...)`; auditing via `@EnableJpaAuditing`.
- Transactions in the service layer (`@Transactional`, `readOnly=true` for reads); never in controllers.
- Pagination with Pageable/Page; projections for lightweight reads; tune HikariCP pool.
- Overlap protection needs a DB-level constraint (PostgreSQL exclusion); map constraint violations to 409.

### springboot-patterns
- Layers controller -> service -> repository; constructor injection; thin controllers returning DTO records, never entities.
- Validate with `@Valid` + Bean Validation on request records; central `@RestControllerAdvice` for consistent error bodies.
- `@Transactional` on service methods; profiles for dev/prod; config via env vars.
- Structured logging (SLF4J), no secrets/PII in logs; paginate list endpoints.

### spring-boot-security-jwt
- Stateless `SecurityFilterChain` (Spring Security 6): no sessions, CSRF off for pure token API, `OncePerRequestFilter` validating Bearer JWT.
- JJWT 0.12.6 (api compile; impl+jackson runtime); secret from env (>= 256 bits), never hardcoded; validate issuer and expiry.
- Hash passwords with BCrypt (`PasswordEncoder`); generic error on login failure.
- Permit public booking and token confirm/cancel endpoints explicitly; everything else authenticated; `@PreAuthorize` for roles.
- Use `spring-security-test` for secured endpoint tests; cookies must be HttpOnly/Secure if used.

### springboot-verification
- Run full loop before PR: `mvn verify` (build + unit + integration with Testcontainers), static analysis (SpotBugs/PMD/Checkstyle if configured), JaCoCo coverage, dependency/secret scan, diff review; produce a pass/fail report.

### database-migrations
- Prefer reversible, forward-only changes; expand-contract for renames; concurrent indexes on large tables; batched backfills.
- Project uses Hibernate ddl-auto: constraints Hibernate cannot express (PostgreSQL exclusion constraint with btree_gist) must be applied via init SQL / migration and be idempotent.

### tdd
- Red before green: failing test first, minimal code, one slice at a time; refactor after green.
- Test behavior through public seams, not internals; confirm seams before writing tests.
- Avoid implementation-coupled mocks, tautological asserts, and horizontal slicing (all tests first).

### vitest
- Jest-compatible API (`describe/it/expect/vi`); config shares vite.config via `defineConfig`; environment `jsdom`/`happy-dom` for DOM tests.
- Mock with `vi.fn/vi.spyOn/vi.mock`; fake timers via `vi.useFakeTimers`; mock fetch at the boundary.
- Coverage via V8 (`--coverage`); in CI use `vitest run`.

### zod
- Define schemas with `z.object`; derive types with `z.infer`; use `safeParse` at boundaries (API responses, form input) and handle `error.issues`.
- Prefer built-in validators (`z.string().email()`, `.min/.max`); use transforms/refinements sparingly.

### responsive-design
- Mobile-first breakpoints; fluid typography with `clamp()`; CSS Grid/Flex; container queries for component-level adaptation.
- Booking form and agenda must work at phone width (no horizontal scroll); touch targets >= 44px.

### spring-boot-test-patterns
- Slice tests (`@WebMvcTest`, `@DataJpaTest`) for speed; `@SpringBootTest` + Testcontainers PostgreSQL for integration (real Postgres incl. exclusion constraints).
- MockMvc for controllers; use the real DB for concurrency/overlap tests instead of mocks.

### springboot-tdd
- JUnit 5 + AssertJ + Mockito; failing test first; name tests by behavior; Testcontainers for DB; deterministic tests (inject Clock, no sleeps).

### springboot-security
- Validate all input, parameterized queries only, CORS restricted to known origins, secrets via env, rate-limit sensitive endpoints, never log tokens/passwords, generic auth errors.

### postgres-patterns
- Proper types (timestamptz), constraints, indexes per query pattern; exclusion constraints (`EXCLUDE USING gist` with btree_gist and tstzrange) for non-overlap; avoid unbounded queries.

### java-coding-standards
- Java 21: records for DTOs, immutability, `Optional` only for returns, meaningful exceptions, constructor injection, no raw types, small focused classes.

### docker-patterns
- Pinned images, non-root user, minimal layers, healthchecks, .dockerignore, no secrets in images.

### systematic-debugging
- Reproduce first, form hypotheses, change one variable at a time, find root cause before fixing; add a regression test.

### security-and-hardening
- Validate/escape inputs, least privilege, secure defaults, no secrets in repo, dependency hygiene, safe error messages (no stack traces to clients).

## Project Conventions

| File | Path | Notes |
|------|------|-------|
| (none yet) | - | AGENTS.md / CLAUDE.md do not exist at project root yet. Project context lives in knowledge-base/ (10 canonical files + README), CHANGES.md and docs/discovery/. |

Read the convention files listed above for project-specific patterns and rules. All referenced paths have been extracted — no need to read index files to discover more.
