 Implementation is complete. Let me do a final sanity check that no file has
  obvious issues.                                                             
                                                                              
  Listed 2 directories (ctrl+o to expand)                                     
                                                                                  
● Everything is in place. Here's a summary of what was built.                     
                                                                                  
  ---                                                                     
  Summary                                                 
                                                                              
  account-service — Budget CRUD + orchestration                                   
                                                                                  
  New files (com.piggymetrics.account):
  - domain/Budget.java — Mongo budgets document with accountName, category, limit,
   currency, period, warningThreshold (default 0.80), active, deleted, timestamps.
   Soft-deleted flag preserves history. Constant TOTAL_CATEGORY = "_TOTAL".       
  - domain/BudgetPeriod.java — MONTH, QUARTER, YEAR.                       
  - domain/BudgetStatus.java, domain/BudgetEvaluation.java — DTOs returned from
  statistics-service.                                                          
  - repository/BudgetRepository.java — derived queries on (accountName, active, 
  deleted, category, period).                                                   
  - service/BudgetService(Impl).java — create / list / update / softDelete /
  evaluate with ownership checks and duplicate-active guard at service layer
  ((accountName, category, period) must be unique among active non-deleted  
  budgets).                                                               
  - controller/BudgetController.java under /budgets:
    - User-scoped (Principal-based): GET POST /current, PUT DELETE 
  /current/{budgetId}, GET /current/status.                        
    - Service-scoped (@PreAuthorize("#oauth2.hasScope('server')")): GET 
  /{accountName}/active, GET /{accountName}/status.                     
  - client/BudgetEvaluationClient(+Fallback).java — Feign to statistics-service
  (uses contextId = "budget-evaluation" so it coexists with the existing
  StatisticsServiceClient). Fallback returns [] so UI/cron degrade gracefully.

  statistics-service — Budget Evaluation

  New files (com.piggymetrics.statistics):
  - domain/Budget.java (DTO), domain/BudgetPeriod.java (with startOfPeriod(today)
  for calendar-aligned MONTH/QUARTER/YEAR boundaries), domain/BudgetStatus.java, 
  domain/BudgetEvaluation.java.                                                 
  - service/BudgetEvaluationService(Impl).java — for each budget:
    - pulls DataPoints for the account since the period start (new repository
  method findByIdAccountAndIdDateGreaterThanEqual),                          
    - sums matching ItemMetrics by title (or all, for _TOTAL),
    - relies on the existing day-normalized ItemMetric.amount so summing across
  days yields period totals in USD base,                                       
    - uses the existing ExchangeRatesService to convert to the budget's own
  currency,                                                                
    - computes percentUsed and maps to UNDER_BUDGET / WARNING (>= threshold) /
  EXCEEDED (>= 100%).                                                         
  - controller/BudgetEvaluationController.java — POST 
  /budgets/evaluate/{accountName} (server scope only).
  - repository/DataPointRepository.java:16 — added    
  findByIdAccountAndIdDateGreaterThanEqual.       
                                           
  notification-service — Budget Alerts

  New files (com.piggymetrics.notification):
  - domain/BudgetPeriod.java, domain/BudgetStatus.java,
  domain/BudgetEvaluation.java (DTOs).                 
  - domain/BudgetAlertState.java + repository/BudgetAlertStateRepository.java —
  stores per-(accountName, budgetId) cooldown (lastStatus, lastAlertedAt) in   
  collection budgetAlertStates with a unique compound index.                
  - client/AccountServiceClientFallback.java — backup path throws (preserves
  existing skip-on-failure behavior); getBudgetStatus path returns [] so the sweep
   keeps going for other recipients.                                              
  - service/BudgetAlertService(Impl).java — cron-driven via @Scheduled(cron = 
  "${budget.alert.cron:0 0 * * * *}"), @RefreshScope. For each opted-in recipient,
   it fans out with CompletableFuture.runAsync (matches the existing async pattern
   in NotificationServiceImpl), fetches evaluations via AccountServiceClient, and
  for each WARNING/EXCEEDED either past the cooldown or transitioned to a new
  status, sends an email and records the alert.

  Modified files:
  - domain/NotificationType.java — added
  BUDGET_ALERT("budget.alert.email.subject", "budget.alert.email.text", null).
  - client/AccountServiceClient.java — added getBudgetStatus(...) and wired   
  fallback.                                                                
  - repository/RecipientRepository.java — added findWithBudgetAlertsActive().
  - service/EmailService(+Impl).java — added sendFormatted(...) for          
  runtime-formatted alert bodies (existing send(...) untouched for backup/remind
  flows).                                                                       
         
  Config

  - config/src/main/resources/shared/notification-service.yml:
    - feign.hystrix.enabled: true (required because I added a fallback to
  AccountServiceClient).
    - New budget.alert block: cron (hourly default), cooldown.hours: 24,
  email.subject / email.text templates.

  Security posture (as requested)

  - All user-facing /budgets/current* endpoints run under the standard Principal
  and are blocked by the existing ResourceServerConfig
  (anyRequest().authenticated()).
  - Internal endpoints GET /budgets/{accountName}/active, GET
  /budgets/{accountName}/status, and POST /budgets/evaluate/{accountName} require
  #oauth2.hasScope('server'), matching the pattern used by the existing statistics
   update endpoint.
  - Outbound Feign calls use the pre-existing OAuth2FeignRequestInterceptor
  (client-credentials, server scope), so no additional wiring was needed.

  Notes / caveats for you to verify

  - I have not compiled or run tests — the environment doesn't have the Maven
  wrapper set up here, and the change spans three services. Worth doing a mvn -pl
  account-service,statistics-service,notification-service -am package in the
  normal build environment before merging.
  - Budget evaluation aggregates daily datapoint snapshots, which is the same
  granularity the rest of statistics-service operates at; actual spend is
  therefore within ±1 day of calendar boundaries, not transaction-accurate.
  - Cooldown deliberately resets on status transition (WARNING → EXCEEDED
  re-alerts immediately) — I thought that was the more useful behavior, but flip
  the check in shouldAlert if you'd rather gate on time-only.
  - I restricted the AccountServiceClient fallback for getAccount(...) to throwing