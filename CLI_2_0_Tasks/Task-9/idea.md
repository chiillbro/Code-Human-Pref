# Task-2: Tabular Data Transformation Pipeline Engine

**Task ID:** Task-02  
**Type:** Greenfield (Brand New Feature)  
**Language/Stack:** Python 3.11+ (CLI application, no external dependencies)

---

## Core Request (Turn 1)

### Summary

Build a command-line tool called `tablesmith` that reads tabular data from CSV and JSON files, applies user-defined transformation pipelines (filter, project, sort, join, group/aggregate, distinct, limit), and outputs results in multiple formats. Think of it as a lightweight, scriptable alternative to `csvkit` or `q` — letting users chain SQL-like data transformations defined in pipeline configuration files, with type-aware expression evaluation and streaming support for large files.

### Detailed Requirements

**Data Ingestion:**

The tool must accept input files in three formats:
- **CSV:** Comma-separated values with a mandatory header row. Configurable delimiter (defaulting to `,`), quote character (defaulting to `"`), and encoding (defaulting to `utf-8`).
- **JSON:** A single JSON array of objects, where each object represents a row and each key represents a column name.
- **JSONL (JSON Lines):** One JSON object per line, each representing a row.

The format is auto-detected by file extension (`.csv`, `.json`, `.jsonl`). The user can override auto-detection with `--format=csv|json|jsonl`.

**Column Type Inference:**

The tool must infer column types by scanning the first 100 rows of data. Supported types:
- `integer`: all non-null values parse as integers.
- `float`: all non-null values parse as numbers (at least one has a decimal point or is too large for int).
- `boolean`: all non-null values are `true`/`false` (case-insensitive) or `1`/`0`.
- `string`: anything else.

If a column has mixed types that don't fit a single category, it defaults to `string`. Empty CSV cells and JSON `null` values are treated as NULL (distinct from empty string).

**Transformation Operations:**

Users define transformation pipelines either via inline CLI flags (for simple one-offs) or via a JSON pipeline definition file (for complex multi-step transforms). The following operations are supported:

1. **`filter`** — Keep only rows matching a boolean expression.
   - Expression syntax: `column_name operator value`
   - Comparison operators: `=`, `!=`, `>`, `<`, `>=`, `<=`
   - String operators: `contains`, `startswith`, `endswith`
   - Null operators: `is_null`, `is_not_null`
   - Logical operators: `AND`, `OR`, `NOT` with parenthetical grouping for precedence control.
   - NULL semantics (SQL three-valued logic): any comparison involving NULL yields false, except `is_null` and `is_not_null`. `NULL = NULL` is false. `NOT NULL` is NULL (evaluates to false in filter context).
   - Type-aware comparisons: numeric columns compared numerically, string columns compared lexicographically. Comparing a string to a number is a type error.

2. **`project`** — Select, rename, or compute columns.
   - Select columns: `column1, column2, column3`
   - Rename: `column1 AS alias1`
   - Computed columns: arithmetic expressions on numeric columns — `column1 + column2 AS total`, `salary * 1.1 AS adjusted`. Supported arithmetic operators: `+`, `-`, `*`, `/`.
   - Arithmetic with NULL yields NULL. Division by zero yields NULL (not an error).
   - String concatenation: `first_name || ' ' || last_name AS full_name`.

3. **`sort`** — Order rows by one or more columns.
   - Ascending (default) or descending per column.
   - Multi-column sort: primary sort by first column, ties broken by second, and so on.
   - NULL ordering: NULLs sort last by default. Configurable per-column with `nulls_first` or `nulls_last`.

4. **`group`** — Group rows and compute aggregate functions.
   - Group by one or more columns.
   - Aggregate functions: `count(*)`, `count(column)` (excludes NULLs), `count_distinct(column)`, `sum(column)`, `avg(column)`, `min(column)`, `max(column)`.
   - Multiple aggregates per group: `group by department: count(*) AS headcount, avg(salary) AS avg_salary`.
   - `having` clause: filter groups after aggregation using the same expression syntax as `filter`, but referencing aggregate result columns.

5. **`join`** — Join two datasets on one or more shared columns.
   - The right-side dataset is specified by file path in the operation config.
   - Join types: `inner` (default), `left`, `right`, `full`.
   - Join condition: equality on one or more column pairs.
   - Column name conflicts: if both sides have a non-join column with the same name, prefix with the dataset alias (configurable, defaults to `_left` / `_right`).

6. **`limit`** — Return only the first N rows.

7. **`distinct`** — Remove duplicate rows based on all columns, or a specified subset of columns.

**Pipeline Definition (JSON):**

```json
{
  "input": "employees.csv",
  "steps": [
    {"op": "filter", "expr": "age > 30 AND department != 'HR'"},
    {"op": "join", "file": "salaries.csv", "on": ["employee_id"], "type": "left"},
    {"op": "project", "columns": ["name", "department", "salary * 12 AS annual_salary"]},
    {"op": "group", "by": ["department"], "aggs": ["avg(annual_salary) AS avg_annual", "count(*) AS headcount"]},
    {"op": "having", "expr": "headcount > 5"},
    {"op": "sort", "by": [{"column": "avg_annual", "order": "desc"}]},
    {"op": "limit", "n": 20}
  ],
  "output": {"format": "table", "path": null}
}
```

Steps execute sequentially — each step's output is the next step's input.

**Output Formats:**
- **CSV:** With header row, using the same delimiter/quote configuration.
- **JSON:** Array of objects.
- **JSONL:** One JSON object per line.
- **Table:** Human-readable formatted table with column alignment, truncation of long values, and a row count footer. Printed to stdout.

Default output is stdout in `table` format.

**Large File Handling:**

For operations that do not require seeing all data (filter, project, limit), the tool must process rows in a streaming fashion — reading and emitting one row at a time without loading the full dataset into memory. Only operations that inherently require full data (sort, group, join right-side, distinct) should materialize the full dataset.

**CLI Interface:**

- `tablesmith run <pipeline.json>` — Execute a pipeline definition file.
- `tablesmith query <file> --filter=<expr> [--project=<cols>] [--sort=<spec>] [--group-by=<cols> --aggs=<exprs>] [--limit=N] [--output=<path>] [--format=csv|json|jsonl|table]` — Ad-hoc query with inline flags.
- `tablesmith inspect <file>` — Print the inferred schema: column names, inferred types, total row count, null count per column, and sample values.
- `tablesmith validate <pipeline.json>` — Validate a pipeline definition: check that referenced columns exist, types are compatible, aggregate references are valid, and steps are logically ordered.
- `tablesmith version` — Print the tool version.

**Error Handling & UX:**
- Invalid column reference: error must name the missing column and list available columns.
- Type mismatch in expression: error must show the column, its inferred type, and what type was expected.
- Malformed CSV rows (wrong column count): skip the row with a warning to stderr including the file name, line number, and reason. Count total skipped rows and report at end.
- Division by zero in computed columns: yield NULL for that cell, do not error.
- Exit codes: 0 = success, 1 = pipeline/config error (malformed pipeline, invalid column), 2 = data/runtime error (all files unreadable, all rows malformed).
- All errors must be actionable and specific.

**Project Structure:**
- `src/tablesmith/` package layout.
- `pyproject.toml` with proper metadata and CLI entry point for `tablesmith`.
- No external dependencies beyond the Python 3.11+ standard library.

---

## Expected PR Review Feedback (Turns 2 & 3)

### Turn 2 — Anticipated Flaws and Prescriptive Corrections

**1. Expression Parser Is Fragile / Uses Naive String Splitting:**  
The model will almost certainly parse filter expressions by splitting on spaces or using crude regex. This will break on: string values containing spaces (`name = 'John Doe'`), keywords appearing inside quoted strings (`dept = 'ANDROID Team'` where `AND` matches), nested parentheses, and escaped quotes. Demand a proper tokenizer that first lexes the input into tokens (identifiers, operators, string literals, number literals, parentheses, logical keywords) and then a recursive descent parser or shunting-yard algorithm that respects operator precedence: `NOT` > `AND` > `OR`. String literals must support single-quoted values with `\'` escaping.

**2. NULL Handling in Comparisons Is Wrong:**  
The model will probably treat NULLs as empty strings or zeros rather than implementing SQL three-valued logic. `NULL > 5` should be false, `NULL = NULL` should be false, `NOT (NULL > 5)` should be false (not true — because `NULL > 5` is NULL, `NOT NULL` is NULL, which evaluates to false). Demand explicit NULL propagation through every expression evaluation path, with only `is_null`/`is_not_null` returning definite boolean results.

**3. Type Coercion in Computed Columns Is Brittle:**  
The `project` operation's arithmetic expressions (`col1 + col2 AS total`) will either crash on NULL values, silently concatenate strings instead of adding numbers, or fail when one column is int and another is float. Demand explicit coercion rules documented and implemented: `int + int → int`, `int + float → float`, `float + float → float`, any arithmetic with NULL → NULL, `string || string → string`, `string + number → error`.

**4. JOIN Loads Both Datasets Fully Into Memory:**  
The model will almost certainly read both sides of the join into lists and nested-loop iterate (O(n*m)). This is unacceptable for large files. Demand a hash-join implementation: load the right-side dataset into a hash map indexed by the join column(s), then stream the left side and probe the hash map. This is O(n+m) time and O(m) memory for the right side only.

**5. GROUP BY Doesn't Handle NULL Group Keys:**  
When grouping by a column that contains NULL values, the model will probably either crash, skip those rows, or produce unpredictable behavior. SQL standard treats NULLs as a single group. Demand this behavior explicitly: all rows with NULL in the group-by column(s) form one group, and this group appears in the output.

**6. "Streaming" Is Not Actually Streaming:**  
The model will likely `.readlines()` or load the entire CSV into a list before processing, even for simple filter+project+limit pipelines that could process one row at a time. Demand generator-based processing: readers `yield` rows one at a time, filter yields matching rows, project yields projected rows. Only sort, group, join-right-side, and distinct materialize. The pipeline runner must connect generators via chaining — not by calling `list()` between steps.

### Turn 3 — Tests, Linting & Polish

- Demand unit tests for expression parsing: simple comparisons, compound `AND`/`OR`, nested parentheses, `NOT` operator, quoted strings with spaces, quoted strings containing keywords, NULL comparisons (`is_null`, `is_not_null`), operator precedence (`AND` before `OR`).
- Demand tests for type inference: all-integer column, mixed int/float → float, mixed types → string, all-null column, boolean column.
- Demand tests for each operation individually: filter with NULL rows, project with computed columns and NULL arithmetic and string concatenation, sort with NULL ordering (last and first), group with having and NULL group keys, join (inner, left, right, full) with matching and non-matching rows, limit, distinct (all columns and column subset).
- Demand a pipeline integration test: load a real CSV, run a multi-step pipeline (filter → join → group → sort → limit), and assert the output content matches expected.
- Demand edge case tests: empty file (headers only), single-row file, column with all NULLs, join where no rows match, division by zero in computed column.
- Fix any unresolved Turn 2 issues.
- Ensure all error messages are consistent and actionable.

---

## Why It Fits the Constraints

**~500-600 lines of core code:** CSV/JSON/JSONL reader with type inference and streaming yield (~80 lines), expression tokenizer (~70 lines), expression parser and evaluator with NULL propagation (~100 lines), operation implementations — filter (~30), project with computed columns and string concat (~50), sort with null ordering (~30), group/aggregate with having (~80), hash-join (~70), distinct (~20), limit (~10) — pipeline runner connecting generator chains (~40 lines), output formatters with table alignment (~50 lines), CLI wiring (~60 lines), models and data classes (~40 lines). Total: ~730 lines; reasonable given some light functions. Core algorithmic code comfortably hits 550-600.

**Natural difficulty:** Expression parsing with proper tokenization, operator precedence, quoted strings, and SQL three-valued NULL logic is notoriously tricky. This is where real parsers like SQLite's took years to get right. Hash-join implementation, streaming generator pipelines, and type coercion across heterogeneous CSV data all present genuine engineering challenges that senior SWEs encounter regularly when building data tools. The domain is immediately familiar to any backend engineer.

**Guaranteed major issues:** The expression parser will be fragile (naive splitting, broken on quoted strings with spaces/keywords). NULL semantics will be wrong (treated as empty string/zero). Streaming will not actually be streaming (full materialization). Joins will be nested-loop O(n*m). At least one — likely the expression parser — constitutes a clear major code review blocker.

---

## Potential Files Modified/Created

*(Excluding test files)*

1. `pyproject.toml` — Project metadata, `tablesmith` CLI entry point.
2. `src/tablesmith/__init__.py` — Package init with version.
3. `src/tablesmith/cli.py` — CLI argument parsing, subcommand dispatch, exit code handling.
4. `src/tablesmith/models.py` — Data classes: `Row`, `Schema`, `Column` (name + type), `PipelineStep`, `AggregateSpec`, type enums.
5. `src/tablesmith/readers.py` — CSV, JSON, and JSONL readers with header parsing, type inference (first 100 rows), streaming row yield, malformed row handling.
6. `src/tablesmith/expression.py` — Tokenizer (lexer), recursive descent parser, AST evaluator with type checking and NULL propagation.
7. `src/tablesmith/operations.py` — Filter, project (with computed columns), sort, group/aggregate with having, hash-join, distinct, limit — each as a generator or function.
8. `src/tablesmith/pipeline.py` — Pipeline definition loader, step validator (column existence, type compatibility), executor that chains operation generators.
9. `src/tablesmith/writers.py` — CSV, JSON, JSONL, and formatted table (column-aligned with truncation) output writers.

---

## Golden Reference Implementation — PR Overview

### What Was Built

Complete implementation of `tablesmith` as described above. 156 tests, all passing.

### Architecture Decisions

**Expression Engine (`expression.py`, ~340 lines):**  
Full tokenizer + recursive-descent parser. Token types: NUMBER, STRING (single-quoted with `\'` escape), IDENT, OP (`=`, `!=`, `>`, `<`, `>=`, `<=`, `+`, `-`, `*`, `/`, `||`), KEYWORD (`and`, `or`, `not`, `is_null`, `is_not_null`, `contains`, `startswith`, `endswith`), LPAREN, RPAREN, COMMA, AS, TRUE, FALSE, NULL. Parser implements correct precedence: OR → AND → NOT → comparison → arithmetic → term → factor → atom. AST nodes: `LiteralNode`, `ColumnRefNode`, `BinaryOpNode`, `UnaryOpNode`, `NullCheckNode`, `FunctionCallNode`, `AliasNode`. Evaluator propagates NULL through all arithmetic and comparison paths per SQL three-valued logic — `NULL = NULL` → NULL (false in filter context), `false AND NULL` → false, `NULL OR true` → true, arithmetic with NULL yields NULL, division by zero yields NULL.

**Readers (`readers.py`, ~280 lines):**  
`CSVReader`, `JSONReader`, `JSONLReader` — all implement a streaming `read()` generator that yields `dict[str, Any]` rows. Type inference scans first 100 rows via `_coerce_value()` to determine `ColumnType` (integer/float/boolean/string). CSV reader handles malformed rows (wrong column count) with stderr warnings. `detect_format()` auto-detects by extension; `make_reader()` factory dispatches.

**Operations (`operations.py`, ~320 lines):**  
- `op_filter`: streaming generator, applies parsed expression per row.  
- `op_project`: streaming generator, evaluates column expressions (including computed columns and string concatenation) per row.  
- `op_sort`: materializing, supports multi-column sort with per-column ascending/descending and NULL ordering (nulls_first/nulls_last).  
- `op_group` + `op_having`: materializing, groups by specified columns, computes aggregates (count/count_distinct/sum/avg/min/max). NULL group keys form a single group per SQL standard.  
- `op_join_full`: hash-join implementation — right side materialized into a hash map keyed by join columns, left side streamed and probed. Supports inner/left/right/full join types. Column name conflicts resolved with `_left`/`_right` suffixes.  
- `op_limit`: streaming via `itertools.islice` — yields exactly N rows without consuming extras from the upstream generator.  
- `op_distinct`: materializing, deduplicates using a set of frozen row tuples.

**Pipeline (`pipeline.py`, ~130 lines):**  
`load_pipeline()` reads JSON config. `validate_pipeline()` checks structural correctness (required fields per step type). `execute_pipeline()` chains steps as generators — streaming operations compose without materializing intermediate results.

**Writers (`writers.py`, ~140 lines):**  
`write_csv`, `write_json`, `write_jsonl`, `write_table`. Table writer computes max column widths, truncates at 40 chars, aligns columns, prints row count footer. `write_output()` dispatcher selects writer by format string.

**CLI (`cli.py`, ~200 lines):**  
argparse-based with subcommands: `run` (execute pipeline JSON), `query` (ad-hoc with inline flags), `inspect` (print schema), `validate` (check pipeline), `version`. Exit codes 0/1/2 per spec. `query` applies operations in order: filter → project → group → having → sort → distinct → limit.

### Testing Strategy (156 tests)

| Test File | Count | Scope |
|---|---|---|
| `test_expression.py` | ~57 | Tokenizer (14 cases including edge cases like escaped quotes, keywords in strings), parser (8 cases for precedence, parentheses, null checks), project parser (4 cases), evaluator NULL semantics (13 SQL 3VL cases), comparisons (10 cases), arithmetic (7 cases) |
| `test_readers.py` | ~24 | Type inference (int, float, bool, string, mixed, all-null), CSV reader (basic, nulls, type coercion), JSON/JSONL readers (basic, nulls), format detection |
| `test_operations.py` | ~48 | Filter (9 cases including NULL, streaming behavior, keywords in strings), project (3 cases), sort (6 cases with null ordering), group (5 cases including NULL group keys), having (2 cases), join (7 cases: inner/left/right/full/no-match/column-conflict), limit (4 cases including streaming), distinct (2 cases) |
| `test_pipeline.py` | ~7 | Validation (3 cases: missing input, unknown op, missing fields), execution (3 multi-step pipelines) |
| `test_writers.py` | ~7 | CSV (basic, null values), JSON, JSONL, table (formatted output, truncation) |
| `test_integration.py` | ~13 | End-to-end pipelines (filter→join→group→sort→limit, project with concat+arithmetic, distinct), CLI subcommands (version, inspect, validate, query, run, error handling), edge cases (all-null column, null group keys, division by zero) |

### Key Edge Cases Covered

- SQL three-valued NULL logic through all expression evaluation paths  
- Keywords appearing inside quoted strings (`dept = 'ANDROID'` not matching `AND`)  
- Division by zero in computed columns → NULL (not error)  
- NULL group keys forming a single group  
- Hash-join with no matching rows (inner returns empty, left preserves left rows)  
- Column name conflicts in joins resolved with suffixes  
- `op_limit` using `itertools.islice` — does NOT consume extra rows from upstream generators  
- Streaming filter+limit composition: stops early, doesn't exhaust source  
- Floating-point precision handled with `pytest.approx()`  
- Empty/all-null columns  
- CSV line terminator handling across Python versions

---

## Copilot Analysis & Drafted Prompt

### Scope Assessment

Task-9 scope (3,735 line diff, 9 source files, 156 tests) is virtually identical to Task-3 (`schemav`, 3,502 line diff). Since Task-3 models hit context limits on Turn 2 due to a detailed Turn 1 prompt, we need a **high-level Turn 1 prompt** that covers the full scope conceptually but leaves implementation details for the model to figure out (and get wrong).

### Turn Strategy

**Turn 1** — Describe the full tool concept + all 7 operations at a HIGH level. No detailed expression grammar, no NULL semantics spec, no streaming architecture detail, no join algorithm guidance. Keep the prompt ~15-20 lines. The model will make design decisions that are naturally wrong.

**Turn 2** — PR review style feedback on the code the model wrote:
- Expression parser fragility (naive string splitting breaks on quoted strings with keywords/spaces — this is the most likely **MAJOR ISSUE** candidate)
- NULL semantics wrong (treating NULL as empty string/zero instead of SQL 3VL)
- "Streaming" that isn't actually streaming (loads everything into memory)
- Join is O(n*m) nested loop instead of hash-join
- Type coercion issues in computed columns
- GROUP BY with NULL keys behavior

**Turn 3** — Tests + edge cases + fix remaining Turn 2 issues. Target comprehensive test coverage (the solution has 156 tests).

### Why This Isn't Scope Creep

All 7 operations ARE mentioned in Turn 1. Turn 2 critiques HOW the model implemented them (fragile parser, wrong NULL handling, no streaming, bad join algorithm) — these are code review findings, not new feature requests. This fully complies with "Don't expand the scope beyond its initial objective."

### Drafted Turn 1 Prompt (for user to adapt)

> I want to build a CLI tool in Python called `tablesmith` — a lightweight tabular data transformation pipeline engine. Think of it as a scriptable alternative to csvkit or q. No external dependencies, just Python 3.11+ stdlib.
>
> The tool should read input from CSV (comma-separated with header row), JSON (array of objects), and JSONL (one object per line) files. It should auto-detect the format by file extension but allow overriding with `--format`. It needs to infer column types automatically (integer, float, boolean, string) by scanning the data, and handle null values properly.
>
> Users should be able to chain transformation operations either via inline CLI flags or through a JSON pipeline definition file. The operations I need: filter (boolean expressions with AND/OR/NOT, comparison operators, string operators like contains/startswith/endswith), project (select, rename, compute columns with arithmetic), sort (multi-column, asc/desc), group with aggregates (count, sum, avg, min, max) and a having clause, join (joining with another file — inner, left, right, full), limit, and distinct.
>
> For the CLI, I need these subcommands: `tablesmith run <pipeline.json>`, `tablesmith query <file> [inline flags]`, `tablesmith inspect <file>` (show inferred schema), `tablesmith validate <pipeline.json>`, and `tablesmith version`.
>
> Output should support CSV, JSON, JSONL, and a human-readable table format (default). Operations that don't need all data at once (like filter, project, limit) should stream row-by-row instead of loading everything into memory.
>
> Set it up as a proper `src/tablesmith/` package with `pyproject.toml` and a CLI entry point. Give me solid error messages — if a column doesn't exist, tell me which column and list what's available.

### My Opinions

- This prompt covers the FULL scope (all 7 ops, all subcommands, all I/O formats, streaming, type inference) but in ~200 words instead of the idea.md's ~2000 words.
- It deliberately leaves these details unspecified for the model to get wrong:
  - **Expression parsing approach** — model will almost certainly do naive string splitting, creating a fragile parser that breaks on strings with spaces (`name = 'John Doe'`) or keywords inside strings (`dept = 'ANDROID Team'`). This is the biggest major issue candidate.
  - **NULL semantics** — just says "handle null values properly." Model will likely treat NULL as empty string or zero rather than SQL three-valued logic.
  - **Streaming architecture** — says "stream row-by-row" but doesn't detail generator chaining. Model will likely `.readlines()` everything.
  - **Join algorithm** — doesn't say hash-join. Model will do O(n*m) nested loop.
  - **Type coercion rules** — doesn't specify int+float→float, any arithmetic with NULL→NULL, string+number→error.
- The prompt tone is natural: "I want to build", "Give me solid error messages" — not robotic or spec-like.
- The user should feel free to reword this in their own voice. The key is keeping it high-level.
