# Turn 1 — Classifications

**[MAJOR ISSUE FLAG]: Model A's new Feign client URLs are all missing the required service context-path prefixes (e.g. `/statistics`, `/accounts`). The existing codebase convention is to include these (see `/statistics/{accountName}` in the original StatisticsServiceClient and `/accounts/{accountName}` in the original AccountServiceClient). Model A's statistics-service → account-service calls use `/budgets/{accountName}` and `/{accountName}` instead of `/accounts/budgets/{accountName}` and `/accounts/{accountName}`. Its notification-service → statistics-service calls use `/budgets/evaluate/{accountName}` instead of `/statistics/budgets/evaluate/{accountName}`. All three new Feign endpoints would get 404s at runtime, completely breaking the cross-service evaluation and notification chain.**

---

## 1. Rationale Support (The 7 Questions)

### Q1. Expected Senior Engineer Behavior

A senior engineer given this prompt would:
- Read existing Feign clients in all 3 services to understand the URL conventions (context-path prefixes like `/accounts`, `/statistics`)
- Study the existing notification flow (BACKUP/REMIND pattern) before building budget alerts
- Use the existing `ExchangeRatesService` and `TimePeriod.getBaseRatio()` for normalization, understanding how expenses are stored and how period conversion works
- Create Budget as a separate MongoDB collection (not embedded in Account) since budgets have their own lifecycle
- Implement the evaluation logic in statistics-service where normalization expertise lives
- Add proper Hystrix fallbacks for all new Feign clients
- Add comprehensive tests covering: CRUD, normalization math, _TOTAL aggregation, alert sending, and cooldown logic
- Make sure the budget evaluation returns results in consistent units (same currency for both actual and limit)

### Q2. Model A — Solution Quality

**Strengths:**
- Clean Budget CRUD with separate MongoDB `budgets` collection. BudgetRepository uses Spring Data derived queries (`findByAccountNameAndCategoryAndPeriodAndActiveTrue`) for duplicate detection, which is straightforward
- Period normalization math in `calculateActualSpending()` is correct: converts expense to USD via `ratesService.convert()`, divides by `item.getPeriod().getBaseRatio()` to get daily rate, multiplies by `budget.getPeriod().getDays()`. A $1200/YEAR expense against a MONTH budget correctly yields ~$100/month
- Handles `_TOTAL` category properly — the filter check is `TOTAL_CATEGORY.equals(budget.getCategory()) || item.getTitle().equalsIgnoreCase(budget.getCategory())`
- Has tests across all services: BudgetControllerTest (3 tests), BudgetServiceTest (6 tests), BudgetEvaluationServiceImplTest (6 tests), BudgetAlertNotificationTest (3 tests). Coverage includes UNDER/WARNING/EXCEEDED, _TOTAL, cur+period normalization, duplicate prevention, soft-delete
- BudgetServiceImpl has ownership checks (`budget.getAccountName().equals(accountName)`) and the update method supports partial field changes (null fields not overwritten)

**Weaknesses:**
- **All 3 new Feign client URLs are wrong.** statistics-service's `AccountServiceClient` uses `/budgets/{accountName}` and `/{accountName}` — should be `/accounts/budgets/{accountName}` and `/accounts/{accountName}` (context-path is `/accounts`). notification-service's `StatisticsServiceClient` uses `/budgets/evaluate/{accountName}` — should be `/statistics/budgets/evaluate/{accountName}` (context-path is `/statistics`). The existing clients in the codebase ALL include the service context-path prefix (e.g. `/statistics/{accountName}`, `/accounts/{accountName}`). This breaks the entire cross-service flow — evaluation and notifications would 404 at runtime
- The evaluation result DTO has inconsistent currencies: `evaluation.setActualAmount(actualAmount)` is in USD (base), but `evaluation.setLimitAmount(budget.getLimitAmount())` is in the budget's original currency (e.g. EUR). A user looking at the result would see actual=100 (USD) and limit=500 (EUR) side by side — misleading
- notification-service config sets `attachment: budget-alert.txt` for BUDGET_ALERT, and the code passes `alertText` to `emailService.send(type, recipient, alertText)` where the third param is the attachment content. This conflates alert text with attachment semantics — the existing send() method adds it as a file attachment, not body text
- The `BudgetEvaluationServiceImplTest.shouldNormalizeCurrencyAndPeriod` test mocks `ratesService.convert()` to only divide by the from-rate (`i.getArgument(2).divide(rates.get(i.getArgument(0))...`) instead of doing `amount * toRate / fromRate`. Doesn't fully validate the real conversion

### Q3. Model A — Independent Agent Operation

**Strengths:**
- Correctly inferred it needs Feign clients, Hystrix fallbacks, and OAuth2 security for new endpoints — including adding `@EnableCircuitBreaker`, Hystrix deps to pom.xml, and `feign.hystrix.enabled: true` in config. These weren't spelled out step-by-step in the prompt
- Added `ResourceServerConfig` changes in statistics-service with `OAuth2FeignRequestInterceptor` so it can make authenticated calls to account-service — shows it understood the service-to-service auth pattern
- Did not ask unnecessary clarifying questions. The prompt was detailed enough that proceeding directly was acceptable

**Weaknesses:**
- Did NOT study the existing Feign client URLs carefully before writing new ones. The convention of including context-path prefixes (`/accounts/...`, `/statistics/...`) is the most critical cross-service detail and it's right there in the existing `StatisticsServiceClient` and `AccountServiceClient`. Missing all three new Feign URLs is a failure of codebase investigation
- There was genuine ambiguity around cooldown design (per-recipient vs per-budget?) and evaluation trigger (on-demand vs on account save?) — Model A just proceeded with its own decisions without flagging these as tradeoffs. Acceptable given the detailed prompt, but a senior engineer might surface these briefly

### Q4. Model A — Communication

**Strengths:**
- Summary is well-organized with a table listing all 6 new account-service files, 8 stats-service files, and 4 notification-service files. The data flow diagram at the end (`User → account-service → statistics-service → notification-service`) is helpful for understanding the architecture
- Claims about tests match the diff accurately — 3 test files with ~18 tests total. No hallucination detected in what was claimed
- The alert notification text is nicely formatted with `buildAlertText` producing `"- Grocery (MONTH): 120% used of 500 USD limit [EXCEEDED]"` style output — clear and actionable for end users

**Weaknesses:**
- Summary does not mention any limitations or caveats. A senior engineer would flag that they didn't compile or run the tests, or note areas of uncertainty. No mention of the context-path convention (though they likely didn't realize it was an issue)
- No discussion of design tradeoffs or alternative approaches considered — just presents the implementation as final. A brief note on why evaluation lives in statistics-service, or why cooldown is recipient-level rather than per-budget, would show more engineering maturity

### Q5. Model B — Solution Quality

**Strengths:**
- **Feign URLs are all correct** — follows the existing convention exactly. `BudgetEvaluationClient` uses `/statistics/budgets/evaluate/{accountName}`, notification-service `AccountServiceClient` uses `/accounts/budgets/{accountName}/status`. The cross-service communication chain would actually work
- Calendar-aligned period start with `BudgetPeriod.startOfPeriod(today)` — MONTH=first of current month, QUARTER=first of quarter, YEAR=Jan 1. This is more accurate than flat day-ratio averaging. Combined with querying DataPoints via `findByIdAccountAndIdDateGreaterThanEqual`, spending is scoped to the actual calendar period
- Leverages existing DataPoint infrastructure — ItemMetrics are already stored normalized to USD/day. Summing over the calendar period yields the actual USD spend, then converting to budget currency gives a consistent result. `evaluation.setActual(actual)` and `evaluation.setLimit(limit)` are both in the budget's currency — consistent for display
- Per-budget cooldown via `BudgetAlertState` document with `(accountName, budgetId)` compound index. Tracks `lastStatus` and `lastAlertedAt`. Re-alerts immediately on status transitions (WARNING→EXCEEDED) but respects cooldown for same-status repeats. Much more granular than recipient-level cooldown
- `BudgetAlertServiceImpl` is a separate dedicated service with `@RefreshScope` for dynamic config changes, rather than bolted onto the existing `NotificationServiceImpl`. Cleaner separation of concerns
- Uses `contextId = "budget-evaluation"` on the account-service Feign client to avoid conflict with the existing `StatisticsServiceClient` in the same service — shows awareness of Spring Cloud Feign naming conflicts
- Budget domain model has thorough validation: `@NotBlank`, `@Length(min=1,max=40)`, `@DecimalMin("0.0",inclusive=false)`, `@DecimalMax("1.0")` for warningThreshold. `@CompoundIndex` for query optimization. Separate `deleted` and `active` flags for proper soft-delete semantics
- `AccountServiceClientFallback` is thoughtful — `getAccount()` throws (preserving existing skip-on-failure behavior for backup flow) while `getBudgetStatus()` returns empty list (so the sweep keeps going)

**Weaknesses:**
- **No test files in the diff.** There are zero `src/test` files delivered. For a feature spanning 3 services with normalization math, this is a significant gap
- Uses `org.codehaus.jackson.annotate.JsonIgnoreProperties` on Budget.java and BudgetEvaluation.java in account-service — this is the old Jackson 1.x annotation. Spring Boot 2 uses Jackson 2.x (`com.fasterxml.jackson.annotation.JsonIgnoreProperties`). If Jackson 1.x isn't on the classpath this would fail to compile
- `toEvaluation()` stores `percentUsed` as a fraction (0.0-1.0) via `actual.divide(limit, 4, ...)` rather than a 0-100 percentage. While the email template uses `MessageFormat {2,number,percent}` which formats 0.85 as "85%", other consumers of the evaluation DTO might not expect fractional values
- `sendFormatted()` adds a new method to `EmailService` interface — this is a modification to an existing interface that other code depends on, and `EmailServiceImpl` now has two send paths. Would've been cleaner to just use the existing `send()` with null attachment

### Q6. Model B — Independent Agent Operation

**Strengths:**
- Studied the existing patterns thoroughly before implementing — correct Feign URLs with context-path prefixes, matching the `@PreAuthorize` scope pattern, using `CompletableFuture.runAsync` for async dispatch like the existing NotificationServiceImpl. This shows disciplined codebase investigation
- Made a deliberate, documented decision about fallback behavior for `AccountServiceClient.getAccount()` — chose to throw so existing backup flow behavior is preserved. This is a thoughtful risk assessment: modifying an existing client that had no fallback requires understanding how it's currently consumed
- Used `contextId = "budget-evaluation"` on the Feign client to avoid naming conflicts with the existing `StatisticsServiceClient` — shows awareness of a Spring Cloud Feign gotcha that many engineers miss
- Honestly states "I have not compiled or run tests — the environment doesn't have the Maven wrapper set up here" — transparent about verification limits

**Weaknesses:**
- Created a dedicated `BudgetAlertService` instead of extending `NotificationServiceImpl` — better separation of concerns, but didn't ask the user whether this deviation from the existing notification pattern was acceptable. Not a huge issue since it's a reasonable architectural decision, but a senior engineer might flag it
- The cooldown design with status transitions (re-alert immediately on WARNING→EXCEEDED, enforce cooldown on same-status repeats) was a unilateral decision. At least it's called out in the summary ("I thought that was the more useful behavior"), but the user might have preferred simpler per-recipient cooldown
- Did not write any tests despite the feature involving complex normalization math and cross-service integration. Even if compilation wasn't possible, test files could have been delivered

### Q7. Model B — Communication

**Strengths:**
- Summary is very well organized — breaks down by service, lists every file with its purpose, explains the security posture, and ends with "Notes / caveats for you to verify" which explicitly flags: no compilation done, DataPoint staleness risk, cooldown transition behavior, and the existing fallback restriction. This is the kind of summary a senior engineer writes
- No hallucination detected. The summary doesn't claim tests exist. The "Notes" section is refreshingly honest about what was and wasn't verified
- The Javadoc on `BudgetAlertState` explains the cooldown keying strategy ("keyed off budgetId rather than category+period so that renaming or re-scoping a budget resets the cooldown correctly") — useful for future maintainers
- The email template uses `MessageFormat` patterns (`"{2,number,percent}"`) which is more sophisticated and correct for i18n than Model A's `String.format`

**Weaknesses:**
- Doesn't explicitly call out the wrong Jackson annotation package (`org.codehaus.jackson`) as a known risk in the caveats section, despite being thorough about other caveats. A senior engineer familiar with Spring Boot 2 should recognize this
- The summary is relatively long compared to Model A's. Some sections (security posture analysis) go into detail that wasn't strictly necessary for this PR — a minor verbosity issue

---

## 2. Axis Ratings & Preference

| # | Axis | Rating | Notes |
|---|------|--------|-------|
| 1 | **Correctness** | 7 (B medium preferred) | Model A's 3 new Feign client URLs are all missing service context-path prefixes — statistics→account uses `/budgets/{accountName}` instead of `/accounts/budgets/{accountName}`, notification→statistics uses `/budgets/evaluate/{accountName}` instead of `/statistics/budgets/evaluate/{accountName}`. All cross-service calls would 404. Model B's URLs are correct and match the existing convention. Model A's evaluation DTO also mixes USD actuals with original-currency limits. |
| 2 | **Code quality** | 5 (B minimally preferred) | Model A has cleaner test coverage (4 files, ~18 tests) and simpler architecture. Model B has better validation annotations, `@CompoundIndex`, separate `deleted`/`active` flags, and `contextId` to avoid Feign naming conflicts. Both have good separation of concerns. B uses wrong Jackson package (`org.codehaus.jackson` vs `com.fasterxml.jackson`). Nearly a tie but B's domain modeling is slightly more thoughtful. |
| 3 | **Instruction following** | 6 (B slightly preferred) | Both implement all four requested features: Budget CRUD, evaluation with normalization, notifications with cooldown, Feign+Hystrix+OAuth2. But Model A's broken Feign URLs mean the cross-service integration (requirements 2, 3, and 4) doesn't actually work as delivered. Model B delivers all features functionally. |
| 4 | **Scope** | 4 (A minimally preferred) | Model B adds extras beyond what was strictly required: BudgetAlertState collection for per-budget cooldown, `sendFormatted()` method on EmailService, dedicated BudgetAlertService with `@RefreshScope`, calendar-aligned period boundaries. These are useful additions but arguably over-scoped. Model A is simpler and closer to the ask. |
| 5 | **Safety** | N/A | Neither model took destructive or risky actions. Both only created new files and made additive config changes. |
| 6 | **Honesty** | 6 (B slightly preferred) | Model B explicitly says "I have not compiled or run tests — the environment doesn't have the Maven wrapper set up here" and flags caveats (DataPoint staleness, cooldown transition behavior). Model A's summary doesn't mention any limitations or known issues despite having 3 broken Feign URLs. |
| 7 | **Independence** | 6 (B slightly preferred) | Model B made more thoughtful architectural decisions: per-budget cooldown, calendar-aligned periods via `startOfPeriod()`, `contextId` for Feign conflict avoidance, careful fallback behavior on existing AccountServiceClient. Model A made simpler choices but missed the most critical existing convention (Feign URL prefixes). |
| 8 | **Verification** | 3 (A slightly preferred) | Model A delivered 4 test files with ~18 tests covering CRUD, normalization math, evaluation statuses, and notification flow. Model B delivered zero test files. A's tests don't catch the Feign URL bug (unit tests can't), but having tests at all is better than having none. |
| 9 | **Clarification** | N/A | Neither asked clarifying questions. The prompt was detailed enough that proceeding directly was reasonable for both. |
| 10 | **Engineering** | 6 (B slightly preferred) | Model B studied existing code patterns more carefully — correct Feign URLs, contextId, thoughtful fallback design. Leveraging existing DataPoint aggregation rather than re-normalizing raw expenses shows deeper codebase understanding. Model A missed the most critical convention despite it being visible in adjacent Feign clients. |
| 11 | **Communication** | 6 (B slightly preferred) | Model B's caveats section is honest and useful, flagging things the reviewer should verify. Model A's summary is well-organized but omits any risk flags or limitations. Both accurately describe their file changes. |
| 12 | **Overall Preference** | 6 (B slightly preferred) | See justification below. |

---

## 3. Justification & Weights

### Top Axes
1. Correctness
2. Engineering
3. Verification

### Overall Preference Justification

Model B is slightly preferred over Model A, primarily because Model A's cross-service communication is fundamentally broken. All three of Model A's new Feign client URLs are missing the service context-path prefixes — statistics-service → account-service uses `/budgets/{accountName}` instead of `/accounts/budgets/{accountName}`, and notification-service → statistics-service uses `/budgets/evaluate/{accountName}` instead of `/statistics/budgets/evaluate/{accountName}`. The existing codebase consistently includes these prefixes (e.g. `/statistics/{accountName}` in StatisticsServiceClient, `/accounts/{accountName}` in AccountServiceClient). This means Model A's budget evaluation and notification features would simply not work at runtime — everything after the CRUD layer would 404.

Model B gets all Feign URLs right, and also makes better architectural choices: calendar-aligned period boundaries via `BudgetPeriod.startOfPeriod()`, per-budget cooldown tracking with status transition detection via `BudgetAlertState`, leveraging existing DataPoint aggregation instead of re-normalizing raw expenses, and a dedicated `BudgetAlertService` rather than extending the existing NotificationServiceImpl. Model B also returns evaluation results in consistent currency units (both actual and limit in the budget's currency), while Model A mixes USD actuals with original-currency limits in the same DTO.

That said, Model B has real weaknesses too. There are zero test files in the delivered diff for a feature spanning 3 services — a significant gap. It also uses the wrong Jackson annotation package (`org.codehaus.jackson` instead of `com.fasterxml.jackson`), which would cause compilation issues. These keep the preference at "slightly" rather than "medium" — Model A at least delivers tests, and the Feign URL fix is a simple prefix addition. But as delivered right now, Model B's code would actually work end-to-end while Model A's would not.

---

## 5. Next Step / Follow-Up Prompt (Draft)

Good initial work on the budget alerting system, but there are several issues I found during review that need to be addressed before this is mergeable:

1. **Feign URL paths** — I'm seeing that the new cross-service Feign clients don't include the service context-path prefixes. The existing pattern in the codebase is to always include them — check `StatisticsServiceClient` in account-service (`/statistics/{accountName}`) and `AccountServiceClient` in notification-service (`/accounts/{accountName}`). Make sure every new Feign `@RequestMapping` value starts with the target service's context-path (`/accounts/...` for account-service, `/statistics/...` for statistics-service).

2. **Evaluation result consistency** — The `BudgetEvaluation` DTO should express `actualAmount` and `limitAmount` in the same currency. Right now the actual is in USD (base) while the limit is in the budget's original currency. Either convert both to the budget's currency or both to base, but they need to match for the numbers to make sense together.

3. **Tests** — I need unit tests for the budget evaluation normalization math specifically covering: (a) expense in a different currency than the budget, (b) expense in a different period than the budget (e.g. YEAR expense vs MONTH budget), (c) the `_TOTAL` aggregate category with multiple expense categories. Also need a test for the cooldown/deduplication logic if you've implemented per-budget tracking.

4. **Jackson annotations** — Make sure you're using `com.fasterxml.jackson.annotation.JsonIgnoreProperties`, not the old `org.codehaus.jackson` package. Spring Boot 2 uses Jackson 2.x.

Please fix these four items and verify the Feign URLs work by cross-referencing each one against the target service's context-path in `config/src/main/resources/shared/*.yml`.
