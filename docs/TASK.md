# Catalog File Processing Service — Task Description (v2)

> v2 is based on the reference implementation in this repository (branch `step-1`, tags `step-2` … `step-8`).
> Changes from v1: PostgreSQL in Docker from Step 1; two new database steps (9 and 10);
> class names, signatures, and acceptance criteria now match the reference code.
> Kafka is out of scope.

## 1. Goal

Build a service that automatically ingests product catalog data from external partners (suppliers,
marketplaces, B2B clients) who send files (JSON, CSV) instead of calling an API.

The service:

1. **Scans** an input folder on a schedule (`@Scheduled` + cron) and picks up `*.json` and `*.csv` files.
2. **Parses** each file into one common DTO. The service picks the parser by file extension.
3. **Validates** the file and each product (Bean Validation + custom business rules).
4. **Persists** valid products to PostgreSQL. If the partner already has a product with the same SKU, the service updates it instead of inserting a duplicate.
5. **Moves** the file to `processed/`, or to `failed/` together with a `.error` file that explains why.
6. Processes files **in parallel**. One broken file must never block or crash processing of the other files.

## 2. Tech stack

| Area | Technology |
|---|---|
| Language / build | Java 21, Maven (wrapper `./mvnw`) |
| Framework | Spring Boot 4.x (Web MVC, Data JPA, Validation, Scheduling) |
| Structured data | Jackson 3 (`tools.jackson.*`): `ObjectMapper` for JSON, `CsvMapper` + `CsvSchema` (`jackson-dataformat-csv`) for CSV |
| Filesystem | `java.nio.file`: `Files`, `Path`, `DirectoryStream`, `StandardCopyOption` |
| Date & time | `java.time`: `Instant`, `LocalDate`, `Clock`, `Duration`, `ZoneOffset` |
| Concurrency | `ExecutorService`, `CompletableFuture` (optional: virtual threads) |
| Database | **PostgreSQL 16+ in Docker (docker compose)**, Spring Data JPA / Hibernate, **Flyway** (from Step 9) |
| Tests | JUnit 5, Mockito, AssertJ, `@DataJpaTest`; **Testcontainers PostgreSQL** (required from Step 9) |
| Misc | Lombok, records, SLF4J + Logback (MDC) |

> Note: v1 listed OpenCSV. The reference implementation uses Jackson CSV only, so do not add the
> `opencsv` dependency (or remove it if it is already there).

## 3. Data model

Each file contains a product export from **one** partner.

### JSON format

```json
{
  "partnerId": "PARTNER-A",
  "exportDate": "2026-03-23T10:30:00Z",
  "products": [
    {
      "name": "Apple Fruit",
      "sku": "802999",
      "price": 41238.0,
      "specialPrice": 35000.0,
      "specialFrom": "2026-03-01",
      "specialTo": "2026-04-01",
      "state": "ACTIVE",
      "brand": "Shelf 3",
      "categories": ["Golden apple Bundles"],
      "imageUrl": "https://img.example.com/apple.jpg"
    }
  ]
}
```

### CSV format

```csv
Name,SKU,Price,Special Price,Special From,Special To,State,Brand,Category,Image URL
Banana Fruit,000000001,180.0,,,,ACTIVE,Angel Of M,Fruits,https://img.example.com/banana.jpg
Apple Fruit,802999,41238.0,35000.0,2026-03-01,2026-04-01,ACTIVE,Shelf 3,Golden apple Bundles,https://img.example.com/apple.jpg
```

A CSV file has no `partnerId` or `exportDate` columns. Take both from the file name, which must follow the
pattern `products_{partnerId}_{yyyy-MM-dd}.csv`, for example `products_PARTNER-A_2026-03-23.csv`. The date in the
file name is a `LocalDate`. Convert it to an `Instant` at the start of the day in UTC.

### Product fields

| # | Field | Type | Rules / notes |
|---|---|---|---|
| 1 | name | String | `@NotBlank` |
| 2 | sku | String | `@NotBlank`; unique per partner (upsert); unique within one file |
| 3 | price | BigDecimal | `@NotNull @Positive` |
| 4 | specialPrice | BigDecimal | optional, `@Positive`, must be `< price` |
| 5 | specialFrom | LocalDate | required if `specialPrice` is set |
| 6 | specialTo | LocalDate | required if `specialPrice` is set; must be after `specialFrom` and not in the past |
| 7 | state | enum `ProductState` | `ACTIVE` / `INACTIVE`, `@NotNull` |
| 8 | brand | String | optional (Step 9 moves it to a `brands` table) |
| 9 | categories | List&lt;String&gt; | JSON: array; CSV: one `Category` column, wrapped into a one-element list |
| 10 | imageUrl | String | optional, `@URL` |

### File-level fields

| Field | Type | Rules |
|---|---|---|
| partnerId | String | not blank |
| exportDate | Instant | not in the future, not older than `file-processing.max-export-age-days` (default 30) |

## 4. Competency coverage

| Competency | Where it is covered | Evidence (reference code) |
|---|---|---|
| Manage files with language filesystem capabilities | Steps 1, 4, 7, 8 | `Files.createDirectories` on startup; `DirectoryFileScanner` (`DirectoryStream` + glob); `DefaultFileMover` (`Files.move`, `Files.writeString` for `.error`); `@TempDir` tests |
| Handle structured data with libraries | Steps 2, 3, 5 | Jackson JSON (`JsonMapperBuilderCustomizer`), Jackson CSV (`CsvMapper`, `CsvSchema`, `CsvReadFeature.EMPTY_STRING_AS_NULL`), records, Bean Validation on nested DTOs |
| Manage application lifecycle via a framework | Steps 1, 7, 8 (+ Docker in Step 1) | `@PostConstruct` directory creation; `@Validated @ConfigurationProperties` (fail-fast on startup); `@PreDestroy` graceful executor shutdown; `@EnableScheduling` / `@Scheduled`; optionally `spring-boot-docker-compose` to manage the DB container lifecycle |
| Implement framework dependency injection principles | All steps, mostly 2, 3, 5 | constructor injection only; `List<FileParser>` injection; `@Qualifier("csvObjectMapper")`; `@Bean` + `@Primary` composite validator; `Clock` bean; interfaces (`FileScanner`, `FileMover`) for testability |
| Use design patterns to create new structures | Steps 3, 5, 7 | Strategy (`FileParser`, `ProductExportValidator`), Factory/Resolver (`FileParserFactory`), Composite (`CompositeProductExportValidator`), static factory methods + cached instance (`ValidationResult.ok()`, `ProcessingResult.success()`), Value Object (records) |
| Manipulate date and time with language APIs | Steps 2, 3, 5, 6 | `Instant` vs `LocalDate`, `LocalDate → Instant` (`atStartOfDay(ZoneOffset.UTC)`), `Duration`, an injected `Clock` and `Clock.fixed()` in tests, date-range rules |
| Implement concurrency (bonus, from v1) | Step 7 | fixed thread pool, `CompletableFuture.supplyAsync/allOf/orTimeout`, per-file isolation, `REQUIRES_NEW` race handling for categories |
| Implement and modify database structure | Step 9 | Flyway baseline + migration scripts, constraints, normalization, data-validation checks |
| Wrap queries into transactions | Step 6 (introduction) + Step 10 (new) | explicit transaction boundaries, `TransactionTemplate`, propagation, commit and rollback tests |

## 5. General rules for every step

- Work on a branch per step (`step-N`) and open a pull request for review. Merge the PR before you start the next step.
- Every step must keep the application starting (`./mvnw spring-boot:run`) and all tests green (`./mvnw test`).
- Use constructor injection only (no field `@Autowired` in production code).
- Do not call `Instant.now()` or `LocalDate.now()` directly. Use the injected `Clock`.
- Use SLF4J for logging. No `System.out`.

---

## Step 1. Environment, configuration, and PostgreSQL in Docker

Set up the project, configuration, and local infrastructure. Make the application lifecycle visible: on
startup, validate the configuration, log it, and create the working directories.

**What to do**
- Dependencies: `spring-boot-starter-webmvc`, `-data-jpa`, `-validation`, `jackson-dataformat-csv`, PostgreSQL JDBC driver, Lombok (configure it as an annotation processor in `maven-compiler-plugin`); tests: `spring-boot-starter-test`, `-data-jpa-test` (Testcontainers).
- **Docker:** add a `docker-compose.yml` with a `postgres` service:
  - image `postgres:16` (or newer), port `5432`, a named volume for data, and a `healthcheck` (`pg_isready`)
  - DB name, user, and password from environment variables, with defaults for local dev (no real secrets in git)
  - Optional: add `spring-boot-docker-compose` (dev-only) so Spring starts and stops the container together with the application. This is a good lifecycle topic to discuss.
- Replace `application.properties` with `application.yml`:
  - `spring.datasource.*` pointing to PostgreSQL (`jdbc:postgresql://localhost:5432/catalog`), values from env variables, for example `${DB_URL:...}`
  - `spring.jpa.hibernate.ddl-auto: update` (temporary; Step 9 switches it to `validate` + Flyway)
  - `file-processing.input-dir`, `processed-dir`, `failed-dir` (relative paths, for example `data/input-folder`), `cron`, `thread-pool-size`, `max-export-age-days`
- `src/test/resources/application.yml` with a `test` profile (Testcontainers) so that tests do not depend on a running Docker container.
- `FileProcessingProperties` with `@ConfigurationProperties(prefix = "file-processing")`, enabled via `@ConfigurationPropertiesScan`.
- `@PostConstruct`: create `input/`, `processed/`, `failed/` with `Files.createDirectories` and log the effective configuration with SLF4J.
- Packages: `config, dto, model, parser, scanner, validation, processor, repository, service, exception`.
- README: how to start the DB (`docker compose up -d`) and the app.

**Done when**
- `docker compose up -d` starts PostgreSQL and its health check reports healthy.
- The application starts against PostgreSQL without errors, creates the directories, and logs the properties.
- `./mvnw test` (the context-loads test) passes.

> The reference branch `step-1` covers the whole step: Docker Compose PostgreSQL, Testcontainers, and the fixes
> from the first original `step-2` commits (relative paths, SLF4J logging, Lombok scope).
> Tip: if the JVM timezone is a legacy alias such as `Europe/Kiev`, PostgreSQL rejects the connection
> (`invalid value for parameter "TimeZone"`). Run the JVM with `-Duser.timezone=UTC`.

## Step 2. Modeling and data mapping

**What to do**
- Records `ProductExportFileDto(partnerId, Instant exportDate, List<ProductDto> products)` and `ProductDto` with the 10 fields above. Enum `ProductState`.
- JPA entities:
  - `Product`: id, name, sku, price (`precision 10, scale 2`), specialPrice, specialFrom, specialTo, state (`@Enumerated(STRING)`), brand, imageUrl, partnerId, sourceFileName, importedAt (`Instant`)
  - `Category`: id, `name` (`nullable = false, unique = true`), `@ManyToMany(mappedBy = "categories")`
  - `Product.categories`: `@ManyToMany` with a `@JoinTable(name = "product_categories")`, plus the helpers `addCategory`, `removeCategory`, `replaceCategories`
  - `equals` and `hashCode` must use business keys (`sku + partnerId`, `name`), not the generated id
- `ProductRepository extends JpaRepository<Product, Long>` with `findBySkuAndPartnerId`.
- `JacksonConfig`:
  - `JsonMapperBuilderCustomizer` → `MapperBuilder::findAndAddModules`. Extend Boot's auto-configured JSON mapper instead of replacing it.
  - `@Bean csvObjectMapper()` → `CsvMapper.builder().findAndAddModules().enable(CsvReadFeature.EMPTY_STRING_AS_NULL)`. Inject it with `@Qualifier("csvObjectMapper")`.

**Done when**
- Unit test: the JSON example deserializes into `ProductExportFileDto`, and the `Instant` and `LocalDate` fields are correct.
- `@DataJpaTest`: a product with categories can be saved and read back.

## Step 3. Parsing layer (Strategy + Factory/Resolver)

**What to do**
- `interface FileParser { ProductExportFileDto parse(Path file); boolean supports(String extension); }`
- `JsonFileParser`: uses the JSON `ObjectMapper` and `Files.readString`. Wraps Jackson and IO errors into `JsonParsingException`.
- `FileNameMetadata(partnerId, LocalDate exportDate)` and `FileNameParser.parse(String fileName)`. Use a precompiled `static final Pattern`. Throw `IllegalArgumentException` for a bad name or an invalid date.
- `CsvProductRow`: a flat record that matches the CSV columns 1:1, with a single `category` field.
- `CsvFileParser`: injects `@Qualifier("csvObjectMapper") ObjectMapper` and `FileNameParser`. Builds a static `CsvSchema` with `withSkipFirstDataRow(true)`, reads the rows with `readerFor(CsvProductRow.class).with(schema).readValues(...)`, maps each row to a `ProductDto` (category → `List.of(category)`), and converts the file-name date to an `Instant` (UTC). Wraps errors into `CsvParsingException`.
- `FileParserFactory` (works as a resolver: it looks up existing beans): injects `List<FileParser>` and has `getParser(extension)`. Throws `UnsupportedFileFormatException` if no parser supports the extension.

**Done when**
- Tests: JSON parsing with all fields and with minimal fields; malformed JSON → `JsonParsingException`.
- Tests: CSV parsing with all fields, empty optional fields, multiple rows, and a single category in a list; malformed CSV → `CsvParsingException`.
- Tests: `FileNameParser` handles valid names and rejects an invalid pattern or date.
- Tests: the factory returns the right parser for `json` and `csv`, and throws for `xml`.

## Step 4. File scanner and file mover (NIO)

**What to do**
- `interface FileScanner { List<Path> scan(Path dir) throws IOException; }` and `DirectoryFileScanner`, which uses `Files.newDirectoryStream(dir, "*.{json,csv}")`, keeps regular files only, and returns a sorted list.
- `interface FileMover { Path moveToProcessed(Path); Path moveToFailed(Path, String error); }` and `DefaultFileMover`:
  - `moveToProcessed`: `Files.createDirectories` + `Files.move(..., REPLACE_EXISTING)`
  - `moveToFailed`: move the file and write `<file>.error` with `Files.writeString`

**Done when** (tests use `@TempDir`)
- A directory with 2 JSON files, 1 CSV file, and other files → the scanner returns exactly the 3 matching paths, sorted.
- After `moveToProcessed`, the file exists only in `processed/`.
- After `moveToFailed`, the file and its `.error` file (with the expected message) are in `failed/`.

## Step 5. Validation (Composite + Strategy)

**What to do**
- Bean Validation annotations on the DTOs (`@NotBlank`, `@NotNull`, `@Positive`, `@URL`, `@NotEmpty List<@Valid ProductDto>`).
- `@Validated` on `FileProcessingProperties`, with `@NotBlank` on the directories and `@Min(1)` on the numeric properties, so the application refuses to start with a bad config.
- `ValidationResult(List<String> errors)` record: `ok()` (cached instance), `invalid(String...)`, `invalid(List)`, `valid()`, `merge(other)`. Make the list immutable with `List.copyOf`.
- `interface ProductExportValidator { ValidationResult validate(ProductExportFileDto dto); }` with these rules:
  1. `BeanConstraintValidator`: delegates to `jakarta.validation.Validator`. Error format: `products[0].name: must not be blank`.
  2. `ExportDateRangeValidator`: checks that `exportDate` is not in the future and not older than `maxExportAgeDays`. Uses the injected `Clock`.
  3. `SkuUniquenessWithinFileValidator`: no duplicate SKU within one file; the error reports both indexes.
  4. `SpecialPricingValidator`: if `specialPrice` is set, then `specialFrom` and `specialTo` are required, `specialFrom < specialTo`, `specialTo` is not in the past, and `specialPrice < price` (skip this comparison without an NPE if `price` is null).
- `CompositeProductExportValidator` runs **all** rules and merges the errors, so the partner gets the full report in one pass. Wire it in `ValidatorConfig` as the `@Primary @Bean` with an explicit, ordered list. Explain in the PR why this is not Chain of Responsibility.
- `TimeConfig`: `@Bean Clock clock()` → `Clock.systemUTC()`.

**Done when**: one focused test per rule (valid, blank name, negative price, future export date, too-old export date, special price without dates, special price ≥ price, null price without an NPE, from ≥ to, `specialTo` in the past, duplicate SKU) + a composite test that merges errors from several rules + `ValidationResult` tests.

> Review note: the reference tag `step-5` does not check that `specialTo` is not in the past yet. The student should implement it.

## Step 6. Persistence service and first transaction boundary

**What to do**
- `CategoryRepository.findByName`.
- `ProductImportService.importProducts(ProductExportFileDto dto, String sourceFileName)`, annotated `@Transactional`:
  - Upsert: `findBySkuAndPartnerId`. If found, update the product; otherwise create a new one.
  - Map the fields, set `partnerId`, `sourceFileName`, and `importedAt = Instant.now(clock)`.
  - Resolve categories by name (find or create) and call `product.replaceCategories(...)`.
- Explain in the PR why the whole file is imported in **one** transaction. This is the starting point for Step 10.

**Done when** (`@DataJpaTest`)
- An import persists all fields correctly, including categories and `importedAt` from a fixed `Clock`.
- Importing the same SKU for the same partner twice updates the row (count stays 1).
- The same SKU for different partners creates 2 rows.
- Two products in one file that share a new category produce one category row.

## Step 7. Concurrent file processing

**What to do**
- `ProcessingResult(fileName, ProcessingStatus, errors)` with static factories `success`, `validationError`, `parseError`.
- `SingleFileProcessor.process(Path)`: runs resolve parser → parse → validate → import → move. It **never throws**. Exceptions are caught and the file goes to `failed/` with the error. Put the file name into the MDC (`file`) while the file is processed.
- `ExecutorConfig`: an `ExecutorService` bean (pool size from properties) and a `@PreDestroy` method that calls `shutdown()` + `awaitTermination(30s)` + `shutdownNow()` as a fallback.
- `FileProcessingOrchestrator.processAllFiles()`: scan → `CompletableFuture.supplyAsync(..., executor).orTimeout(timeout).exceptionally(...)` for each file → `allOf().join()` → log a summary (total, success, failed). Add the `file-processing-timeout-minutes` property.
- Concurrency-safe category creation: two threads may insert the same new category at the same time. Use `CategoryInsertService.findOrCreate` with `@Transactional(propagation = REQUIRES_NEW)` and `saveAndFlush`. `CategoryService.getOrCreate` catches `DataIntegrityViolationException` and re-reads the category in the caller's transaction.
- Try it: switch the pool to `Executors.newVirtualThreadPerTaskExecutor()` and compare.

**Done when**
- 5 files (3 valid, 2 invalid) → 3 in `processed/`, 2 in `failed/` with `.error` files.
- An exception while parsing one file does not affect the others.
- A concurrent duplicate category insert is handled (retry test).
- The executor shuts down without hanging threads (add a test for the `@PreDestroy` logic; the reference does not have one yet).

## Step 8. Scheduling, logging, end-to-end

**What to do**
- `@EnableScheduling` and a `FileProcessingScheduler` with `@Scheduled(cron = "${file-processing.cron}")` that calls the orchestrator.
- `logback-spring.xml`: a console pattern that includes `%X{file}`, INFO by default, and WARN for the `test` profile.
- Sample files in `data/input-folder/`: valid JSON, valid CSV, invalid JSON, and invalid CSV.
- Run end to end with PostgreSQL in Docker.

**Done when**
- The scheduler triggers on the cron schedule. Valid files end up in `processed/` and their rows are visible in PostgreSQL (`psql` or any DB client). Invalid files end up in `failed/` with descriptive `.error` files.
- The logs show the whole pipeline for each file.

---

## Step 9 (NEW). Implement and modify the database structure

**Goal:** stop relying on `ddl-auto` and manage the schema explicitly. Then change the schema to meet new requirements **without losing or breaking existing data**.

**New requirements**
1. **Data integrity:**
   - a SKU is unique per partner in the DB, not just in the service: `UNIQUE (partner_id, sku)`
   - `CHECK (price > 0)`, `CHECK (special_price IS NULL OR special_price < price)`, `CHECK (special_from IS NULL OR special_to IS NULL OR special_from < special_to)`
2. **Brand as an entity** (v1 already said "maps to Brand entity"): move the free-text `products.brand` to a `brands(id, name UNIQUE)` table with `products.brand_id` FK.
3. **Import audit:** add a table `import_batches(id, file_name, partner_id, export_date, status, total_products, error_message, started_at, finished_at)`, and link `products.last_import_batch_id → import_batches.id`.
4. **Performance:** add indexes for the main lookups, for example `products(partner_id, sku)` (already covered by the unique constraint) and `product_categories(category_id)`. Explain each index in the PR.

**What to do**
- Add Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`). Switch `ddl-auto` to `validate`.
- `V1__baseline.sql`: the schema exactly as Hibernate generated it in Steps 1–8, for `products`, `categories`, and `product_categories` (use `baseline-on-migrate` for an existing local DB).
- `V2__product_constraints.sql`: first **clean or deduplicate** any data that would violate the constraints (keep the latest `imported_at` per `(partner_id, sku)`), then add the constraints.
- `V3__brands.sql`: expand → migrate → contract:
  - create `brands`, `INSERT INTO brands SELECT DISTINCT trim(brand) ...`, add `brand_id`, back-fill it with an `UPDATE ... FROM brands`, and add the FK
  - drop `products.brand` in a later migration (`V4`) once the code no longer uses it. Explain why expand/contract keeps the running application compatible.
- `V5__import_batches.sql`: create the table, the FK, and the indexes.
- Update the entities and repositories (`Brand`, `BrandRepository`, `ImportBatch`, `ImportBatchRepository`). Hibernate `validate` must pass.
- Tests run against **real PostgreSQL via Testcontainers** (`@ServiceConnection`), so migrations and constraints are actually exercised.

**Outcomes / Done when**
- **Updated schema definitions:** migration scripts `V1…V5` + updated entities; the app starts with `ddl-auto: validate`.
- **Migration scripts adjusting existing structures:** `V2`–`V4` migrate a DB that already contains data. A test seeds data with the V1 schema (Flyway `target`), migrates to the latest version, and checks the result.
- **Data validation results confirming structural consistency:** a `SchemaConsistencyTest` (or SQL script plus results in the PR) that shows:
  - the row counts of products and categories are unchanged after the migration
  - every non-null legacy brand is linked to a `brands` row, and there are no orphan FKs
  - no duplicate `(partner_id, sku)` rows
  - inserting an invalid row (duplicate SKU, `price <= 0`, `special_price >= price`) fails with a constraint violation

## Step 10 (NEW). Wrap queries in transactions for data consistency

**Goal:** design explicit transaction boundaries so that one file import is **atomic** and its audit record stays consistent, and prove it with commit and rollback tests.

**Analysis (write it in the PR description)**
- List the queries done per file: find/insert brand, find/insert categories, find/upsert products, insert/update `import_batches`.
- Decide which queries must commit or roll back together and which must not:
  - products + categories links + the `SUCCESS` batch for one file → **one transaction**
  - category or brand creation under concurrency → its own `REQUIRES_NEW` transaction (already the case for categories); explain why
  - the `FAILED` batch record must survive the rollback of the product writes → a separate transaction
  - moving the file on disk is not transactional. Do it **after commit** (or in `TransactionSynchronization.afterCommit`) and explain what happens if the move fails.

**What to do**
- Refactor the data access layer to use **explicit** transactions:
  - an `ImportTransactionService` (or similar) that uses `TransactionTemplate` (programmatic) to wrap: create the batch `IN_PROGRESS` → upsert all products → mark the batch `SUCCESS`
  - on any exception: the template rolls back, and a separate `REQUIRES_NEW` call records the batch as `FAILED` with the error message
  - `@Transactional(readOnly = true)` on read-only queries; set `rollbackFor` explicitly where checked exceptions can be thrown
  - avoid the self-invocation pitfall (`@Transactional` on a method called from the same bean has no effect). Explain how your design avoids it.
- Optional: choose an isolation level and justify it, or use optimistic locking (`@Version` on `Product`) for concurrent updates of the same SKU coming from two files.

**Outcomes / Done when**
- **Transactional logic that groups related queries:** one file = one atomic unit of work; the audit record is consistent with the data.
- **Data access layer refactored to use explicit transactions:** no implicit or class-level `@Transactional` left where it hides the boundary.
- **Tests covering commit and rollback scenarios** (Testcontainers PostgreSQL):
  - commit: a valid file → all products, the category links, and a `SUCCESS` batch are visible from a **new** transaction
  - rollback: a failure injected at product N of M (for example a mocked repository or a constraint violation from Step 9) → **0** products from that file, existing rows unchanged, and a `FAILED` batch with the error is present
  - partial-write protection: a re-import of an updated file that fails midway leaves the previous product values intact
  - category creation in `REQUIRES_NEW` is not rolled back with the outer transaction (explain whether that is desired)
  - the file goes to `processed/` only after a successful commit and to `failed/` after a rollback

---

## If time allows

1. REST endpoint `POST /api/import/trigger` to start processing manually, and `GET /api/import-batches` to view the audit records.
2. Containerize the application itself (multi-stage `Dockerfile`) and run app + DB with one `docker compose up`.
3. A new strategy, `FileSource` (`LocalFileSource`, `S3FileSource`), to read files from S3.

## Out of scope

- Kafka and any messaging integration.
