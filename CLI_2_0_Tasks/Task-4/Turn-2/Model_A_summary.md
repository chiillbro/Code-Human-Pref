
                                                                                            
● All four issues are addressed. Here's a summary of what I verified / fixed.
                                                                                          
  ---                                                              
  1. Feign URL paths — already correct (no change needed)                                   
                                                         
  I double-checked every new Feign @RequestMapping against the target service's
  context-path:

  ┌────────────────────────────────────┬─────────┬─────────────────────────────┬──────┐ 
  │                                    │ Target  │                             │ Matc │ 
  │               Caller               │ context │         Feign value         │ hes  │ 
  │                                    │  -path  │                             │      │ 
  ├────────────────────────────────────┼─────────┼─────────────────────────────┼──────┤ 
  │ account-service/.../BudgetEvaluati │ /statis │ /statistics/budgets/evaluat │ yes  │ 
  │ onClient.java:23                   │ tics    │ e/{accountName}             │      │ 
  ├────────────────────────────────────┼─────────┼─────────────────────────────┼──────┤ 
  │ notification-service/.../AccountSe │ /accoun │ /accounts/budgets/{accountN │ yes  │
  │ rviceClient.java:23                │ ts      │ ame}/status                 │      │
  └────────────────────────────────────┴─────────┴─────────────────────────────┴──────┘

  Both already start with the service prefix, matching the pattern of the existing
  StatisticsServiceClient (/statistics/...) and AccountServiceClient.getAccount
  (/accounts/...). If you were looking at a particular line that still seemed wrong, let me
  know which one and I'll recheck.

  2. Evaluation currency consistency

  The math was already converting to budget currency, but the field names (limit / actual)
  and documentation made the invariant easy to misread. I:

  - Renamed both fields on all three DTOs to limitAmount / actualAmount so the currency
  pairing is obvious from the name.
  - Added a Currency invariant Javadoc block on each DTO stating explicitly that both are in
   the budget's currency.
  - Renamed the local variable in BudgetEvaluationServiceImpl.evaluate from actual to
  actualInBudgetCurrency and added an inline comment explaining the USD→budget-currency
  re-expression.

  Files updated:
  - account-service/src/main/java/com/piggymetrics/account/domain/BudgetEvaluation.java
  -                                                                                    
  statistics-service/src/main/java/com/piggymetrics/statistics/domain/BudgetEvaluation.java
  - notification-service/src/main/java/com/piggymetrics/notification/domain/BudgetEvaluation
  .java                                                                                     
  - statistics-service/src/main/java/com/piggymetrics/statistics/service/BudgetEvaluationSer
  viceImpl.java (uses the new setters)                                                      
  - notification-service/src/main/java/com/piggymetrics/notification/service/BudgetAlertServ
  iceImpl.java:125-132 (uses new getters in MessageFormat)                                  
                                                          
  3. Unit tests for evaluation normalization

  New file: statistics-service/src/test/java/com/piggymetrics/statistics/service/BudgetEvalu
  ationServiceImplTest.java

  Follows the existing pattern in StatisticsServiceImplTest (JUnit 4, @InjectMocks/@Mock,
  initMocks). The convert mock is a faithful re-implementation of
  ExchangeRatesServiceImpl.convert using the same 1 USD = 0.8 EUR / 80 RUB fixtures the
  other tests use, so assertions exercise the real conversion formula rather than a
  simplification.

  Six tests, covering each of your requested normalization scenarios and the status ladder:

  Test: shouldConvertActualFromBaseCurrencyIntoBudgetCurrency                             
  Covers: Currency mismatch: budget in EUR, $1000 USD base → 800.00 EUR actual, WARNING at
    80%                                                                                   
  ────────────────────────────────────────                                                
  Test: shouldAggregateYearlyItemAcrossMonthBudgetPeriod                                  
  Covers: Period mismatch: $12,000/YEAR item normalized to daily via                      
    TimePeriod.YEAR.baseRatio, summed across 30 days of a MONTH budget → ~$985.55/month,  
    WARNING                                                                               
  ────────────────────────────────────────
  Test: shouldAggregateAllCategoriesForTotalCategory
  Covers: _TOTAL aggregation: three distinct categories (Rent/Food/Transport) summed across
    10 days, compared against a _TOTAL budget
  ────────────────────────────────────────
  Test: shouldIgnoreNonMatchingCategoriesWhenBudgetIsCategorySpecific
  Covers: sanity: category-specific budgets must filter correctly
  ────────────────────────────────────────
  Test: shouldMarkBudgetExceededAtOrAboveHundredPercent
  Covers: EXCEEDED threshold
  ────────────────────────────────────────
  Test: shouldStayUnderBudgetBelowWarningThreshold
  Covers: UNDER_BUDGET threshold

  I could not run mvn test here (the sandbox has Maven on path but no JDK), so while the
  test math has been traced by hand against the real conversion formula, please run the
  suite locally before merging.

  4. Jackson annotations

  Migrated all four new DTOs I authored from
  org.codehaus.jackson.annotate.JsonIgnoreProperties to
  com.fasterxml.jackson.annotation.JsonIgnoreProperties, matching the pattern already used
  by statistics-service/.../ExchangeRatesContainer.java:

  - account-service/.../domain/Budget.java:3
  - account-service/.../domain/BudgetEvaluation.java:3
  - statistics-service/.../domain/Budget.java:3
  - notification-service/.../domain/BudgetEvaluation.java:3

  I did not touch the pre-existing Account.java files (in both account-service and
  statistics-service), which still use the old package — those are outside the scope of this
   change. Happy to do a follow-up sweep for those if you want.
