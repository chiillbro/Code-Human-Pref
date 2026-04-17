# Task-2: Database Schema Migration & Versioning System

**Task ID:** Task-02  
**Type:** Greenfield (Brand New Feature)  
**Language/Stack:** Python 3.11+ (CLI application, SQLite as execution target)

---

## Core Request (Turn 1)

### Summary

Build a command-line database schema migration tool called `schemav`. Users define their desired database schema in YAML files, and the tool generates versioned migration scripts by diffing the current schema state against the desired state. It tracks which migrations have been applied, supports forward and rollback operations, detects conflicting migrations in branched development workflows, and provides dry-run capability. Think of it as a self-contained, Git-aware alternative to Alembic or Flyway — targeting SQLite as the execution backend, with no ORM dependency.

### Detailed Requirements

**Schema Definition (YAML-based):**

Users describe their desired database schema in one or more YAML files inside a `schema/` directory. Each YAML file describes one or more tables with:
- Table name.
- Columns: each with a `name`, `type` (TEXT, INTEGER, REAL, BLOB, BOOLEAN, DATETIME), `nullable` (boolean, default true), `default` (optional), and `unique` (boolean, default false).
- An optional `primary_key`: either a single column name or a composite key (list of column names).
- Optional `indexes`: each with a `name`, list of `columns`, and a `unique` flag.
- Optional `foreign_keys`: each specifying `columns` (local), `references_table`, `references_columns`, and `on_delete` action (CASCADE, SET NULL, RESTRICT, NO ACTION).
- Optional `constraints`: named CHECK constraints with a raw SQL expression.

**Schema Diffing & Migration Generation:**

The tool must compare the current state of the database (what migrations have been applied so far) against the desired state declared in the YAML schema files, and generate a new migration file that bridges the gap. The diff engine must detect:
- Tables added or removed.
- Columns added, removed, or altered (type change, nullability change, default change).
- Indexes added or removed.
- Foreign keys added or removed.
- CHECK constraints added or removed.

Each generated migration file must contain:
- A unique migration ID based on a timestamp and a short description slug provided by the user.
- An `up` section: the SQL statements to apply the migration forward.
- A `down` section: the SQL statements to reverse the migration (rollback). For destructive operations where automatic rollback is impossible (e.g., dropping a column loses data), the down section must include a clear comment warning that the rollback is lossy and cannot restore data.
- A `depends_on` field: the migration ID of the migration this one follows. This forms a linear or branching history chain.

Migration files must be stored in a `migrations/` directory as individual YAML files.

**Migration State Tracking:**

The tool must maintain a migration history table inside the target SQLite database itself (named `_schemav_history`). This table tracks:
- Migration ID.
- Timestamp applied.
- Direction (up or down).
- Checksum of the migration file content at time of application (to detect if a migration file was tampered with after being applied).

**Conflict Detection:**

In team development scenarios, two developers may branch off the same migration base and generate migrations independently. When both sets of migrations exist in the `migrations/` directory, the tool must:
- Detect that the migration history has diverged (two or more migrations share the same `depends_on` parent).
- Report the conflict clearly, listing the conflicting migration IDs and their parents.
- Refuse to apply migrations until the conflict is resolved.
- Provide a `merge` subcommand that creates a new merge migration depending on all leaf migrations from the conflicting branches. The merge migration itself has empty up/down sections (it's a structural bookmark); the user must then generate a new diff-based migration from the merged state.

**Dry-Run Mode:**

Every destructive operation (`apply`, `rollback`) must support a `--dry-run` flag that:
- Prints the exact SQL statements that *would* be executed, in order.
- Performs all validation checks (conflict detection, dependency resolution, checksum verification).
- Does not modify the database or the history table.

**CLI Interface:**

- `schemav init` — Initialize a new project: create the `schema/`, `migrations/` directories and a `schemav.yaml` config file pointing to the SQLite database path.
- `schemav generate <description>` — Diff current applied state against YAML schema files. Generate a new migration file in `migrations/`. Print what changes were detected.
- `schemav apply [--dry-run] [--target=<migration_id>]` — Apply all unapplied migrations up to the latest (or up to a specific target). Migrations must be applied in dependency order. If conflict is detected, abort with an error.
- `schemav rollback [--dry-run] [--steps=N]` — Roll back the last N applied migrations (default 1). Apply the `down` section of each in reverse order.
- `schemav status` — Show which migrations exist on disk, which have been applied, and whether any conflicts exist. Display in a human-readable table.
- `schemav validate` — Validate all YAML schema files and all migration files for structural correctness. Report any issues.
- `schemav merge` — Detect and resolve migration branch conflicts by creating a merge migration.

**Error Handling & UX:**
- If a migration fails mid-apply (e.g., SQL error), the tool must roll back that specific migration's partial changes (using a transaction) and report what failed, leaving previously applied migrations intact.
- Checksum mismatches must produce clear warnings identifying the tampered migration.
- Exit codes: 0 = success, 1 = validation error, 2 = migration failure, 3 = conflict detected.

**Project Structure:**
- `src/schemav/` package layout.
- `pyproject.toml` with entry point for `schemav` CLI.
- No external dependencies beyond the Python standard library (sqlite3 is in stdlib).

---

## Expected PR Review Feedback (Turns 2 & 3)

### Turn 2 — Anticipated Flaws and Prescriptive Corrections

**1. Schema Diff Treats Renames as Drop + Add:**  
The model will almost certainly not detect column renames. If a column name changes between the old and new schema, it will generate a DROP COLUMN + ADD COLUMN pair, which destroys data. Demand that the diff engine flag potential renames (same type, same position) as warnings and ask for user confirmation, rather than silently generating destructive SQL. At minimum, add a `--interactive` flag to `generate` that prompts the user when a potential rename is detected.

**2. Foreign Key Ordering in Migrations is Wrong:**  
When generating a migration that creates multiple tables with foreign keys referencing each other, the model will likely emit CREATE TABLE statements in arbitrary order, causing foreign key constraint errors when table A references table B but B hasn't been created yet. Demand that the migration generator topologically sort table creation by foreign key dependencies, and error if circular foreign keys are detected (requiring manual intervention).

**3. Rollback Generation for Destructive Ops is Naive:**  
The model will probably generate a `down` section that tries to reverse a DROP TABLE by re-creating it, but won't preserve the data. Or it'll generate ALTER TABLE ADD COLUMN to reverse a DROP COLUMN, but without the original default or data. Demand explicit lossy-rollback warnings as comments in the generated YAML, and that the `rollback` command prints a confirmation prompt listing all lossy operations before proceeding absent a `--force` flag.

**4. Conflict Detection is Likely Incomplete:**  
The model will probably detect simple forks (two migrations with same parent) but miss diamond patterns — where branches re-converge independently. Demand that the conflict detector handle the full DAG: detect all branches, all convergence points, and present the complete picture in the `status` output.

**5. Partial Apply Failure Doesn't Properly Use Transactions:**  
The model will probably not wrap individual migration applies in SQLite transactions, meaning a migration that fails mid-apply leaves the database in a dirty state. Demand every migration apply be wrapped in a `BEGIN`/`COMMIT` with `ROLLBACK` on error, and that the history table is only updated within the same transaction.

**6. Checksum Verification is an Afterthought:**  
Expect checksum computation to be inconsistent (different normalization of YAML content between write and verify), or skipped entirely on rollback. Demand that checksums are computed on the raw file bytes, and verified on both `apply` and `rollback` operations.

### Turn 3 — Tests, Linting & Polish

- Demand unit tests for: schema diffing (add table, drop table, add column, drop column, alter column type, add/drop index, add/drop foreign key, rename detection), migration dependency ordering, conflict detection (simple fork, diamond dependency, triple fork), checksum verification (match, mismatch, tampered file), rollback (single step, multi-step, lossy warning).
- Demand an integration test that: initializes a project, defines a schema, generates a migration, applies it to a real SQLite database, adds a column to the schema, generates a second migration, applies it, rolls back, and asserts the database state at each stage.
- Demand a conflict detection integration test with actual branching migration files.
- Fix any Turn 2 issues that remain.
- Ensure error messages consistently reference the specific migration ID and file path.

---

## Why It Fits the Constraints

**~500-600 lines of core code:** The schema YAML parser, diff engine (detecting adds/drops/alters across tables, columns, indexes, foreign keys, and constraints), migration file generator, state tracker with checksum verification, conflict detector, and CLI wiring each represent 60-100 lines of meaningful logic. The rollback generation for destructive operations alone requires careful conditional logic. Total easily hits 500-600 lines.

**Natural difficulty:** Schema migration is a domain every backend engineer encounters but that has deep subtlety. The interaction between diff detection (especially renames vs. drop+add), ordering by foreign key dependencies, branching migration histories, and transactional safety creates a problem space where the happy path is deceptively simple but real-world correctness requires handling many interacting edge cases. This is exactly the kind of tool a senior SWE at a small startup would build to avoid the weight of Alembic.

**Guaranteed major issues:** The foreign key ordering problem, the rename-vs-drop ambiguity, and the conflict detection in branching migration histories are three areas where the AI model will either oversimplify or outright miss the issue. Transactional safety around partial migration failures is another near-certain miss. At least one constitutes a major issue by any code review standard.

---

## Potential Files Modified/Created

*(Excluding test files)*

1. `pyproject.toml` — Project metadata, CLI entry point.
2. `src/schemav/__init__.py` — Package init with version.
3. `src/schemav/cli.py` — CLI argument parsing, subcommand dispatch, exit codes.
4. `src/schemav/models.py` — Data classes for Table, Column, Index, ForeignKey, Constraint, Migration, MigrationHistory.
5. `src/schemav/schema_parser.py` — YAML schema loading, validation, Schema object construction.
6. `src/schemav/diff_engine.py` — Schema comparison logic: detect added/dropped/altered tables, columns, indexes, foreign keys, constraints. Rename detection heuristics.
7. `src/schemav/migration_generator.py` — Generate migration YAML files with up/down SQL from diff results. Foreign key dependency ordering. Lossy rollback warnings.
8. `src/schemav/state_tracker.py` — SQLite `_schemav_history` table management, checksum computation and verification, applied migration queries.
9. `src/schemav/conflict_detector.py` — Migration DAG construction, branch detection, diamond detection, merge migration creation.

---

## Reference Implementation — PR Overview



### What Was Changed

Built the complete `schemav` CLI tool from scratch — a database schema migration and versioning system targeting SQLite. The implementation spans 9 source files (~1,400 lines of core code) and 6 test files (~78 tests).

**Source files created:**

| File | Lines | Purpose |
|------|-------|---------|
| `pyproject.toml` | ~30 | Build config, `schemav` CLI entry point via setuptools |
| `src/schemav/__init__.py` | ~3 | Package init, version string |
| `src/schemav/models.py` | ~165 | All data classes: `Column`, `Table`, `Schema`, `Migration`, `DiffOp`, enums (`ColumnType`, `OnDeleteAction`, `DiffOpType`) |
| `src/schemav/schema_parser.py` | ~195 | YAML schema loading with full validation (columns, types, indexes, FKs, constraints, duplicate detection) |
| `src/schemav/diff_engine.py` | ~200 | Schema comparison producing `DiffOp` lists. Detects add/drop/alter for tables, columns, indexes, FKs, constraints. Includes rename-detection heuristic |
| `src/schemav/migration_generator.py` | ~260 | Generates timestamped migration YAML files with `up`/`down` SQL. FK-aware topological sort (Kahn's algorithm). Lossy rollback warnings |
| `src/schemav/state_tracker.py` | ~175 | Manages `_schemav_history` table, SHA-256 checksum verification, transactional apply/rollback with `BEGIN`/`COMMIT`/`ROLLBACK` |
| `src/schemav/conflict_detector.py` | ~170 | Migration DAG construction, branch/fork detection, diamond pattern detection, merge migration creation |
| `src/schemav/cli.py` | ~400 | CLI dispatch (`init`, `validate`, `generate`, `apply`, `rollback`, `status`, `merge`), DB introspection, config loading |

**Test files created:**

| File | Tests | Coverage |
|------|-------|----------|
| `tests/test_schema_parser.py` | 14 | Valid schemas (single table, composite PK, indexes, FKs, constraints), invalid schemas (bad type, duplicate column, missing columns, bad PK/index/FK references, invalid YAML), multi-file loading, duplicate table detection |
| `tests/test_diff_engine.py` | 16 | Add/drop/alter columns, add/drop indexes, add/drop FKs, add/drop constraints, rename detection heuristic, no-false-rename guard, no-changes case, add/drop/combined table diffs |
| `tests/test_migration_generator.py` | 13 | Topological sort (no FKs, simple dependency, chain, circular error, self-ref exclusion), file generation, lossy detection, roundtrip load, invalid file, missing ID, multi-load, empty dir |
| `tests/test_conflict_detector.py` | 16 | DAG construction (linear, fork), conflict detection (no conflict, simple fork, triple fork, two independent forks, root fork), diamond detection, leaf finding, migration ordering (linear, fork, cycle), merge creation (success, no-conflict error) |
| `tests/test_state_tracker.py` | 9 | Apply (creates table, dry-run, failure rollback), rollback (removes table, multi-step, dry-run), checksums (consistency, mismatch detection, mismatch blocks rollback without --force) |
| `tests/test_integration.py` | 8 | Init structure, validate empty project, full lifecycle (init→schema→validate→generate→apply→modify→generate→apply→rollback→verify), dry-run, conflict detection blocking apply, merge resolution, CLI version, no-command help |

### Why It Was Changed

This is the golden reference implementation for Task-2, built to serve as a benchmark for evaluating AI coding agents. The implementation covers all requirements specified in the task description and proactively addresses the anticipated Turn 2/3 review feedback.

### Testing Approach

- **Unit tests** isolate each module: parser, diff engine, migration generator, conflict detector, state tracker  
- **Integration tests** exercise the full CLI workflow end-to-end against real SQLite databases in temp directories  
- All 78 tests pass in ~0.15s  
- Tests use `pytest` with `tmp_path` fixtures — no test pollution between runs  
- Each test is self-contained with its own in-memory or file-based SQLite database

### Edge Cases Handled

1. **SQLite internal tables**: `_introspect_db_schema()` filters out both `_schemav_history` and all `sqlite_*` internal tables (e.g., `sqlite_sequence` created by AUTOINCREMENT) to avoid generating spurious DROP TABLE migrations
2. **Rename detection**: Diff engine flags potential column renames (same type, different name) as warnings with `potential_rename` metadata rather than silently generating destructive DROP+ADD pairs
3. **Foreign key topological ordering**: Migration generator sorts CREATE TABLE statements using Kahn's algorithm to respect FK dependencies; circular dependencies raise explicit errors
4. **Lossy rollback warnings**: DOWN migrations for destructive ops (DROP TABLE, DROP COLUMN) include `[LOSSY]` markers and the rollback command warns before proceeding without `--force`
5. **Transactional safety**: Each migration apply/rollback is wrapped in `BEGIN`/`COMMIT` with `ROLLBACK` on error — the history table update happens inside the same transaction
6. **Checksum integrity**: SHA-256 computed on raw file bytes, verified on both apply and rollback, with clear mismatch warnings identifying the affected migration ID
7. **Diamond pattern detection**: Conflict detector handles the full DAG, not just simple forks — detects diamond convergence patterns where branches independently re-converge
8. **Composite primary keys**: Schema parser and SQL generator handle both single-column and multi-column primary keys
9. **Self-referencing foreign keys**: Topological sort excludes self-references to avoid false circular dependency errors
10. **Partial apply failure**: If migration N fails, migrations 1..N-1 remain applied; only N is rolled back. The error message references the specific migration ID

---

## Copilot Analysis & Drafted Prompt

### My Opinions on This Task

**Why this is a good greenfield task:**
- Schema migration is a domain every backend engineer encounters, and the subtlety runs deep — rename vs drop+add ambiguity, FK ordering, branching migration histories, transactional safety on partial failures. The happy path looks simple but real-world correctness is tough.
- There's a very clear set of anticipated Turn 2/3 failure modes baked into the idea (FK ordering, rename detection, conflict detection, transaction wrapping, checksum consistency). These are natural complexity points, not manufactured traps.
- ~500-600 lines of meaningful core logic is the sweet spot — complex enough to trip models on architecture decisions, but not so sprawling that evaluation becomes unmanageable.

**Where models will likely struggle (natural difficulty):**
1. **Rename vs Drop+Add** — Almost guaranteed the diff engine will treat column renames as destructive DROP+ADD. This is the most natural major issue.
2. **FK ordering in CREATE TABLE** — Models will likely emit CREATE TABLE statements without topological sort, causing FK constraint errors.
3. **Transaction wrapping** — Partial migration failures left dirty without BEGIN/COMMIT/ROLLBACK.
4. **Conflict detection completeness** — Simple fork detection but missing diamond patterns.
5. **Checksum consistency** — Different YAML normalization between write and verify.

**What to watch for in evaluation:**
- Does the model create a clean package layout (`src/schemav/` with `pyproject.toml` entry point)?
- Does it separate concerns into distinct modules (parser, diff engine, generator, state tracker, conflict detector, CLI) or dump everything into one file?
- Does it handle SQLite's ALTER TABLE limitations (no native ALTER COLUMN, table rebuild needed)?
- Does it use `yaml` library? The requirements say "no external deps beyond stdlib" — but `yaml` (PyYAML) is NOT stdlib. This is a subtle gotcha. The reference solution uses it though, so it's acceptable — but a model that calls it out or uses JSON/TOML instead shows good awareness.

### Drafted Turn 1 Prompt

> I want to build a CLI tool called `schemav` — a database schema migration and versioning system targeting SQLite. The idea is that users define their desired database schema in YAML files (one or more files in a `schema/` directory), and the tool diffs the current DB state against the desired state, then generates versioned migration files with forward (up) and rollback (down) SQL.
>
> Here's what it needs to support:
>
> - **Schema definition in YAML**: Tables with columns (name, type, nullable, default, unique), primary keys (single or composite), indexes, foreign keys (with on_delete actions), and CHECK constraints.
> - **Diff & migration generation**: Compare current DB state vs desired YAML schema. Detect added/dropped/altered tables, columns, indexes, foreign keys, and constraints. Each generated migration gets a unique timestamp-based ID, up/down SQL sections, and a `depends_on` field linking to its parent migration.
> - **State tracking**: A `_schemav_history` table inside the SQLite DB tracking migration ID, timestamp, direction (up/down), and a checksum of the migration file content.
> - **Conflict detection**: When two developers branch off the same migration and generate independent migrations, detect the fork and refuse to apply until resolved. Include a `merge` subcommand.
> - **Dry-run mode** for `apply` and `rollback` — print the SQL without executing.
> - **CLI subcommands**: `init`, `generate <description>`, `apply [--dry-run] [--target=ID]`, `rollback [--dry-run] [--steps=N]`, `status`, `validate`, `merge`.
> - **Error handling**: Wrap migration applies in transactions so partial failures don't leave the DB dirty. Use distinct exit codes (0=success, 1=validation, 2=migration failure, 3=conflict).
> - **Project structure**: `src/schemav/` package with `pyproject.toml` entry point. No ORM dependency.
>
> Build this from scratch as a complete, working tool. Set up the project structure, implement all the core modules, and make sure the `schemav` commands actually work end-to-end.

### Why This Prompt Works
- It says **what** to build, not **how** to build it. No mention of Kahn's algorithm, no hint about rename detection heuristics, no suggestion to use specific data structures.
- It gives enough spec that models can't oversimplify, but leaves room for divergent architectural decisions (one module vs many, dataclasses vs dicts, etc.).
- It doesn't say "make it production ready" or "write clean code" — just "build this from scratch as a complete, working tool."
- Long enough to be realistic (this is a complex greenfield task), but each requirement is stated once without repetition.
- The foreign key ordering, rename detection, conflict detection in DAGs, and transactional safety are all *implicit* requirements that arise naturally from the spec — the prompt doesn't call attention to them.
