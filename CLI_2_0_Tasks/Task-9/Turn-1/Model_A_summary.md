 Done. Here's what was built:           
                                                                                          
  Layout

  pyproject.toml          # installable package, entry point: tablesmith →
  tablesmith.cli:main
  README.md
  tablesmith/
    __init__.py
    __main__.py           # python -m tablesmith
    cli.py                # argparse front-end; custom _StepAction preserves flag ORDER
    pipeline.py           # Pipeline engine + JSON pipeline-file loader
    ops.py                #
  filter/select/rename/drop/sort/group/join/compute/limit/distinct
    expr.py               # safe AST-based expression evaluator (no eval())
    ioformats.py          # CSV/JSON/JSONL read+write, format detection, delimiter sniffing
    typeinfer.py          # per-column automatic int/float/bool/null inference
    errors.py             # TablesmithError with step/row/hint + did-you-mean suggestions

  Key design choices

  - Inline chaining — each op flag (--filter, --sort, …) uses a shared _StepAction that
  appends (op, arg) to one ordered list, so --filter x --sort y --filter z runs in exactly
  that order. Same flag can be repeated.
  - Pipeline files are JSON (stdlib-only rules out YAML). -p file.json loads steps; any
  inline flags are appended after, and CLI -o/input override the file's input/output.
  - Type inference samples up to 200 rows per column and coerces only when the whole sample
   agrees; otherwise leaves strings alone. --no-infer disables it.
  - Expressions parsed via ast with a strict node/function whitelist — comparisons, bool
  ops, arithmetic, ternary, and helpers like lower/contains/coalesce/matches. No attribute
  access or dunders, so no sandbox escape.
  - Errors carry step, step_index, row_index, source and a hint; unknown
  columns/ops/aggregates use difflib for "did you mean…".
  - Join builds a hash index on the right side; supports inner/left/right/outer,
  left_key=right_key, and a collision prefix.

  Verified

  - pip install . produces a working tablesmith binary (tested in a fresh venv).
  - filter → select → sort → JSON out ✔
  - group with avg/count + alias ✔
  - left join CSV↔JSON ✔
  - compute + limit, rename + distinct ✔
  - stdin JSONL → stdout CSV ✔
  - pipeline file with 5 steps + --explain ✔
  - Error cases: column typo → suggestion, bad aggregate → list of valid ones, bad expr →
  pointer, missing file, bad pipeline JSON ✔

  One known sharp edge

  --sort -age is eaten by argparse as a flag; use --sort=-age or --sort 'age:desc'
  (documented in README and --help).
