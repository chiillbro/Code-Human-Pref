● Everything green — 43/43 tests, no warnings, and the original
  failure example still behaves identically. Here's what changed.
                                       
  ---
  1. Conditional steps (condition:) + skipped status
                                                    
  New module bujjictl/condition.py — a safe expression evaluator
  built on ast.parse (no eval), whitelisting only
  BoolOp/Compare/UnaryOp/Call/Constant. Functions available
  inside expressions:

  ┌─────────────────────────┬────────────────────────────────┐
  │                         │                                │
  ├─────────────────────────┼────────────────────────────────┤
  │ succeeded('id') /       │ status checks (failed covers   │
  │ failed('id')            │ timed_out too)                 │
  ├─────────────────────────┼────────────────────────────────┤
  │ status('id')            │ raw status string              │
  ├─────────────────────────┼────────────────────────────────┤
  │ exit_code('id')         │ last attempt's exit code       │
  ├─────────────────────────┼────────────────────────────────┤
  │ stdout('id') /          │ last attempt's captured output │
  │ stderr('id')            │                                │
  ├─────────────────────────┼────────────────────────────────┤
  │ and or not == != in not │ combinators                    │
  │  in                     │                                │
  └─────────────────────────┴────────────────────────────────┘

  Example:
  - id: deploy
    depends_on: [build]
    condition: "succeeded('build')"
  - id: cleanup                    
    depends_on: [build]
    condition: "failed('build')"
  - id: prod-only               
    depends_on: [probe]
    condition: "'MODE=prod' in stdout('probe')"
                                               
  Validation (model.py) now also checks: condition parses, uses
  only whitelisted syntax, and every step id it references is
  listed in that step's depends_on (so the result is guaranteed
  available when evaluated).

  Engine rework (engine.py): the old "eager BFS-cancel on
  failure" is replaced by a gate evaluated once all of a step's
  deps are terminal:

  - step with condition: → expression decides. False → skipped.
  True → runs (even if a dep failed — that's how
  cleanup-on-failure works).
  - step without condition: (default gate, matches your
  propagation rule):
    - any dep failed/timed_out/cancelled → cancelled (unchanged
  behavior)
    - all deps skipped → skipped
    - otherwise (≥1 succeeded, rest skipped) → runs

  skipped is a new terminal status; it does not fail the
  workflow. Cascades naturally: skip/cancel resolutions inside
  _schedule_ready loop until fixpoint.

  Report: per-step condition and reason fields (renamed from
  cancel_reason since it now also explains skips), skipped in the
   summary, ↷ glyph in the pretty-printer. New
  examples/conditions.yaml.

  2. Graceful SIGINT/SIGTERM

  In Engine:

  - Installs SIGINT/SIGTERM handlers for the duration of run()
  (restored afterwards). Handler:                             
    a. sets an Event,            
    b. SIGTERM → SIGKILL to every running step's process group
  (children die too),                                         
    c. marks every pending step cancelled (reason: "run 
  interrupted"),                                        
    d. notify_all() to wake the scheduler.
  - Worker threads: communicate() returns when the proc is
  killed; the attempt is recorded with error: "interrupted", the
  step becomes cancelled, and no retry is attempted.            
  Retry-backoff sleeps use Event.wait(delay) so they wake
  immediately on interrupt.                              
  - Scheduling lock is now an RLock so the handler can re-enter
  safely if it fires while the main thread holds the lock.     
  - run() always returns a RunReport (status interrupted); the
  CLI still writes the JSON report and exits 130.

  Verified

  - 43/43 unit tests (17 new): condition parsing/rejection,
  validation of condition refs, success/failure branching,
  stdout-match conditions, mixed-skip propagation runs,
  all-skipped propagation skips, default gate still cancels on
  real failure, programmatic interrupt, real-process SIGINT →
  exit 130 + report on disk, and a test that asserts the child
  sleep PID is actually dead after SIGINT.
  - Manual SIGINT against a 4-step workflow: 2 shells + 2 sleep
  30 children all reaped (no orphans), fast step that finished
  before the interrupt stays succeeded, report written, exit 130.
  - examples/hello.yaml and examples/failure.yaml behave exactly
  as before.

  Heads-up / small schema change: per-step cancel_reason in the
  JSON report is now reason (covers both skip and cancel), and
  there's a new condition field and skipped summary count.
