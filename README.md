# Catalog File Processing Service

This is the reference implementation of a mentoring project. A Spring Boot service scans a folder for partner
catalog files (JSON or CSV), parses and validates them, saves the products to a database, and moves each file
to `processed/` or `failed/`.

- Task description: [docs/TASK.md](docs/TASK.md)
- Mentor guide (how to share steps with a student): [docs/MENTORING.md](docs/MENTORING.md)

## Steps (git tags)

| Tag | Step | Main topics |
|---|---|---|
| `step-0-skeleton` | Spring Initializr skeleton | – |
| `step-1` (branch) | Environment & configuration | `@ConfigurationProperties`, `@PostConstruct` |
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

Step 1 is a **branch**, not a tag: one squashed commit on top of `step-0-skeleton`. It includes the Step 1
fixes that originally came with the first `step-2` commits (relative paths, SLF4J logging, Lombok `provided`
scope), so `./mvnw test` passes. The branch is not an ancestor of `step-2`, so `git diff step-1 step-2` works,
but commit ranges such as `step-1..step-2` also list the original Step 1 commits.

## Run

```bash
./mvnw test
./mvnw spring-boot:run      # drop files into data/input-folder
```

The reference steps 1–8 use an in-memory H2 database. TASK.md v2 asks the student to use PostgreSQL in Docker
from Step 1.
