# Task-2: Scoped Logger Context Manager with Async Local Storage Integration

## Task ID
Task-02

## Type
Substantial New Feature

## Core Request (Turn 1)

### Title
Implement automatic request-scoped logging context via AsyncLocalStorage with context lifecycle management

### Description

**Background:** In modern Node.js services, a single process handles many concurrent requests. Developers need each log line to carry request-scoped context (request ID, user ID, tenant ID, trace/span IDs) without manually threading a child logger through every function call. Node.js's `AsyncLocalStorage` API (stable since v16) solves this, but pino has no built-in integration. Users must write boilerplate middleware and manually manage `AsyncLocalStorage` stores.

**Objective:** Build a first-class `pino.context()` API that wraps `AsyncLocalStorage` and allows users to bind contextual data to the current async execution scope. All log calls within that scope automatically include the bound context fields — without creating child loggers and without any changes to calling code.

### Acceptance Criteria

1. **New `pino.context(logger)` factory:**
   ```js
   const pino = require('pino')
   const logger = pino()
   const ctx = pino.context(logger)

   // Returns an enhanced logger + context management API
   // ctx.logger — the context-aware logger instance
   // ctx.run(store, fn) — run fn within a new context scope
   // ctx.set(key, value) — add/update a field in the current scope
   // ctx.get(key) — read a field from the current scope
   // ctx.bind(fields) — returns a new run-scoped function that merges fields
   ```

2. **`ctx.run(store, fn)` method:**
   - Creates a new `AsyncLocalStorage` scope.
   - `store` is a plain object of key-value pairs that will be merged into every log line emitted within `fn` and its async descendants.
   - Must return whatever `fn` returns (including Promises — must be async-transparent).
   - Scopes nest: inner `run()` calls merge their store with the outer store (inner wins on conflict).

3. **`ctx.set(key, value)` / `ctx.get(key)`:**
   - `set` mutates the current scope's store. Throws if called outside a `run()` scope.
   - `get` reads from the current scope's store. Returns `undefined` if no active scope or key not found.

4. **Transparent injection into log output:**
   - The context fields must appear in the JSON output **after** the base bindings but **before** the logged object fields, so that explicitly logged fields can override context fields.
   - Implementation must hook into pino's existing `mixin` mechanism or into the `write` path in `lib/proto.js`. The context fields must be injected at serialization time, NOT by creating hidden child loggers.
   - When no context scope is active, the injection must be a no-op (zero overhead).

5. **`ctx.bind(fields)` helper:**
   - Returns a function `(fn) => (...args) => ctx.run(fields, () => fn(...args))`.
   - Useful as Express/Fastify middleware: `app.use(ctx.bind({ reqId: req.id }))`.

6. **Context-aware child loggers:**
   - `ctx.logger.child(bindings)` must return a child that also inherits context injection.
   - The child's own bindings take precedence over context fields.

7. **Cleanup and memory safety:**
   - Context stores must not leak. When `run()` completes (including after async operations), the store must be garbage-collectible.
   - Provide a `ctx.enterWith(store)` escape hatch (mirrors `AsyncLocalStorage.enterWith`) with a documentation warning about its footgun nature.

8. **TypeScript definitions:** Full types for `pino.context()`, `ContextManager`, `ctx.run`, `ctx.set`, `ctx.get`, `ctx.bind`, `ctx.enterWith`.

9. **Validation:** If `AsyncLocalStorage` is not available (very old Node.js versions or edge runtimes), `pino.context()` must throw a clear error message.

---

## Expected PR Review Feedback (Turns 2 & 3)

### Turn 2 — Anticipated Implementation Flaws

1. **Mixin integration conflict:** The model will likely implement context injection by setting `opts.mixin` on the logger, which will **overwrite** any user-supplied `mixin` function. **Demand** that the implementation composes with an existing `mixin` — the context fields should be merged before/after the user's mixin, respecting `mixinMergeStrategy`.

2. **Nested scope merging bug:** The initial implementation will probably replace the store on nested `run()` calls instead of merging outer + inner. **Demand** proper prototype-chain or `Object.assign`-based inheritance so outer fields are visible unless shadowed.

3. **`set()` mutation safety:** Calling `ctx.set()` will likely mutate the store object directly, which means parallel async branches sharing the same scope will see each other's mutations. **Demand** copy-on-write semantics for `set()` so each async continuation gets its own snapshot.

4. **Child logger context leak:** Child loggers created inside a `run()` scope will likely capture the context store at creation time rather than reading it dynamically at log time. **Demand** that child loggers always read the *current* ALS store, not a snapshot.

5. **Missing `enterWith` safety:** The model will likely implement `enterWith` without any warnings or safeguards. **Demand** at minimum a deprecation-style warning via `process.emitWarning` on first use.

6. **Performance regression:** The `write()` path will gain an `AsyncLocalStorage.getStore()` call on every log. The model may not benchmark this. **Demand** that when no context manager is attached, the code path remains identical to the baseline (no ALS lookup).

### Turn 3 — Tests, Linting & Polish

1. **Unit tests:**
   - Basic `run()` with fields appearing in log output.
   - Nested `run()` with field merging and override.
   - `set()` and `get()` inside and outside scopes.
   - `bind()` producing correct scoped functions.
   - Child logger interaction with context.
   - Composition with user-supplied `mixin`.
   - `enterWith` behavior and warning emission.
   - Error on unavailable `AsyncLocalStorage` (mock it away).

2. **Async tests:**
   - Verify context propagation across `setTimeout`, `setImmediate`, `Promise.then`, and `async/await`.
   - Verify isolation between concurrent `run()` scopes (simulate two parallel requests).

---

## Drafted Turn 1 Prompt

> In our production Node.js services, we constantly need request-scoped context in our logs — things like request IDs, user IDs, tenant IDs, trace/span IDs. Right now the only way to do this with pino is to create child loggers on every incoming request and manually pass them through every function call. This is messy and doesn't scale well.
>
> Node.js has `AsyncLocalStorage` (stable since v16) which can automatically propagate data through async call chains. I want a first-class `pino.context()` API that uses AsyncLocalStorage under the hood, so I can bind fields to the current async scope and have them automatically appear in every log line — without needing to create child loggers or change any calling code.
>
> It should support nested scopes (inner fields override outer), reading/writing the current scope's fields, a helper for Express/Fastify-style middleware usage, and an escape hatch similar to `AsyncLocalStorage.enterWith` for advanced use. TypeScript types and tests for the new feature are expected. If `AsyncLocalStorage` isn't available, it should throw a clear error.
>
> Performance matters — when no context scope is active, the logging path should stay as fast as it is today.

---

## My Opinions & Analysis

**Why this task is interesting for evaluation:**
- This is a substantial new feature touching core pino internals. The model has to understand pino's `write` → `asJson` pipeline and specifically how the `mixin` mechanism works in `lib/proto.js` (the `write()` function reads `this[mixinSym]` and calls it to merge extra fields). The solution needs to *compose* with any existing user-supplied mixin, not overwrite it — this is a very common pitfall.
- AsyncLocalStorage integration has subtle correctness concerns: nested scope merging, copy-on-write for `set()` mutations across parallel async branches, child loggers reading the *current* ALS store dynamically vs. snapshotting at creation time.
- The prompt deliberately avoids prescribing the exact API shape (e.g., I don't specify `ctx.run`, `ctx.set`, `ctx.get` etc. by name), so Model A and B can differ in their approach. One might create a wrapper class, the other might patch the logger directly — that divergence is what makes the comparison interesting.

**Likely model failure points (for Turn 2/3 feedback):**
1. **Mixin overwrite** — Model likely just sets `opts.mixin` or `logger[mixinSym]`, clobbering any existing user mixin. This is a major correctness issue.
2. **Nested scope replacement** — Model will probably replace the store instead of merging outer + inner on nested `run()` calls.
3. **`set()` mutating the shared store** — Calling `set()` will probably mutate the store object in-place, leaking changes to parallel async branches.
4. **Child logger snapshotting** — Child loggers created inside a scope will probably capture the context store at creation time instead of dynamically reading the current ALS store at log time.
5. **No `enterWith` warning** — Model will probably implement `enterWith` without `process.emitWarning` on first use.
6. **Performance when no context is active** — The `write()` path might gain an unnecessary `AsyncLocalStorage.getStore()` call on every log even when context was never set up.

**Files that should be touched by a correct solution:**
- New `lib/context.js` (the `ContextManager` class)
- `lib/symbols.js` (add `contextSym`)
- `pino.js` (export `pino.context`)
- `pino.d.ts` (TypeScript interface for `ContextManager`, `pino.context()`)
- New `test/context.test.js`

3. **Integration tests:**
   - Combine context with redaction: ensure redacted paths work on context fields.
   - Combine context with multistream: ensure context fields appear in all streams.
   - Combine context with transports: ensure context fields survive worker thread serialization.

4. **Memory leak test:** Run a loop of 10,000 `ctx.run()` calls and verify heap does not grow unboundedly (use `process.memoryUsage()`).

---

## Why It Fits the Constraint

- **~500–600+ lines of core code:** New `lib/context.js` module (~250 lines for `ContextManager` class, `run`, `set`, `get`, `bind`, `enterWith`, nested scope merging, copy-on-write). Modifications to `lib/proto.js` `write()` method (~40 lines for ALS store lookup and injection). Modifications to `lib/proto.js` `child()` (~30 lines for context-aware child creation). Modifications to `pino.js` for `pino.context()` static method and option threading (~40 lines). New symbols in `lib/symbols.js` (~10 lines). TypeScript definitions in `pino.d.ts` (~100 lines). Total ~550+ lines.
- **High difficulty:** Requires deep understanding of `AsyncLocalStorage` semantics (especially `enterWith` vs `run`), pino's mixin/write pipeline, and the performance implications of ALS on the hot path. Copy-on-write semantics for `set()` and nested scope merging are subtly tricky.
- **Naturally imperfect in one turn:** The interplay between ALS context, mixins, child loggers, redaction, and the serialization pipeline creates many integration edge cases. Concurrent request isolation is hard to get right without careful ALS understanding.

---

## Potential Files Modified

1. **`lib/context.js`** *(new file)* — `ContextManager` class with `run()`, `set()`, `get()`, `bind()`, `enterWith()`, nested scope support, copy-on-write store.
2. **`lib/proto.js`** — Modify `write()` to look up ALS store and inject context fields; modify `child()` for context-aware inheritance.
3. **`pino.js`** — Add `pino.context()` static method export; wire context manager into logger instance.
4. **`lib/symbols.js`** — Add `contextSym`, `contextManagerSym` symbols.
5. **`lib/tools.js`** — Modify `asChindings()` or `asJson()` if context fields are injected at serialization time.
6. **`pino.d.ts`** — Add `ContextManager` interface, `pino.context()` overload, `RunStore` type, augment `LoggerOptions`.

---

## PR Overview

### Summary

Implements `pino.context()` — a first-class AsyncLocalStorage integration that lets every log call within an async scope automatically include request-scoped fields (request ID, tenant, trace ID, etc.) without threading child loggers or modifying call sites.

### Design Decisions

**Mixin composition over `write()` modification:** Rather than modifying `proto.js`'s `write()` function, the context manager attaches itself via pino's existing `mixin` mechanism. The `_attachMixin()` method replaces the logger's `[mixinSym]` with a composed function that reads the ALS store and merges it with any pre-existing user mixin result. This means:
- Zero changes to `proto.js` or `tools.js`
- Child loggers inherit context dynamically through the prototype chain
- User-supplied `mixin` functions compose naturally (context fields override mixin fields; explicit log fields override both)

**Copy-on-write `set()`:** Uses `ALS.enterWith()` with a cloned store (`Object.assign({}, store, { [key]: value })`) so mutations within a scope don't leak to parent or sibling scopes.

**Nested scope merging:** `run()` merges outer + inner stores via `Object.assign({}, outerStore, innerStore)`, so inner fields shadow outer ones while preserving inherited context.

### Files Changed

| File | Status | Description |
|------|--------|-------------|
| `lib/context.js` | **New** (~210 lines) | `ContextManager` class wrapping `AsyncLocalStorage`. Exports `createContext` factory. Methods: `run()`, `set()`, `get()`, `bind()`, `enterWith()`, `getStore()`. |
| `lib/symbols.js` | Modified | Added `contextSym = Symbol('pino.context')` |
| `pino.js` | Modified | Added `module.exports.context = require('./lib/context').createContext` |
| `pino.d.ts` | Modified | Added `ContextManager` interface, `ContextOptions`, `context()` export, `contextSym` in symbols |
| `test/context.test.js` | **New** (~730 lines) | 44 tests across 13 suites |

### Test Coverage (44 tests, 13 suites)

| Suite | Tests | Coverage |
|-------|-------|----------|
| Validation | 5 | Factory, error cases, instance type |
| `run()` basics | 6 | Field injection, return values, async transparency, input validation |
| Nested `run()` | 3 | Merge semantics, scope restoration, deep nesting (3 levels) |
| `set()` / `get()` | 8 | COW isolation, outside-scope errors, `getStore()` |
| `bind()` | 2 | Scoped function production, composition with `run()` |
| `enterWith()` | 4 | Scope setting, warning emission, validation, merge with outer |
| Child loggers | 3 | Dynamic inheritance, binding override, explicit field override |
| User mixin | 3 | Composition, conflict resolution (context > mixin), three-layer priority |
| Async propagation | 5 | `await`, `Promise.then`, `setTimeout`, `setImmediate`, concurrent isolation |
| Redaction | 1 | Redacted paths work on context fields |
| Multistream | 1 | Context fields appear in all streams |
| Zero-cost path | 2 | No-scope logging, all log levels with context |
| Memory safety | 1 | 10K iterations, no unbounded heap growth |

### Regression Testing

309 existing tests across 9 core suites pass with 0 failures:
- `basic`, `levels`, `custom-levels`, `mixin`, `error`, `formatters`, `redact`, `metadata`, `multistream`

### Lint

All new and modified files pass `neostandard` (ESLint 10.2.0) with zero warnings or errors.
