# Task-3: Multi-Currency Account Wallet with Real-Time Conversion and Historical Tracking

## Task ID
Task-03

## Type
Complex Refactor + Substantial New Feature

## Core Request (Turn 1)

### Title
Refactor Account Service to Support Multi-Currency Wallets with Real-Time Conversion, Balance Aggregation, and Historical Rate Tracking

### Description

PiggyMetrics currently allows each `Item` (income/expense) to have its own `Currency` field, but the `Saving` object and the overall account have no concept of a **wallet** — a unified view of balances across multiple currencies. The statistics-service normalizes everything to a base currency for datapoints, but the account-service itself has no balance tracking, no real-time conversion, and no way to see "How much do I have in total, in my preferred display currency?" Implement a multi-currency wallet system within account-service, backed by exchange rates fetched from statistics-service.

**Functional Requirements:**

1. **Wallet Domain Model:** Create a `Wallet` embedded document within `Account`:
   - `baseCurrency` (Currency enum — the user's preferred display currency, default USD)
   - `List<CurrencyBalance> balances` — one entry per currency the user has transactions in:
     - `currency` (Currency)
     - `nativeBalance` (BigDecimal — sum of incomes minus expenses in this currency, raw)
     - `convertedBalance` (BigDecimal — nativeBalance converted to `baseCurrency` at current rates)
     - `lastUpdated` (Date)
   - `totalBalance` (BigDecimal — sum of all `convertedBalance` values)
   - `totalIncome` (BigDecimal — sum of all incomes, converted to baseCurrency)
   - `totalExpenses` (BigDecimal — sum of all expenses, converted to baseCurrency)
   - `netSavingsRate` (BigDecimal — `(totalIncome - totalExpenses) / totalIncome * 100`, or 0 if no income)

2. **Wallet Recalculation Service:**
   - Create a `WalletService` (interface + impl) in account-service that:
     a. Accepts an `Account` object.
     b. Fetches current exchange rates from statistics-service via a new Feign client method (reuse the existing `ExchangeRatesClient` pattern — the statistics-service already has rates cached).
     c. Groups all income/expense items by currency.
     d. For each currency, sums incomes and subtracts expenses, applying `TimePeriod` normalization to a **monthly** basis (so a $12,000/year salary becomes $1,000/month, and a $50/day expense becomes ~$1,522/month).
     e. Converts each currency's monthly balance to `baseCurrency` using the fetched rates.
     f. Computes `totalBalance`, `totalIncome`, `totalExpenses`, `netSavingsRate`.
   - The wallet must be recalculated:
     - Whenever the user calls `PUT /accounts/current` (save changes).
     - On demand via a new `POST /accounts/current/wallet/refresh` endpoint.

3. **Exchange Rates Proxy Endpoint in Statistics Service:**
   - Add `GET /statistics/rates` (server scope) that returns the currently cached exchange rates map (`Map<Currency, BigDecimal>`) from the existing `ExchangeRatesServiceImpl`. This avoids account-service calling the external API directly — it goes through the statistics-service which already caches rates.

4. **New Feign Client in Account Service:**
   - Create `ExchangeRatesServiceClient` Feign interface in account-service targeting statistics-service's new `/statistics/rates` endpoint.
   - Include a Hystrix fallback that returns hardcoded fallback rates (1.0 for USD, approximate rates for EUR/RUB) so wallet calculations degrade gracefully instead of failing.

5. **Wallet REST API:**
   - `GET /accounts/current/wallet` — returns the current wallet summary (balances, totals, savings rate).
   - `POST /accounts/current/wallet/refresh` — forces a recalculation with fresh exchange rates.
   - `PUT /accounts/current/wallet/base-currency` — changes the user's base currency and triggers an immediate recalculation. Request body: `{ "currency": "EUR" }`.

6. **Historical Balance Snapshots:**
   - Create a `WalletSnapshot` document in account-service's MongoDB:
     - `accountName` (String, indexed)
     - `snapshotDate` (Date)
     - `baseCurrency`, `totalBalance`, `totalIncome`, `totalExpenses`, `netSavingsRate`
     - `balances` (copy of the CurrencyBalance list at that point)
   - Every time the wallet is recalculated, persist a snapshot (but **at most one per day per account** — overwrite if same day).
   - `GET /accounts/current/wallet/history?from=2024-01-01&to=2024-12-31` — returns the time series of wallet snapshots for charting.

7. **Integration with Existing Save Flow:**
   - Modify `AccountServiceImpl.saveChanges()` to call `walletService.recalculate(account)` after saving, so the wallet is always up-to-date.
   - The existing `GET /accounts/current` response must include the wallet object (no breaking changes — wallet is a new field on Account, serialized alongside existing fields).

### Acceptance Criteria
- Account responses include a `wallet` object with per-currency balances, totals, and savings rate.
- All monetary amounts are normalized to a monthly basis before aggregation.
- Currency conversion uses live rates fetched from statistics-service (not the external API directly).
- Hystrix fallback provides approximate rates when statistics-service is unavailable.
- Historical snapshots are persisted daily and queryable by date range.
- Changing base currency triggers full recalculation and is reflected immediately.
- No breaking changes to existing API responses.

---

## Expected PR Review Feedback (Turns 2 & 3)

### Turn 2 — Anticipated Flaws to Critique

1. **TimePeriod normalization to monthly basis done wrong:** The model will likely either skip normalization entirely (just sum raw amounts regardless of period) or use incorrect ratios. The existing `TimePeriod.baseRatio` normalizes to DAYS, but the wallet needs MONTHLY normalization. Expect the model to divide by `baseRatio` when it should multiply, or confuse daily-normalized values with monthly ones. Demand explicit conversion: `monthlyAmount = amount * (30.44 / timePeriod.baseRatio)` using the DAY base ratio of each period.

2. **Saving object ignored in balance calculation:** The `Account` has a `Saving` field (amount, currency, interest, deposit, capitalization) that represents accumulated savings. The model will likely compute wallet balance from incomes/expenses only and forget to include the `saving.amount` as part of the total balance. Demand the saving amount be converted and included.

3. **Division by zero in netSavingsRate:** When `totalIncome` is zero, `(totalIncome - totalExpenses) / totalIncome` throws ArithmeticException. The model will likely miss this edge case. Demand explicit zero-income handling.

4. **Snapshot overwrite logic missing:** The "at most one snapshot per day" requirement means the model needs to query for an existing snapshot with the same `accountName` + `snapshotDate` (date-only, not datetime) and update it, rather than inserting duplicates. Expect the model to either skip this entirely (creating many snapshots per day) or use a flawed date comparison (comparing full timestamps instead of truncated dates).

5. **Feign fallback returns null rates map:** The Hystrix fallback for the exchange rates Feign client will likely return `null` or an empty map, which will cause NPEs in the wallet calculation code. Demand actual fallback rates with sensible defaults.

6. **No caching of rates within a single recalculation:** If a user has items in 3 currencies, the model might call the Feign endpoint 3 times (once per conversion). Demand that rates are fetched once and passed through.

7. **History endpoint returns unbounded results:** The `GET /wallet/history` endpoint will likely not enforce a maximum date range or pagination, allowing arbitrarily large result sets. Demand a maximum range of 1 year and default pagination.

### Turn 3 — Tests, Linting, and Polish

1. **Unit tests for `WalletService.recalculate()`:** Test with: single-currency account, multi-currency account, empty incomes, empty expenses, zero-income (savings rate edge case), items with different TimePeriods in the same currency.

---

## Copilot Draft — Turn 1 Prompt & Opinions

### Drafted Turn 1 Prompt (for your reference — tweak as needed)

> Right now PiggyMetrics lets each income/expense `Item` carry its own `Currency`, and the statistics-service normalizes everything to USD for its datapoints — but the account-service has zero concept of a wallet. There is no way for a user to see "here's my total balance across all currencies, converted into my preferred display currency."
>
> I want you to implement a multi-currency wallet system in the account-service. At a high level:
>
> 1. **Wallet on Account** — Each account should have a wallet that tracks per-currency balances (native + converted to a base display currency), total balance, total income, total expenses, and a net savings rate percentage.
>
> 2. **Monthly normalization** — Items have different `TimePeriod` values (HOUR, DAY, MONTH, QUARTER, YEAR). The wallet should normalize all amounts to a monthly basis before aggregation. Don't forget the `Saving` object should factor into the total balance too.
>
> 3. **Currency conversion** — The account-service should get exchange rates from the statistics-service (which already fetches and caches them from an external API). Don't call the external rates API directly from account-service. If the statistics-service is down, wallet calculations should degrade gracefully with approximate fallback rates instead of failing.
>
> 4. **Wallet endpoints** — Provide REST endpoints to: get the current wallet, force a refresh, change the user's base display currency (triggering recalculation). The wallet should also auto-recalculate whenever the user saves account changes.
>
> 5. **Daily historical snapshots** — Every time the wallet is recalculated, persist a snapshot to MongoDB. Limit it to at most one snapshot per account per day (overwrite if same day). Provide a history endpoint that returns snapshots filtered by date range.
>
> 6. **Statistics service changes** — You'll need to expose the cached exchange rates from the statistics-service via a new endpoint so the account-service can consume them.
>
> Make sure existing API responses aren't broken — the wallet should be a new field on Account, not a replacement of anything.

### My opinions / what I'm watching for

- **TimePeriod normalization is the big trap.** The statistics-service `TimePeriod` enum has `baseRatio` values that normalize to DAY, not MONTH. The models will likely either skip normalization, confuse daily with monthly, or use `baseRatio` directly without converting to monthly. The correct approach: `monthlyAmount = amount * (30.4368 / periodDays)`. This is genuinely subtle.

- **Saving object gets forgotten.** Both `Account.saving.amount` and its currency need to be converted and included in totalBalance. Most models will only look at incomes and expenses.

- **Division by zero in netSavingsRate.** When totalIncome is zero, dividing by it blows up. Easy to miss.

- **Feign fallback returning null/empty.** The Hystrix fallback for the rates client needs to return actual approximate rates, not null or an empty map — otherwise the wallet calculation NPEs.

- **Snapshot overwrite logic.** "At most one per day" means the model needs to query for an existing snapshot with the same accountName + truncated date (day only, not full timestamp) and update it rather than inserting a duplicate.

- **History endpoint unbounded results.** Without a max date range or pagination, the history endpoint could return arbitrarily large datasets. Worth watching if models think about this.

- **The prompt is complex enough to naturally produce 3+ turns.** Turn 1 will get the foundation — expect flaws in normalization logic, missing saving inclusion, missing edge cases. Turn 2 can address those specific bugs. Turn 3 can cover tests and polish.

- **Difficulty source.** The real complexity is inherent: cross-service Feign integration, correct financial normalization math, MongoDB snapshot idempotency, and graceful degradation. No traps needed — the problem itself is hard enough.
2. **Unit tests for the Hystrix fallback rates:** Verify wallet calculation with fallback rates produces reasonable (non-zero, non-null) results.
3. **Controller tests for wallet endpoints:** MockMvc for GET wallet, POST refresh, PUT base-currency (valid currency, invalid currency string → 400), GET history (with and without date range params).
4. **Repository tests for WalletSnapshot:** Verify daily overwrite behavior — save two snapshots on the same day for the same account, assert only one exists.
5. **Verify no breaking change:** Write a test that deserializes an Account response and confirms the existing fields (incomes, expenses, saving, note, lastSeen) are still present alongside the new wallet field.
6. **Ensure `@JsonInclude(NON_NULL)` on Wallet** so accounts without a wallet yet don't serialize an ugly `"wallet": null`.

---

## Why It Fits the Constraint

- **~550–650 lines of new core code:**
  - `Wallet.java`, `CurrencyBalance.java` domain models (~60 lines)
  - `WalletSnapshot.java` entity (~40 lines)
  - `WalletSnapshotRepository.java` (~25 lines)
  - `WalletService` interface + `WalletServiceImpl` with normalization + conversion logic (~180 lines)
  - `WalletController.java` REST endpoints (~70 lines)
  - `ExchangeRatesServiceClient.java` Feign + fallback (~50 lines)
  - New endpoint in `StatisticsController.java` + `StatisticsService` (~40 lines)
  - Modifications to `Account.java`, `AccountServiceImpl.java`, `AccountController.java` (~50 lines)
  - Config updates (~20 lines)
  - `WalletSnapshotController` or merged into AccountController history endpoint (~40 lines)

- **High difficulty:** Correct multi-currency normalization combined with TimePeriod conversion is mathematically complex and error-prone. Integrating with the existing save flow without breaking changes requires careful understanding of the current data model. The snapshot daily-overwrite logic requires correct date truncation. The Feign + Hystrix fallback chain adds distributed systems complexity.

- **Natural multi-turn material:** Monthly normalization math will be wrong, saving.amount will be forgotten, division-by-zero will be missed, snapshot deduplication will be flawed, and fallback rates will be incomplete — each is a distinct, legitimate critique.

---

## Potential Files Modified

> At least 6 core files, excluding tests:

| # | File Path | Change |
|---|-----------|--------|
| 1 | `account-service/src/main/java/com/piggymetrics/account/domain/Wallet.java` | **New** — Wallet + CurrencyBalance model |
| 2 | `account-service/src/main/java/com/piggymetrics/account/domain/WalletSnapshot.java` | **New** — Historical snapshot entity |
| 3 | `account-service/src/main/java/com/piggymetrics/account/domain/Account.java` | Add `Wallet wallet` field |
| 4 | `account-service/src/main/java/com/piggymetrics/account/repository/WalletSnapshotRepository.java` | **New** — Snapshot persistence |
| 5 | `account-service/src/main/java/com/piggymetrics/account/service/WalletService.java` | **New** — Wallet recalculation interface |
| 6 | `account-service/src/main/java/com/piggymetrics/account/service/WalletServiceImpl.java` | **New** — Core normalization + conversion logic |
| 7 | `account-service/src/main/java/com/piggymetrics/account/controller/AccountController.java` | Add wallet endpoints |
| 8 | `account-service/src/main/java/com/piggymetrics/account/service/AccountServiceImpl.java` | Integrate wallet recalculation into save flow |
| 9 | `account-service/src/main/java/com/piggymetrics/account/client/ExchangeRatesServiceClient.java` | **New** — Feign client to statistics-service for rates |
| 10 | `statistics-service/src/main/java/com/piggymetrics/statistics/controller/StatisticsController.java` | Add `/rates` endpoint |
| 11 | `statistics-service/src/main/java/com/piggymetrics/statistics/service/ExchangeRatesServiceImpl.java` | Expose cached rates via new service method |

---

## Reference Implementation — PR Overview

### Summary

Implements a multi-currency wallet system within account-service. Each account now has a `Wallet` embedded document with per-currency balances (normalized to monthly), totals converted to a user-configurable base currency, and a net savings rate. Exchange rates are fetched from statistics-service via a new Feign client with Hystrix fallback. Historical wallet snapshots are persisted daily and queryable by date range.

### Files Changed (16 files)

#### New Files — account-service (10)

| # | File | Lines | Purpose |
|---|------|-------|---------|
| 1 | `.../domain/CurrencyBalance.java` | ~50 | Per-currency balance: native + converted amounts |
| 2 | `.../domain/Wallet.java` | ~75 | Wallet aggregate: baseCurrency, balances, totals, netSavingsRate. `@JsonInclude(NON_NULL)` |
| 3 | `.../domain/WalletSnapshot.java` | ~110 | MongoDB document for daily snapshots. `@CompoundIndex(unique=true)` on (accountName, snapshotDate) |
| 4 | `.../repository/WalletSnapshotRepository.java` | ~20 | MongoRepository with date-range and daily-lookup finders |
| 5 | `.../client/ExchangeRatesServiceClient.java` | ~18 | Feign client targeting `GET /statistics/rates` |
| 6 | `.../client/ExchangeRatesServiceClientFallback.java` | ~28 | Hystrix fallback returning hardcoded approximate rates (USD=1, EUR=0.85, RUB=75) |
| 7 | `.../service/WalletService.java` | ~18 | Interface: recalculate, changeBaseCurrency, getHistory |
| 8 | `.../service/WalletServiceImpl.java` | ~210 | Core logic: TimePeriod→monthly normalization, multi-currency conversion, saving inclusion, zero-income guard, daily snapshot persistence with overwrite |
| 9 | `.../controller/WalletController.java` | ~65 | REST endpoints: GET wallet, POST refresh, PUT base-currency, GET history |
| 10 | `.../test/.../service/WalletServiceImplTest.java` | ~280 | 14 tests: single/multi-currency, period normalization, zero income, saving inclusion, base currency change, snapshot persistence/overwrite, history, fallback rates |

#### New Test Files (2)

| # | File | Lines | Purpose |
|---|------|-------|---------|
| 1 | `.../test/.../controller/WalletControllerTest.java` | ~155 | 6 MockMvc tests: get wallet, refresh, change currency (valid + invalid), history (with/without dates) |
| 2 | `.../test/.../repository/WalletSnapshotRepositoryTest.java` | ~100 | 4 @DataMongoTest tests: find by date, find between dates, daily overwrite, null for nonexistent |

#### Modified Files — account-service (3)

| # | File | Change |
|---|------|--------|
| 1 | `.../domain/Account.java` | Added `Wallet wallet` field + getter/setter |
| 2 | `.../service/AccountServiceImpl.java` | Added `@Autowired WalletService`, calls `walletService.recalculate(account)` after save (wrapped in try/catch) |
| 3 | `.../test/.../service/AccountServiceTest.java` | Added `@Mock WalletService`, added `verify(walletService).recalculate()` to save test |

#### Modified Files — statistics-service (2)

| # | File | Change |
|---|------|--------|
| 1 | `.../controller/StatisticsController.java` | Added `@Autowired ExchangeRatesService`, new `GET /rates` endpoint (server scope) |
| 2 | `.../test/.../controller/StatisticsControllerTest.java` | Added `@Mock ExchangeRatesService`, new test for `/rates` endpoint |

### Architecture Decisions

1. **TimePeriod normalization to monthly**: Uses the same day-count ratios as the statistics-service (`YEAR=365.2425, QUARTER=91.3106, MONTH=30.4368, DAY=1, HOUR=0.0416`). Formula: `monthlyAmount = amount * (30.4368 / periodDays)`. A $12,000/year salary becomes ~$999.33/month; a $50/day expense becomes ~$1,521.84/month.

2. **Currency conversion**: `result = amount * (rates[to] / rates[from])`. Rates are relative to USD (base). Fetched once per recalculation via Feign, not per-conversion.

3. **Saving amount included**: The `saving.amount` is converted to baseCurrency and added to `totalBalance`, but NOT to `totalIncome` or `totalExpenses` (it's a separate stored value, not a recurring flow).

4. **Division-by-zero guard**: When `totalIncome == 0`, `netSavingsRate` is set to `BigDecimal.ZERO` instead of throwing `ArithmeticException`.

5. **Daily snapshot overwrite**: Uses `findByAccountNameAndSnapshotDate()` to check for existing same-day snapshot before saving. The `@CompoundIndex(unique=true)` on (accountName, snapshotDate) provides a DB-level safety net. Snapshot persistence failures are caught and logged without breaking the wallet recalculation.

6. **Hystrix fallback**: Returns a complete `Map<Currency, BigDecimal>` with approximate rates (not null or empty), so wallet calculations degrade gracefully when statistics-service is unavailable.

7. **`@JsonInclude(NON_NULL)`**: Applied to `Wallet` so accounts without a wallet yet don't serialize `"wallet": null`.

8. **Wallet recalculation in saveChanges**: Wrapped in try/catch so a wallet calculation failure doesn't break the core account save flow.

### Test Coverage

- **WalletServiceImplTest** (14 tests): Single-currency recalculation, multi-currency with 2+ currencies, daily normalization ($50/day → ~$1521.84/month), yearly normalization ($12k/year → ~$999.33/month), empty/null incomes and expenses, zero-income savings rate, saving amount inclusion (same and different currency), base currency change, history with and without date range, snapshot persistence and daily overwrite, fallback rates.
- **WalletControllerTest** (6 tests): GET wallet returns wallet object, POST refresh triggers recalculation, PUT base-currency with valid currency, PUT base-currency with invalid string → 500, GET history with and without date params.
- **WalletSnapshotRepositoryTest** (4 tests): Find by account+date, find between dates with ordering, daily overwrite via find-then-update, null for nonexistent.
- **StatisticsControllerTest** (1 new test): GET /rates returns the exchange rates map.
- **AccountServiceTest** (1 updated test): Verifies `walletService.recalculate()` is called during saveChanges.
