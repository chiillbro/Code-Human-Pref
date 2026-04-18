                                                                                         
● All four issues are addressed. Here's the summary:
                                                                                          
  ---                                                                                     
  Changes made                                                                            

  1. Feign URL paths

  Verified both Feign clients already include the correct context-path prefixes:
  - BudgetEvaluationClient → /statistics/budgets/evaluate/{accountName}
  (statistics-service has context-path /statistics)                    
  - AccountServiceClient.getBudgetStatus → /accounts/budgets/{accountName}/status
  (account-service has context-path /accounts)                                   
                                              
  These match the existing patterns from StatisticsServiceClient
  (/statistics/{accountName}) and AccountServiceClient.getAccount
  (/accounts/{accountName}). No change was needed.

  2. Evaluation result consistency

  The code already converts actual from base currency (USD) to the budget's own currency
  before comparison with limit. Improved variable naming (actualInBaseCurrency →
  actualInBudgetCurrency) and added explicit comments at
  BudgetEvaluationServiceImpl.java:67-78 making the currency flow undeniable.

  3. Unit tests — BudgetEvaluationServiceImplTest.java

  7 tests covering the requested normalization scenarios:

  Test: shouldConvertActualToEurWhenBudgetCurrencyDiffersFromBase                       
  What it covers: Expense in USD base, budget in EUR — verifies USD→EUR conversion      
  ────────────────────────────────────────                                              
  Test: shouldCorrectlyAggregateYearExpenseOverMonthBudgetPeriod                        
  What it covers: Per-day amounts (from a YEAR expense) summed over 30 daily DataPoints vs
                                                                                        
    a MONTH budget                                                                      
  ────────────────────────────────────────                                                
  Test: shouldSumAllCategoriesForTotalBudget
  What it covers: _TOTAL category sums Rent + Food across multiple DataPoints
  ────────────────────────────────────────
  Test: shouldConvertTotalCategoryWithMultipleCurrencies
  What it covers: _TOTAL with multiple categories AND cross-currency conversion
  ────────────────────────────────────────
  Test: shouldReturnWarningWhenPercentEqualsThreshold
  What it covers: Boundary: exactly at 80% triggers WARNING
  ────────────────────────────────────────
  Test: shouldReturnUnderBudgetWhenBelowThreshold
  What it covers: Below threshold → UNDER_BUDGET
  ────────────────────────────────────────
  Test: shouldReturnZeroActualWhenNoDataPoints
  What it covers: Empty period → zero actual, UNDER_BUDGET

  The rate mock mirrors ExchangeRatesServiceImpl.convert exactly: amount × (rates[to] /
  rates[from]).

  4. Jackson annotations

  Replaced org.codehaus.jackson.annotate.JsonIgnoreProperties with
  com.fasterxml.jackson.annotation.JsonIgnoreProperties in all four new files:
  - account-service/.../domain/Budget.java
  - account-service/.../domain/BudgetEvaluation.java
  - statistics-service/.../domain/Budget.java       
  - notification-service/.../domain/BudgetEvaluation.java