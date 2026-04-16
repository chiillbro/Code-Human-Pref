 All green — 38/38 sampling tests, 186/186 core regression
  tests, lint clean.                                    
                                       
  Summary of changes

  1. Token-bucket rate limiter (lib/sampling.js)

  Replaced the fixed-window counter with a token bucket:
  - Capacity = limit, refill rate = limit / window tokens per ms,
   starts full                                                   
  - Each emission consumes one token; refill is computed
  continuously from elapsed time                        
  - No allocation per-call, just two numbers (tokens, last) in
  the closure                                                 
  - Eliminates the 2x-boundary-burst failure mode of the previous
   implementation                                                
                 
  New tests assert:
  - An initial burst can drain the bucket but cannot exceed
  capacity                                                 
  - Smooth refill over time yields the proportional token count
  (no boundary bursts)                                         
                      
  2. Dedupe LRU eviction (lib/sampling.js)

  - Added dedupe.maxKeys option, default 10000
  - Uses Map's insertion-order semantics for LRU:
    - Promotion on hit: seen.delete(key); seen.set(key, entry)
  moves the key to the tail
    - Eviction on insert: when seen.size >= maxKeys,
  seen.keys().next().value yields the oldest key, which is
  removed
  - The evicted entry's pending timer is clearTimeout'd and its
  summary is flushed synchronously via writeSym — suppression
  counts are never silently dropped
  - The timer callback guards with seen.get(key) === state so a
  timer that fires after eviction is a no-op (not a double-emit)
  - maxKeys validated at construction time (positive integer)

  New tests assert LRU eviction order, that promoted keys
  survive, and that maxKeys validation rejects 0 / non-integers.

  3. Unknown-level validation (lib/sampling.js)

  - assertSampling now receives levels and throws a clear error
  for any label that isn't '*' and isn't in levels.values
  - The error message lists the known level names to make typos
  trivial to fix
  - '*' wildcard is always permitted
  - Custom level names and child-override paths both validate
  correctly (verified by tests)

  Also updated pino.d.ts (new maxKeys field on
  SamplingDedupeOptions) and docs/api.md (token-bucket semantics
  and maxKeys documented).