# Task-2: Recurring Transactions Engine with Auto-Posting and Conflict Resolution

## Task ID
Task-02

## Type
Substantial New Feature

## Core Request (Turn 1)

### Title
Implement a Recurring Transactions Engine with Auto-Posting, Skipping, and Conflict Resolution

### Description

PiggyMetrics currently stores income and expense `Item` objects with a `TimePeriod` field (YEAR, QUARTER, MONTH, DAY, HOUR) that is used purely for normalization in the statistics service. There is **no mechanism for automatically creating actual transaction records on a schedule** based on these recurring items. Implement a full recurring transactions engine within the account-service that materializes scheduled items into concrete transaction records.

**Functional Requirements:**

1. **Transaction Domain Model:** Create a new `Transaction` entity stored in account-service's MongoDB:
   - `id` (auto-generated String)
   - `accountName` (String, indexed)
   - `type` (enum: `INCOME`, `EXPENSE`)
   - `title` (String — matches the source Item's title)
   - `amount` (BigDecimal)
   - `currency` (Currency enum)
   - `sourceItemTitle` (String — reference back to the recurring Item that generated this)
   - `status` (enum: `POSTED`, `SKIPPED`, `PENDING_REVIEW`)
   - `scheduledDate` (Date — the date this transaction was supposed to fire)
   - `postedDate` (Date — when it was actually posted, null if SKIPPED)
   - `note` (String, optional)

2. **Recurrence Schedule Engine:**
   - Add a new `RecurrenceConfig` embeddable to the existing `Item` domain:
     - `enabled` (boolean, default false — opt-in per item)
     - `anchorDate` (Date — the reference starting date for recurrence calculation)
     - `nextScheduledDate` (Date — computed: the next date this item should fire)
     - `autoPost` (boolean — if true, automatically mark as POSTED; if false, mark as PENDING_REVIEW)
   - Implement a `RecurrenceCalculator` utility class that, given an `Item`'s `TimePeriod` and `anchorDate`, computes the next N scheduled dates. Must handle:
     - Monthly recurrence on the 31st (rolls to last day of shorter months — e.g., Feb 28/29)
     - Quarterly recurrence (every 3 months from anchor)
     - Yearly recurrence
     - Daily and hourly recurrence

3. **Scheduled Auto-Posting Job:**
   - Implement a `@Scheduled` job in account-service (configurable cron via Spring Cloud Config) that:
     a. Scans all accounts for Items where `recurrenceConfig.enabled == true` and `nextScheduledDate <= now()`.
     b. For each eligible item, creates a `Transaction` record with the appropriate status.
     c. Advances the `nextScheduledDate` to the next occurrence.
     d. If the item has `autoPost == true`, the transaction is immediately `POSTED` and the account's `lastSeen` is updated.
     e. If `autoPost == false`, the transaction is `PENDING_REVIEW`.
   - After posting, trigger a statistics update via the existing `StatisticsServiceClient` Feign call.

4. **Transaction REST API:**
   - `GET /accounts/current/transactions` — list all transactions for the authenticated user. Support query params: `?status=POSTED&from=2024-01-01&to=2024-12-31&type=INCOME` (all optional, combinable).
   - `GET /accounts/current/transactions/{transactionId}` — get a single transaction.
   - `PUT /accounts/current/transactions/{transactionId}/approve` — change a PENDING_REVIEW transaction to POSTED (sets postedDate to now).
   - `PUT /accounts/current/transactions/{transactionId}/skip` — change a PENDING_REVIEW transaction to SKIPPED.
   - `DELETE /accounts/current/transactions/{transactionId}` — hard-delete a transaction (only allowed for PENDING_REVIEW status).

5. **Conflict Resolution:**
   - If the scheduled job runs and finds that the `nextScheduledDate` is more than one period behind (e.g., system was down for 3 days and daily tasks piled up), it must **batch-create all missed transactions** with their correct `scheduledDate` values, not just the latest one.
   - Each missed transaction should be marked `PENDING_REVIEW` regardless of the `autoPost` flag (to force the user to acknowledge the gap).

6. **Account Service Integration:**
   - When a user updates their account via `PUT /accounts/current` and modifies an Item's recurrence settings (enable/disable, change anchor date), the `nextScheduledDate` must be recomputed immediately.
   - Disabling recurrence on an item should NOT delete existing transactions — they are historical records.

7. **Configuration:**
   - Add `recurrence.job.cron` to `shared/account-service.yml` (default: `0 0 1 * * *` — 1 AM daily).
   - Add `recurrence.max-backfill-count` (default: 30) — cap on how many missed transactions to backfill per item per run (safety valve).

### Acceptance Criteria
- Users can enable/disable recurrence per income/expense item with an anchor date.
- The scheduled job correctly materializes transactions on their scheduled dates.
- Monthly recurrence on the 31st correctly handles Feb, Apr, Jun, Sep, Nov.
- Missed transactions (system downtime) are backfilled as PENDING_REVIEW up to the configured cap.
- Users can list, filter, approve, skip, and delete transactions.
- Statistics are updated after auto-posting.
- All new endpoints are secured with OAuth2.
- Recurrence computation is a clean, testable utility — not buried in the service layer.

---

## Expected PR Review Feedback (Turns 2 & 3)

### Turn 2 — Anticipated Flaws to Critique

1. **Month-end rollover bug:** The model will almost certainly mishandle the case where `anchorDate` is the 31st and the next month has fewer days. Expect it to either crash with an invalid date or silently skip February. Demand correct "last day of month" clamping logic using `java.time.LocalDate.withDayOfMonth` with `Math.min`.

2. **Backfill logic missing or flawed:** The "catch-up on missed dates" requirement is the hardest part. The model will likely either: (a) only create one transaction for the current date, ignoring missed ones, or (b) create an infinite loop if the anchor date is far in the past. Demand the `max-backfill-count` cap to be enforced and each backfilled transaction to have its correct historical `scheduledDate`.

3. **No pagination on transaction list:** `GET /accounts/current/transactions` will return all transactions unbounded. Demand Spring Data `Pageable` with sensible defaults (page=0, size=20) and proper sorting (by scheduledDate descending).

4. **Race condition in scheduled job:** If the cron job runs concurrently on multiple instances (no distributed lock), two instances could create duplicate transactions for the same item+date. Demand either a unique compound index on `(accountName, sourceItemTitle, scheduledDate)` or a distributed lock mechanism.

5. **Filter query built with string concatenation:** The model will likely implement the `GET /transactions` filtering by building a Mongo query via if/else string concatenation instead of using Spring Data's `Criteria` API or `@Query` with `SpEL`. Demand it use `MongoTemplate` with `Criteria` for type-safe query construction.

6. **`nextScheduledDate` not recomputed on Item update:** The model will add recurrence fields to `Item` but forget to recalculate `nextScheduledDate` when the user changes the item's `period` or `anchorDate` via `PUT /accounts/current`.

---

## My Analysis & Drafted Turn 1 Prompt

### Repo Understanding

PiggyMetrics is a Spring Boot / Spring Cloud microservices demo — account-service, statistics-service, notification-service, auth-service, gateway, config, registry. Each has its own MongoDB. Communication is via Feign + Eureka discovery, OAuth2 secured, Hystrix circuit breakers.

The **account-service** is the heart here. `Account` stores `List<Item> incomes` and `List<Item> expenses`. Each `Item` has a `TimePeriod` enum (YEAR, QUARTER, MONTH, DAY, HOUR) — but this is **only** used for normalization in the statistics-service. There's no mechanism that actually generates real transactions on a schedule from these items. That's the gap this task fills.

Key files the model will need to touch:
- `Item.java` — add RecurrenceConfig
- `AccountServiceImpl.java` — recompute nextScheduledDate on save
- `AccountApplication.java` — add @EnableScheduling
- `config/shared/account-service.yml` — add cron + backfill config
- New files: Transaction.java, TransactionStatus, TransactionType, RecurrenceConfig, RecurrenceCalculator, TransactionService, TransactionServiceImpl, TransactionController, TransactionRepository, plus tests

### Where Models Will Likely Struggle (My Opinions)

1. **Month-end clamping** — The anchor Jan 31 → Feb 28 → Mar 31 → Apr 30 pattern is easy to get wrong. Most models will naively use `plusMonths(1)` which loses the original anchor day after a clamped month (Jan 31 → Feb 28 → Mar 28 instead of Mar 31). The fix is to always reference the original `anchorDayOfMonth` and do `Math.min(anchorDay, month.lengthOfMonth())`.

2. **Backfill logic** — If the system was down for 3 days (daily recurrence), the model needs to generate 3 separate PENDING_REVIEW transactions with correct historical dates, not just one for today. It also needs a `maxBackfillCount` safety cap. Many models will either only create 1 transaction or potentially loop forever for anchor dates far in the past.

3. **Race conditions / duplicate prevention** — Without a unique compound index on `(accountName, sourceItemTitle, scheduledDate)`, concurrent cron runs could create duplicates. Models often forget this.

4. **Transaction filtering** — The `GET /transactions` endpoint needs combinable optional filters (status, type, date range). Models tend to either do `if/else` string concatenation for the Mongo query (brittle) or only support some filter combos. The gold standard uses Spring Data's derived query methods with all 8 combinations, which is verbose but type-safe.

5. **Pagination** — Models will often return `List<Transaction>` unbounded instead of using Spring Data `Page<Transaction>` with `Pageable`.

6. **Forgetting to recompute nextScheduledDate** — When a user updates their account (PUT /current), if they changed an Item's period or anchorDate, the model needs to recalculate `nextScheduledDate`. This is easy to overlook.

7. **No tests or weak tests** — Given the complexity, having unit tests for RecurrenceCalculator especially (month-end clamping edge cases, leap years) and TransactionService (backfill, approve/skip/delete, status guards) is important.

### Drafted Turn 1 Prompt

> Right now in the account-service, each Item (income or expense) has a `TimePeriod` (YEAR, QUARTER, MONTH, DAY, HOUR) that's only used for statistics normalization. But there's no way for these items to actually generate real transaction records on a recurring schedule.
>
> I want you to build a recurring transactions engine in the account-service. Here's what I need:
>
> - A new `Transaction` entity stored in MongoDB tracking things like the account name, type (income/expense), amount, currency, the source item that generated it, a status (posted, skipped, or pending review), the scheduled date, and the actual posted date.
> - Users should be able to opt-in to recurrence per item — they need an anchor date (when recurrence starts), and a flag for whether transactions should auto-post or land in a pending review state.
> - A scheduled job (cron configurable via Spring Cloud Config, default 1 AM daily) that scans all accounts, finds items due for recurrence, and creates the appropriate transactions. If autoPost is enabled, mark them as POSTED; otherwise PENDING_REVIEW.
> - If the system was down and missed scheduled dates, the job needs to backfill all the missed transactions (each with its correct historical scheduled date), but cap it at a configurable max (default 30). Backfilled transactions should always be PENDING_REVIEW regardless of the autoPost flag.
> - A REST API under the current account's transactions endpoint: list with filtering (status, type, date range) and pagination, get by id, approve (pending → posted), skip (pending → skipped), and delete (only pending).
> - Monthly recurrence anchored on the 31st needs to handle shorter months correctly (e.g., Jan 31 → Feb 28/29 → Mar 31 → Apr 30).
> - When a user updates their account and changes an item's recurrence settings, the next scheduled date needs to be recomputed right away.
> - After auto-posting, trigger a statistics update via the existing StatisticsServiceClient.
> - Add appropriate tests.

### Notes for You (the tasker)

- This prompt is pretty detailed already. You could trim it if you want to leave more surface area for model divergence — for instance, you could leave out the "backfill should always be PENDING_REVIEW" rule and see if models think of that themselves.
- You could also omit the month-end clamping requirement to see if models handle it correctly on their own (they probably won't — which becomes Turn 2 material).
- Don't mention class names like `RecurrenceCalculator` or `RecurrenceConfig` — let the models pick their own names.
- Feel free to make it shorter / more conversational. The key functional requirements are: (1) Transaction entity, (2) opt-in recurrence per item with anchor date, (3) scheduled job with backfill + cap, (4) REST API with filtering/pagination, (5) month-end clamping, (6) recompute on item update, (7) stats update after posting, (8) tests.

7. **Statistics update not triggered after batch backfill:** The model will trigger statistics update per-transaction during backfill rather than once at the end, causing N redundant Feign calls. Demand a single aggregate statistics update after all backfills for an account are complete.

### Turn 3 — Tests, Linting, and Polish

1. **Unit tests for `RecurrenceCalculator`:** Test all TimePeriod values, month-end edge cases (Jan 31 → Feb 28 → Mar 31), leap year (Feb 29), anchor date in the future, anchor date far in the past.
2. **Unit tests for backfill logic:** Verify correct count of backfilled transactions, proper status assignment (PENDING_REVIEW), max-backfill-count enforcement, correct historical scheduledDates.
3. **Controller tests for transaction CRUD:** MockMvc tests for list (with filters), get by ID (valid, 404), approve (valid, already-posted → 409), skip, delete (valid, not-pending → 403).
4. **Service test for the scheduled job:** Mock repositories and Feign clients, verify correct transaction creation and nextScheduledDate advancement.
5. **Ensure `@Indexed` on `accountName` + composite unique index on `(accountName, sourceItemTitle, scheduledDate)` in the Transaction document.**
6. **Remove any debug logging or `System.out.println` calls left from development.**

---

## Why It Fits the Constraint

- **~550–650 lines of new core code:**
  - `Transaction.java` domain + enums (~60 lines)
  - `RecurrenceConfig.java` embeddable (~30 lines)
  - `TransactionRepository.java` (~30 lines)
  - `RecurrenceCalculator.java` utility (~80 lines)
  - `TransactionService` interface + impl (~150 lines)
  - `RecurrenceJobService` scheduled job (~100 lines)
  - `TransactionController.java` REST endpoints (~80 lines)
  - Modifications to `Item.java`, `AccountServiceImpl.java`, `AccountController.java` (~60 lines)
  - Config updates (~20 lines)

- **High difficulty:** Date/calendar math with month-end edge cases is notoriously bug-prone. The backfill logic with a safety cap adds complexity. Filtering with multiple optional query parameters requires careful query building. The race condition scenario forces architectural thinking about idempotency.

- **Natural multi-turn material:** Month-end rollover is a classic bug magnet, backfill is easy to get wrong, pagination is always forgotten first pass, and the race condition/duplicate prevention is something models rarely consider proactively.

---

## Potential Files Modified

> At least 6 core files, excluding tests:

| # | File Path | Change |
|---|-----------|--------|
| 1 | `account-service/src/main/java/com/piggymetrics/account/domain/Transaction.java` | **New** — Transaction entity |
| 2 | `account-service/src/main/java/com/piggymetrics/account/domain/RecurrenceConfig.java` | **New** — Embeddable recurrence settings |
| 3 | `account-service/src/main/java/com/piggymetrics/account/domain/Item.java` | Add `RecurrenceConfig` field |
| 4 | `account-service/src/main/java/com/piggymetrics/account/repository/TransactionRepository.java` | **New** — Mongo repository with custom queries |
| 5 | `account-service/src/main/java/com/piggymetrics/account/service/RecurrenceCalculator.java` | **New** — Date computation utility |
| 6 | `account-service/src/main/java/com/piggymetrics/account/service/TransactionService.java` | **New** — Transaction service interface |
| 7 | `account-service/src/main/java/com/piggymetrics/account/service/TransactionServiceImpl.java` | **New** — Transaction business logic + scheduled job |
| 8 | `account-service/src/main/java/com/piggymetrics/account/controller/TransactionController.java` | **New** — REST endpoints for transactions |
| 9 | `account-service/src/main/java/com/piggymetrics/account/service/AccountServiceImpl.java` | Recompute nextScheduledDate on item update |
| 10 | `config/src/main/resources/shared/account-service.yml` | Add recurrence config properties |

---

## Reference Implementation — PR Overview

### Summary

Implements a complete Recurring Transactions Engine within account-service. Items can now have recurrence enabled with an anchor date; a scheduled job materializes concrete `Transaction` records in MongoDB on each period. Users interact with transactions via new REST endpoints for listing (with pagination and filtering), approval, skipping, and deletion.

### Files Changed (14 files)

#### New Files (11)

| # | File | Lines | Purpose |
|---|------|-------|---------|
| 1 | `account-service/.../domain/RecurrenceConfig.java` | ~48 | Embeddable recurrence settings (enabled, anchorDate, nextScheduledDate, autoPost) |
| 2 | `account-service/.../domain/TransactionType.java` | ~6 | Enum: INCOME, EXPENSE |
| 3 | `account-service/.../domain/TransactionStatus.java` | ~6 | Enum: POSTED, SKIPPED, PENDING_REVIEW |
| 4 | `account-service/.../domain/Transaction.java` | ~120 | MongoDB document with @CompoundIndex for duplicate prevention |
| 5 | `account-service/.../repository/TransactionRepository.java` | ~45 | MongoRepository with 10 finder methods for all filter combinations |
| 6 | `account-service/.../service/RecurrenceCalculator.java` | ~145 | Stateless date arithmetic utility with month-end clamping |
| 7 | `account-service/.../service/TransactionService.java` | ~25 | Service interface |
| 8 | `account-service/.../service/TransactionServiceImpl.java` | ~255 | Core business logic: scheduled job, CRUD, backfill with DuplicateKey handling |
| 9 | `account-service/.../controller/TransactionController.java` | ~55 | REST endpoints: list, get, approve, skip, delete |
| 10 | `account-service/.../service/RecurrenceCalculatorTest.java` | ~195 | 16 unit tests covering all periods, month-end clamping, leap year, nulls |
| 11 | `account-service/.../service/TransactionServiceImplTest.java` | ~260 | 12 unit tests for CRUD, scheduled job, backfill, duplicate handling |
| 12 | `account-service/.../controller/TransactionControllerTest.java` | ~175 | 8 MockMvc tests for all endpoints with filters and pagination |

#### Modified Files (3)

| # | File | Change |
|---|------|--------|
| 1 | `account-service/.../domain/Item.java` | Added `RecurrenceConfig recurrenceConfig` field + getter/setter |
| 2 | `account-service/.../service/AccountServiceImpl.java` | Added `recomputeRecurrenceSchedules()` call in `saveChanges()` — recomputes nextScheduledDate when recurrence settings change |
| 3 | `account-service/.../AccountApplication.java` | Added `@EnableScheduling` annotation |

#### Config Files (1)

| # | File | Change |
|---|------|--------|
| 1 | `config/.../shared/account-service.yml` | Added `recurrence.job.cron` (1 AM daily) and `recurrence.max-backfill-count` (30) |

#### Updated Test Files (1)

| # | File | Change |
|---|------|--------|
| 1 | `account-service/.../service/AccountServiceTest.java` | 2 new tests: recurrence recomputation on save, skip when disabled |

### Architecture Decisions

1. **Month-end clamping**: `RecurrenceCalculator` stores the original anchor day-of-month and applies `Math.min(anchorDay, targetMonth.lengthOfMonth())` on each advance. This means Jan 31 → Feb 28 → Mar 31 → Apr 30 — the anchor day is always restored when the target month supports it.

2. **Duplicate prevention**: `@CompoundIndex(unique=true)` on `(accountName, sourceItemTitle, scheduledDate, type)` ensures idempotency at the DB level. The scheduled job catches `DuplicateKeyException` and logs a warning rather than failing.

3. **Backfill safety**: Missed transactions are capped at `recurrence.max-backfill-count` (default 30) per item per run. All backfilled transactions are forced to `PENDING_REVIEW` regardless of the item's `autoPost` setting, requiring user acknowledgment.

4. **Single statistics update per account**: The scheduled job calls `statisticsClient.updateStatistics()` once per account after processing all items, not per-transaction.

5. **Pagination**: Transaction list uses Spring Data `Page<Transaction>` with `PageRequest.of(page, size, Sort.by(DESC, "scheduledDate"))`, defaulting to page=0, size=20.

6. **Query building**: Uses Spring Data derived query methods (8 variations) rather than string concatenation or raw Mongo queries, with an if/else cascade in the service layer to select the correct method based on which filters are provided.

7. **Recurrence recomputation on save**: `AccountServiceImpl.saveChanges()` calls `recomputeRecurrenceSchedules()` before persisting, which iterates all items and recomputes `nextScheduledDate` via `RecurrenceCalculator.computeNextDate()` for any item with enabled recurrence.

### Test Coverage

- **RecurrenceCalculatorTest**: All 5 TimePeriod values, month-end clamping (Jan 31 → Feb 28/29 → Mar 31 → Apr 30), leap year (Feb 29 → Feb 28 in non-leap), anchor far in past, null/invalid inputs, hourly recurrence.
- **TransactionServiceImplTest**: CRUD operations (approve, skip, delete with status validation), filter queries, scheduled job (enabled/disabled recurrence, autoPost on/off, duplicate key handling, cross-account error isolation, nextScheduledDate advancement).
- **TransactionControllerTest**: MockMvc tests for all 5 endpoints with various query parameters.
- **AccountServiceTest**: Recurrence recomputation on save, no recomputation when disabled.
