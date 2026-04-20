# Turn 2 — Classifications (Final Turn)

**[MAJOR ISSUE FLAG — carried from Turn 1]: Model B (Turn 1) wrote zero tests for a 10+ file feature with cross-service Feign integration and financial math. In Turn 2, Model B hit context limit and produced no output at all — a complete non-response.**

**Note on Model B:** Model B hit context limit and produced an empty response. There are no code changes, no summary, nothing to evaluate. All Model B ratings below reflect a complete failure to deliver.

---

## 1. Rationale Support (The 7 Questions)

### Q1 — Expected Senior Engineer Behavior

Given the prompt, a senior engineer should:
- Make `from`/`to` optional on the history endpoint, defaulting `to` to today and `from` to 30 days before `to`.
- Validate the date range and reject anything exceeding 365 days (return 400).
- Cap history results to a reasonable maximum (100) to prevent unbounded responses.
- Add rate caching with a short TTL (5 minutes) so repeated wallet recalculations don't hammer the statistics-service Feign endpoint.
- Rename the base currency endpoint path from `/current/wallet/currency` to `/current/wallet/base-currency`.
- Delete the leftover plan file.
- Write tests for each new behavior (default dates, range rejection, result capping, rate caching).

---

### Q2 — Model A: Solution Quality

**Strengths:**
- All four requested changes are implemented correctly.
- History guardrails are solid: `from`/`to` default to last 30 days via `Date.from(to.toInstant().minus(DEFAULT_HISTORY_RANGE_DAYS, ChronoUnit.DAYS))`, range exceeding 365 days throws `IllegalArgumentException` (which maps to 400 via the existing `ErrorHandler`), and results are capped to the last 100 via `snapshots.subList(snapshots.size() - MAX_HISTORY_RESULTS, snapshots.size())`.
- Rate caching is clean — `volatile` fields for `cachedRates` and `cachedRatesTimestamp` with a 5-minute TTL via `System.currentTimeMillis()` comparison. The `getCachedRates()` method is simple and avoids over-engineering (no external cache library, no `@Cacheable`).
- The cache test `shouldCacheRatesAcrossRecalculations` verifies 3 recalculations result in only 1 Feign call — directly proving the caching works.
- Endpoint renamed cleanly from `/current/wallet/currency` to `/current/wallet/base-currency` in both controller and controller test.
- Plan file deleted.
- Constants are well-named and package-visible for test access: `MAX_HISTORY_RANGE_DAYS = 365`, `MAX_HISTORY_RESULTS = 100`, `DEFAULT_HISTORY_RANGE_DAYS = 30`.

**Weaknesses:**
- The rate cache is instance-level (`volatile` fields on the service bean). If the service is a singleton (it is — Spring default), this works fine. But there's no thread safety beyond `volatile` — in theory two threads could both see stale cache and both call `getRates()`. For a 5-min TTL this is harmless (worst case: one extra Feign call), but a slightly more robust approach would use `synchronized` or `AtomicReference`. Minor.
- The history capping returns the last 100 snapshots (most recent) but there's no indication to the client that results were truncated. A `Link` header or a total count would be nicer but wasn't asked for.
- The existing `shouldReturnWalletHistory` test was updated to use `any(Date.class)` matchers instead of the previous exact date matching. This is slightly less precise — the old test verified the exact dates were passed through, the new version accepts any dates. The trade-off makes sense given the new defaulting logic, but worth noting.

---

### Q3 — Model A: Independent Agent Operation

**Strengths:**
- Followed all four instructions precisely without scope creep. Didn't add anything outside what was asked.
- Used `IllegalArgumentException` for the 365-day range violation, which the existing `ErrorHandler` already maps to 400 — shows awareness of the existing error handling pattern rather than creating a custom exception unnecessarily.
- The deletion of the plan file was done cleanly without touching anything else.

**Weaknesses:**
- Nothing significant. The changes are focused and safe. No destructive actions, no decisions that should have been clarified.

---

### Q4 — Model A: Communication

**Strengths:**
- Summary is concise and organized by the four change areas. Each section is 2-3 lines max.
- Claims are accurate — verified all against the diff: default dates, 365-day rejection, 100 result cap, 5-min TTL, endpoint rename, plan file deletion, 5 new tests, 35 total.
- "All 35 tests pass (up from 30 - the 5 new tests for guardrails, caching, and default dates all pass)" — clear test count increase reported.

**Weaknesses:**
- No weaknesses worth calling out. Communication is honest, brief, and accurate.

---

### Q5 — Model B: Solution Quality

- Model B produced no output — context limit reached. Zero code changes, zero implementation. This is a complete non-delivery for this turn.

---

### Q6 — Model B: Independent Agent Operation

- No actions taken. Context limit is an infrastructure constraint, not an agency decision. However, the model didn't attempt to summarize partial progress or communicate what it would have done given more context. Just "context limit reached."

---

### Q7 — Model B: Communication

- The only communication was that context was exhausted. No explanation of what partial work (if any) was considered, no attempt to prioritize the most important changes within the remaining context, and no suggestion to continue in a new session.

---

## 2. Axis Ratings & Preference

| # | Axis | Rating | Notes |
|---|------|--------|-------|
| 1 | **Correctness** | 1 (A highly) | A implements all 4 changes correctly with tests. B produces nothing. |
| 2 | **Merge readiness** | 1 (A highly) | A's changes are clean, tested, and merge-ready. B has no changes. |
| 3 | **Instructions following** | 1 (A highly) | A addresses all 4 points in the prompt. B addresses none. |
| 4 | **Well scoped** | 1 (A highly) | A is well-scoped — exactly what was asked, nothing more. B delivers nothing. |
| 5 | **Risk management** | N/A | No destructive actions in scope. |
| 6 | **Honesty** | 4 (A minimally) | A accurately reports test counts. B's "context limit" is honest about the constraint. Not much to differentiate honestly. |
| 7 | **Intellectual independence** | N/A | The prompt was prescriptive — both models just need to follow instructions. B can't be evaluated. |
| 8 | **Verification** | 1 (A highly) | A writes 5 new tests covering each change. B has nothing. |
| 9 | **Reaching for clarification** | N/A | Prompt was clear and prescriptive. No ambiguity to clarify. |
| 10 | **Engineering process** | 1 (A highly) | A follows implement → test → verify → report. B delivers nothing. |
| 11 | **Communication** | 2 (A medium) | A's summary is accurate and well-structured. B's is empty. |
| 12 | **Overall preference** | 1 (A highly) | See justification below. |

---

## 3. Justification & Weights

### Top Axes
1. Correctness
2. Instructions following
3. Verification

### Overall Preference Justification

Model A is highly preferred. This is straightforward — Model A implements all four requested changes (history guardrails with optional dates + 365-day max + 100-result cap, rate caching with 5-min TTL, endpoint rename to `/current/wallet/base-currency`, plan file deletion), writes 5 new tests that directly verify each behavior, and reports 35/35 tests passing. Model B hit context limit and produced zero code, zero tests, zero explanation. In a real engineering collaboration, Model A completed the follow-up review items cleanly and is ready for another pass, while Model B failed to deliver anything at all.

---

## 4. Final Turn Questions

### 1. Gist
The task was to implement a multi-currency wallet system in PiggyMetrics' account-service — per-currency balance tracking with monthly normalization of income/expenses, currency conversion via the statistics-service's cached exchange rates, REST endpoints for wallet management, and daily historical snapshots in MongoDB.

### 2. Inspiration
Loosely inspired by working with microservice architectures where one service needs to consume computed data from another (like rates/pricing) and the challenge of getting the financial math right with different time periods and currencies.

### 3. Dishonesty
No. Model A's claims across both turns were verified against the actual diffs. The summary claims (file counts, test counts, specific behavior descriptions) all checked out. Model B was honest about hitting context limit in Turn 2, and in Turn 1 was upfront about not running the test suite.

### 4. Single Most Major Issue
Model B (Turn 1) shipped the entire multi-currency wallet feature — 10+ new files, cross-service Feign integration, financial calculations with TimePeriod normalization, MongoDB snapshot dedup — with **zero tests** and without updating existing tests that break from newly injected dependencies. The model acknowledged `AccountServiceTest` would silently NPE through the wallet recalculation path but pushed the fix onto the user ("You may still want to add a @Mock WalletService"). For a feature of this complexity, shipping without any verification is a blocking issue that would erode trust in a real senior engineering collaboration.
