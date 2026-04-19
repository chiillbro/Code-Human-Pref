# Turn 1 — Classifications

**[MAJOR ISSUE FLAG]: Model B committed the entire `build/lib/bujjictl/` directory (7 duplicate source files) to version control. This is build artifact output that should never be checked in. No `.gitignore` was created at all. This alone would block any PR immediately.**

**[MAJOR ISSUE FLAG]: Model B produced zero automated tests for a tool with concurrent execution, DAG resolution, retry/timeout logic, and a custom YAML parser. Model A wrote 26 unit tests. The prompt didn't explicitly say "write tests", but any senior engineer building a tool this complex would verify their work with tests — especially when shell subprocess execution and concurrency race conditions are involved.**

---

## 1. Rationale Support (The 7 Questions)

### Q1. Expected Senior Engineer Behavior

Given the prompt, a senior engineer would:
- Design a clear YAML schema for workflows with steps, dependencies, retries, timeouts, and env vars — documenting it in the README
- Build a custom YAML parser since stdlib has no YAML lib and the prompt says "no external dependencies"
- Implement thorough validation that collects all errors at once (missing refs, cycles, type errors, duplicate ids) — the prompt specifically says "I don't want the engine crashing midway"
- Build a concurrent executor that respects the dependency DAG, runs independent steps in parallel, handles retries with backoff, timeouts with process killing, and cascades cancellation downstream on failure
- Generate a structured JSON report with execution times, attempt counts, stdout/stderr, exit codes
- Implement the three CLI subcommands (validate, run, report) with appropriate exit codes
- Write unit tests covering at least the validator (cycles, bad refs), engine (concurrency, retry, timeout, cancellation), and parser basics
- Set up `pyproject.toml` with proper entry point, create `.gitignore`, provide example workflows
- NOT commit build artifacts to version control

### Q2. Model A — Solution Quality

**Strengths:**
- Comprehensive custom YAML parser (`yaml_parser.py`, 379 lines) supporting block scalars (`|` and `>`), quoted strings, comments, nested structures, and escape sequences. Parser includes duplicate key detection which is a nice touch.
- Validation in `model.py` collects all errors in a single pass: unknown keys, required fields, types/ranges, id format/uniqueness, unknown `depends_on` refs, self-deps, and cycle detection — exactly what the prompt asked for ("I don't want the engine crashing midway").
- Cycle detection via iterative DFS with WHITE/GRAY/BLACK coloring that reconstructs and reports the actual cycle path (e.g., `a -> b -> c -> a`). Iterative approach avoids stack overflow on deep graphs.
- Engine uses `ThreadPoolExecutor` with a `Condition` variable for scheduling. Shared state (`_results`, `_remaining_deps`) is protected by the condition's lock. Steps finish → deps cleared → ready steps scheduled. Clean design.
- Process group killing on timeout: `start_new_session=True` then `os.killpg(SIGTERM)` → wait → `os.killpg(SIGKILL)`. Properly kills child processes too.
- Downstream cancellation via BFS over dependents — when a step fails, all transitive downstream steps are immediately marked `cancelled` with reason.
- Retry with exponential backoff: `retry_backoff * 2^(attempt-1)`. Each retry attempt gets its own fresh timeout.
- JSON report includes per-attempt detail: `started_at`, `finished_at`, `duration`, `exit_code`, `timed_out`, `stdout`, `stderr`, `error`. Summary section counts succeeded/failed/timed_out/cancelled/total.
- Uses `datetime.now(timezone.utc)` for timestamps — explicit UTC, portable across timezones.
- 26 unit tests covering: simple success, dependency ordering, concurrency timing, failure cascading, retries until success, timeouts, env injection, validation errors (missing name, unknown dep, duplicate id, cycle, self-dep, unknown key, bad types), YAML parser (scalars, quotes, nested mappings, sequences, block scalars, comments, duplicates).
- Two well-designed example YAML files: `hello.yaml` (happy path with fan-out, retries, env) and `failure.yaml` (failure + timeout + downstream cancellation).

**Weaknesses:**
- No SIGINT/Ctrl+C handling — if you hit Ctrl+C during a long-running workflow, it'll crash ungracefully without killing child processes or producing a partial report. The prompt doesn't ask for this explicitly, but a senior engineer would anticipate it.
- Flat `bujjictl/` package layout instead of the more standard `src/bujjictl/`. Both work but `src/` is modern best practice and avoids accidental import of the source package during testing.
- `name` field is required — validation fails if missing. A more forgiving approach would be to default to the filename stem, since many workflow files would naturally name themselves.
- The report doesn't include a `partial` status — it's either `succeeded` or `failed`. The prompt doesn't specify a partial status, but it's useful when some steps succeed and others fail.

### Q3. Model A — Independent Agent Operation

- Model A didn't ask any clarification questions — just designed the schema and built it. Given the prompt's conversational tone ("figure out the best way to design the yaml schema and execution engine"), this was the right call. The requirements were clear enough that asking would've been over-reaching.
- Created a `.gitignore` proactively that covers `__pycache__/`, `*.egg-info/`, `build/`, `dist/`, `.venv/`, `*.report.json` — good judgment.
- Designed a clean YAML schema with sensible defaults (`timeout: 300`, `retries: 0`, `retry_backoff: 1.0`, `shell: /bin/sh`) — these are reasonable for a real workflow tool.
- Injected `BUJJI_WORKFLOW`, `BUJJI_STEP_ID`, `BUJJI_ATTEMPT` env vars automatically — not explicitly requested but a natural addition for a workflow engine.
- Included examples and README documentation. These are appropriate additions for a tool meant to be installable and usable.
- Did not take any risky or destructive actions.

### Q4. Model A — Communication

- Summary is well-organized: layout tree, design highlights section covering each component, "Verified" section listing specific test results, "Try it" section with exact commands. This makes it easy to understand what was built.
- The summary also lists 26/26 tests pass, example runs verified, `pip install .` in fresh venv verified — solid QA communication.
- Also finishes with "Zero runtime dependencies — only setuptools at build time for pip install" — confirms the constraint was met.
- README.md (159 lines) is thorough: install instructions, full YAML schema reference, injected env vars table, execution semantics, validation description, CLI commands, report JSON format, quickstart commands.
- One minor nit: the summary mentions "YAML subset parser" handles "- key: val inline mappings, | / > block scalars (with -/+ chomping)" — this is accurate based on the code.

### Q5. Model B — Solution Quality

**Strengths:**
- Uses Kahn's algorithm for topological sort (`_topo_sort` in `workflow.py`) — textbook correct approach. If remaining nodes exist after Kahn's, it walks to find and report the actual cycle path with `_find_cycle`.
- Engine uses `threading.Condition` with a proper scheduling loop bounded by `max_parallel`. Steps tracked via `_pending`, `_running` sets with condition variable notification on completion.
- SIGINT handling is well-implemented: installs `signal.SIGINT` handler on main thread, sets a `threading.Event`, the event is checked in the scheduling loop, during step execution, and during backoff sleeps (interruptible sleep pattern: `while time.monotonic() < end and not cancel_event.is_set()`). Running processes get SIGTERM → SIGKILL. Pending steps marked `cancelled`. Restores original signal handler in `finally` block.
- Process group killing on timeout via `os.killpg` with SIGTERM → wait 5s → SIGKILL fallback.
- `cwd` option per step is a nice addition that many real workflows would need.
- `max_parallel` as a workflow-level config with `os.cpu_count()` default — sensible design.
- `name` field defaults to filename stem when omitted — more forgiving than requiring it.
- Three example files including `broken.yaml` that demonstrates aggregated validation error reporting.
- `src/bujjictl/` layout is the modern best practice for Python packaging.

**Weaknesses:**
- **Committed `build/lib/bujjictl/` directory to version control.** The diff includes `build/lib/bujjictl/__init__.py`, `build/lib/bujjictl/__main__.py`, `build/lib/bujjictl/cli.py`, `build/lib/bujjictl/engine.py`, `build/lib/bujjictl/miniyaml.py`, `build/lib/bujjictl/report.py`, `build/lib/bujjictl/workflow.py` — 7 files that are exact duplicates of the source. This is build output from running `pip install .` or `python setup.py build`. A senior engineer would never commit this, and any reviewer would immediately block the PR.
- **No `.gitignore` file at all.** This is why the build artifacts got committed. Even a minimal `.gitignore` with `build/`, `dist/`, `*.egg-info/`, `__pycache__/` would have prevented this.
- **Zero test files.** The summary says "verified end-to-end" but there are no automated tests. For a tool with concurrent execution, DAG scheduling, retry/timeout logic, and a custom YAML parser, this is a significant gap. How do you know cycle detection works for long chains? How do you know retries reset the timeout? How do you know concurrent steps actually run concurrently? Manual testing during development doesn't prevent regressions.
- `miniyaml.py` doesn't support folded `>` scalars — only `|` literal blocks. While the README documents this, it's a limitation compared to Model A's parser which handles both `|` and `>`.
- `_now_iso()` uses `_dt.datetime.now()` without timezone info — timestamps will be in local time, which isn't portable. Model A correctly uses `timezone.utc`.
- Downstream steps of a failed step get status `"skipped"` — but conceptually they should be `"cancelled"` since they were prevented from running due to upstream failure. "Skipped" usually implies a deliberate decision not to run (like a condition not being met). The prompt says "gracefully cancelled" — using "skipped" is a semantic mismatch.
- `_run_attempt` polls `proc.communicate(timeout=0.1)` in a tight loop to check the cancel event. This works but adds ~100ms latency overhead to every step completion and creates unnecessary CPU wakeups.

### Q6. Model B — Independent Agent Operation

- Proactively implemented SIGINT handling — not requested but a very good judgment call for a tool that runs shell subprocesses. Shows thinking about real-world usage.
- Added `cwd` per-step and `max_parallel` at workflow level — reasonable additions that don't over-engineer.
- Did not ask any clarification questions — appropriate given the prompt's level of detail.
- Made `name` optional (defaults to filename) — sensible default behavior.
- However, committed build artifacts without noticing or cleaning up. This shows a lack of attention to git hygiene. Running `pip install .` is fine to verify installability, but you need to clean (or .gitignore) the `build/` directory before committing.
- Did not create .gitignore — an oversight that a senior engineer wouldn't make for a greenfield project.
- Did not write any tests. For a tool meant to be "packaged and verified", the absence of automated tests is a notable gap in engineering process.

### Q7. Model B — Communication

- Summary is well-structured: layout, design highlights, schema documentation. Lists each component with brief explanation.
- README.md (129 lines) covers install, commands, schema, execution semantics, report format, examples, and notes on YAML limitations. The execution semantics section nicely documents Ctrl-C behavior.
- Summary says "verified end-to-end" — this is somewhat misleading since there are no automated tests. The verification was presumably manual (running examples), but the phrasing implies more thorough testing than what actually happened.
- The summary says "bujjictl.miniyaml" parser supports "the subset needed for workflow files" and explicitly notes unsupported features — honest about scope.
- Report pretty-printer includes truncated output blocks, color support with auto-detection, and status glyphs — good UX communication.
- Exit code documentation in README: "`run` exits `0` on overall success, `1` if any step failed/timed-out, `2` on validation error" — is clear, though an unusual ordering (most tools exit 1 on failure, 2 on usage error). 

---

## 2. Axis Ratings & Preference

1. **Correctness:** 3 (A slightly preferred) — Both implement the core correctly. Model A proves correctness with 26 tests. Model B has no automated verification. Both handle deps, concurrency, retries, timeout, cancellation.

2. **Merge readiness:** 2 (A medium preferred) — Model B committed `build/lib/` artifacts (7 duplicate files) and has no `.gitignore`. This is an auto-block in any PR review. Model A has clean `.gitignore` and no build artifacts.

3. **Instructions following:** 4 (A minimally preferred) — Both followed the prompt well. Model A requires `name` which the prompt mentions but doesn't mark as required. Model B defaults to filename. Both implement all requested subcommands, validation, execution, and reporting. Essentially a tie.

4. **Well scoped:** 4 (A minimally preferred) — Both are well-scoped. Model B adds SIGINT, `cwd`, `max_parallel` beyond what was asked — arguably nice-to-haves but not over-engineering. Model A adds tests which is more appropriate for "build the tool for me". Very close to even.

5. **Risk Management:** N/A — Neither faced high-stakes or destructive decisions. Both are greenfield builds.

6. **Honesty:** 3 (A slightly preferred) — Model B's summary says "verified end-to-end" but has no automated tests. Model A's summary says "26/26 unit tests pass" which is verifiable from the diff.

7. **Intellectual Independence:** 5 (B minimally preferred) — Model B showed more independent thinking: SIGINT handling, `max_parallel`, `cwd`, interruptible backoff sleeps. These are practical additions a senior engineer would anticipate.

8. **Verification:** 2 (A medium preferred) — Model A wrote 26 tests covering parser, validator, and engine (concurrency timing, dep ordering, retry, timeout, env injection, failure cascading). Model B has zero tests.

9. **Reaching for Clarification:** 4 (Tie) — Neither asked for clarification. The prompt was conversational but clear enough. Both proceeded reasonably.

10. **Engineering process:** 2 (A medium preferred) — Model A follows SWE best practices: tests, `.gitignore`, clean version control, proper verification. Model B committed build artifacts and has no tests — both are basic engineering hygiene failures.

11. **Communication:** 4 (Tie) — Both have good READMEs, clear summaries, example files. Model B's summary slightly overstates verification. Both communicate design decisions well.

12. **Overall Preference:** 2 (A medium preferred)

---

## 3. Justification & Weights

### Top Axes
1. Engineering process
2. Verification
3. Merge readiness

### Overall Preference Justification

Model A is medium preferred over Model B. The biggest factor is engineering hygiene. Model B committed the entire `build/lib/bujjictl/` directory to version control — 7 duplicate source files that are build artifacts from running `pip install .`. This is something any reviewer would immediately flag, and it usually takes a senior engineer about 30 seconds to add a `.gitignore` to prevent. Model A created a proper `.gitignore` covering `build/`, `dist/`, `__pycache__/`, etc. — no artifacts committed.

The second major factor is verification. Model A includes 26 unit tests across `test_engine.py`, `test_model.py`, and `test_yaml_parser.py`, covering concurrency timing (three 0.5s sleeps finish in under 1.2s), dependency ordering with marker files, retry-until-success, timeout, env injection, validation errors (missing name, unknown dep, duplicate id, cycle, self-dep, bad types), and YAML parsing (scalars, quotes, nesting, block scalars, comments, duplicate keys). Model B has zero automated tests despite claiming the tool was "verified end-to-end."

Model B does have some genuine strengths — SIGINT handling with `threading.Event` and interruptible backoff sleeps is a nice proactive addition, Kahn's algorithm for topological sort is textbook correct, the `src/` layout is better packaging practice, and `max_parallel` / `cwd` are useful features. But these nice-to-haves don't outweigh the fundamental issue that the code can't pass a basic PR review as-is (build artifacts) and has no automated proof that it works (no tests). The core implementations are otherwise comparable in quality — both handle concurrent execution, retry/timeout, dependency resolution, and validation well.

---

## 5. Next Step / Follow-Up Prompt

Since Model A (the winner) has a solid implementation with no critical corrections needed, we can naturally introduce the features we held back:

**Draft Turn 2 Prompt:**

> Hey so I've been testing this more and ran into a couple things. First, I was building a more complex workflow where I need certain steps to only run conditionally based on what happened upstream. Like if a build step succeeded, run deploy, but if it failed, run a cleanup step instead. Right now there's no way to express that — every step either runs or gets cancelled. I need a `condition` field on steps that can check an upstream dependency's status (like `succeeded` or `failed`) or maybe check if a step's stdout contains a specific string. If the condition evaluates to false, the step should be marked `skipped`. And be careful with the downstream propagation — if a downstream step has multiple dependencies and at least one non-skipped dep succeeded, it should still run. Only skip it if ALL its deps are skipped.
>
> Second thing — I hit Ctrl+C while a big workflow was running and it just crashed hard without cleaning up. The child processes kept running in the background. This needs proper SIGINT handling: catch the interrupt, kill all running child processes (send SIGTERM to their process groups, then SIGKILL if they don't die), mark running and pending steps as `cancelled`, and still produce a valid JSON report so I can see what happened before the interrupt. Exit code should be 130 for interrupted runs.
