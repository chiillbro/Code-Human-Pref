 Done — 15/15 tests pass. Summary of changes:
                                                                          
  1. Unknown-column failures are fail-fast and complete                   

  errors.py:unknown_column() now always emits all three pieces: the bad
  name, a did you mean '…'? suggestion (when one exists), and the full
  available columns: … list. There is no code path in expr.py or any op
  where a missing column degrades to None — Expression._eval(Name) raises,
   and Filter.apply / step_column_flow pre-validate referenced columns
  before touching rows.

  2. tablesmith inspect <file>

  New tablesmith/schema.py with infer_schema() (column, inferred type,
  nulls, non_null, total, samples) and render_schema_table(). CLI: text
  table by default, --json for machine output, --samples N, --no-infer.

  3. tablesmith validate <pipeline.json>

  Pipeline.validate() in pipeline.py plus step_column_flow() in ops.py
  statically thread the column set through every step. Catches: bad JSON,
  missing steps, non-object step, unknown op, missing required field (e.g.
   filter without expr), and any column reference that can't exist at that
   point. Exit code 1 on first failure, 0 with a per-step ok → columns: …
  trace on success. --input DATA lets you supply the schema if the
  pipeline file doesn't name one.

  4. Pipeline-file + inline flags

  Already supported; now explicitly documented in --help and README, and
  the step-index in error messages is computed against the combined
  pipeline (cli.py:_cmd_run offsets by len(pipe.steps)), so -p file.json
  --select nosuch reports step #2 not step #1.

  5. Tests — tests/test_tablesmith.py (stdlib unittest)

  - TestFilterUnknownColumn — library-level ColumnError contents; CLI exit
   code 1 + no partial stdout + all three hint components; no-close-match
  case still lists columns.
  - TestInspect — infer_schema() descriptor shape; CLI table has
  column/type/nulls/samples headers and age int 1; --json parses with
  correct type/nulls/samples.
  - TestValidate — valid pipeline → exit 0; missing expr, unknown op,
  non-object step, missing steps, bad column ref → each exit 1 with
  specific message.
  - TestPipelinePlusInline — proves inline ops run after file steps by
  asserting result order (Carol, Eve after file-filter → inline-sort →
  inline-limit); --explain shows filter | select | limit; inline error
  reported at combined index.

  Run with python -m unittest discover -v.
