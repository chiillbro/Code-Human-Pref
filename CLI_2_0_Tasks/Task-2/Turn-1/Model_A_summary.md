● Everything passes. Here's a summary of the implementation:                  
                                                                              
  ---            
  Summary
         
  Configuration shape

  pino({
    sampling: {
      debug: { rate: 0.05 },               // probability
      info:  { every: 10 },                // emit every Nth
      warn:  { limit: 100, window: 1000 }, // rate limit per fixed window
      error: { dedupe: { window: 5000 } }, // collapse duplicate storms + summary
      trace: (args) => args[0]?.important, // custom predicate shorthand
      '*':   0.5                           // wildcard fallback, number = rate
  shorthand
    }
  })

  Files changed

  ┌──────────────────┬────────────────────────────────────────────────────────┐
  │       File       │                         Change                         │
  ├──────────────────┼────────────────────────────────────────────────────────┤
  │ lib/sampling.js  │ Rule validation, per-level sampler generation          │
  │ lib/sampling.js (new) │ (every/rate/limit/test/dedupe), rule merging for  │
  │                       │ children                                          │
  ├───────────────────────┼───────────────────────────────────────────────────┤
  │ lib/symbols.js        │ Added samplingSym                                 │
  ├───────────────────────┼───────────────────────────────────────────────────┤
  │                       │ genLog(level, hook, sampler) — returns the        │
  │ lib/tools.js          │ original LOG function unchanged when no sampler;  │
  │                       │ otherwise returns a wrapper that short-circuits   │
  │                       │ before format()/write()                           │
  ├───────────────────────┼───────────────────────────────────────────────────┤
  │                       │ setLevel() looks up sampler per level from        │
  │ lib/levels.js         │ this[samplingSym] and passes to                   │
  │                       │ genLog/levelMethods                               │
  ├───────────────────────┼───────────────────────────────────────────────────┤
  │                       │ child() handles options.sampling — shallow-merges │
  │ lib/proto.js          │  rules over parent's, rebuilds samplers, re-runs  │
  │                       │ setLevel; null/false disables entirely            │
  ├───────────────────────┼───────────────────────────────────────────────────┤
  │ pino.js               │ Parses sampling opt, builds state, stores under   │
  │                       │ samplingSym                                       │
  ├───────────────────────┼───────────────────────────────────────────────────┤
  │                       │ SamplingRule, SamplingDedupeOptions,              │
  │ pino.d.ts             │ SamplingOptions; added to LoggerOptions +         │
  │                       │ ChildLoggerOptions                                │
  ├───────────────────────┼───────────────────────────────────────────────────┤
  │ docs/api.md           │ Full option documentation for root + child        │
  ├───────────────────────┼───────────────────────────────────────────────────┤
  │ test/sampling.test.js │ 30 tests covering all strategies, composition,    │
  │  (new)                │ child inheritance/override, validation, and       │
  │                       │ hot-path preservation                             │
  └───────────────────────┴───────────────────────────────────────────────────┘

  Key guarantees met

  - Zero overhead when unconfigured — genLog returns the exact same LOG function
  as before; verified by a test asserting logger.info.name === 'LOG'. The only
  added cost in setLevel() is one this[samplingSym] lookup (undefined/null) at
  setup time, never per-log-call.
  - Early drop — sampling check runs before format(), mixin, serializers, asJson,
  and the logMethod hook. Verified by tests that assert serializers/hooks are
  never invoked for dropped logs.
  - Dedupe — first occurrence emits immediately; duplicates within window ms are
  counted; on window expiry a summary line is written directly via this[writeSym]
  (bypassing sampling so the summary itself can't be dropped) with { sampling: {
  suppressed: N, key } }. Timers are unref()'d so they don't hold the process
  open.
  - Child inheritance — children share parent sampler state via the prototype
  chain by default (so a root rate-limit budget applies across children). When a
  child passes options.sampling, rules are shallow-merged over the parent's and
  the child gets fresh independent state; sampling: null/false on a child disables
   all sampling; { level: false } disables one inherited level.

  Verification

  - node --test test/sampling.test.js → 30/30 pass
  - Core regression suite (basic, levels, custom-levels, hooks, mixin,
  serializers, formatters, metadata, error, redact, is-level-enabled,
  mixin-merge-strategy) → all pass except one pre-existing formatter with
  transport failure caused by the WSL↔Windows UNC-path environment (worker-thread
  URL resolution), unrelated to this change
  - eslint . → clean
  - tsc --noEmit → clean