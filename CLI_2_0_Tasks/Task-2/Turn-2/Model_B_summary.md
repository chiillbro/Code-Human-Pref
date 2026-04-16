 All clean. Summary of the three fixes:            
                                                                 
  1. Token-bucket rate limiter (lib/sampling.js)                 

  Replaced the fixed-window counter with a proper token bucket:
  - Starts with tokens = limit (full)
  - Refills continuously at limit / window tokens per ms, capped
  at limit                                                      
  - Each emitted log consumes one token; drop when tokens < 1
  - No more 2× boundary bursts — verified by tests for initial
  burst cap, smooth refill cap (<= 2*limit after a full window),
  and partial proportional refill                               
                                 
  2. Dedupe LRU with maxKeys (lib/sampling.js)

  - New dedupe.maxKeys option, default 10000, validated as a
  positive integer
  - On hit: seen.delete(key); seen.set(key, entry) → promotes to
  MRU via Map insertion order
  - On insert at capacity: evict seen.keys().next().value
  (oldest), clearTimeout its pending timer, and flush its summary
   if count > 0 so suppressed counts are never silently lost
  - Tests cover promotion-prevents-eviction, summary-on-eviction,
   and bounded growth under unique-key floods

  3. Unknown level labels throw (lib/sampling.js)

  buildSampling() now iterates rule keys and throws
  sampling.<label>: unknown level "<label>" (known levels: ...)
  for anything that isn't '*' or a key in levels.values. Applies
  to both root construction and child({}, { sampling }). Custom
  level names remain accepted.

  Docs (docs/api.md) and types (pino.d.ts) updated to reflect
  token-bucket semantics, dedupe.maxKeys, and the unknown-label
  validation. 38 sampling tests + core regression suite pass;
  eslint and tsc --noEmit are clean.
