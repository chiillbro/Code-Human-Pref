 All green — 44/44 sampling tests, 256/256 core regressions,    
  lint clean.                                           
                                       
  Summary

  1. Timer/LRU race test

  Added dedupe summary emits exactly once when eviction races
  with timer (test/sampling.test.js). Sets maxKeys: 1, window:
  30, logs a/a/b (the third insert evicts a and synchronously
  flushes its summary), then waits 60ms past the original timer.
  Verifies exactly one summary for a with suppressed: 1, no
  summary for b. This exercises both guards: clearTimeout on the
  evicted entry and the seen.get(key) === state check in the
  timer callback (belt-and-braces).

  2. TypeScript JSDoc for token bucket

  Updated SamplingRule.limit and SamplingRule.window in
  pino.d.ts:
  - limit is now documented as bucket capacity (back-to-back
  allowance when full)                                      
  - window is now documented as full-refill time with explicit
  mention that the refill rate is limit / window tokens per ms
  and that this avoids the fixed-window boundary-burst problem
                                                              
  3. Feature integration tests (describe('sampling - integration
  with other features'))

  - sampling + redact — logs with redacted paths pass through
  sampling; emitted lines still show [Redacted] for configured
  paths, non-redacted fields survive intact
  - sampling + serializers — serializer call count is asserted to
   equal exactly the number of emitted logs (4 out of 10 at
  every: 3), proving serializers never run for dropped logs and
  are called exactly once per emitted log
  - sampling + msgPrefix — prefix correctly prepended to each
  emitted msg; dropped logs don't show up at all
  - sampling + msgPrefix on child — verifies parent+child prefix
  concatenation ([HTTP] [req] a) continues to work through the
  sampled path
  - sampling + dedupe summary + redact — redaction still applies
  to logs that pass sampling, and documents the intentional
  design decision that dedupe summaries bypass msgPrefix (they go
   straight through writeSym to avoid any recursion into the
  sampler)

  Files touched this round

  - lib/sampling.js — no code changes needed; all behavior
  already correct
  - pino.d.ts — token-bucket JSDoc for limit/window
  - test/sampling.test.js — 6 new tests (1 timer-race + 5
  integration)