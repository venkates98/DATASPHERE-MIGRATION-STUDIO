# DataSphere Migration Studio

**Connect. Map. Validate. Migrate.**

A Java 17 / Spring Boot web application for migrating data between relational databases
(SQL Server, Oracle, PostgreSQL, MySQL - in any direction). It connects to a source and a target, discovers their
structure, suggests a column mapping with deterministic rules (no AI, no LLM, no cloud service), lets you review
and approve it, and only then enables the migration preview.

> **Status: any registered source can be paired with any registered target.** Mapping, approval, preview, batch INSERT
> and post-copy count checks are implemented. Every pair is **PARTIAL SUPPORT**: data movement is tested on in-memory
> H2 stand-ins, not live vendor servers. MariaDB, DB2 and SQLite are not registered. See [What is missing](#what-is-missing).

**Repository:** https://github.com/venkates98/DATASPHERE-MIGRATION-STUDIO

## How to use (quick start)

Full walkthrough: **[User guide](docs/USER-GUIDE.md)** (same content as **Help & Documentation** in the app).

1. Run `.\run-dev.ps1` and open http://localhost:8080. Sign in.
2. Open **Migration Projects** → create a project.
3. **Connections** — pick source type and target type independently (SQL Server, Oracle, PostgreSQL, MySQL). Test, then save. Passwords are never stored on disk.
4. **Tables** — choose schemas, load or suggest pairs, save. Target tables must already exist.
5. **Mapping** — generate suggestions, review every row (score is a rule of thumb, not a probability), accept/change/reject.
6. **Validate & approve** — run validation, tick the confirmation, click **Approve Mapping & Continue**.
7. **Preview** — read-only check. Then **Start migration** (INSERT only, after you confirm). Then **Validate results** (row counts; result is PARTIAL).

Changing the mapping or the live database structure withdraws approval. Migration never starts by itself.

## Workflow

1. **Connections** - connect the source and the target (type, host, port, database or Oracle service name, user, password).
2. **Tables** - discover schemas and tables, pick table pairs (or use *Suggest table pairs by name*) and save the selection.
3. **Mapping** - generate suggestions. Every row shows Source Column, Source Type, Suggested Target, Target Type, Score,
   Reason and Status. Accept, reject, clear or change the target (dropdown with search), choose a conversion, filter
   unmapped columns and review duplicate-target and ambiguity warnings.
4. **Validate & approve** - the server validates; you tick the confirmation and press **Approve Mapping & Continue**.
5. **Preview** - read-only: re-checks approval and live metadata, counts source rows, shows INSERT and generated
   CREATE TABLE text. Nothing is written. Migration never starts automatically.
6. **Start** - after you confirm writes, the engine INSERTs in batches (never DROP/TRUNCATE/UPDATE). Then you can run
   post-copy validation (row counts and sampled null counts). Overall validation is **PARTIAL**, never claimed as 100%.

Project states include `READY_FOR_MIGRATION`, `MIGRATING`, `MIGRATED`, `MIGRATION_FAILED`, `VALIDATED`.

## How suggestions are produced

Fixed rules only, repeatable for the same input:

| Evidence | Default weight |
|---|---|
| Exact name (case and separator insensitive, camelCase and snake_case treated alike) | 40 |
| Abbreviation / synonym dictionary (context aware: `ID`, `CD`, `NO` are not expanded blindly) | 25 |
| Name similarity (Levenshtein + token based; supporting evidence only) | 15 |
| Data type compatibility (length, precision, scale, range) | 10 |
| Length and scale fit | 5 |
| Constraints (PK, FK, unique, nullable) | 5 |

Bands: 90-100 high, 75-89 good, 50-74 needs review, below 50 weak. Weights and thresholds are configurable in
`application.properties` (`datasphere.mapping.*`; the weights must add up to 100 or startup fails). The built-in
abbreviation dictionary is `src/main/resources/mapping/abbreviations.properties`; point
`datasphere.mapping.abbreviation-file` at your own file to extend or override it without code changes.

How to read the score:

- It is a rule of thumb, **not a probability** and not calibrated against real migrations.
- An exact name match earns the full name block (80 of 100 points), so it can reach 100.
- A match that needs the dictionary (`EMP_NM` -> `EMPLOYEE_NAME`) cannot earn the "exact" 40 points, so it tops out
  around 60: the band says *needs review* even though the status is **Suggested**. That is intentional - an
  abbreviation is a guess a person should confirm.
- The engine never invents a column, maps one-to-one, does not auto-select when the best candidates are close
  (status *Needs review*, candidates listed), and blocks a mapping with incompatible types unless a conversion is chosen.
- Only metadata is used: names, types, length, precision, scale, nullability, PK/FK/unique. No row values are read.

## Approval and safety

- Approval is stored with the mapping version, a SHA-256 of the mapping, a SHA-256 fingerprint of the source and
  target metadata, the user and the time.
- Any change to the mapping withdraws the approval (`MAPPING_MODIFIED`). If the live database structure no longer
  matches the fingerprint, the approval is withdrawn on the next preview/start/approve (`MAP-META-001`).
- The **backend** enforces this in `MigrationGateService`: no approval (`MAP-APPR-001`), outdated approval
  (`MAP-APPR-002`), failed validation (`MAP-VAL-002`), changed metadata (`MAP-META-001`). The UI buttons are only a convenience.
- Concurrent edits are detected with an expected mapping version (`PRJ-CONFLICT-001`).
- No code path drops, truncates, deletes or overwrites target data, or runs SQL typed in the browser. Generated SQL
  quotes identifiers from discovered metadata and uses `?` placeholders for values. Writes are INSERT only, after
  explicit confirmation.

## Supported databases

Registered adapters (extensible; adding a vendor means driver + adapter + dialect mappings + tests):

| ID | Product | Default port | Driver |
|---|---|---|---|
| SQLSERVER | Microsoft SQL Server | 1433 | `com.microsoft.sqlserver.jdbc.SQLServerDriver` |
| ORACLE | Oracle Database (service name only) | 1521 | `oracle.jdbc.OracleDriver` |
| POSTGRESQL | PostgreSQL | 5432 | `org.postgresql.Driver` |
| MYSQL | MySQL | 3306 | `com.mysql.cj.jdbc.Driver` |

Same-vendor pairs (Oracle→Oracle, and so on) are allowed. The compatibility matrix at `/schema-comparison` is generated
from adapters; every cell is **PARTIAL SUPPORT** until a live server test exists.

**Tested with**: H2 in `MSSQLServer`, `Oracle` and `PostgreSQL` compatibility modes (discovery, mapping, INSERT copy).
Type conversion and SQL dialects are unit-tested for all four products including MySQL.
**Not tested against** real SQL Server, Oracle, PostgreSQL or MySQL servers.

## Requirements

- JDK 17 or newer
- Maven 3.9+
- Internet access for the first build (Maven dependencies)

## Run locally (development)

```powershell
.\run-dev.ps1
# equivalent to:
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Open <http://localhost:8080>. The `dev` profile enables a development-only administrator (`venkat11484`) whose
password is stored **only as a BCrypt hash** in `application-dev.properties`. It exists solely for local development.

### Run from Eclipse / an IDE

Run `DataSphereMigrationStudioApplication` (**Run As > Java Application**). With the project root as the working
directory it reads the git-ignored `.env` file, which holds the local administrator as a BCrypt hash. If that file is
missing the app stops with "No administrator configured" - by design. Alternatively run
`src/test/java/.../DevApplication.java`, which activates the `dev` profile (never packaged into the production jar).

## Run with your own administrator (non-dev)

Provide a BCrypt hash, never a plain password, via environment variables or a git-ignored `.env` file
(see [`.env.example`](.env.example)):

```powershell
$env:DATASPHERE_ADMIN_USERNAME = "your-admin"
$env:DATASPHERE_ADMIN_PASSWORD_HASH = '{bcrypt}$2a$12$...your hash...'   # single quotes: the hash contains $
mvn spring-boot:run
```

Startup fails if the hash is not BCrypt, or if the `dev` and `prod` profiles are active together.

## Configuration

| Variable | Purpose |
|---|---|
| `DATASPHERE_DATA_DIR` | Where projects and mappings are saved (default `./data`, git-ignored). |
| `DATASPHERE_DB_ALLOWED_HOSTS` | Comma-separated host allow-list for database connections (SSRF control). **Required in `prod`.** |
| `DATASPHERE_ADMIN_USERNAME`, `DATASPHERE_ADMIN_PASSWORD_HASH` | Administrator (BCrypt hash only). |
| `DATASPHERE_COOKIE_SECURE` | Secure session cookie (forced by `prod`). |

The data directory holds project JSON files **without database passwords**. Passwords are kept in server memory only,
so after an application restart you re-enter them (the project shows "enter the password again"). Anyone who can read
the data directory can see host names, database names and user names - protect it with file permissions.

## Build and test

```powershell
mvn clean test          # 242 tests, no external database required
mvn clean package       # builds target/datasphere-migration-studio-<version>.jar
java -jar target\datasphere-migration-studio-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

Tests cover exact, case-insensitive, abbreviation, synonym and similarity matching; type compatibility with
precision/scale; ambiguity; duplicate targets; unmapped columns; manual mapping; approval; invalidation after a
modification; migration blocked without approval; approval invalidated by a metadata change (ALTER/DROP); persistence
across a reload; no password on disk; and any-to-any database pairs.

## REST API (all require a logged-in session; state-changing calls require the CSRF token)

| Call | Purpose |
|---|---|
| `POST /api/connections/{role}/test` | Test a connection without saving (`role` = `source` or `target`) |
| `POST/GET /api/projects`, `GET/DELETE /api/projects/{id}` | Create, list, read, delete a project |
| `PUT /api/projects/{id}/connections/{role}` | Test and save a connection |
| `GET /api/projects/{id}/schema/{role}/schemas\|tables\|columns` | Discovery |
| `POST /api/projects/{id}/tables/suggest`, `PUT /api/projects/{id}/tables` | Suggest and save table pairs |
| `POST /api/projects/{id}/metadata/refresh` | Re-read metadata and reconcile the mapping |
| `GET/PUT /api/mapping/{id}` | Read the mapping; apply changes (accept, reject, clear, set target, conversion, reset) |
| `POST /api/mapping/{id}/suggest\|validate\|approve` | Suggest, validate, approve |
| `GET /api/databases`, `GET /api/compatibility` | Adapter catalog and generated pair matrix |
| `POST /api/migration/{id}/preview` | Read-only preview (INSERT and CREATE TABLE text) |
| `POST /api/migration/{id}/start` | Batch INSERT after `confirmWrite: true` |
| `POST /api/migration/{id}/cancel`, `GET .../status` | Cancel at batch boundary; job status |
| `POST /api/migration/{id}/validate` | Post-copy counts (PARTIAL, never 100%) |

## What is missing

- Applying generated `CREATE TABLE` on the target (scripts are shown; target tables must already exist).
- True keyset resume for composite primary keys (restart a table from the beginning; use skip-duplicates).
- Upsert / overwrite of existing target rows (intentionally not offered).
- Full column-value equality, hashing of entire tables, and Testcontainers against live vendor servers.
- MariaDB, DB2 and SQLite adapters (not faked).
- Durable job history UI (runs are stored on the project JSON; dashboard counters stay in memory).

## Known limitations

- Oracle connections use a **service name** only (no SID, no TNS alias). IPv6 literal hosts are not supported.
- The host allow-list compares host names before connecting; it cannot fully stop DNS rebinding. Also restrict outbound
  traffic at the network level.
- Mapping suggestions are a heuristic. Similar names can mean different things (`STATUS` vs `STATE`); a person must review.
  Do not treat a score as proof of correctness, and do not assume a migration is 100% accurate.
- Row counts in the preview are `COUNT(*)` at one moment and can change afterwards.
- A single administrator; no user management or roles beyond `ROLE_ADMIN`. Project data is not partitioned per user.
- Project files are plain JSON and the audit log is a log file, not a tamper-proof audit store.
- The UI is server-rendered Thymeleaf with vanilla JavaScript (no Angular build step); it was checked manually in one
  browser, not by automated browser tests.
- Login throttling state is in memory (reset on restart, not shared between instances). Behind a reverse proxy,
  configure `server.forward-headers-strategy=native` only when the proxy is trusted.

## Security and deployment checklist

- [ ] Do not run the `dev` profile outside your own machine (the app refuses `dev` + `prod` together).
- [ ] Supply `DATASPHERE_ADMIN_USERNAME` and a **BCrypt** `DATASPHERE_ADMIN_PASSWORD_HASH` from a secret store.
- [ ] Serve over HTTPS only; use the `prod` profile (forces Secure cookies).
- [ ] Set `DATASPHERE_DB_ALLOWED_HOSTS` and also restrict outbound network access to approved database hosts.
- [ ] Use read-only database accounts for sources; give targets only the privileges they need.
- [ ] Protect `./data` and `./logs` with file permissions.
- [ ] Put the app behind a reverse proxy/firewall; restrict who can reach it.
- [ ] Keep dependencies patched (`mvn versions:display-dependency-updates`).

## Project layout

```
com.datasphere.migration
  config, security, controller, dto, service, exception, logging, repository
  db        adapters, dialects, drivers, connection factory, host policy, credential vault
  metadata  JDBC discovery and fingerprints
  mapping   engine (name, abbreviation, scoring), type (normalizer, compatibility, conversion registry)
  engine    batch INSERT, checkpoints, cancel
  validation post-copy counts
  project   project aggregate, discovery, mapping workflow, approval, migration gate, persistence
src/main/resources
  templates (layout, fragments, pages), static (css, js), mapping/abbreviations.properties, application*.properties
```
