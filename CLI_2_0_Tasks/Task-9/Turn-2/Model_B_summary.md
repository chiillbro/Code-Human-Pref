 No regressions. Here's a summary of everything that was done:
                                                                     
  ---                     
  1. Hardened unknown-column errors (no silent fallback)
                                                        
  errors.py — unknown_column() now always includes both the "did you
  mean..." suggestion AND the full available-columns list:

  tablesmith: error (step #1 [filter]): unknown column 'agee'
    hint: did you mean 'age'?
    hint: available columns: id, dept, name, salary, age

  Previously, when there was a close match the available list was hidden.
  Every op that touches column names (filter, select, sort, group,
  compute, rename, drop, join, distinct) raises ColumnError on the first
  unknown reference — there's no path that silently returns empty or
  skips.

  2. tablesmith inspect <file>

  New subcommand that prints per-column schema:

  Column  Type  Nulls  Total  Sample
  ──────  ────  ─────  ─────  ──────────────────────────────
  id      int       0      6  1, 2, 3, 4, 5
  name    str       0      6  "Alice", "Bob", "Carol", ...
  age     int       1      6  34, 29, 41, 23, 38

  Supports -F json for machine-readable output. Each entry has: column,
  type, nulls, total, samples.

  3. tablesmith validate <pipeline.json>

  New subcommand that checks structure and column references:

  - Catches: missing steps, unknown op names, missing required fields,
  invalid JSON, non-object steps
  - With --input FILE (or if the pipeline file has input): traces columns
  through each step statically — a select that drops a column followed by
  a filter on that column is caught before execution
  - Exits 0 with "ok" message or exits 1 with error details

  4. Pipeline + inline mode

  Already worked in the original code but is now explicitly documented in
  --help epilog and thoroughly tested. Pipeline file steps run first,
  inline flags append after — so tablesmith -p transform.json --filter
  'bonus > 1000' works as expected.

  5. Static column validation on every Op

  Added validate_columns(available) and output_columns(available) to all
  10 operations. This powers the validate command's ability to trace the
  column set through multi-step pipelines without executing them.

  Tests: 26 pass

  Test class: FilterColumnFailFastTest                                  
  Count: 8                                                              
  Covers: typo raises ColumnError, shows suggestion + available, no silent
                                                                        
    fallback, cross-op (compute/select/sort)                            
  ────────────────────────────────────────                              
  Test class: InspectOutputShapeTest                                    
  Count: 5                                                              
  Covers: JSON fields present, types inferred, null count, samples capped,
                                                                        
    text header                                                         
  ────────────────────────────────────────                              
  Test class: ValidateCatchesMalformedTest
  Count: 8
  Covers: missing steps, unknown op, missing field, bad JSON, non-object
    step, valid pipeline, bad column ref, cross-step column tracing
  ────────────────────────────────────────
  Test class: PipelineInlineCombinedTest
  Count: 5
  Covers: inline appended after, execution order, inline-only, input
    override, explain shows combined
