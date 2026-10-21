# Catalog File Processing Service

This is the reference implementation of a mentoring project. A Spring Boot service scans a folder for partner
catalog files (JSON or CSV), parses and validates them, saves the products to a database, and moves each file
to `processed/` or `failed/`.

- Task description: [docs/TASK.md](docs/TASK.md)
- Mentor guide (how to share steps with a student): [docs/MENTORING.md](docs/MENTORING.md)

## Steps (git tags)

| Tag | Step | Main topics |
|---|---|---|
| `step-1` (branch) | Environment & configuration | `@ConfigurationProperties`, `@PostConstruct`, Docker Compose PostgreSQL, Testcontainers |
| `step-2` | Modeling & data mapping | records, JPA entities, Jackson JSON/CSV config, `@Qualifier` |
| `step-3` | Parsing layer | Strategy, Factory/Resolver, `List<>` injection |
| `step-4` | File scanner & mover | `java.nio.file` |
| `step-5` | Validation | Bean Validation, Composite + Strategy, `Clock` |
| `step-6` | Persistence | Spring Data JPA, upsert, `@Transactional` |
| `step-7` | Concurrent processing | `ExecutorService`, `CompletableFuture`, `@PreDestroy`, `REQUIRES_NEW` |
| `step-8` | Scheduling & logging | `@Scheduled`, Logback + MDC, sample files |
| – | Step 9: DB structure (Flyway) | described in TASK.md, no reference code yet |
| – | Step 10: Transactions | described in TASK.md, no reference code yet |

The diff for any step: `git diff step-3 step-4`.

Step 1 is a **branch**, not a tag, built on the initial Spring Initializr commit. It includes the Step 1
fixes that originally came with the first `step-2` commits (relative paths, SLF4J logging, Lombok `provided`
scope), so `./mvnw test` passes. The branch is not an ancestor of `step-2`, so `git diff step-1 step-2` works,
but commit ranges such as `step-1..step-2` also list the original Step 1 commits.

## Run

Requirements: Java 21 and Docker (Docker Desktop or another engine).

```bash
docker compose up -d        # PostgreSQL 16 on localhost:5432, wait until it is "healthy"
docker compose ps
./mvnw spring-boot:run      # drop files into data/input-folder
./mvnw test                 # starts its own PostgreSQL with Testcontainers
docker compose down         # stop the DB (add -v to also delete the data volume)
```

- `spring-boot-docker-compose` (dev only) also starts `docker-compose.yml` automatically on `spring-boot:run`,
  so the first command is optional. It is skipped in tests.
- DB settings come from env variables with local defaults: `DB_URL`, `DB_USER`, `DB_PASSWORD` (app) and
  `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_PORT` (compose). The defaults are `catalog` / `catalog` / `catalog`.
- The JVM runs with `-Duser.timezone=UTC` (set in `pom.xml`), because PostgreSQL rejects legacy zone names
  such as `Europe/Kiev`. If you run the app from the IDE, add the same VM option.

From `step-1` on, the app uses PostgreSQL. The tags `step-2` … `step-8` still use the original in-memory H2 setup.
