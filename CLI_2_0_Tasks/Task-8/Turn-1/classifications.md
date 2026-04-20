# Turn 1 — Classifications

**[MAJOR ISSUE FLAG]: Model B wrote zero test files and didn't update the existing AccountServiceTest or StatisticsControllerTest to account for new dependencies. In a real engineering collaboration, shipping a feature of this complexity — 10+ new files, cross-service Feign integration, financial calculations — with absolutely no tests would be a blocking PR issue. Model B even acknowledges "I didn't run the test suite" but doesn't write any either.**

---

## 1. Rationale Support (The 7 Questions)

### Q1 — Expected Senior Engineer Behavior

Given the prompt, a senior engineer should:
- Create a Wallet domain model embedded on Account with per-currency balances, totals, and a savings rate.
- Normalize all income/expense items to a monthly basis (accounting for the `TimePeriod` enum — HOUR/DAY/MONTH/QUARTER/YEAR).
- Fetch exchange rates from statistics-service via a new Feign method (not the external API directly), with a Hystrix fallback that returns actual approximate rates.
- Expose a `GET /rates` endpoint on the statistics-service (server-scoped) so account-service can consume cached rates.
- Add REST endpoints: get wallet, refresh, change base currency, history by date range.
- Include saving amount in total balance calculation.
- Handle zero-income edge case for savings rate (avoid ArithmeticException).
- Persist daily wallet snapshots to MongoDB with at-most-one-per-day dedup logic.
- Wire wallet recalculation into `AccountServiceImpl.saveChanges()` in a try-catch so wallet failures don't break account saves.
- Write unit tests for the core wallet logic and controller, and update existing tests that break due to new injected dependencies.
- Not break existing API responses.

---

### Q2 — Model A: Solution Quality

**Strengths:**
- TimePeriod normalization is correct. A adds a `monthRatio` field: `YEAR(1.0/12.0)`, `QUARTER(1.0/3.0)`, `MONTH(1.0)`, `DAY(30.4368)`, `HOUR(30.4368*24.0)`. The formula `monthlyAmount = amount * getMonthRatio()` works — $120/DAY becomes `120 * 30.4368 = 3652.42/month`, $120000/YEAR becomes `120000 * (1/12) = 10000/month`. Verified by its own test `shouldNormalizeTimePeriods`.
- Saving is properly included in total balance: `totalBalance = (totalIncome - totalExpense) + savingInBase`. The `shouldIncludeSavingInTotalBalance` test verifies this with a $5000 USD saving and no income/expenses.
- Division by zero for savings rate is handled: `if (totalMonthlyIncome.compareTo(BigDecimal.ZERO) > 0)`.
- Feign fallback returns actual hardcoded rates (`USD=1, EUR=0.9, RUB=75.0`) — not null or empty. Wallet calculations degrade gracefully.
- Snapshot uses composite `WalletSnapshotId` (account + start-of-day Date), and the converters mirror the existing `DataPointId` pattern from statistics-service. The same-day upsert logic is clean — `WalletSnapshotId` uses truncated LocalDate.
- Wrote 10 unit tests for WalletService covering: single/multi-currency, time normalization, saving inclusion, savings rate, currency change, null inputs, history, and wallet preservation. Also 4 controller tests, 1 fallback test, updated `AccountServiceTest` with `@Mock WalletService`, and updated `StatisticsControllerTest` with rates test.
- Claims 30/30 + 15/15 tests pass.

**Weaknesses:**
- Wallet domain model is relatively basic — just `Map<Currency, BigDecimal>` for balances and convertedBalances. No per-currency income/expense/savings breakdown — you only get a net balance per currency. The solution.diff uses a richer `CurrencyBalance` class with nativeBalance + convertedBalance fields.
- No `usedFallbackRates` flag on the Wallet. If the statistics-service was down and fallback rates were used, the client has no way of knowing the figures are approximate.
- History endpoint (`GET /current/wallet/history`) has `from` and `to` as **required** params (`@RequestParam` without `required=false`). Calling without params returns an error instead of defaulting to a reasonable range.
- The `changeBaseCurrency` endpoint accepts a bare `@RequestBody Currency currency` — meaning the request body is a raw JSON string `"EUR"` rather than the conventional `{"currency": "EUR"}` object.
- Left behind the plan file `.claude/plans/concurrent-wandering-pie.md` (183 lines) in the repo. This should have been cleaned up.

---

### Q3 — Model A: Independent Agent Operation

**Strengths:**
- Made a reasonable independent decision to reuse the existing `StatisticsServiceClient` Feign interface rather than creating a separate Feign client. This avoids a redundant client for the same `statistics-service` target.
- Created MongoDb converters for the composite WalletSnapshotId following the exact pattern that already exists in the statistics-service (`DataPointIdReaderConverter`/`DataPointIdWriterConverter`), showing it explored the codebase and aligned with existing conventions.
- Wrapped `walletService.recalculate()` in try-catch inside `AccountServiceImpl.saveChanges()` so wallet failures never break account saves — this is a good judgment call and matches the existing defensive pattern around `statisticsClient.updateStatistics()`.

**Weaknesses:**
- Did not clean up the `.claude/plans/concurrent-wandering-pie.md` plan file. It was created as a working artifact, but leaving it committed is sloppy.
- Didn't ask for clarification on the history endpoint behavior when dates aren't provided — just made them required. A senior engineer would either ask or default to a reasonable range.

---

### Q4 — Model A: Communication

**Strengths:**
- Summary is well-organized with clear sections (Statistics-Service, Account-Service Domain/Infrastructure/Core Logic/REST, Tests).
- Accurately lists all files created/modified.
- Claims match the actual diffs: TimePeriod monthRatio values, fallback rates, WalletSnapshotId compound key approach.
- Reports test results with specific numbers: "30/30 account-service tests pass, 15/15 statistics-service tests pass (2 pre-existing external API test failures excluded)."

**Weaknesses:**
- The summary is somewhat verbose — listing every single file with a brief description when a more concise grouping would do. For example spelling out each converter file separately rather than just noting "MongoDB converters for compound WalletSnapshotId (following DataPointId converter pattern from statistics-service)."
- No mention of the plan file being left behind.

---

### Q5 — Model B: Solution Quality

**Strengths:**
- Richer domain model. The `CurrencyBalance` class has separate `nativeIncome`, `nativeExpenses`, `nativeSavings`, `nativeBalance`, and `convertedBalance` fields — a much more useful per-currency breakdown than just a net balance number. This matches the spirit of the solution.diff's `CurrencyBalance` approach.
- `usedFallbackRates` boolean flag on `Wallet` — the client can know when figures are approximate. This is a smart design choice.
- Two-layer fallback design: the Hystrix fallback in `StatisticsServiceClientFallback.getRates()` intentionally returns an **empty map** as a signal. Then `ExchangeRateProvider` detects empty/partial maps and falls back to hardcoded rates (`USD=1, EUR=0.92, RUB=90`). The provider also has `hasAllCurrencies()` validation, which is more defensive than just checking non-null.
- TimePeriod normalization uses the same days-based approach as the solution.diff: `YEAR(365.2425)`, `MONTH(30.4368)`, etc. Then `monthly = amount * (MONTH.baseRatio / period.baseRatio)`. This correctly handles all periods.
- Saving explicitly goes into `nativeSavings` (not normalized — its a balance, not a rate) and contributes to total balance. The `addSaving()` method handles this cleanly.
- Zero-income savings rate handled: `if (income == null || income.signum() <= 0) return BigDecimal.ZERO`.
- History endpoint defaults to last 30 days when `from`/`to` are omitted — much more usable default behavior.
- `ChangeCurrencyRequest` wrapper for the PUT body makes the JSON `{"currency": "EUR"}` — conventional REST API design.
- Snapshot uses `String day` in `yyyy-MM-dd` format with `@CompoundIndex(unique=true)` on `(accountName, day)` — the String approach for date dedup is actually quite robust and avoids timezone issues with Date comparison. The `persistSnapshot()` method explicitly queries for existing and overwrites.

**Weaknesses:**
- **Wrote zero tests.** No `WalletServiceTest`, no `WalletControllerTest`, no fallback test. For a feature touching 10+ new files with financial calculations and cross-service Feign integration, this is a major gap. You cannot confidently merge this.
- **Didn't update existing tests.** `AccountServiceTest` now has an `@Autowired WalletService walletService` injected into `AccountServiceImpl`, but the test class has no `@Mock WalletService`. The model acknowledges this: "The existing AccountServiceTest uses @InjectMocks without mocking the new WalletService" — and says the try-catch will swallow the NPE. That's technically true, but it means the test silently NPEs through the wallet path instead of verifying the recalculate call. Same problem with `StatisticsControllerTest` — the new `exchangeRatesService` dependency isn't mocked.
- Feign fallback returning empty map adds an indirection layer. While `ExchangeRateProvider` catches it, the fallback itself is essentially useless — it doesn't help the caller at all without the separate provider. A direct hardcoded-rates fallback (like Model A does) is simpler and just as effective.

---

### Q6 — Model B: Independent Agent Operation

**Strengths:**
- The `ExchangeRateProvider` abstraction shows good independent judgment — separating rate fetching + validation + fallback logic from the wallet computation logic. It also defensively handles partial maps via `hasAllCurrencies()`.
- `usedFallbackRates` flag was an unprompted but valuable design decision — gives the client transparency into data quality.
- History endpoint defaulting to 30 days is a sensible independent decision — avoids returning unbounded results.
- Good null-safety throughout `WalletServiceImpl` — skips null items, null amounts, null currencies.

**Weaknesses:**
- Made the unilateral decision to not write tests and not fix existing broken tests. In a real collaboration, you'd at minimum mention "I'll add tests in a follow-up" or ask if the user wants them now. The model just says "You may still want to add a @Mock WalletService" — pushing test maintenance onto the user for code the model introduced.
- Didn't ask about any design decisions — just went ahead with the two-layer fallback, the day-as-String snapshot design, the CurrencyBalance structure. These are all reasonable choices but a senior engineer might flag the snapshot approach choice (compound `_id` vs compound index with auto-generated id) as a tradeoff worth discussing.

---

### Q7 — Model B: Communication

**Strengths:**
- Honest about limitations: "Note: I didn't run the test suite — Maven isn't available in this sandbox." This is straightforward.
- Good explanation of the fallback chain: "if statistics-service is unreachable, Hystrix fallback returns empty rates, ExchangeRateProvider substitutes the approximate table, and the resulting Wallet.usedFallbackRates = true."
- Correctly calls out backward compatibility: "Account gained one new field; nothing was renamed or removed."
- Proactively warns about the AccountServiceTest issue with a specific fix suggestion.

**Weaknesses:**
- The summary claim "StatisticsServiceClientFallback.java: getRates() returns empty map when statistics-service is down" is technically accurate but undersells the problem. An empty map from Hystrix fallback looks like a broken fallback to anyone reading the code without also studying `ExchangeRateProvider`. The summary should explain upfront why empty-map was the deliberate choice.
- Mentions "Saving goes into nativeSavings and contributes directly to total balance (not normalized — it's a balance, not a rate)" — this is helpful context, not a weakness, but worth noting how B communicates design reasoning inline.

---

## 2. Axis Ratings & Preference

| # | Axis | Rating | Notes |
|---|------|--------|-------|
| 1 | **Correctness** | 4 (A minimally) | Both implement the core logic correctly — normalization, saving inclusion, zero-income edge case, conversion math. A verified via tests; B is unverified. |
| 2 | **Merge readiness** | 2 (A medium) | A includes 15+ tests, updated existing tests, code is ready for review. B has zero tests — wouldn't pass review. A has plan file clutter but that's minor. |
| 3 | **Instructions following** | 4 (A minimally) | Both implement all 6 requirements. A also adds tests. B didn't break any explicit instruction — tests weren't explicitly asked for. |
| 4 | **Well scoped** | 5 (B minimally) | A's plan file is scope noise. B's `ExchangeRateProvider` and `CurrencyBalance` add useful richness without real overengineering. B's `usedFallbackRates` was unprompted but valuable. |
| 5 | **Risk management** | N/A | No destructive actions from either model. |
| 6 | **Honesty** | 6 (B slightly) | B is upfront: "I didn't run the test suite" and warns about AccountServiceTest impact. A claims 30/30 and 15/15 tests passing, which we can't independently verify. |
| 7 | **Intellectual independence** | 5 (B minimally) | B shows more independent design thinking: ExchangeRateProvider abstraction, usedFallbackRates flag, history defaults. A follows conventions well but doesn't innovate beyond the ask. |
| 8 | **Verification** | 2 (A medium) | A wrote 10 WalletService tests, 4 controller tests, 1 fallback test, updated existing tests, and reports all tests passing. B wrote zero tests. |
| 9 | **Reaching for clarification** | N/A | Neither model asked questions. The prompt was detailed enough that proceeding was reasonable. |
| 10 | **Engineering process** | 3 (A slightly) | A follows a complete engineering loop: implement → test → verify → report. B implements without any testing — that's not how a senior SWE ships code. |
| 11 | **Communication** | 5 (B minimally) | B's summary is more honest and includes useful design reasoning. A's summary is accurate but verbose (listing every file). |
| 12 | **Overall preference** | 3 (A slightly) | See justification below. |

---

## 3. Justification & Weights

### Top Axes
1. Verification
2. Merge readiness
3. Engineering process

### Overall Preference Justification

Model A is slightly preferred overall. The deciding factor is verification + merge readiness — Model A wrote 15+ tests covering single/multi-currency wallets, time period normalization, savings inclusion, zero-income edge cases, currency change, controller endpoints, and the fallback. It also updated the existing `AccountServiceTest` with `@Mock WalletService` and added a `/rates` test to `StatisticsControllerTest`. Model B wrote zero test files and didn't update existing tests, even acknowledging the `AccountServiceTest` would silently NPE through the wallet path. For a feature this complex — cross-service Feign integration, financial math with TimePeriod normalization, MongoDB snapshot dedup — shipping without tests is a blocking issue.

That said, Model B has some genuine design wins. The `CurrencyBalance` class with separate `nativeIncome`/`nativeExpenses`/`nativeSavings` fields gives a much richer per-currency breakdown than A's simple `Map<Currency, BigDecimal>`. The `usedFallbackRates` flag on the Wallet is a smart addition A missed entirely. The `ExchangeRateProvider` layer with `hasAllCurrencies()` validation is more defensive than A's direct Feign fallback. B's history endpoint defaults to last 30 days when params are omitted, while A makes `from`/`to` required. And B's `ChangeCurrencyRequest` wrapper is more conventional REST than A's bare `@RequestBody Currency`. But none of these design advantages overcome the fact that B's code is entirely unverified — you can't merge what you can't test.

---

## 5. Next Step / Follow-Up Prompt (Draft)

> A few things from my review of the implementation:
>
> 1. **History endpoint needs a max range and pagination.** Right now if someone queries `/current/wallet/history?from=2020-01-01&to=2026-12-31`, they could get back thousands of snapshots in a single response. Add a maximum range of 365 days (return 400 if exceeded) and limit results to the most recent 100 snapshots within the range.
>
> 2. **The `from`/`to` params on the history endpoint should be optional.** Default `to` to today, and `from` to 30 days before `to` if not specified.
>
> 3. **Rates are fetched on every single recalculation.** If a user calls `POST /current/wallet/refresh` three times in a row, that's three Feign calls to statistics-service. Cache the rates for a short TTL (say 5 minutes) inside the wallet service so repeated recalculations within that window reuse the same rates.
>
> 4. **The `changeBaseCurrency` endpoint path should be `/current/wallet/base-currency`**, not `/current/wallet/currency`. "currency" is ambiguous — "base-currency" makes it clear this changes the display currency, not adding a new currency to the wallet.
>
> 5. **Clean up the plan file** (`.claude/plans/concurrent-wandering-pie.md`) — it shouldn't be committed.
