The project's pinned Mockito 2.18.x and JaCoCo 0.7.6 both predate JDK 17 — the existing
  test suite also fails on JDK 21. This confirms my test code is fine; test execution
  needs JDK 8/11 (the project's intended runtime). Let me clean up the temp directory.

● Bash(rm -rf /mnt/c/temp-piggy 2>&1 && echo "cleaned") 
  ⎿  cleaned                                                                              

● Now let me verify the final state of the changed files back in the real working
  directory.                                                                              

  Read 1 file, listed 1 directory (ctrl+o to expand)     
                                                                                          
● Everything is in order. Final state summary.                                            
                                                                                          
  ---                                                   
  Summary                                                                                 
                                                        
  1. Budget alert cooldown tests — done                                                   

  New file: notification-service/src/test/java/com/piggymetrics/notification/service/Budge
  tAlertServiceImplTest.java

  Mocks AccountServiceClient, EmailService, BudgetAlertStateRepository (plus
  RecipientRepository and Environment — needed for the sendBudgetAlerts() entry point and
  for env.getProperty(key, default) in sendAlert). Uses ReflectionTestUtils.setField to
  pin cooldownHours = 24L deterministically (the @Value field is bare without Spring
  context).

  Because sendBudgetAlerts() dispatches each recipient on CompletableFuture.runAsync,
  positive assertions use verify(..., timeout(500)) and negative ones use verify(...,
  after(500).never()) — matches the existing NotificationServiceImplTest pattern.

  Four tests cover exactly what you asked:

  Test: shouldSendEmailForFirstWarningWhenNoPriorStateExists                            
  Cooldown behaviour: First alert → email sent, state saved with lastStatus=WARNING and a
    fresh lastAlertedAt                                                                 
  ────────────────────────────────────────                                              
  Test: shouldNotSendEmailForSameStatusInsideCooldownWindow                             
  Cooldown behaviour: Prior WARNING 1h ago, cooldown 24h → suppressed; verifies         
    findByAccountNameAndBudgetId was called (proves the async task ran) then asserts    
    sendFormatted never invoked and save never called                                   
  ────────────────────────────────────────
  Test: shouldSendEmailOnStatusTransitionEvenInsideCooldownWindow
  Cooldown behaviour: Prior WARNING 30min ago, new status EXCEEDED → email sent even
  inside
     the 24h window; captured state's lastStatus is EXCEEDED
  ────────────────────────────────────────
  Test: shouldSendEmailForSameStatusAfterCooldownExpires
  Cooldown behaviour: Prior WARNING 25h ago, cooldown 24h → email sent and state re-saved

  2. mvn compile on all three services — done

  Ran via Windows Maven + JDK 21 against a copy of the project (WSL sandbox has no native
  JDK).

  First run surfaced one real compile error:

  BudgetEvaluationClient.java:18: cannot find symbol
    symbol: method contextId()
    location: @interface org.springframework.cloud.openfeign.FeignClient

  Root cause: this project pins Spring Cloud Finchley.RELEASE (pom.xml:35), and the
  contextId attribute on @FeignClient was only added in Finchley.SR1. I fixed it by
  collapsing BudgetEvaluationClient / BudgetEvaluationClientFallback into the existing
  StatisticsServiceClient / StatisticsServiceClientFallback — one client per remote
  service, matching the rest of the codebase and avoiding the version-specific feature
  altogether.

  Files affected by the fix:
  - account-service/.../client/StatisticsServiceClient.java:25-29 — added
  evaluateBudgets(...) with the /statistics/budgets/evaluate/{accountName} path.
  - account-service/.../client/StatisticsServiceClientFallback.java — added fallback
  returning Collections.emptyList().                                                
  - account-service/.../service/BudgetServiceImpl.java — autowires StatisticsServiceClient
   and calls statisticsClient.evaluateBudgets(...).                                       
  - Deleted: account-service/.../client/BudgetEvaluationClient.java,
  BudgetEvaluationClientFallback.java.                              
                                      
  Second run — clean + test-compile:

  [INFO] piggymetrics 1.0-SNAPSHOT .......................... SUCCESS [  1.913 s]
  [INFO] account-service 1.0-SNAPSHOT ....................... SUCCESS [  8.668 s]
  [INFO] statistics-service 1.0-SNAPSHOT .................... SUCCESS [  2.715 s]
  [INFO] notification-service 1.0.0-SNAPSHOT ................ SUCCESS [  2.510 s]
  [INFO] BUILD SUCCESS

  Both compile and test-compile pass on all three modules — confirms the Jackson swap,
  limitAmount/actualAmount rename, the new client refactor, and both new test classes all
  compile cleanly. Only warnings are pre-existing deprecation notices from Account.java /
  Recipient.java (still on the old org.codehaus.jackson package — out of scope per the
  earlier discussion).

  What I could not verify, and why

  I tried to execute the two new test suites but test execution failed on JDK 21 for
  reasons unrelated to my code:

  - JaCoCo 0.7.6.201602180812 (pinned in the parent pom, from 2016) crashes the JVM at
  startup on JDK 21: FATAL ERROR in native method: processing of -javaagent failed.
  - Bypassing JaCoCo, the Mockito pulled in by spring-boot-starter-parent:2.0.3.RELEASE
  (~Mockito 2.18.x) uses sun.misc.Unsafe.defineClass, which was removed in JDK 17: Could
  not find sun.misc.Unsafe.

  I confirmed this is pre-existing by running the existing StatisticsServiceImplTest under
   the same JDK 21 — it fails with the same 4 errors. So the test suite, mine and
  pre-existing alike, needs JDK 8 or 11 to run (which matches the
  <java.version>1.8</java.version> in the pom). Please run mvn test locally with your
  normal JDK — all test code compiles, so the suite should execute cleanly there.
