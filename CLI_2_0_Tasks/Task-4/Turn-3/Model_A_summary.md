 All three services compile cleanly — both main and test sources. Here's the summary:
                                                                                                                 
  ---
  What was done
               
  1. Compilation fix: contextId not available in Finchley

  @FeignClient(contextId = ...) was added in Spring Cloud Greenwich, but this project uses Finchley.RELEASE. The
  compile error was:

  cannot find symbol: method contextId()

  Fix: Merged the BudgetEvaluationClient into the existing StatisticsServiceClient interface (which already
  points at statistics-service), added evaluateBudgets(...) as a second method alongside updateStatistics(...),
  and updated the fallback. Deleted the standalone BudgetEvaluationClient.java and
  BudgetEvaluationClientFallback.java. Updated BudgetServiceImpl to inject StatisticsServiceClient instead.

  2. Budget alert cooldown test

  New file: notification-service/src/test/java/.../service/BudgetAlertServiceImplTest.java

  Four test cases matching your requirements:

  ┌──────────────────────────────────────────────────────┬────────────────────────────────┬──────────────────┐
  │                         Test                         │            Scenario            │     Expected     │
  ├──────────────────────────────────────────────────────┼────────────────────────────────┼──────────────────┤
  │ shouldSendEmailOnFirstAlertForBudget                 │ No prior BudgetAlertState      │ Email sent,      │
  │                                                      │                                │ state saved      │
  ├──────────────────────────────────────────────────────┼────────────────────────────────┼──────────────────┤
  │ shouldNotSendEmailWhenSameStatusWithinCooldownWindow │ WARNING alert 1h ago, still    │ No email         │
  │                                                      │ WARNING, cooldown=24h          │                  │
  ├──────────────────────────────────────────────────────┼────────────────────────────────┼──────────────────┤
  │ shouldSendEmailOnStatusTransitionEvenWithinCooldown  │ WARNING alert 1h ago, now      │ Email sent       │
  │                                                      │ EXCEEDED, cooldown=24h         │ (transition)     │
  ├──────────────────────────────────────────────────────┼────────────────────────────────┼──────────────────┤
  │ shouldSendEmailWhenCooldownHasExpired                │ WARNING alert 25h ago, still   │ Email sent       │
  │                                                      │ WARNING, cooldown=24h          │ (expired)        │
  └──────────────────────────────────────────────────────┴────────────────────────────────┴──────────────────┘

  Mocks AccountServiceClient, EmailService, BudgetAlertStateRepository, RecipientRepository, and Environment.
  Uses ReflectionTestUtils.setField for the @Value-injected cooldownHours. Follows the existing
  NotificationServiceImplTest async verification pattern (verify(..., timeout(500))).

  3. Compilation verified

  mvn compile -pl account-service,statistics-service,notification-service -am -DskipTests

  account-service ......... SUCCESS
  statistics-service ...... SUCCESS
  notification-service .... SUCCESS
  BUILD SUCCESS

  Test sources also compile (test-compile) for both statistics-service and notification-service.