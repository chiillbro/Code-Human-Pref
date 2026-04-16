Done. Here's the summary:             
                                                                              
  Design         

  Option shape (added to LoggerOptions.sampling):
  {
    sampling: {
      rules: { 
        info:  { type: 'probabilistic', rate: 0.1 },
        debug: { type: 'rateLimit', maxPerInterval: 100, intervalMs: 1000 },
        trace: { type: 'everyN', n: 10 }                                    
      },                                
      dedupe: { windowMs: 5000, levels: ['error', 'warn'], summary:
  '…{count}…{window}…{msg}…' }
    }
  }

  Key design decisions

  - Zero overhead when disabled. buildSamplingState() returns null when sampling
  isn't configured. In setLevel(), the loop only wraps a level's method when
  sampling[label] exists — otherwise it assigns the unwrapped genLog(...) output,
  so the hot path is identical to pre-change pino. Levels without a rule also keep
   the unwrapped fn even when sampling is set for other levels.
  - Early drop, before serialization. Wrapping lives at the level-method layer
  (installed in lib/levels.js:setLevel), which runs before genLog's format() call
  and before write()/asJson(). Suppressed logs never pay serialization cost.
  - Specialised wrappers. wrapWithSampling returns one of three shapes
  (sampler-only, deduper-only, both) so the hot path has no per-call branching
  beyond what each strategy requires.
  - Child inheritance. samplingSym is attached as an own property on the parent;
  children get it via prototype lookup, so parent-wrapped level methods (already
  on the parent) are inherited too. When a child passes options.sampling, child()
  builds a fresh state and re-runs setLevel to install child-scoped wrappers.
  Shared parent state means shared counters (what you want for a rateLimit that
  should cap aggregate output across children).
  - Dedupe key extraction handles the common shapes: string msg, {…}, 'msg',
  Error, { err: Error }. When the window closes with suppressed duplicates, a
  summary message is emitted through the same underlying log fn on the next
  matching call.

  Files changed

  - lib/sampling.js (new) — samplers, deduper, state builder, key extractor
  - lib/symbols.js — new samplingSym
  - lib/levels.js — setLevel installs wrappers only when a rule exists; new
  wrapWithSampling
  - lib/proto.js — child() honours options.sampling override and re-runs setLevel                                                                                                   pr-26
  - pino.js — reads opts.sampling, attaches state to instance
  - pino.d.ts — SamplingOptions / SamplingRule / DedupeOptions on LoggerOptions
  and ChildLoggerOptions
  - test/sampling.test.js (new) — covers all three sampler types, dedupe
  (suppression + summary + distinct msgs), unconfigured-level fast path, child
  inherit, child override, validation errors

  Validation

  - node --check passes on every modified JS file.
  - Standalone unit of lib/sampling.js was exercised directly (everyN,
  probabilistic 0/1, rateLimit cap, dedupe allow/deny + summary key shapes,
  rejection of invalid rule type and out-of-range rate, buildSamplingState
  returning null vs populated state). All behaved as expected.
  - I could not run test/sampling.test.js end-to-end: this sandbox has no node
  binary on $PATH and the cross-mount Windows node.exe can't resolve WSL paths to
  install the project's deps (flush-write-stream, split2, safe-stable-stringify,
  …). You should run npm test (or at minimum node --test test/sampling.test.js)
  locally before merging.