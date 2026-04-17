 All 25 context tests pass, lint is clean, and TypeScript
  compiles.                                             
                                       
  Summary

  I implemented a first-class, request-scoped context API for
  pino, backed by AsyncLocalStorage.

  New public API (on the pino namespace)

  - pino.context(fields, fn, ...args) — opens a new scope whose
  fields are auto-merged into every log line emitted (even across
   await / setImmediate / timers) until fn returns. Nested scopes
   inherit from the parent; inner fields override outer.
  - pino.getContext() — returns a shallow copy of the current
  scope's fields (or undefined).
  - pino.setContext(fields) — merges fields into the current
  scope (does not leak to parent). Throws outside an active
  scope.
  - pino.enterContextWith(fields) — escape hatch mirroring
  AsyncLocalStorage.enterWith for integrations that can't wrap a
  callback.
  - pino.contextMiddleware(fieldsOrFn?) — returns a
  connect/Express/Fastify-style (req, res, next) middleware that
  opens a scope for the request lifetime.

  Files changed

  - lib/context.js (new): lazy ALS init; clear error if
  async_hooks / AsyncLocalStorage isn't available; exports
  getContextStore() for the hot path.
  - lib/proto.js: write() now merges ALS fields before mixin so
  precedence is explicit log obj > context > mixin > chindings.
  - pino.js: wires the new static methods onto the exported
  namespace.
  - pino.d.ts: adds context, getContext, setContext,
  enterContextWith, contextMiddleware plus ContextFields,
  ContextFieldsResolver, ContextMiddleware types.
  - test/context.test.js (new): 25 tests covering fast-path
  no-scope behavior, async propagation, nested scope precedence,
  explicit override, mixin interaction, child loggers, validation
   errors, middleware paths, and a simulated
  missing-AsyncLocalStorage environment.
  - test/types/pino-top-export.tst.ts: type assertions for the
  new API.
  - docs/api.md: new "Statics" entries with examples.

  Performance

  getContextStore() is just als === null ? undefined :
  als.getStore(). When no scope has ever been opened, it returns
  via a single null check — this is the only addition to the hot
  path. A quick local micro-benchmark shows no measurable
  overhead versus the baseline, and the logging path is unchanged
   when ALS has never been initialized.

  Notes

  - The _resetForTests hook on lib/context.js is intentionally
  prefixed with _ and not part of the public API; it's used by
  the "AsyncLocalStorage unavailable" test, which simulates the
  missing-module case via Module._load and reloads lib/context.js
   in isolation.
  - I verified existing test files still pass (basic, mixin,
  mixin-merge-strategy, error, hooks, custom-levels, esm). The
  single pre-existing formatter with transport failure in
  test/formatters.test.js reproduces on main without my changes,
  so it's unrelated.
  - Lint is clean on all modified/new files; tsc -p tsconfig.json
   and tstyche test/types/pino-top-export.tst.ts both pass.