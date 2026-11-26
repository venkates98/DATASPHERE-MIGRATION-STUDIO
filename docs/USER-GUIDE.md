# DataSphere Migration Studio — User guide

This is the step-by-step guide for people who will run a migration. It matches what the application does in this build.

A shorter copy of the same steps lives in the app under **Help & Documentation**.

---

## What this software does

DataSphere helps you copy table data from one relational database to another.

You choose **any registered source** and **any registered target**:

| Product | Default port | Notes |
|---|---|---|
| Microsoft SQL Server | 1433 | Encrypts the connection; “trust server certificate” is for test servers only |
| Oracle Database | 1521 | **Service name** only (not SID, not TNS alias) |
| PostgreSQL | 5432 | |
| MySQL | 3306 | The database name is also the schema |

Source and target may be different products or the same product (for example Oracle → Oracle).

The application:

1. Connects and reads **structure** (schemas, tables, columns, keys) — not production row values for mapping.
2. Suggests column mappings with **fixed rules** (names, abbreviations, types). There is no AI and no cloud service.
3. Lets you review, change, reject or leave columns unmapped.
4. Requires you to **approve** the mapping on the server.
5. Shows a **preview** (no writes).
6. Copies rows with **INSERT** in batches, only after you confirm.
7. Optionally compares **row counts** afterwards.

It does **not**:

- Drop, truncate or overwrite target data
- Create missing target tables on the server (it only *shows* CREATE TABLE text)
- Claim 100% mapping or copy accuracy
- Support MariaDB, DB2 or SQLite in this build

Every source–target pair is **PARTIAL SUPPORT** until you have verified it on your own servers. Automated tests use in-memory stand-in databases.

---

## Before you start

- JDK 17+ and Maven 3.9+
- Network access from this machine to both databases
- A **read-only** (or SELECT-only) account on the source, if your DBA can provide one
- An account on the target that can **INSERT** (and SELECT for validation). Do not grant DROP/TRUNCATE
- Target tables **already created** with columns you will map into
- For production: HTTPS, a real administrator hash, and `DATASPHERE_DB_ALLOWED_HOSTS`

---

## Start the application

**Development (Windows):**

```powershell
.\run-dev.ps1
```

Open http://localhost:8080 and sign in with the local development administrator (only for your machine).

**Your own administrator:** copy `.env.example` to `.env`, put a BCrypt hash in `DATASPHERE_ADMIN_PASSWORD_HASH`, then run the jar or `mvn spring-boot:run`.

If startup says “No administrator configured”, the `.env` file is missing or the hash is not BCrypt.

---

## Step-by-step in the browser

### 1. Create a project

1. Sign in.
2. Open **Migration Projects**.
3. Enter a name and create the project.
4. You land on a five-step workspace.

### 2. Connections

On **Source database**:

1. Select the database type. The default port and field labels update.
2. Enter host, port, database name (Oracle: **service name**), user, password.
3. Optional schema (not used as a separate field for MySQL).
4. **Test connection**. A success message shows product name and user. Nothing is saved yet.
5. **Save connection**. Status becomes “Saved · ready”.

Repeat for **Target database**.

If the chip says “Saved · enter password”, the process was restarted — type the password and save again.

**Typical errors**

| Message idea | What to check |
|---|---|
| Connection failed | Host, port, firewall, database up |
| Authentication failed | User and password |
| Host not permitted | Production allow-list (`DATASPHERE_DB_ALLOWED_HOSTS`) |
| Driver not available | Application classpath (bundled drivers for the four products) |

### 3. Tables

1. Open **Tables**.
2. Pick source schema and target schema. If a schema shows “No tables”, choose another (for example BANK instead of PUBLIC).
3. **Load tables**, or **Suggest table pairs by name**.
4. Each pair is one source table → one existing target table.
5. **Save selection**.

### 4. Mapping

1. **Generate mapping suggestions**.
2. For each source column, decide:
   - **Accept** — use the suggested target
   - **Change** — pick another target from the dropdown
   - **Reject / Clear** — leave unmapped
   - **Conversion** — only when types do not fit (for example text → number)
3. **Accept all clean suggestions** accepts rows the engine already marked Suggested (not Needs review).
4. Watch warnings: duplicate targets, unmapped required columns, close scores (ambiguity).

**How to read the score**

- 90–100 high, 75–89 good, 50–74 needs review, below 50 weak
- This is a rule of thumb, **not a probability**
- `EMP_NM` → `EMPLOYEE_NAME` often scores about **60** and still shows **Suggested**. Confirm it yourself

### 5. Validate and approve

1. Open **Validate & approve**.
2. **Validate mapping**. Errors must be cleared; warnings should be read.
3. Tick the confirmation checkbox.
4. **Approve Mapping & Continue** and confirm in the dialog.

Approval is stored on the **server** (user, time, mapping version, hashes). If you change a mapping, change tables, or the live database structure changes, approval is withdrawn. You must validate and approve again. Preview and Start stay blocked until then.

### 6. Preview

**Run migration preview**:

- Re-checks approval and live metadata
- Counts source rows (`COUNT(*)` at that moment)
- Shows INSERT shape and generated CREATE TABLE (not executed)

Nothing is written.

### 7. Start copy

1. **Start migration**.
2. Confirm that the application may INSERT into the target.
3. Wait for the run to finish. Status: COMPLETED, COMPLETED_WITH_ERRORS, FAILED, or CANCELLED.

Behaviour:

- Batches of rows (default 1000)
- INSERT only
- Duplicate key: the job fails that batch unless you chose skip-duplicates
- **Cancel** stops at the next batch; committed batches stay
- Source is not modified

### 8. Validate results

**Validate results** compares source and target **row counts** (and some null counts).

- **FAILED** — counts disagree
- **PARTIAL** — counts match but values were not fully compared
- The application will **not** report “fully validated” for a count-only check

Matching counts do **not** prove every value is identical.

---

## Compatibility matrix

Open **Schema Comparison**. The table is generated from registered adapters. “PARTIAL SUPPORT” means the code path exists; it is not a certificate that your live servers were tested.

---

## After a restart

- Projects and mappings reload from the data folder (`./data` by default).
- Passwords do not. Re-enter them on Connections and save.
- Login lock-outs and dashboard counters reset.

---

## What not to do

- Do not treat a high mapping score as proof the columns mean the same thing.
- Do not run the `dev` profile on a shared or production server.
- Do not grant the target user DROP or TRUNCATE “just in case” — this application does not use those statements, but a leaked password would.
- Do not paste JDBC URLs with passwords into tickets or logs.
- Do not assume MariaDB will work because MySQL is listed.

---

## Getting help from an error

The red message includes a **code** (for example `MAP-APPR-001`) and a **correlation ID**. Quote both. The Help page lists every code and the suggested action. Stack traces stay in the server log (`./logs`), with passwords masked.
