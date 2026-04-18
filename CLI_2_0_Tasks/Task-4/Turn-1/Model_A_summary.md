 The implementation is complete. Here's a summary of everything that was built:
                                                                              
  ---            
  Budget Alerting System - Implementation Summary
                                                 
  1. Budget CRUD (account-service) — 6 new files

  ┌───────────────────────────────┬───────────────────────────────────────────┐ 
  │             File              │                  Purpose                  │ 
  ├───────────────────────────────┼───────────────────────────────────────────┤ 
  │                               │ MongoDB document with category,           │ 
  │ domain/Budget.java            │ limitAmount, limitCurrency, period,       │ 
  │                               │ warningThreshold (default 80%), active    │ 
  │                               │ flag, timestamps                          │ 
  ├───────────────────────────────┼───────────────────────────────────────────┤ 
  │ domain/BudgetPeriod.java      │ Enum: MONTH, QUARTER, YEAR with           │ 
  │                               │ day-ratios for normalization              │ 
  ├───────────────────────────────┼───────────────────────────────────────────┤ 
  │                               │ Spring Data queries:                      │ 
  │ repository/BudgetRepository.j │ findByAccountNameAndActiveTrue,           │ 
  │ ava                           │ duplicate-check by (account, category,    │ 
  │                               │ period, active)                           │ 
  ├───────────────────────────────┼───────────────────────────────────────────┤ 
  │ service/BudgetService.java    │ Interface: create, list, update, delete   │ 
  ├───────────────────────────────┼───────────────────────────────────────────┤ 
  │ service/BudgetServiceImpl.jav │ Duplicate prevention on create/update,    │ 
  │ a                             │ partial updates, soft-delete (sets        │ 
  │                               │ active=false)                             │ 
  ├───────────────────────────────┼───────────────────────────────────────────┤ 
  │                               │ GET/POST /budgets/current (user auth via  │
  │ controller/BudgetController.j │ Principal), GET /budgets/{accountName}    │
  │ ava                           │ (service auth via @PreAuthorize("#oauth2. │
  │                               │ hasScope('server')"))                     │
  └───────────────────────────────┴───────────────────────────────────────────┘

  2. Budget Evaluation (statistics-service) — 8 new files, 3 modified

  File: domain/Budget.java                                                      
  Purpose: DTO matching account-service's Budget                                
  ────────────────────────────────────────                                      
  File: domain/BudgetPeriod.java                                                
  Purpose: Enum matching account-service                                        
  ────────────────────────────────────────                                                                                                                                                                                                                                                                      "" 09:22 18-Apr-26
  File: domain/BudgetStatus.java                                                
  Purpose: UNDER_BUDGET, WARNING, EXCEEDED                                      
  ────────────────────────────────────────                                      
  File: domain/BudgetEvaluation.java                                            
  Purpose: Result DTO: budgetId, category, period, limit, actual, usagePercentage,
                                                                                
    status                                                                      
  ────────────────────────────────────────                                      
  File: client/AccountServiceClient.java                                        
  Purpose: Feign client to account-service with fallback                        
  ────────────────────────────────────────                                      
  File: client/AccountServiceClientFallback.java                                
  Purpose: Returns empty list / null on failure                                 
  ────────────────────────────────────────                                      
  File: service/BudgetEvaluationService[Impl].java
  Purpose: Core logic: fetches budgets + account expenses, normalizes currencies
    (via ExchangeRatesService.convert()) and time periods (daily base ratio ->
    budget period), computes usage %, determines status against warning threshold
  ────────────────────────────────────────
  File: controller/BudgetEvaluationController.java
  Purpose: GET /budgets/evaluate/current (user auth), GET
    /budgets/evaluate/{accountName} (service auth)

  Modified: StatisticsApplication.java (+@EnableCircuitBreaker),
  ResourceServerConfig.java (+OAuth2 Feign interceptor for service-to-service
  calls), pom.xml (+Hystrix deps), statistics-service.yml (+feign.hystrix.enabled)

  3. Notifications (notification-service) — 4 new files, 5 modified

  ┌───────────────────────────────────────────┬───────────────────────────────┐ 
  │                   File                    │            Purpose            │ 
  ├───────────────────────────────────────────┼───────────────────────────────┤ 
  │ domain/BudgetAlert.java                   │ DTO for deserialized          │ 
  │                                           │ evaluation results            │ 
  ├───────────────────────────────────────────┼───────────────────────────────┤ 
  │                                           │ Added BUDGET_ALERT with email │ 
  │ domain/NotificationType.java              │  subject/text/attachment      │ 
  │                                           │ property keys                 │
  ├───────────────────────────────────────────┼───────────────────────────────┤
  │                                           │ Feign client to               │
  │ client/StatisticsServiceClient.java       │ statistics-service /budgets/e │
  │                                           │ valuate/{accountName}         │
  ├───────────────────────────────────────────┼───────────────────────────────┤
  │ client/StatisticsServiceClientFallback.ja │ Returns empty list on failure │
  │ va                                        │                               │
  └───────────────────────────────────────────┴───────────────────────────────┘

  Modified: NotificationServiceImpl.java — new sendBudgetAlertNotifications() cron
   method that: finds recipients ready for BUDGET_ALERT (cooldown via existing
  frequency mechanism), fetches evaluations from statistics-service, filters for
  WARNING/EXCEEDED status, builds alert text, sends email, marks notified.
  RecipientServiceImpl + RecipientRepository — added findReadyForBudgetAlert()
  query. NotificationServiceApplication.java (+@EnableCircuitBreaker). pom.xml
  (+Hystrix starter).

  4. Config

  - notification-service.yml: Budget alert cron (0 0 */6 * * * = every 6 hours),
  email template, attachment name
  - statistics-service.yml: feign.hystrix.enabled: true

  5. Tests — 3 new files

  - BudgetServiceTest.java — create, duplicate prevention, find, update,
  soft-delete, ownership checks
  - BudgetEvaluationServiceImplTest.java — UNDER_BUDGET/WARNING/EXCEEDED status,
  _TOTAL category, currency+period normalization
  - BudgetAlertNotificationTest.java — alert sent on exceeded, skipped when under
  budget, error resilience

  Data flow

  User → account-service (CRUD budgets)
                  ↓ Feign (service auth)
  statistics-service (evaluate: fetch budgets + expenses, normalize
  currency/period, compute status)
                  ↓ Feign (service auth)
  notification-service (cron: check recipients, get evaluations, filter
  WARNING/EXCEEDED, email with cooldown)