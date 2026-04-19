 Both features done and verified — 42/42 tests pass. 
                                                        
  ---                                  
  1. Conditional steps (condition:)
                                   
  New module bujjictl/condition.py — a tiny tokenizer +
  recursive-descent parser for a safe boolean mini-language (no
  eval). Supports:

  <dep>.status    == | !=
  succeeded|failed|timed_out|skipped|cancelled
  <dep>.exit_code == | !=  <int>
  <dep>.stdout    contains "<string>"
  <dep>.stderr    contains "<string>"
  combined with: and, or, not, ( )

  Validation (model.py): condition must parse, and may only
  reference step ids that appear in that step's depends_on (so
  the referenced result is guaranteed to be terminal when
  evaluated).

  Engine semantics rework (engine.py): scheduling no longer
  eagerly cancels downstream on failure. Instead a step becomes
  ready once all deps are terminal, then it is resolved:

  ┌─────────────────────────────────────┬────────────────────┐
  │                Case                 │       Result       │
  ├─────────────────────────────────────┼────────────────────┤
  │ has condition → evaluates true      │ run                │
  ├─────────────────────────────────────┼────────────────────┤
  │ has condition → evaluates false     │ skipped (with      │
  │                                     │ reason)            │
  ├─────────────────────────────────────┼────────────────────┤
  │ no condition, any dep               │ cancelled (old     │
  │ failed/timed_out/cancelled          │ behaviour          │
  │                                     │ preserved)         │
  ├─────────────────────────────────────┼────────────────────┤
  │ no condition, deps exist and all    │ skipped            │
  │ are skipped                         │                    │
  ├─────────────────────────────────────┼────────────────────┤
  │ no condition, ≥1 dep succeeded      │ run                │
  │ (rest may be skipped)               │                    │
  └─────────────────────────────────────┴────────────────────┘

  Skip/cancel resolution is synchronous and re-scans, so a chain
  of skips propagates immediately. New skipped status,
  skip_reason, and condition are in the JSON report and the
  pretty-printer. A run with only succeeded+skipped steps is
  overall succeeded.

  See examples/conditional.yaml — deploy runs on build success,
  cleanup runs on build failure, prod-only is gated on
  probe.stdout contains "MODE=prod", and notify (depends on both
  deploy + cleanup) still runs because one non-skipped dep
  succeeded.

  2. Graceful Ctrl-C / SIGTERM

  Engine.run() installs SIGINT/SIGTERM handlers (restored on
  exit) that call Engine.interrupt():

  - sets an interrupted flag (idempotent)
  - snapshots all live Popen handles (tracked per step in
  _procs), marks those steps for cancellation, marks every
  pending step cancelled with reason "run interrupted", and wakes
   the scheduler
  - outside the lock, sends SIGTERM to each child's process
  group, polls briefly, then SIGKILL
  - worker threads notice the flag (before launching, after the
  proc dies, and via an interruptible backoff sleep) and mark
  their step cancelled
  - run() returns a complete RunReport with status interrupted;
  CLI writes the JSON and exits 130

  Verified live: sent SIGINT to a running bujjictl run with two
  sleep 30 steps → both process groups killed (trap caught
  SIGTERM, exit 143), no orphans, pending step cancelled, valid
  JSON report written, exit code 130.

  Files touched

  bujjictl/condition.py     (new)  condition parser/AST/evaluator
  bujjictl/model.py                Step.condition + validation
  bujjictl/engine.py        (rewritten) ready→resolve scheduling,
   SKIPPED, proc tracking, interrupt()
  bujjictl/report.py               skipped/interrupted styles,
  condition + skip_reason rows
  bujjictl/cli.py                  exit 130 on interrupted
  examples/conditional.yaml (new)
  tests/test_condition.py   (new)  9 tests
  tests/test_engine.py             +7 tests (conditions, skip
  propagation, interrupt)
  README.md                        condition grammar, new
  semantics, interrupt section, exit codes