Great, implementation looking nice now. Let's clean up a couple of things:


1. I want to make sure the dedupe behaves perfectly when entries expire via timers and get evicted via LRU at the same time. So, can you please write a test setting maxKeys=1 with a short window. Insert key "a", then immediately insert key 'b' (which evicts 'a') and wait for the timer to fire. Verify that the summary for 'a' is emitted exactly once, not twice. 
2. The TypeScript types for limit and window in SamplingRule still describe fixed-window semantics. Please update the JSDoc to describe the token-bucket behavior, like limit is the bucket capacity and window is how long it takes to fully refill.
3. Finally, write a few tests combining sampling with other pino features to ensure we are not breaking the core library:
   - Sampling + redaction (ensure redacted fields still work on sampled logs)
   - Sampling + serializers (ensure serializers are only called for logs that pass the sampling check)
   - Sampling + msgPrefix (verify prefixes apply correctly to sampled logs)