# Task-1: Event-Driven Workflow Orchestration Engine

**Task ID:** Task-01  
**Type:** Greenfield (Brand New Feature)  
**Language/Stack:** Python 3.11+ (CLI application, no web framework)

---

## Core Request (Turn 1)

### Summary

Build a command-line workflow orchestration engine called `flowctl`. Users define multi-step automation workflows in YAML files, and the engine handles parsing, validation, dependency resolution, execution, and reporting. Think of it as a lightweight, local-first alternative to tools like Airflow or GitHub Actions — designed for running on a single machine with no external infrastructure dependencies.

### Detailed Requirements

**Workflow Definition (YAML-based):**

Users write YAML files that describe a workflow composed of named steps. Each workflow file must include:
- A workflow-level `name` and optional `description`.
- A `steps` section containing one or more named steps.
- Each step defines:
  - A `command`: the shell command to execute.
  - An optional `depends_on`: a list of step names that must complete successfully before this step can begin.
  - An optional `retry` block: `max_attempts` (integer, default 1) and `backoff_seconds` (number, default 0 — the wait between retries).
  - An optional `timeout_seconds`: maximum wall-clock time allowed for a single execution attempt of the step. If the step exceeds this, it must be killed and marked as timed out.
  - An optional `condition`: a rule that determines whether the step should execute based on the status or output of one of its upstream dependencies. If the condition evaluates to false, the step (and its entire downstream subgraph) is marked as "skipped."
  - An optional `env`: a map of environment variables to inject into the step's execution environment (merged with the parent process's environment).

**Workflow Validation:**

Before execution, the engine must validate:
- The YAML is well-formed and conforms to the expected schema (correct keys, correct types).
- All `depends_on` references point to step names that actually exist in the workflow.
- The dependency graph contains no cycles (direct or indirect).
- Timeout and retry values are non-negative.
- Step names are unique and contain only alphanumeric characters, hyphens, and underscores.

Validation errors must be reported clearly with the specific step name and reason for each violation.

**Execution Semantics:**

- Steps with no dependencies (root steps) begin execution immediately.
- Steps with satisfied dependencies begin as soon as all their dependencies have completed successfully.
- Steps whose dependencies are independent of each other must execute concurrently, not sequentially. The engine must take advantage of parallelism where the dependency graph allows it.
- If a step fails after exhausting all retry attempts, all downstream steps that depend on it (directly or transitively) must be marked as "cancelled" and not executed.
- If a step times out on a given attempt, it counts as a failed attempt for retry purposes. If all attempts are exhausted via timeouts, the step is marked as "timed_out."
- Conditional steps: if a step's condition evaluates to false, the step is marked as "skipped." All downstream steps that depend exclusively on skipped steps are also marked as "skipped." However, if a downstream step has multiple dependencies and at least one non-skipped dependency succeeded, the step should still execute (the skipped dependency is treated as satisfied).
- The engine must capture `stdout` and `stderr` for every execution attempt of every step.

**Execution Report:**

After a workflow run completes (whether all steps succeed, some fail, or some are skipped), the engine must produce a structured JSON execution report containing:
- Workflow name, start time, end time, and overall status (`succeeded`, `failed`, `partial`).
- For each step: name, status (`succeeded`, `failed`, `timed_out`, `cancelled`, `skipped`), number of attempts, duration per attempt, captured stdout/stderr per attempt, and the final exit code.
- A summary section: total steps, succeeded count, failed count, skipped count, cancelled count, total wall-clock duration.

**CLI Interface:**

The tool must expose the following subcommands:
- `flowctl validate <workflow.yaml>` — Validate a workflow file and report any errors. Exit code 0 on success, 1 on validation failure.
- `flowctl run <workflow.yaml> [--report=<path>]` — Execute the workflow. Print real-time status updates to stderr as steps start, complete, fail, or are skipped. Write the JSON execution report to the specified path (default: `./flowctl-report.json`).
- `flowctl report <report.json>` — Pretty-print a previously generated execution report in a human-readable table format showing step names, statuses, durations, and attempt counts.
- `flowctl version` — Print the tool version.

**Error Handling & UX:**
- All user-facing error messages must be clear, actionable, and reference the specific step/field causing the issue.
- The tool must handle interrupts (Ctrl+C / SIGINT) gracefully: kill all running step processes, mark them as "cancelled," and still produce a partial execution report.
- Non-zero exit codes: 0 = all steps succeeded, 1 = validation error, 2 = one or more steps failed, 130 = interrupted.

**Project Structure:**
- The project must use a `src/flowctl/` package layout.
- Include a `pyproject.toml` with proper metadata, entry point configuration so `flowctl` is an installable CLI command, and dependency declarations.
- No external dependencies beyond the Python standard library (the tool must work with just Python 3.11+).

---

## Expected PR Review Feedback (Turns 2 & 3)

### Turn 2 — Anticipated Flaws and Prescriptive Corrections

**1. Cycle Detection is Likely Shallow:**  
The model will likely implement a basic visited-set DFS that catches direct A→B→A cycles but misses more subtle cases like A→B→C→A through longer chains, or fails to detect multiple disjoint cycles in the same graph. Demand a proper topological sort using Kahn's algorithm that also reports *which* steps form the cycle, not just "cycle detected."

**2. Concurrent Execution Has Race Conditions:**  
The model will probably use `threading` or `asyncio` but manage shared execution state (step statuses, the report being built) without proper synchronization. Expect status updates from concurrent steps to interleave incorrectly or, worse, corrupt the execution report dict. Demand explicit synchronization for the shared execution state — if using threads, require a lock around state mutations; if using asyncio, require single-threaded event loop discipline.

**3. Retry + Timeout Interaction is Wrong:**  
The model will likely implement retry and timeout as independent concerns. Expect the timeout to not reset between retry attempts, or the retry counter to not increment on timeouts. Demand that each retry attempt gets its own fresh timeout window, and that timeouts explicitly count as failed attempts toward the retry limit.

**4. Conditional + Cancellation Propagation is Oversimplified:**  
Expect the model to implement conditions as a simple boolean gate on the immediate step only, not correctly propagating skip/cancel status through the transitive downstream subgraph. The nuanced case — a step with multiple dependencies where one is skipped but another succeeds — will almost certainly be handled incorrectly. Demand explicit unit-testable logic for this cascading resolution.

**5. SIGINT Handling is Afterthought or Missing:**  
The model probably won't handle SIGINT at all or will handle it by just catching KeyboardInterrupt at the top level without actually killing child processes. Demand proper signal handling that sends SIGTERM to all running subprocess PIDs, waits briefly for them to exit, then produces the partial report.

**6. Monolithic Executor:**  
The execution logic will likely be a single large function that handles scheduling, subprocess management, retry loops, condition evaluation, and state tracking all intertwined. Demand that the executor be decomposed: scheduling logic (what's ready to run) should be separate from step running (subprocess management + retries), which should be separate from state management (tracking statuses and building the report).

### Turn 3 — Tests, Linting & Polish

- Demand unit tests for: cycle detection (simple cycle, long chain cycle, multiple disjoint cycles, self-referencing step), topological ordering with diamond dependencies, conditional skip propagation (single-parent skip, multi-parent with mixed statuses), retry exhaustion, timeout behavior, and SIGINT handling.
- Demand an integration test that runs an actual multi-step workflow YAML with real shell commands and asserts on the produced JSON execution report.
- Demand that all CLI subcommands have at least one end-to-end test verifying correct exit codes and output.
- Fix any remaining issues from Turn 2 that were not fully addressed.
- Ensure consistent error message format across all validation errors.

---

## Why It Fits the Constraints

**~500-600 lines of core code:** The core modules — YAML parser with schema validation, DAG construction and cycle detection, concurrent executor with retry/timeout/conditions, report generation, and CLI wiring — each require 60-120 lines of non-trivial logic. Combined with data models and the conditional propagation logic, this comfortably lands in the 500-600 line range for core code alone.

**Natural difficulty:** DAG-based workflow execution is a well-understood but edge-case-rich domain. The interaction between concurrency, retries, timeouts, conditional branching, and cancellation propagation creates a combinatorial space where getting each feature working in isolation is straightforward, but getting them to interact correctly is genuinely hard. This is the kind of task a senior SWE would assign as a multi-week project.

**Guaranteed major issues:** The concurrent execution + shared state management and the conditional/cancellation propagation through the dependency graph are two areas where AI models consistently produce code that works for the happy path but breaks on edge cases. The SIGINT handling requirement adds another dimension the model will likely handle poorly. At least one of these will constitute a major issue.

---

## Potential Files Modified/Created

*(Excluding test files)*

1. `pyproject.toml` — Project metadata, CLI entry point, dependencies.
2. `src/flowctl/__init__.py` — Package init with version.
3. `src/flowctl/cli.py` — CLI argument parsing, subcommand dispatch, exit code handling.
4. `src/flowctl/models.py` — Data classes for Workflow, Step, ExecutionState, StepResult, ExecutionReport.
5. `src/flowctl/parser.py` — YAML loading, schema validation, Workflow object construction.
6. `src/flowctl/dag.py` — DAG construction from step dependencies, cycle detection, topological sort, downstream subgraph resolution.
7. `src/flowctl/executor.py` — Concurrent step execution, subprocess management, retry loop, timeout handling, condition evaluation, SIGINT handling.
8. `src/flowctl/reporter.py` — JSON report generation, human-readable table formatting for the `report` subcommand.

---

## PR Overview (Reference Implementation)

### What was built

A complete CLI workflow orchestration engine (`flowctl`) that parses YAML workflow definitions, validates them, resolves dependency DAGs, executes steps concurrently with retry/timeout support, and produces JSON execution reports.

### Architecture

- **models.py** — Enums (`StepStatus`, `WorkflowStatus`) and dataclasses (`StepDefinition`, `WorkflowDefinition`, `StepResult`, `ExecutionReport`, etc.). `Condition.evaluate()` checks upstream step status or output content.
- **parser.py** — YAML parsing with comprehensive validation: schema checks, dependency reference validation, step name regex, non-negative numeric constraints, condition consistency. Returns `WorkflowDefinition` or raises `ValidationError` with a list of specific error messages.
- **dag.py** — `build_graph()` produces adjacency + in-degree maps. `topological_sort()` via Kahn's algorithm with `CycleError` reporting cycle members. `get_ready_steps()` finds steps whose deps are all resolved with at least one succeeded/skipped dep. `get_steps_to_cancel()` uses fixed-point iteration to cascade cancellations through transitive deps. `resolve_skips()` propagates skips through the topology.
- **executor.py** — `ExecutionState` is a thread-safe container (lock-protected sets for completed/failed/running/cancelled/skipped). `_run_step_attempt()` runs subprocesses with timeout via process groups (`os.killpg`). `_run_step()` handles retry loops with backoff. `execute_workflow()` installs SIGINT handler, supports dry-run mode. `_execute_loop()` is the main scheduling loop: each iteration checks for cancellations, finds ready steps, launches threads, and detects completion or deadlock.
- **reporter.py** — JSON serialization/deserialization of execution reports, plus a human-readable table formatter.
- **cli.py** — Four subcommands: `validate`, `run`, `report`, `version`. Exit codes: 0 (success), 1 (validation error), 2 (workflow failure), 130 (interrupted).

### Key design decisions

1. **Thread-per-step concurrency** — Simple, no external deps. Each step runs in its own thread; a shared `ExecutionState` with a lock coordinates state changes.
2. **Fixed-point cancellation** — When a step fails, `get_steps_to_cancel()` iterates until stable so cancellations cascade through arbitrarily deep dependency chains in a single scheduling loop iteration.
3. **Process group killing** — Timed-out steps are killed via `os.killpg(SIGKILL)` to ensure child processes are also terminated.
4. **Condition-based skipping** — Steps can specify conditions on upstream status or output content. Skip propagation follows the same "at least one live dep" rule as normal execution.

### Edge cases handled

- Diamond dependencies (A→B, A→C, B→D, C→D): if B fails, D waits for C before deciding whether to cancel or proceed.
- Cascading cancellation: A→B→C, A fails — both B and C are cancelled in the same loop iteration.
- Partial success: workflow reports `PARTIAL` status when some steps succeed and others fail/cancel.
- SIGINT during execution: kills all running processes, marks running steps as cancelled, produces a report.
- Retry with backoff: steps can retry N times with configurable delay between attempts.
- Environment variable injection: step-level `env` merged with parent process environment.

### Test coverage

88 tests across 6 test files:
- **test_parser.py** (16): valid/invalid YAML, schema violations, dependency validation, condition checks
- **test_dag.py** (29): graph building, topological sort, cycle detection, ready step computation, cancellation cascading, skip propagation
- **test_executor.py** (16): step execution, retry, timeout, failure cascading, diamond patterns, conditions, dry-run, SIGINT
- **test_reporter.py** (4): JSON roundtrip, structure validation, human-readable formatting
- **test_models.py** (11): enum values, condition evaluation, dataclass properties, report summaries
- **test_integration.py** (12): end-to-end CLI invocations via subprocess (validate, run, report subcommands)

---

## Copilot's Analysis & Drafted Turn 1 Prompt

### Scope Strategy

The full task has three feature clusters that can naturally split across turns:

1. **Turn 1 — Core workflow engine:** YAML parsing, validation, DAG resolution, concurrent execution with retry/timeout, failure cascading, JSON report, CLI subcommands. This is a complete, usable tool on its own.
2. **Turn 2 — "I noticed you missed…" follow-up:** Conditional steps (condition field gating execution based on upstream status/output, skip propagation through the downstream subgraph) + SIGINT handling (graceful interrupt, kill child processes, partial report, exit 130). Both are natural discoveries after you actually use the tool.
3. **Turn 3 — PR review / tests / fixes:** Demand tests for edge cases (cycle detection variants, diamond deps, cascading cancellation, conditional skip propagation, retry+timeout interaction, SIGINT). Fix bugs from Turn 2. Race conditions in concurrent state if present.

**What's deliberately held back from Turn 1:**
- `condition` field and all skip propagation logic — this is the most complex DAG interaction and makes a great "I just realized this is missing" moment
- SIGINT handling — classic "I tested Ctrl+C and it just crashed" feedback
- Both fit naturally as Turn 2 discoveries, not scope creep

### Turn 1 Major Issue Opportunities (what models will likely get wrong)

Even without conditions and SIGINT, there are several areas where models will naturally stumble:
- **Cycle detection** — likely uses a visited-set DFS instead of proper Kahn's algorithm, won't report which steps form the cycle
- **Concurrent execution race conditions** — shared state (step statuses, report dict) without proper synchronization
- **Retry + timeout interaction** — timeout won't reset between retry attempts, or timeouts won't count as failed attempts
- **Cancellation propagation** — oversimplified for diamond dependencies (A→B, A→C, B→D, C→D: if B fails, does D wait for C?)
- **Monolithic executor** — scheduling, subprocess management, retry loops, state tracking all jammed into one function

### Drafted Turn 1 Prompt

> I want to build a CLI workflow orchestration tool called `flowctl` — kind of a lightweight, local-first alternative to Airflow or GitHub Actions for single-machine automation. Users define multi-step workflows in YAML files, and the engine handles parsing, dependency resolution, execution, and reporting.
> 
> **Workflow YAML format:**
> YAML files describe a workflow with a `name`, optional `description`, and a `steps` section. Each step has:
> - `command` — shell command to run
> - `depends_on` — optional list of step names that must complete successfully before this step starts
> - `retry` — optional block with `max_attempts` (default 1) and `backoff_seconds` (default 0, wait between retries)
> - `timeout_seconds` — optional max wall-clock time for a single execution attempt. Kill and mark as timed out if exceeded.
> - `env` — optional map of environment variables to inject into the step's execution environment
> 
> **Validation (before execution):**
> - YAML is well-formed with correct keys and types
> - All `depends_on` references point to steps that actually exist
> - No cycles in the dependency graph (direct or indirect)
> - Timeout and retry values are non-negative
> - Step names are unique, alphanumeric + hyphens + underscores only
> - Errors should clearly reference the specific step name and what's wrong
> 
> **Execution:**
> - Root steps (no deps) begin immediately
> - Independent steps run concurrently
> - If a step fails after exhausting all retries, cancel all downstream dependents (direct and transitive)
> - Timeout counts as a failed attempt for retry purposes — each retry attempt gets its own fresh timeout window
> - Capture stdout and stderr for every attempt of every step
> 
> **Execution Report (JSON):**
> After a run completes, produce a JSON report with: workflow name, start/end time, overall status (`succeeded`, `failed`, `partial`), and for each step: status, attempt count, duration per attempt, stdout/stderr per attempt, final exit code. Include a summary with total/succeeded/failed/timed_out/cancelled counts.
> 
> **CLI subcommands:**
> - `flowctl validate <workflow.yaml>` — validate, report errors. Exit 0 on success, 1 on failure.
> - `flowctl run <workflow.yaml> [--report=<path>]` — execute, print real-time status to stderr, write JSON report (default: `./flowctl-report.json`).
> - `flowctl report <report.json>` — pretty-print a previous report as a human-readable table.
> - `flowctl version` — print version.
> 
> **Exit codes:** 0 = all steps succeeded, 1 = validation error, 2 = one or more steps failed.
> 
> **Project setup:** Use `src/flowctl/` package layout with a `pyproject.toml` (proper metadata, entry point so `flowctl` is installable). No external dependencies — standard library only, Python 3.11+.

### My Opinions

- The prompt gives clear WHAT without specifying HOW. No mention of Kahn's algorithm, threading, locks, process groups, etc. Models have room to approach differently.
- It's long enough to be challenging (parsing + validation + concurrent execution + retry/timeout + report + CLI) but not so long it eats the whole context.
- The "partial" status for mixed success/failure workflows is a subtle requirement that models often miss.
- Holding back conditions + SIGINT for Turn 2 is the right call — both are meaty features that naturally emerge from testing.

### Turn 2 Draft Prompts (for later)

**Conditional steps discovery:** "I was testing more complex workflows and realized there's no way to conditionally skip steps based on what happened upstream. I need a `condition` field on steps that can check an upstream dependency's status (succeeded/failed/skipped) or whether its stdout contains a specific substring. If the condition evaluates to false, the step should be marked 'skipped' — and any downstream steps that depend exclusively on skipped steps should also skip. But if a downstream step has multiple deps and at least one non-skipped dep succeeded, it should still run."

**SIGINT discovery:** "I also ran a big workflow and hit Ctrl+C midway. The tool just crashed without cleaning up. It needs to handle SIGINT gracefully — kill all running child processes, mark running steps as cancelled, mark pending steps as cancelled, and still produce a partial execution report. Exit code should be 130 for interrupted runs."
