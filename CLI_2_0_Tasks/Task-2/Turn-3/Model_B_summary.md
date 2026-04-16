All clean — 43/43 sampling tests, 233/233 core regression, lint
   and tsc --noEmit pass.                               
                                       
  What changed

  1. Dedupe eviction/timer race test (test/sampling.test.js)
  New dedupe LRU eviction does not double-emit when timer fires
  after eviction test:
  - maxKeys: 1, window: 30
  - Logs 'a' twice (count=1), then 'b' → 'a' is LRU-evicted
  synchronously, its summary is flushed and its timer is   
  clearTimeout'd                                        
  - Waits 80ms (well past the original 30ms timer) and asserts
  the summary list is still exactly ['a'] — proving the cleared
  timer + the seen.get(key) === state guard in dedupeFlush     
  together guarantee exactly-once emission                
                                            2. TypeScript JSDoc (pino.d.ts)
  SamplingRule.limit / SamplingRule.window now describe
  token-bucket semantics: limit = bucket capacity (starts full,
  one token per emission, allows bursts up to capacity), window =
   full-refill period (refill rate = limit / window tokens per
  ms).

  3. Feature integration tests (test/sampling.test.js, new
  sampling - integration with other features describe block)
  - redaction: every: 2 + redact: ['secret'] → only sampled lines
   emitted, secret is [Redacted] on each                         
  - serializers: every: 2 + custom foo serializer → 6 calls
  produce 3 lines and the serializer is invoked exactly 3 times
  (confirms sampled-out logs never reach serialization) and the
  serialized values are correct on the lines that pass         
  - msgPrefix: every: 2 + msgPrefix: '[S] ' → sampled lines carry
   the prefix                                                    
  - child msgPrefix + inherited sampling: parent prefix + child
  prefix combine correctly while the child still applies the
  parent's inherited sampler