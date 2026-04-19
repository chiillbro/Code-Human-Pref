# Turn 2 — Classifications

**[NOTABLE ISSUE]: Model B has a potential deadlock in SIGINT handling. The engine uses a regular `threading.Lock` for `self._cond`, and the signal handler calls `self.interrupt()` which does `with self._cond:` (acquiring that lock). If the signal fires while the main thread is inside `_dispatch_ready()` — which is called with the lock already held — the main thread tries to re-acquire a non-reentrant lock it already holds → deadlock. Model A explicitly addresses this by using `threading.RLock` with a comment explaining why.**

---

## 1. Rationale Support (The 7 Questions)

### Q1. Expected Senior Engineer Behavior

Given the Turn 2 prompt ("I need a condition field" + "Ctrl+C crashed hard"), a senior engineer would:
- Design a condition expression language that can check upstream step status (`succeeded`/`failed`) and stdout substring matching — the two use cases the prompt specifically mentions.
- Parse and validate conditions at load time, not at runtime during execution. Specifically, ensure that any step id referenced in a condition is listed in that step's `depends_on` — otherwise the result might not be available when the condition evaluates.
- Rework the engine's scheduling so that the old eager "BFS-cancel-on-failure" is replaced by a gating system: once all deps are terminal, evaluate the condition (or apply default rules). This is necessary because conditional steps MUST be allowed to run even when a dep failed (that's the whole point of `condition: "failed('build')"`).
- Implement the prompt's skip-propagation rules precisely: skip only if ALL deps are skipped; still run if at least one non-skipped dep succeeded.
- Use the `skipped` status label (as the prompt specifies) and ensure it doesn't count as a workflow failure.
- For SIGINT: install signal handlers for SIGINT (and ideally SIGTERM), kill running child process groups (SIGTERM → SIGKILL), mark running/pending steps as `cancelled`, still produce a valid JSON report, exit 130.
- Be careful about lock re-entrancy: signal handlers run on the main thread, so if a signal fires while the main thread holds a lock and the handler tries to acquire the same lock, use an `RLock` or restructure to avoid deadlock.
- Use interruptible sleeps during retry backoff so that Ctrl+C doesn't have to wait for a backoff timer to expire.
- Write tests covering: condition parsing/rejection, condition evaluation with success/failure branching, stdout matching, skip propagation (mixed deps and all-skipped), the default gate still cancelling on real failure, and interrupt behavior (process killing, report generation, exit code).

### Q2. Model A — Solution Quality

**Strengths:**
- Condition parser uses `ast.parse` + hand-rolled evaluator (157 lines). Whitelists specific AST node types (`BoolOp`, `Compare`, `UnaryOp`, `Call`, `Constant`), rejects everything else at validation time. No `eval()` anywhere. This is clean — leverages Python's own parser but restricts to a safe subset. Supports `succeeded('id')`, `failed('id')`, `status('id')`, `exit_code('id')`, `stdout('id')`, `stderr('id')`, with `and`/`or`/`not`/`==`/`!=`/`in`/`not in`.
- `failed('id')` returns True for both `failed` and `timed_out` statuses — nice pragmatic touch. Most users would consider a timed-out step as "failed" when deciding whether to run cleanup.
- Validation in `model.py` checks that condition expressions parse, use only whitelisted syntax, and every step id referenced is in that step's `depends_on`. Errors are collected with the rest of the validation and reported together.
- Engine scheduling rework is well-designed: `_gate()` returns `(run, alt_status, reason)`. Steps with condition → expression decides. Steps without condition → default gate (any dep failure-like → cancelled, all skipped → skipped, else runs). `_schedule_ready()` loops until fixpoint so cascading skips resolve in one pass.
- `_on_step_finished()` simplified — just marks status and clears deps from children's remaining sets, no more BFS cancellation. The gating logic in `_schedule_ready()` handles all resolution. Clean refactor.
- SIGINT handling is correct: uses `threading.RLock` (with explicit comment "so the signal handler can re-enter safely if it fires while the main thread holds the lock"), `threading.Event` for the interrupted flag, separate `_procs_lock` for process tracking, `_on_interrupt()` sets event → kills process groups → marks PENDING → CANCELLED → `notify_all()`.
- Interruptible backoff via `self._interrupted.wait(delay)` — clean use of `Event.wait()` which returns immediately when the event is set. Also checks interrupted flag before each retry attempt and after each attempt.
- `KeyboardInterrupt` fallback in the main wait loop: `except KeyboardInterrupt: self._on_interrupt()` — belt and suspenders in case a SIGINT slips through as a Python exception.
- Report JSON: unified `reason` field (replaces `cancel_reason`, now covers both skip and cancel reasons), adds `condition` field showing the expression source. Summary includes `skipped` count.
- Pretty-printer: `↷` glyph for skipped (cyan), `‼` glyph for interrupted (magenta). Shows condition and reason per step.
- 17 new tests (276 lines in `test_condition.py`): 6 parsing tests (basic, stdout contains, reject attribute access, reject unknown func, reject bare name, reject non-literal arg), 2 validation tests (ref must be in depends_on, invalid expression), 6 execution tests (branching on success, branching on failure, stdout condition, skip propagation mixed, skip propagation all-skipped, default gate still cancels), 3 interrupt tests (programmatic interrupt with timing assertion, real CLI SIGINT via subprocess → exit 130 + report on disk, child PID kill verification). 43/43 total.
- Example `conditions.yaml` demonstrates the exact scenario from the prompt: deploy on success, cleanup on failure, stdout-gated step, and a notify step that depends on one skipped + one succeeded dep (still runs).

**Weaknesses:**
- The condition AST is stored as `condition_ast: ast.Expression` on the Step dataclass. This works fine for runtime use but it's a compiled Python AST object that can't be serialized. If someone wanted to serialize Step objects (e.g., for caching), this would cause issues. Minor, since there's no such use case here, but the field design is slightly awkward (two fields: `condition: str` + `condition_ast: ast.Expression`).
- `StepResult` implements the `StepView` Protocol's methods (`last_exit_code()`, `last_stdout()`, `last_stderr()`) directly on the result class. This works but couples the domain model to the condition evaluator's interface. Not a big deal for this codebase, just a design observation.

### Q3. Model A — Independent Agent Operation

- Correctly identified that the scheduling refactor needed to replace eager BFS cancellation with a gating system. This is the key architectural change — you can't just bolt conditions onto the old "cancel everything downstream on failure" logic because the whole point of conditions is to run steps AFTER a failure.
- Signal handling uses RLock, separate procs lock, Event — shows understanding of concurrent programming pitfalls.
- Added KeyboardInterrupt fallback on the wait loop — proactive defensive coding.
- Condition parser leverages `ast.parse` rather than building a custom tokenizer/parser from scratch — pragmatic reuse of stdlib. Less code, fewer bugs.
- Did not ask any clarification questions. The prompt was specific enough (status check + stdout substring + skip propagation rules + exit 130) that no clarification was needed.
- Tests include an end-to-end SIGINT test that spawns the CLI as a subprocess, sends a real signal, and verifies exit code + report on disk. Also a test that verifies the child sleep PID is actually dead after SIGINT (not orphaned). This is above-average testing rigor.

### Q4. Model A — Communication

- Summary is well-structured: tables for condition functions, clear before/after explanation of the gate semantics, "Verified" section listing 43/43 tests with counts.
- README updates are comprehensive: new "Conditional steps" section with grammar table, a "Skip propagation" paragraph explaining the mixed-deps rule, new "Interrupting a run" section, updated report JSON schema showing `reason` and `condition` fields, exit code table updated with 130.
- One thing the summary does well: it explains the engine rework by contrasting old behavior ("eager BFS-cancel on failure") with new behavior ("gate evaluated once all of a step's deps are terminal"). This helps the reader understand WHY the code changed, not just WHAT changed.

### Q5. Model B — Solution Quality

**Strengths:**
- Custom tokenizer + recursive-descent parser (281 lines) for a proper DSL. Syntax is attribute-access style: `build.status == succeeded`, `probe.stdout contains "MODE=prod"`. This is arguably a more natural syntax for a YAML-based workflow tool — it reads like English and doesn't require Python syntax knowledge.
- Parser validates at compile time: known fields (`status`, `exit_code`, `stdout`, `stderr`), valid status names, correct types for comparison operators, `contains` only on stdout/stderr. Keyword collision detection (`and`/`or`/`not`/`contains` can't be step ids).
- Cleaner condition data model: single `Condition` object on Step with `.source`, `.references`, `.evaluate(lookup)`. No separate AST field. Model A has two fields (`condition: str` + `condition_ast: ast.Expression`).
- `_resolve_step()` has defensive broad exception handling during condition evaluation: `except Exception as e: res.status = FAILED`. This prevents crashes from unexpected condition errors, which is better than only catching `ConditionError`.
- `_kill_process_group()` improved with a polling loop (10 iterations × 50ms) instead of a single 200ms sleep before SIGKILL. More robust behavior for processes that take a moment to handle SIGTERM. Also added fallback `proc.kill()` on unexpected exceptions.
- `interrupt()` is a public method — cleaner API for programmatic use.
- Process tracking via `dict[str, subprocess.Popen]` keyed by step id — you can tell which step is running which process. Also maintains `_cancelled_steps: set[str]` to track which running steps had their processes killed, checked by worker threads after `communicate()`.
- Engine semantics rework: same correct gate pattern as Model A. `_dispatch_ready()` loops until fixpoint. `_resolve_step()` handles condition evaluation and default gating. `_propagate_terminal()` clears deps. All structurally clean.
- Validation: `_validate_condition()` extracted as a helper function, checks that condition parses and only references steps in `depends_on`. Clean.
- 16 new tests: 9 condition parsing tests (status eq/ne, contains, exit_code, and/or/not/paren, bad status, bad field, contains on status, trailing), 7 engine tests (run_on_failure, stdout_contains, skip_propagation_all_skipped, skip_propagation_mixed, condition_ref_must_be_in_depends_on, invalid_condition_syntax, interrupt_kills_and_reports). 42/42 total.
- Example `conditional.yaml` covers the same scenarios as Model A's: deploy/cleanup branching, stdout-gated step, notify with mixed deps.

**Weaknesses:**
- **Deadlock in signal handler:** The engine uses `threading.Lock` (not `RLock`) for `self._cond`. The signal handler calls `self.interrupt()` which does `with self._cond:` (acquiring the lock). If the signal fires while the main thread is executing `_dispatch_ready()` — which runs with the lock held — the main thread attempts to re-acquire a non-reentrant lock → deadlock. The main thread's loop is: `with self._cond: _dispatch_ready(); while not all_terminal: _cond.wait(); _dispatch_ready()`. During `_dispatch_ready()`, the lock is held. If SIGINT arrives at that moment, the handler deadlocks. Model A explicitly uses `RLock` to handle this and comments on why. This is a real concurrency bug — not a theoretical one — and is exactly the kind of issue a senior engineer writing signal + lock code needs to get right.
- **Interruptible sleep uses `_cond.wait(timeout=delay)`** instead of a dedicated `Event.wait(delay)`. This works but is semantically muddled — the condition variable is meant for scheduling state changes, not for interrupt signaling. Also, since it acquires the condition lock, it temporarily blocks scheduling while a worker is sleeping through backoff, though in practice the sleep releases the lock via `_cond.wait()`.
- The condition parser, while principled, is 281 lines vs Model A's 157 — nearly double. Both achieve the same result (safe expression evaluation with validation). The custom parser is more code to maintain and test. For a tool where the condition language is a small configuration DSL (not a general-purpose language), leveraging `ast.parse` is more pragmatic.
- Report keeps split fields: `cancel_reason` and `skip_reason`. The pretty-printer shows both. This means a step can have both `cancel_reason` and `skip_reason` as `null` or one populated — more fields in the JSON report for the same information. Model A's unified `reason` field is cleaner.
- Only 1 interrupt test (programmatic via `Timer`). No real SIGINT test (spawning CLI subprocess and sending actual signal). No child PID kill verification. Given that SIGINT handling with signals + locks + process groups is tricky, more thorough testing would be expected.
- `_interrupted` is a plain `bool` rather than `threading.Event`. Works on CPython due to GIL but isn't formally thread-safe. `threading.Event` is designed for cross-thread signaling.

### Q6. Model B — Independent Agent Operation

- Correctly redesigned the scheduling with the gate pattern. Same architectural insight as Model A — the old eager cancellation had to be replaced.
- Made `interrupt()` a public method with clean separation: lock → snapshot procs → mark pending cancelled → notify → kill outside lock. The lock ordering (acquire, mutate, release, then kill) is correct to avoid holding the lock during potentially slow process killing.
- The `_cancelled_steps` tracking is a nice addition — worker threads can check `sid in self._cancelled_steps` after an attempt to know if they were interrupted, separate from the global `_interrupted` flag.
- Custom DSL parser shows ambition — building a proper tokenizer + recursive-descent parser from scratch. Good CS fundamentals, though arguably overkill for this scope.
- Did not notice or address the RLock issue. This is the kind of thing a senior concurrent-programming engineer would catch during implementation — when you install a signal handler that acquires a lock, you need to think about what happens if the thread already holds that lock.

### Q7. Model B — Communication

- Summary is well-organized: condition grammar table, engine semantics rework table, interrupt handling description, files touched list.
- README updates cover conditions grammar, semantics table, interrupts section, updated exit codes, updated report schema. The conditions grammar is presented as a text block with examples — readable.
- Example `conditional.yaml` has helpful inline comments explaining the setup.
- Summary says "verified live: sent SIGINT to a running bujjictl run with two sleep 30 steps" — manual verification is good, but note this only works if SIGINT doesn't happen to arrive during `_dispatch_ready()`.

---

## 2. Axis Ratings & Preference

1. **Correctness:** 3 (A slightly preferred) — Both implement the condition semantics and skip propagation correctly. Model B has a deadlock bug in the signal handler path that can cause the tool to hang on Ctrl+C if the timing is unlucky (signal during `_dispatch_ready()`). Under CPython with simple workflows the window is narrow, but it's a real concurrency defect. Model A's RLock usage is explicitly correct.

2. **Merge readiness:** 3 (A slightly preferred) — Both produce clean, well-structured diffs. Model A's `_on_interrupt()` is safe to merge as-is. Model B's `interrupt()` has the deadlock issue — a reviewer would catch this during concurrency review and request `RLock`. Both have good test coverage.

3. **Instructions following:** 4 (A minimally preferred) — Both implement exactly what the prompt asks: condition field with status + stdout checks, skip propagation rules (mixed deps → run, all skipped → skip), SIGINT with SIGTERM→SIGKILL to process groups, mark running/pending as cancelled, produce JSON report, exit 130. Essentially a tie — both nail every explicit requirement.

4. **Well scoped:** 4 (A minimally preferred) — Both are well-scoped. Model A adds `stderr()` function and `exit_code()` function to conditions — reasonable since the prompt says "maybe check if a step's stdout contains a specific string" (the "maybe" implies "figure out what makes sense"). Model B adds `stderr contains`, `exit_code ==`, `status !=` — same scope additions. Both add SIGTERM handling alongside SIGINT. Neither over-engineers.

5. **Risk Management:** N/A — Neither faced destructive or high-stakes decisions beyond normal code changes.

6. **Honesty:** 4 (Tie) — Both summaries accurately describe what was built. Model A: "43/43 unit tests (17 new)" — verifiable from the diff. Model B: "42/42 tests pass" — verifiable. Neither makes misleading claims about testing or behavior.

7. **Intellectual Independence:** 4 (Tie) — Both independently arrived at the same architectural solution (gate-based scheduling replacing eager BFS cancellation). Both added SIGTERM alongside SIGINT. Both made the condition language richer than the minimum (adding `exit_code`, `stderr`, etc.). Model A's RLock insight shows deeper concurrent-programming awareness, but Model B's custom DSL parser shows more language-design ambition. Roughly even.

8. **Verification:** 3 (A slightly preferred) — Model A: 17 new tests including 3 interrupt tests (programmatic, real CLI SIGINT with subprocess → exit 130 + report, child PID kill verification). Model B: 16 new tests with 1 interrupt test (programmatic via Timer). The interrupt path is the trickiest part of this change — Model A's real-signal test actually exercises the signal handler → process kill → report write → exit code pipeline end-to-end, which is where Model B's deadlock bug lives.

9. **Reaching for Clarification:** 4 (Tie) — Neither asked for clarification. The prompt is specific enough: status checks (`succeeded`/`failed`), stdout substring, skip-if-all-deps-skipped, SIGTERM→SIGKILL, exit 130. No ambiguity that needed resolving.

10. **Engineering process:** 3 (A slightly preferred) — Model A demonstrates better concurrent-programming discipline: RLock with explanatory comment, separate `_procs_lock`, `threading.Event` for the interrupted flag, KeyboardInterrupt fallback. Model B uses regular Lock (deadlock risk), plain bool for interrupted flag (GIL-dependent), condition variable wait for interruptible sleep (semantically muddled). The condition parser difference (ast.parse vs custom) is more a style choice than an engineering quality issue.

11. **Communication:** 4 (Tie) — Both have clear, well-structured summaries, comprehensive README updates, and good example files. Model A explains the "why" of the engine rework slightly better (contrasting old vs new semantics). Model B's conditions grammar in the README is slightly more readable (text block vs function table). Very close.

12. **Overall Preference:** 3 (A slightly preferred)

---

## 3. Justification & Weights

### Top Axes
1. Correctness (deadlock bug)
2. Verification (interrupt testing thoroughness)
3. Engineering process (concurrency discipline)

### Overall Preference Justification

Model A is slightly preferred over Model B. Both models deliver solid implementations of both requested features — conditional steps with status/stdout checks and proper skip propagation, plus SIGINT handling with process group killing, cancelled marking, and exit 130. The architectural approach is nearly identical: both replace the old eager BFS-cancel-on-failure with a gate-based scheduling system that evaluates conditions once all deps are terminal, both loop until fixpoint to cascade skips, and both install signal handlers that kill process groups and mark pending steps cancelled. Both have good test coverage (43 vs 42).

The key differentiator is a concurrency bug in Model B's signal handling. The engine's condition variable is backed by a regular `threading.Lock`. The signal handler calls `self.interrupt()`, which tries to acquire that lock via `with self._cond:`. Since Python signal handlers execute on the main thread, and the main thread may already hold the lock (e.g., during `_dispatch_ready()`), this creates a self-deadlock — the main thread tries to re-acquire a lock it already holds. With a regular Lock, that's a hang. Model A explicitly uses `threading.RLock` and comments on the re-entrancy requirement. This isn't an exotic concern — it's fundamental to writing signal handlers that touch shared state, and it's the kind of thing you'd expect a senior engineer to get right when implementing SIGINT handling for a concurrent executor.

Model A also has more thorough interrupt testing: three tests including a real end-to-end SIGINT test (spawns the CLI as a subprocess, sends signal, checks exit 130 + JSON report on disk) and a child PID kill verification test. Model B has one programmatic test using `Timer`. The real-signal test is particularly valuable because that's the path where the deadlock bug would manifest.

Model B does have legitimate strengths: the custom DSL parser is more principled for a true domain-specific language, the `Condition` data model is cleaner (single object vs two fields), `_kill_process_group()` has a more robust polling loop, and the `interrupt()` public API is a nice touch. But these don't outweigh a concurrency bug in the core feature that was explicitly requested.

This is a "slightly" (3) rather than "medium" (2) preference because: the deadlock window is narrow in practice (only during `_dispatch_ready()` execution, not during `_cond.wait()` which is where the main thread spends most time), both implementations are otherwise high-quality, and the architectural decisions are the same. It's one specific but important bug, not a pattern of quality issues.

---

## 5. Next Step / Follow-Up Prompt

Model A (the winner) has no significant corrections needed — the conditions and SIGINT implementations are solid and well-tested. The one remaining thing from the original scope that neither model has addressed across both turns is the `partial` workflow status. The Turn 1 prompt asked for overall status values of `succeeded`, `failed`, and `partial`, but both models only implemented `succeeded` and `failed` (and now `interrupted`). This is a natural discovery for Turn 3.

**Draft Turn 3 Prompt:**

> So I ran the conditions example where the build step fails, the deploy step gets skipped (condition false), and the cleanup step succeeds. The report says overall status is "failed". But like, the cleanup DID succeed, and deploy was intentionally skipped — it's not like everything failed. Can you add a "partial" status for when you have a mix of outcomes? Like, "succeeded" should mean every step that actually ran succeeded (skipped is fine), "failed" should mean every step that ran failed, and "partial" should be the in-between — some passed, some didn't. Also, one thing I noticed while looking at the report json — there's no easy way to tell at a glance how long the whole workflow spent on retries vs actual execution. Can you add a total_retry_delay to the summary as well? That'd help me figure out if I need to tune backoff settings.
