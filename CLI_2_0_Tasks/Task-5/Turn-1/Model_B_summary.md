 Done — bujjictl is built, packaged, and verified end-to-end.
                                                                              
  Project layout 

  pyproject.toml
  README.md
  src/bujjictl/
    __init__.py, __main__.py
    miniyaml.py    # stdlib-only YAML subset parser
    workflow.py    # schema model + full validation (refs, dups, types, cycles)
    engine.py      # DAG scheduler: concurrency, retries+backoff, timeouts, env,
  cancellation
    report.py      # pretty terminal renderer for JSON reports
    cli.py         # validate / run / report subcommands
  examples/
    demo.yaml      # happy path incl. fan-out, retries, env
    failing.yaml   # demonstrates timeout + downstream skip
    broken.yaml    # demonstrates aggregated validation errors

  Design highlights

  YAML schema — top-level name, max_parallel, env, defaults, steps[]. Each step:
  id, run, needs, env, timeout, retries, retry_backoff, shell, cwd. Full schema
  documented in README.md and workflow.py.

  Validation (workflow.py) — collects all errors in one pass before raising:
  unknown keys, type checks, missing/duplicate ids, self-deps, duplicate deps,
  unknown needs refs, and Kahn-based cycle detection that prints the actual cycle
  path.

  Engine (engine.py) — condition-variable scheduler launches ready steps up to
  max_parallel; each step runs in its own process group so timeouts kill children
  too. Retries use exponential backoff (base * 2^(n-1)) with interruptible sleeps.
   On failure, transitive downstream steps are marked skipped immediately. SIGINT
  triggers graceful cancellation of running + pending steps.

  Reporting — run always emits a structured JSON report (workflow status, per-step
   status, timestamps, duration, every attempt's exit code / stdout / stderr /
  timed_out flag). bujjictl report <file> pretty-prints it with color, status
  glyphs, and truncated output blocks.

  Zero deps — the only non-stdlib need was YAML, so miniyaml.py implements the
  subset required (block mappings/sequences, [a,b] flow lists, | literals,
  comments, typed scalars). pyproject.toml uses plain setuptools; pip install .
  gives you the bujjictl entrypoint (verified in a fresh venv).

  Try it

  pip install .
  bujjictl validate examples/demo.yaml
  bujjictl run examples/demo.yaml -o demo.report.json
  bujjictl report demo.report.json