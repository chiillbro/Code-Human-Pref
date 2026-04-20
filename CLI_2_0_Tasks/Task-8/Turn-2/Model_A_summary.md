All 35 tests pass (up from 30 - the 5 new tests for
  guardrails, caching, and default dates all pass).     
                                       
● All four changes are done. Here's what was changed:
                                                            
  History endpoint guardrails                               
  (WalletServiceImpl.getHistory):
  - from and to are now optional - defaults to last 30 days
  ending today                                               - Rejects ranges exceeding 365 days with
  IllegalArgumentException (maps to HTTP 400 via existing
  ErrorHandler)                                          
  - Caps results to the most recent 100 snapshots within the
   range

  Rate caching (WalletServiceImpl):
  - Added cachedRates + cachedRatesTimestamp fields with a
  5-minute TTL                                            
  - getCachedRates() method reuses cached rates if within
  TTL, only calls statisticsClient.getRates() when stale 
  - Test verifies 3 consecutive recalculations result in
  only 1 Feign call                                     
                   
  Endpoint rename (WalletController):
  - Changed from /current/wallet/currency to
  /current/wallet/base-currency             
                               
  Plan file: Deleted
  .claude/plans/concurrent-wandering-pie.md

  Tests: 5 new test cases added (35 total, all passing) -
  default dates, range rejection, result capping, rate
  caching, default date controller test.