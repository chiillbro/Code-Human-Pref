# Task-1: Budget Alerting and Threshold Management System

## Task ID
Task-01

## Type
Substantial New Feature

## Core Request (Turn 1)

### Title
Implement a Budget Alerting and Threshold Management System for Account Service

### Description

PiggyMetrics currently tracks income and expenses but provides **no way for users to set spending limits or receive alerts when they exceed budget thresholds**. Implement a complete budget alerting system that spans the account-service, notification-service, and statistics-service.

**Functional Requirements:**

1. **Budget Domain Model:** Create a new `Budget` domain entity stored in the account-service's MongoDB. Each account can define multiple budgets, each consisting of:
   - A `category` (string, matching expense item titles — e.g., "Rent", "Groceries", or a special `"_TOTAL"` for aggregate budgets)
   - A `limit` (BigDecimal, in the account's preferred currency)
   - A `period` (reuse `TimePeriod` enum — MONTH, QUARTER, YEAR)
   - A `thresholdPercent` (int, 1–100, default 80) — the percentage at which a warning alert fires
   - `active` (boolean)
   - `createdDate` and `lastTriggeredDate` (Date)

2. **Budget CRUD API on Account Service:**
   - `POST /accounts/current/budgets` — create a new budget for the authenticated user. Validate: no duplicate category+period combos, limit > 0, thresholdPercent between 1 and 100.
   - `GET /accounts/current/budgets` — list all budgets for the authenticated user.
   - `PUT /accounts/current/budgets/{budgetId}` — update an existing budget.
   - `DELETE /accounts/current/budgets/{budgetId}` — soft-delete (set `active = false`).

3. **Budget Evaluation Logic in Statistics Service:**
   - Add a new endpoint `PUT /statistics/{accountName}/evaluate-budgets` callable by the account-service (server scope).
   - This endpoint receives the account's budgets and the current account data, normalizes the expenses to the budget's currency and period using the existing `ExchangeRatesService`, and calculates the percentage spent against each budget's limit.
   - Return a list of `BudgetStatus` objects: `{ budgetId, category, limit, spent, percentUsed, status }` where status is one of `UNDER_BUDGET`, `WARNING` (>= thresholdPercent), `EXCEEDED` (>= 100%).

4. **Budget Alert Notifications:**
   - Extend the notification-service's `NotificationType` enum with a new `BUDGET_ALERT` type.
   - Add a new Feign client method in notification-service to fetch budget statuses from the statistics-service.
   - Implement a new scheduled job (`@Scheduled`, configurable cron) that:
     a. For each recipient with budget alerts enabled, fetches their budget statuses.
     b. Sends an email listing all budgets in `WARNING` or `EXCEEDED` state.
     c. Does NOT re-send the same alert within a configurable cooldown period (e.g., once per day per budget).
   - Extend the `NotificationSettings` / `Recipient` model to include budget alert preferences (active, frequency).

5. **Feign Client Updates:**
   - Account-service needs a new Feign client call to statistics-service's budget evaluation endpoint.
   - Notification-service needs a new Feign client call to statistics-service to fetch budget statuses for a given account.
   - Both must have Hystrix fallbacks.

6. **Configuration:**
   - Budget alert cron schedule must be configurable via Spring Cloud Config (`shared/notification-service.yml`).
   - Add `budget.alert.cooldown.hours` property (default: 24).

### Acceptance Criteria
- A user can create, list, update, and soft-delete budgets via the REST API.
- When a user saves their account (existing `PUT /accounts/current`), account-service triggers a budget evaluation by calling statistics-service.
- Statistics-service correctly normalizes expenses across currencies and periods when calculating budget usage.
- Notification-service sends budget alert emails on schedule for WARNING and EXCEEDED budgets.
- Duplicate alerts within the cooldown window are suppressed.
- All new endpoints are properly secured with OAuth2 (user-authenticated or server-scope as appropriate).
- Hystrix fallbacks are in place for all new inter-service calls.

---

## Expected PR Review Feedback (Turns 2 & 3)

### Turn 2 — Anticipated Flaws to Critique

1. **Currency normalization bugs:** The model will likely hardcode or skip currency conversion when comparing expense amounts against budget limits. The budget limit is in the user's preferred currency, but expenses may be in EUR/RUB/USD — the evaluation logic must normalize all expenses to the budget's currency using live exchange rates. Expect the model to either skip this or do it incorrectly (e.g., dividing instead of multiplying, or not handling the case where the budget currency differs from the account's base currency).

2. **Period normalization errors:** Budgets can be MONTH/QUARTER/YEAR, but individual expenses have their own TimePeriod. The model will likely forget to annualize or de-annualize correctly — e.g., a $1200/year salary compared against a monthly budget needs to be divided by 12, not compared raw.

3. **Missing `_TOTAL` aggregate budget handling:** The `_TOTAL` category requires summing ALL expenses, not filtering by category. The model will probably implement only category-specific budgets and skip or break the aggregate case.

4. **No duplicate budget validation:** The model will likely skip or weakly implement the constraint that a user cannot have two active budgets for the same category + period combination.

5. **Cooldown logic flaw:** The "don't re-send within X hours" logic will either be missing entirely or implemented with a race condition (checking lastTriggeredDate but not atomically updating it, leading to duplicate sends in concurrent scheduled runs).

6. **Monolithic evaluate method:** The budget evaluation logic in statistics-service will likely be a single 80+ line method mixing normalization, aggregation, and status classification. Demand it be decomposed into focused private methods.

7. **No Hystrix fallback for budget evaluation Feign call:** The model will add the Feign client but forget to wire up a fallback class, or the fallback will silently swallow the error without logging.

### Turn 3 — Tests, Linting, and Polish

1. **Unit tests for `BudgetEvaluationService`:** Must cover: single-category budget under/warning/exceeded, `_TOTAL` aggregate budget, multiple currencies, multiple periods, empty expenses list, all expenses filtered out (no match for category).
2. **Unit tests for cooldown suppression logic:** Verify that alerts within the cooldown window are skipped and that alerts after the window are sent.
3. **Controller tests for budget CRUD:** MockMvc tests for create (valid, invalid — missing fields, duplicate category+period, negative limit), list, update, delete.
4. **Integration test for the scheduled budget alert job:** Mock the Feign clients, verify correct emails are sent for a mix of WARNING/EXCEEDED/UNDER_BUDGET scenarios.
5. **Ensure proper `@Valid` / `@NotNull` annotations** on the Budget request DTO.
6. **Error handling:** Proper HTTP 404 when updating/deleting a non-existent budgetId, 409 for duplicate budget conflicts.

---

## Why It Fits the Constraint

- **~550–650 lines of new core code:**
  - `Budget.java` domain model (~50 lines)
  - `BudgetStatus.java` DTO (~40 lines)
  - `BudgetRepository.java` (~20 lines)
  - Budget CRUD in `AccountController` + `AccountService` + `AccountServiceImpl` (~120 lines)
  - `BudgetEvaluationService` + impl in statistics-service (~150 lines)
  - New statistics-service controller endpoint + DTOs (~50 lines)
  - Notification-service `BUDGET_ALERT` type + scheduled job + Feign client (~120 lines)
  - Config updates, Hystrix fallbacks, Feign interfaces (~60 lines)

- **High difficulty:** Requires correct cross-service communication, multi-currency/multi-period normalization math, scheduled job with cooldown logic, and proper OAuth2 scope management across 3 services. No single model will get the normalization math, cooldown deduplication, and aggregate budget calculation all correct in one pass.

- **Natural multi-turn material:** Currency/period normalization is mathematically finicky (easy to get wrong), the `_TOTAL` case is an edge case models routinely miss, and cooldown deduplication requires careful state management — all of which provide ample critique material without scope creep.

---

## Potential Files Modified

> At least 6 core files, excluding tests:

| # | File Path | Change |
|---|-----------|--------|
| 1 | `account-service/src/main/java/com/piggymetrics/account/domain/Budget.java` | **New** — Budget domain model |
| 2 | `account-service/src/main/java/com/piggymetrics/account/domain/Account.java` | Add `List<Budget> budgets` field |
| 3 | `account-service/src/main/java/com/piggymetrics/account/controller/AccountController.java` | Add budget CRUD endpoints |
| 4 | `account-service/src/main/java/com/piggymetrics/account/service/AccountServiceImpl.java` | Add budget management logic + trigger evaluation |
| 5 | `statistics-service/src/main/java/com/piggymetrics/statistics/domain/BudgetStatus.java` | **New** — evaluation result DTO |
| 6 | `statistics-service/src/main/java/com/piggymetrics/statistics/service/BudgetEvaluationService.java` | **New** — budget evaluation interface |
| 7 | `statistics-service/src/main/java/com/piggymetrics/statistics/service/BudgetEvaluationServiceImpl.java` | **New** — normalization + evaluation logic |
| 8 | `statistics-service/src/main/java/com/piggymetrics/statistics/controller/StatisticsController.java` | Add evaluate-budgets endpoint |
| 9 | `notification-service/src/main/java/com/piggymetrics/notification/domain/NotificationType.java` | Add `BUDGET_ALERT` |
| 10 | `notification-service/src/main/java/com/piggymetrics/notification/service/NotificationServiceImpl.java` | Add scheduled budget alert job |
| 11 | `config/src/main/resources/shared/notification-service.yml` | Add budget alert cron + cooldown config |

---

## Reference Implementation — PR Overview

### Summary

This PR implements a complete Budget Alerting and Threshold Management System spanning three microservices: account-service, statistics-service, and notification-service. Users can define per-category spending budgets with configurable thresholds, and the system evaluates budget usage with full multi-currency/multi-period normalization and sends email alerts for WARNING and EXCEEDED budgets on a configurable schedule.

### Files Changed

#### account-service (9 files)

| File | Status | Description |
|------|--------|-------------|
| `domain/Budget.java` | **New** | Budget domain entity with validation annotations. Fields: budgetId, category, limit, period, currency, thresholdPercent (default 80), active, createdDate, lastTriggeredDate. |
| `domain/BudgetEvaluationRequest.java` | **New** | Request DTO wrapping `List<Budget>` + `Account` for the statistics-service evaluate-budgets call. |
| `domain/BudgetStatus.java` | **New** | Response DTO for budget evaluation results. Includes `isAlertWorthy()` convenience method. Status stored as String for cross-service decoupling. |
| `domain/Account.java` | **Modified** | Added `@Valid List<Budget> budgets` field with getter/setter. |
| `service/AccountService.java` | **Modified** | Added 5 methods: `createBudget`, `getBudgets`, `updateBudget`, `deleteBudget`, `evaluateBudgets`. |
| `service/AccountServiceImpl.java` | **Modified** | Full budget CRUD implementation with UUID generation, duplicate detection (case-insensitive category + period), field validation, soft-delete. `evaluateBudgets()` loads account, filters active budgets, calls statistics-service via Feign, returns results. |
| `controller/AccountController.java` | **Modified** | 5 new endpoints: POST/GET/PUT/DELETE for `/current/budgets` (user-auth) + GET `/{accountName}/budget-evaluation` (server-scoped). |
| `client/StatisticsServiceClient.java` | **Modified** | Added `evaluateBudgets(accountName, request)` Feign method targeting `PUT /statistics/{accountName}/evaluate-budgets`. |
| `client/StatisticsServiceClientFallback.java` | **Modified** | Added fallback returning empty list with error logging. |

#### statistics-service (6 files)

| File | Status | Description |
|------|--------|-------------|
| `domain/BudgetItem.java` | **New** | DTO for budget data received from account-service. |
| `domain/BudgetEvaluationRequest.java` | **New** | Request body DTO with `@Valid @NotNull` annotations. |
| `domain/BudgetStatus.java` | **New** | Evaluation result with `BudgetStatusType` enum (UNDER_BUDGET, WARNING, EXCEEDED). |
| `service/BudgetEvaluationService.java` | **New** | Interface with `evaluate(budgets, account)` method. |
| `service/BudgetEvaluationServiceImpl.java` | **New** | Core evaluation engine (~130 lines). Well-decomposed into single-responsibility private methods: `evaluateSingleBudget`, `computeSpentAmount`, `filterExpenses` (handles `_TOTAL`), `normalizeExpense` (2-step: currency then period), `convertCurrency` (amount x toRate/fromRate), `convertPeriod` (amount x toPeriod.baseRatio/fromPeriod.baseRatio), `computePercentUsed` (zero-limit edge case), `classifyStatus`. Uses SCALE=4, HALF_UP rounding. |
| `controller/StatisticsController.java` | **Modified** | Added `PUT /{accountName}/evaluate-budgets` endpoint with server scope. |

#### notification-service (6 files)

| File | Status | Description |
|------|--------|-------------|
| `domain/NotificationType.java` | **Modified** | Added `BUDGET_ALERT("budget.alert.email.subject", "budget.alert.email.text", null)`. |
| `domain/BudgetAlertStatus.java` | **New** | DTO with `isAlertWorthy()` filtering method (true for WARNING/EXCEEDED). |
| `repository/RecipientRepository.java` | **Modified** | Added `findReadyForBudgetAlert()` MongoDB query matching existing BACKUP/REMIND pattern. |
| `service/RecipientServiceImpl.java` | **Modified** | Added `BUDGET_ALERT` case to `findReadyToNotify()` switch. |
| `service/NotificationService.java` | **Modified** | Added `sendBudgetAlertNotifications()` to interface. |
| `service/NotificationServiceImpl.java` | **Modified** | New `@Scheduled(cron="${budget.alert.cron}")` method. For each ready recipient: calls account-service's `evaluateBudgets()` endpoint, filters alert-worthy results, builds human-readable summary, sends email, marks notified. Follows existing async `CompletableFuture` pattern. |
| `client/AccountServiceClient.java` | **Modified** | Added `evaluateBudgets(accountName)` Feign method targeting `GET /accounts/{accountName}/budget-evaluation`. |

#### config (1 file)

| File | Status | Description |
|------|--------|-------------|
| `shared/notification-service.yml` | **Modified** | Added `budget.alert.cron: 0 0 */6 * * *`, `budget.alert.email.subject`, `budget.alert.email.text`. |

#### Tests (4 files)

| File | Status | Description |
|------|--------|-------------|
| `statistics-service/.../BudgetEvaluationServiceImplTest.java` | **New** | 12 tests covering: under/warning/exceeded thresholds, cross-currency conversion, cross-period normalization, `_TOTAL` aggregation, empty/null expenses, multiple budgets, case-insensitive category matching, zero-limit edge case, null-argument validation. |
| `account-service/.../AccountServiceTest.java` | **Modified** | 9 new tests: budget CRUD (create, duplicate rejection, get active only, update, soft-delete, delete nonexistent), evaluateBudgets (calls statistics-service, returns empty for no budgets), validation (null category, negative limit). |
| `statistics-service/.../StatisticsControllerTest.java` | **Modified** | 1 new test: MockMvc test for `PUT /{accountName}/evaluate-budgets` endpoint. Added `@Mock BudgetEvaluationService`. |
| `notification-service/.../NotificationServiceImplTest.java` | **Modified** | 4 new tests: sends alert on WARNING/EXCEEDED, skips when all UNDER_BUDGET, handles errors gracefully across recipients, skips when no budgets exist. |

### Architecture Decisions

1. **Budget evaluation orchestrated via account-service**: Account-service exposes a server-scoped `GET /{accountName}/budget-evaluation` endpoint that internally fetches the account, builds the evaluation request, and delegates to statistics-service. This keeps notification-service simple (single Feign call) and encapsulates the account-to-statistics orchestration.

2. **Soft-delete for budgets**: Budgets are deactivated (`active = false`) rather than removed, preserving history and allowing restoration. All queries filter by `active = true`.

3. **BudgetStatus decoupled via String**: Account-service and notification-service receive status as a String ("WARNING", "EXCEEDED", etc.) rather than importing statistics-service's enum, maintaining cross-service independence.

4. **Existing patterns preserved**: All new code follows established conventions — JUnit 4 + Mockito testing, `@Scheduled` with cron from config, `CompletableFuture.runAsync` for async email sending, `@PreAuthorize` for scope-based authorization, Hystrix fallbacks with error logging.

### Total Line Count
~750 lines of new/modified production code + ~500 lines of test code across 3 services.

---

## Copilot's Drafted Prompt (Turn 1)

PiggyMetrics tracks incomes and expenses per account, but there's no way for users to set spending budgets or get alerted when they're overspending. I want to add a **budget alerting system** that works across account-service, statistics-service, and notification-service.

Here's what I need:

1. **Budget model & CRUD** — Users should be able to create, list, update, and soft-delete budgets on their account. Each budget targets a specific expense category (matching expense item titles like "Rent", "Groceries") or a special `_TOTAL` category that tracks aggregate spending. A budget has a spending limit (in a specific currency), a time period (reuse the existing `TimePeriod` — MONTH, QUARTER, YEAR), and a configurable warning threshold percent (default 80%). Don't allow duplicate active budgets for the same category + period combination.

2. **Budget evaluation** — When evaluating budgets, the system needs to figure out how much the user has actually spent against each budget. This is tricky because expenses can be in different currencies and different time periods than the budget. The evaluation needs to normalize everything properly — convert currencies using the existing exchange rate infrastructure, and convert periods so that e.g. a yearly expense gets compared fairly against a monthly budget. Return a status for each budget: `UNDER_BUDGET`, `WARNING` (hit the threshold %), or `EXCEEDED` (>=100%). The evaluation logic belongs in statistics-service since that's where the normalization expertise already lives.

3. **Budget alert notifications** — Extend the notification service to send email alerts for budgets in WARNING or EXCEEDED state. Follow the same patterns as the existing BACKUP and REMIND notification types — configurable cron schedule, recipient readiness check, async email sending. Don't re-alert within a cooldown window.

4. **Cross-service wiring** — The services need to talk to each other via Feign clients with proper Hystrix fallbacks. New endpoints need appropriate OAuth2 security (user-auth for budget CRUD, server scope for inter-service calls).

Please implement this feature with tests.

---

## Copilot's Opinions & Analysis

**Why this task is good:**
- Spans 3 microservices — forces the model to understand the inter-service communication patterns (Feign, Hystrix, OAuth2 scopes) that are central to this codebase.
- The currency + period normalization math is genuinely tricky. The `TimePeriod` enum has `getBaseRatio()` that normalizes to DAY, and `ExchangeRatesService` has `getCurrentRates()` returning rates relative to USD. Getting the conversion formula right (multiply vs divide, which ratio goes where) is easy to mess up.
- The `_TOTAL` aggregate category is a subtle edge case most models will miss or implement incorrectly.
- The notification-service integration requires following a well-established pattern (look at how BACKUP and REMIND work) — good test of whether the model reads existing code before writing new code.

**Where I expect models to struggle (Turn 2+ critique material):**
1. **Currency conversion direction** — The rates map stores values relative to USD (EUR=0.8, RUB=80). To convert from EUR to USD: `amount * (USD_rate / EUR_rate)` = `amount * (1 / 0.8)`. Models often get this backwards.
2. **Period normalization** — To convert `$1200/YEAR` to monthly: `1200 * (MONTH.baseRatio / YEAR.baseRatio)` = `1200 * (30.4368/365.2425)` ≈ `$100/month`. Models tend to either skip this entirely or invert the division.
3. **`_TOTAL` category handling** — If category is `_TOTAL`, ALL expenses should be summed. Models will likely only handle per-category filtering.
4. **Duplicate budget validation** — The constraint (no two active budgets for same category+period) is easy to skip.
5. **Cooldown / deduplication** — The "don't re-alert within cooldown" logic needs careful state management. Models might miss implementing `lastTriggeredDate` tracking.
6. **Monolithic evaluation method** — The evaluation logic should be decomposed into focused helpers (filterExpenses, normalizeExpense, convertCurrency, convertPeriod, classifyStatus). Models tend to dump everything into one big method.
7. **Hystrix fallback for the new Feign clients** — Models often add the Feign interface but forget the fallback class.

**Prompt quality notes:**
- The prompt defines the *what* without prescribing *how* — no mention of specific class names, file locations, or implementation approaches.
- Leaves room for divergent approaches (e.g., where to put the evaluation orchestration, how to structure the DTOs, whether to embed budgets in Account or create a separate collection).
- Does NOT tell the model to "make it production ready" or steer toward any quality level.
- The cross-service normalization math provides natural, non-contrived difficulty.





