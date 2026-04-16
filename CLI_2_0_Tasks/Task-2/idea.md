# Task-1: Structured Log Sampling and Rate-Limiting Engine

## Task ID
Task-01

## Type
Substantial New Feature

## Core Request (Turn 1)

### Title
Implement a built-in structured log sampling and rate-limiting engine for pino

### Description

**Background:** In high-throughput production systems, logging every single event at `debug` or `trace` level (and sometimes even `info`) can cause significant I/O pressure, storage costs, and downstream pipeline saturation. Currently, pino has no built-in mechanism for sampling or rate-limiting log output. Users must implement ad-hoc wrappers or rely on external infrastructure to drop logs.

**Objective:** Build a first-class **sampling and rate-limiting** subsystem directly into pino's core that allows users to declaratively configure per-level (and optionally per-key) sampling rules at logger creation time.

### Acceptance Criteria

1. **New `sampling` option on `LoggerOptions`:**
   ```js
   const logger = pino({
     sampling: {
       // Per-level rules
       rules: [
         { level: 'trace', method: 'rate', rate: 100 },      // max 100 trace logs/sec
         { level: 'debug', method: 'probability', value: 0.1 }, // 10% of debug logs
         { level: 'info',  method: 'every', value: 5 },       // every 5th info log
       ],
       // Optional: per-key deduplication (suppress repeated identical messages within a window)
       dedupe: {
         enabled: true,
         key: 'msg',           // field to deduplicate on
         ttlMs: 5000,          // suppress duplicates within 5 seconds
         maxKeys: 10000        // bounded LRU cache size
       }
     }
   })
   ```

2. **Three sampling methods must be supported:**
   - **`probability`** — Each log at the configured level is emitted with the given probability (0.0–1.0). The decision must be made via a fast PRNG (e.g., `Math.random()` is acceptable but must be swappable).
   - **`rate`** — Token-bucket rate limiting: at most `rate` logs per second for the given level. Tokens refill smoothly (not burst). Logs that exceed the rate are silently dropped.
   - **`every`** — Deterministic downsampling: emit every Nth log at the configured level.

3. **Per-key deduplication (the `dedupe` sub-option):**
   - When enabled, before a log line is written, the engine computes a cache key from the specified field(s) (default: the `msg` field).
   - If the same key was seen within `ttlMs` milliseconds, the log is suppressed.
   - The cache must be bounded by `maxKeys` using an LRU eviction strategy.
   - Suppressed count should be tracked and, when a key expires from the cache, a single summary log should be emitted: `{ msg: "dedupe: suppressed N occurrences of '<key>'", level: <original_level> }`.

4. **Metadata injection:** When a log *is* emitted by the sampler, the log object must include a `_sampling` metadata field (configurable key name) indicating which rule matched and the current drop count:
   ```json
   { "level": 20, "msg": "cache hit", "_sampling": { "method": "probability", "value": 0.1, "dropped": 42 } }
   ```

5. **Child logger inheritance:** Child loggers created via `.child()` must inherit the parent's sampling configuration by default, but allow override via `child({}, { sampling: ... })`.

6. **Integration with `genLog`:** The sampling check must be injected into the hot path in `lib/tools.js`'s `genLog` function (or the `write` method in `lib/proto.js`) as early as possible to avoid unnecessary serialization work on dropped logs. The check must be **zero-cost** when no sampling rules are configured (i.e., the non-sampled code path must remain identical to today's path).

7. **TypeScript definitions:** Update `pino.d.ts` with full type definitions for the new `sampling` option, including the `SamplingRule`, `SamplingDedupe`, and `SamplingMeta` interfaces.

8. **Validation:** On logger construction, validate all sampling rules (e.g., probability must be 0–1, rate must be > 0, every must be ≥ 1). Throw clear errors for invalid configurations.

---

## Expected PR Review Feedback (Turns 2 & 3)

### Turn 2 — Anticipated Implementation Flaws

1. **Hot-path regression:** The model will likely add the sampling check inside `genLog` but will not properly short-circuit when `sampling` is `undefined`/`null`. Expect the check to add a measurable branch to every log call even when sampling is disabled. **Demand** a zero-cost fast path (e.g., generate a different `LOG` function variant at construction time via `genLog` when sampling is active vs. inactive).

2. **Token-bucket precision:** The initial rate-limiter will almost certainly use a naive `Date.now()` interval check instead of a proper token-bucket with fractional token accrual. This will cause bursty behavior (all tokens available at the start of each second window). **Demand** a smooth refill implementation.

3. **LRU cache implementation:** The dedupe LRU will likely be implemented as a plain `Map` with manual eviction, which is either O(n) on eviction or has no actual LRU ordering. **Demand** a proper bounded LRU using `Map` insertion-order semantics with `delete-then-set` for promoting entries.

4. **Missing edge cases:**
   - What happens when `sampling.rules` contains multiple rules for the same level? (Should be last-wins or throw.)
   - What happens for custom levels not listed in `rules`? (Should pass through unsampled.)
   - The dedupe summary log will likely not respect the logger's current serializers/formatters.
   - Child logger `.child()` override of sampling will likely not deep-merge correctly with parent rules.

5. **Monolithic function:** The entire sampling logic will likely be crammed into a single function in `tools.js`. **Demand** it be extracted into a dedicated `lib/sampling.js` module with clean separation of concerns (rule matching, token bucket, LRU cache, metadata injection).

### Turn 3 — Tests, Linting & Polish

1. **Unit tests required:**
   - Probability sampling: verify that over N iterations, approximately `value * N` logs are emitted (within statistical tolerance).
   - Rate limiting: verify burst behavior, token refill over time, and that logs beyond the rate are dropped.
   - Every-N: verify deterministic counting behavior, including counter reset on child loggers.
   - Dedupe: verify suppression, TTL expiry, LRU eviction at `maxKeys`, and summary log emission.
   - Metadata: verify `_sampling` field is present with correct values.
   - Child inheritance: verify rules are inherited and can be overridden.
   - Validation: verify errors on invalid configs.

2. **Integration tests:**
   - Combine sampling with redaction and verify redacted logs are still correctly sampled.
   - Combine sampling with multistream and verify each stream receives only sampled output.

---

## Drafted Turn 1 Prompt (for reference — adapt as needed)

In high-throughput production environments, we often need to throttle or downsample log output at specific levels to control I/O and storage costs. Right now there's no built-in way to do this in pino — users have to build ad-hoc wrappers.

I'd like to add a first-class `sampling` option to pino's logger options that lets you configure per-level sampling rules. Three sampling strategies should be supported at a minimum:

1. **Probability-based** — each log at the configured level is emitted with a given probability (0–1).
2. **Rate-limited** — at most N logs per second for a given level (token refill should be smooth, not bursty).
3. **Deterministic** — emit every Nth log for a given level.

Additionally, I want a **per-key deduplication** option: if the same message (or a configurable field) is seen within a TTL window, suppress duplicates. The cache needs to be bounded (LRU) and when a suppressed key expires, emit a summary log saying how many occurrences were suppressed.

When a sampled log IS emitted, it should include metadata indicating which rule matched and how many logs were dropped since the last emitted one.

Child loggers should inherit the parent's sampling config by default, but allow overriding or disabling it.

The sampling check needs to happen early enough in the logging path that dropped logs don't incur serialization cost. But critically — when no sampling rules are configured, the existing code path must remain completely untouched (zero overhead).

TypeScript definitions should be updated and all new configs should be validated at construction time with clear error messages.

---

## My Opinions & Analysis

**Why this task is strong:**
- Touches pino's performance-critical hot path, so the model can't just bolt something on carelessly — it needs to understand `genLog` and the level method generation pattern in `lib/levels.js`.
- The zero-cost requirement forces an architectural decision: generate different function variants when sampling is vs. isn't active, not just add an `if` check.
- Token-bucket with smooth refill, bounded LRU, and child-logger deep-merge are each individually non-trivial algorithms.
- Pino uses Symbols extensively and has a specific prototype-based architecture for child loggers. The model needs to figure out the right integration points.

**What I'll be watching for in model responses:**
- Do they create a dedicated `lib/sampling.js` or cram everything into `tools.js`?
- Do they actually implement smooth token-bucket refill, or do they do a naive "reset every second" approach?
- Do they use `Map` insertion-order semantics for proper O(1) LRU, or roll out something worse?
- Do they properly avoid adding branches in the genLog path when sampling is `null`?
- Do they handle child logger override + inheritance correctly through pino's symbol-based architecture?
- Do they validate configs at construction time, or silently accept bad input?
- Are TypeScript types comprehensive (SamplingRule, SamplingDedupe, SamplingMeta, etc.)?

**Estimated difficulty:** High. This feature requires understanding pino's internal architecture deeply before writing any code.

3. **Linting and style:** Ensure new code matches neostandard style (no semicolons, single quotes, 2-space indent). Remove any `console.log` debug statements.

4. **Error handling:** Ensure graceful behavior if `Math.random` or `Date.now` throws (defensive coding at system boundary).

---

## Why It Fits the Constraint

- **~500–600+ lines of core code:** The feature requires: a new `lib/sampling.js` module (~200 lines for rule engine, token bucket, LRU cache), modifications to `lib/tools.js` for `genLog` integration (~60 lines), modifications to `lib/proto.js` for `write`/`child` inheritance (~50 lines), modifications to `pino.js` for option normalization and validation (~60 lines), TypeScript definitions in `pino.d.ts` (~80 lines), and a new `lib/sampling-lru.js` or inlined LRU (~80 lines). Total comfortably exceeds 500 lines.
- **High difficulty:** Requires deep understanding of pino's hot path, Symbol-based internal state, and prototype chain. The token-bucket and LRU algorithms are easy to get subtly wrong. Balancing zero-cost when disabled with full functionality when enabled is a classic performance engineering challenge.
- **Naturally imperfect in one turn:** The interplay between sampling rules, child logger inheritance, dedupe TTL management, and the existing formatter/serializer/redaction pipeline creates many edge cases that a model will miss on the first pass.

---

## Potential Files Modified

1. **`lib/sampling.js`** *(new file)* — Core sampling engine: rule matching, token bucket, probability, every-N, LRU deduplication cache.
2. **`lib/tools.js`** — Modify `genLog()` to inject sampling gate; update `asJson()` to inject `_sampling` metadata.
3. **`lib/proto.js`** — Modify `write()` and `child()` to propagate and override sampling state.
4. **`pino.js`** — Add `sampling` to `defaultOptions`, normalize/validate config, pass to instance.
5. **`lib/symbols.js`** — Add new Symbols (`samplingSym`, `samplingRulesSym`, `samplingDedupeSym`).
6. **`pino.d.ts`** — Add TypeScript interfaces and augment `LoggerOptions`.

---

## PR Overview — Implementation Reference

### Summary

This PR adds a structured log sampling and rate-limiting engine to pino, enabling users to control log volume on a per-level basis through three sampling methods (probability, token-bucket rate limiting, every-N), with optional per-key LRU deduplication. When sampling is not configured, the hot path is completely untouched (zero cost).

### Files Changed

| File | Change Type | Lines | Description |
|------|------------|-------|-------------|
| `lib/sampling.js` | **New** | ~490 | Core module: `validateSamplingConfig`, `LruCache`, `TokenBucket`, `SamplingEngine`, `buildSamplingEngine`, `genSampledLog` |
| `lib/symbols.js` | Modified | +3 | Added `samplingSym` and `samplingMetaKeySym` Symbols |
| `lib/levels.js` | Modified | +15 | `levelMethods` and `setLevel` now accept sampling engine params; conditionally generate sampled log functions |
| `lib/proto.js` | Modified | +18 | `child()` supports `sampling` in options: `null`/`false` disables, object builds new engine; triggers `setLevel` rebuild |
| `pino.js` | Modified | +18 | Added `sampling` to `defaultOptions`; validation, engine building, and instance property assignment |
| `pino.d.ts` | Modified | +50 | Added `SamplingRule`, `SamplingDedupe`, `SamplingMeta`, `SamplingOptions` interfaces; `sampling` on `LoggerOptions` and `ChildLoggerOptions`; new symbols |
| `test/sampling.test.js` | **New** | ~905 | Comprehensive test suite: 71 tests across 9 describe blocks |

### Architecture Decisions

1. **`genSampledLog` vs modifying `genLog`**: Created a separate `genSampledLog()` function rather than adding conditionals inside the existing `genLog()` in `lib/tools.js`. This keeps the hot path for non-sampled loggers completely untouched — no extra branch, no Symbol lookups, no function calls. The sampled variant is only generated when a `SamplingEngine` exists on the instance.

2. **Symbol-based state**: Following pino's existing pattern, sampling state is stored via `samplingSym` and `samplingMetaKeySym` Symbols on each instance. This avoids polluting the public API surface and enables clean prototype-chain inheritance.

3. **`SAMPLED_LOG` reads `this[samplingSym]`**: The generated function reads the engine from `this` at call time (not closure time), which means child loggers that override or disable sampling automatically get the correct behavior without needing separate function closures per child.

4. **LRU Cache with eviction summaries**: The `LruCache.set()` method returns any evicted entry, allowing the engine to emit a "suppressed N occurrences" summary log for evicted dedupe entries. TTL sweeping is done via an `unref`'d `setInterval` so it doesn't keep processes alive.

5. **Child logger override**: Sampling is passed in the `options` (2nd argument) to `child()`, consistent with how `level`, `serializers`, `formatters`, `redact`, and `msgPrefix` are configured. After changing sampling, `setLevel` is called to regenerate all level methods.

### Sampling Methods

- **`probability`**: `Math.random() < value` (or custom `randomFn`). Value 0 = drop all, 1 = pass all.
- **`rate`**: Token bucket with smooth refill. Starts with a burst equal to `rate` tokens, refills at `rate` tokens/second.
- **`every`**: Deterministic counter. Emits every Nth log at the configured level.

### Metadata Injection

When a sampled log is emitted, a `_sampling` (configurable via `metaKey`) object is injected into the log's merge object:

```json
{
  "level": 20,
  "msg": "cache miss",
  "_sampling": {
    "method": "every",
    "value": 100,
    "dropped": 99
  }
}
```

### API Usage

```js
const pino = require('pino')

const logger = pino({
  sampling: {
    rules: [
      { level: 'debug', method: 'every', value: 100 },
      { level: 'info', method: 'probability', value: 0.1 },
      { level: 'warn', method: 'rate', rate: 5 }
    ],
    dedupe: {
      enabled: true,
      ttlMs: 5000,
      maxKeys: 10000
    },
    metaKey: '_sampling'
  }
})

// Child can override or disable
const child = logger.child({ reqId: '123' }, { sampling: null })
```

### Test Coverage (71 tests)

| Suite | Tests | Coverage |
|-------|-------|----------|
| Validation | 21 | All rule/config validation edge cases |
| LruCache | 8 | Set, get, promotion, eviction, clear |
| TokenBucket | 3 | Burst, refill, cap |
| SamplingEngine | 11 | probability/rate/every methods, metadata, drops, destroy |
| Deduplication | 5 | Suppression, TTL expiry, LRU eviction, sweep summaries |
| buildSamplingEngine | 4 | Null/empty/valid configs |
| Integration (pino) | 12 | End-to-end with streams, edge cases, metaKey |
| Child loggers | 4 | Inherit, override, disable (null/false) |
| Constructor validation | 3 | Invalid configs throw at construction |

### Regression Testing

All existing pino test suites pass with zero regressions:
- `basic.test.js`: 112/112 pass
- `levels.test.js`: 51/51 pass
- `custom-levels.test.js`: 18/18 pass
- `mixin.test.js`: 11/11 pass
- `formatters.test.js`: 10/10 pass
- `error.test.js`: 17/17 pass
- `metadata.test.js`: 4/4 pass
- `redact.test.js`: 56/56 pass

### Edge Cases Handled

- Probability 0 (drops everything) and 1 (passes everything) work correctly
- Levels without rules pass through unsampled with no metadata injection
- String-only logs, format-string logs, and `msgPrefix` all work with sampling
- `setLevel` correctly regenerates sampled/unsampled methods after level change
- Dedupe sweep interval is `unref`'d to avoid keeping processes alive
- `destroy()` cleans up all resources (intervals, maps, caches)
- Token bucket refill uses fractional accrual to avoid staircase artifacts
