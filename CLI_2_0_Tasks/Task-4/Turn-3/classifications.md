# Turn 3 — Classifications (Final Turn)

**Context:** Both models operate on the Turn 2 winner's (Model A's) codebase. The prompt asks for 2 things: (1) add `BudgetAlertServiceImplTest` with 4 specific cooldown test scenarios, (2) run `mvn compile` across all 3 services and fix any issues.

**No new major issue flag this turn.** Both models discovered and fixed the same real compile error (`contextId` on `@FeignClient` not available in Spring Cloud Finchley) and both delivered the 4 requested tests. The differentiator is test quality — specifically how async negative assertions are handled.

*(The [MAJOR ISSUE FLAG] hard requirement was already satisfied in Turn 1: Model A's 3 broken Feign client URLs missing context-path prefixes.)*

---

## 1. Rationale Support (The 7 Questions)

### Q1. Expected Senior Engineer Behavior

A senior engineer given this prompt would:
- Run `mvn compile -pl account-service,statistics-service,notification-service` first to identify any compile errors before writing tests
- Recognize the `contextId` compile error as a Spring Cloud version issue — Finchley doesn't support it, Greenwich does. Fix by consolidating the BudgetEvaluationClient into the existing StatisticsServiceClient (one Feign client per remote service, matching codebase conventions)
- Write BudgetAlertServiceImplTest following the existing `NotificationServiceImplTest` pattern (JUnit 4, `@InjectMocks`/`@Mock`, `initMocks`)
- Use `ReflectionTestUtils.setField` for the `@Value`-injected `cooldownHours` since there's no Spring context in unit tests
- Handle async assertions properly: since `BudgetAlertServiceImpl.sendBudgetAlerts()` uses `CompletableFuture.runAsync`, negative assertions require waiting for the async task to complete before asserting `never()`. Use `verify(..., after(ms).never())` or confirm the task ran via a positive verification first, NOT `Thread.sleep`
- Use `ArgumentCaptor` to verify the persisted `BudgetAlertState` has the correct `lastStatus` (especially for the transition test where it should update to EXCEEDED)
- Mock `Environment.getProperty()` to prevent NPEs from `MessageFormat.format()` calls inside `sendAlert()`

### Q2. Model A — Solution Quality

**Strengths:**
- Correctly identified and fixed the `contextId` compile error. Merged `BudgetEvaluationClient` into `StatisticsServiceClient` — one Feign client per remote service, matching the existing codebase pattern. Deleted `BudgetEvaluationClient.java` and `BudgetEvaluationClientFallback.java`, updated `BudgetServiceImpl` to inject `StatisticsServiceClient` instead. Clean fix
- All 4 requested test scenarios are present: first alert sends email, same-status within cooldown suppressed, status transition sends email, cooldown-expired sends email
- Uses `ReflectionTestUtils.setField(alertService, "cooldownHours", 24L)` correctly for the `@Value` field
- Mocks `env.getProperty()` for the email templates to prevent NPE — necessary since `sendAlert()` calls `MessageFormat.format` with env-sourced templates
- Uses `timeout(500)` for positive async assertions (email sent, state saved) matching the existing `NotificationServiceImplTest` pattern
- Fallback method in `StatisticsServiceClientFallback` returns `Collections.emptyList()` — same degradation behavior as the deleted `BudgetEvaluationClientFallback`
- Summary honestly states "the compile error was: cannot find symbol: method contextId()" and explains the fix before claiming success

**Weaknesses:**
- Test #2 (`shouldNotSendEmailWhenSameStatusWithinCooldownWindow`) uses `Thread.sleep(200)` followed by `verify(emailService, never())`. This is a classic flaky-test pattern — if the async task takes >200ms (load spike, CI contention), the assertion fires before the task completes and passes vacuously. Model B correctly uses `verify(alertStateRepository, timeout(500)).findByAccountNameAndBudgetId(...)` to confirm the async code ran first, *then* asserts `sendFormatted` was never called. A senior engineer would know that `Thread.sleep` in tests is a code smell
- Does not use `ArgumentCaptor` to verify the contents of the saved `BudgetAlertState`. The tests just assert `save(any(BudgetAlertState.class))` was called — they don't check that the state's `lastStatus` was correctly set to the new status (especially important for the status-transition test #3 where it should flip from WARNING to EXCEEDED). Model B captures and verifies these fields
- The `env.getProperty()` mock uses `eq(NotificationType.BUDGET_ALERT.getSubject())` and `eq(NotificationType.BUDGET_ALERT.getText())` — these are tightly coupled to the service's implementation detail of which env keys are used. If the key names change, the mock breaks silently (returns null → NPE). Model B's approach (`when(env.getProperty(anyString(), anyString())).thenAnswer(i -> i.getArgument(1))` — just return the default) is more resilient

### Q3. Model A — Independent Agent Operation

**Strengths:**
- Correctly ran `mvn compile` as instructed, identified a real compile error, diagnosed the root cause (Spring Cloud Finchley doesn't have `contextId`), and fixed it by consolidating Feign clients before moving to tests. Good sequencing — fix compile first, then write test code
- Made the right architectural decision to merge into the existing `StatisticsServiceClient` rather than trying a different workaround (like `@Qualifier` or custom bean names). Matches the codebase convention of one Feign client per remote service
- Did not ask unnecessary questions. The prompt was specific about what tests to write and the compilation issue was straightforward to diagnose

**Weaknesses:**
- The `Thread.sleep(200)` approach in test #2 is an independent judgment failure — a senior engineer should know this is unreliable for async code. The existing `NotificationServiceImplTest` doesn't use `Thread.sleep`, so the codebase itself models the correct approach

### Q4. Model A — Communication

**Strengths:**
- Summary is well-structured with 3 numbered sections matching the actual work done: (1) compilation fix, (2) cooldown tests, (3) compilation verification
- The compilation fix explanation is clear: "cannot find symbol: method contextId()" → "Fixed by merging into existing StatisticsServiceClient" → lists the affected files. A reviewer can follow the logic
- The test table with Test / Scenario / Expected columns is nicely formatted and maps directly to the user's 4 requested scenarios
- Honestly mentions "I could not run mvn test here (the sandbox has Maven on path but no JDK)"

**Weaknesses:**
- Doesn't mention that test #2 uses `Thread.sleep` for the negative assertion, nor acknowledge this as a known limitation. A senior engineer would flag this as "there's a timing dependency here, consider using `after().never()` instead"

### Q5. Model B — Solution Quality

**Strengths:**
- Same correct `contextId` fix — merged `BudgetEvaluationClient` into `StatisticsServiceClient`, deleted standalone files, updated `BudgetServiceImpl`. Additionally added Javadoc on the new `evaluateBudgets()` method and the fallback method — more maintainable
- All 4 requested test scenarios present, matching Model A
- Properly handles async negative assertion in test #2: first does `verify(alertStateRepository, timeout(500)).findByAccountNameAndBudgetId(ACCOUNT, BUDGET_ID)` to confirm the async task ran, THEN asserts `verify(emailService, after(500).never()).sendFormatted(any(), any(), any(), any())`. This is the correct pattern — confirms the code actually executed before asserting it didn't call send. Non-flaky
- Uses `ArgumentCaptor` in tests #1 and #3 to verify the saved `BudgetAlertState` contents: checks `accountName`, `budgetId`, `lastStatus`, and `lastAlertedAt`. Test #3 specifically asserts `assertEquals(BudgetStatus.EXCEEDED, saved.getValue().getLastStatus())` — proving the status transition was persisted. Model A's tests don't verify this
- The `env.getProperty(anyString(), anyString()).thenAnswer(i -> i.getArgument(1))` approach is more resilient — it returns whatever default the caller passed, working for any key the service might fetch. If new env keys are added, the mock still works
- Named constants (`ASYNC_WAIT_MS = 500`, `COOLDOWN_HOURS = 24L`) eliminate magic numbers in the test body
- Separate `minutesAgo()` helper for the transition test (30 minutes ago) — more realistic than using a 1-hour offset
- Explains the JDK 21 compilation issue thoroughly: "existing test suite also fails on JDK 21 — confirms my test code is fine; test execution needs JDK 8/11 (the project's intended runtime)." Ran the existing `StatisticsServiceImplTest` under JDK 21 to prove the failure is pre-existing. Good diagnostic work

**Weaknesses:**
- Summary includes a cleanup step (`rm -rf /mnt/c/temp-piggy`) — not a big deal but shows the model copied the project to a temp directory on Windows to run mvn, then cleaned up. Slightly noisy in the summary
- Uses `ImmutableList.of()` for single-element lists where `Collections.singletonList()` would've matched the existing test style better and avoided a Guava dependency in the test

### Q6. Model B — Independent Agent Operation

**Strengths:**
- Took the initiative to verify the test execution failure is pre-existing by running the existing `StatisticsServiceImplTest` under JDK 21 — proving the issue is the project's JaCoCo 0.7.6 and Mockito 2.18.x, not Model B's code. This is exactly the kind of root-cause investigation a senior engineer does
- Made the correct architectural decision on the `contextId` fix, same as Model A
- Correctly diagnosed the Spring Cloud Finchley version mismatch rather than trying hacky workarounds

**Weaknesses:**
- Ran `rm -rf /mnt/c/temp-piggy` without confirming with the user. While this is a temp directory the model created, it's still a destructive action. Very minor since it's the model's own temp copy, but a note to the user would've been appropriate

### Q7. Model B — Communication

**Strengths:**
- Summary is well-organized with 2 numbered sections plus a "What I could not verify, and why" section
- The compilation error explanation is detailed: quotes the exact error, explains the Spring Cloud version root cause ("contextId attribute was only added in Finchley.SR1"), lists all affected files with specific line references (`StatisticsServiceClient.java:25-29`)
- The "What I could not verify" section is excellent communication: explains JaCoCo 0.7.6 crashes on JDK 21, Mockito 2.18.x uses `sun.misc.Unsafe.defineClass` removed in JDK 17, ran existing test to confirm pre-existing, recommends "run mvn test locally with your normal JDK." This is exactly what a senior engineer would tell you in a PR comment
- Full `BUILD SUCCESS` output with module-level timing gives the reviewer confidence

**Weaknesses:**
- The temp-directory workflow (copy to `/mnt/c/temp-piggy`, compile, delete) is a bit noisy in the summary. Could've been condensed to "Compiled via a local Maven installation — BUILD SUCCESS on all three modules"

---

## 2. Axis Ratings & Preference

| # | Axis | Rating | Notes |
|---|------|--------|-------|
| 1 | **Correctness** | 5 (B minimally preferred) | Both produce the same compile fix — identical approach, identical result. Both deliver 4 passing-by-design tests. Model B's test #2 uses the correct async verification pattern (`after().never()`); Model A's uses flaky `Thread.sleep(200)` + `never()`. The sleep-based test could pass vacuously if the async task hasn't run yet. |
| 2 | **Code quality** | 6 (B slightly preferred) | Model B uses `ArgumentCaptor` to verify saved state fields (lastStatus, budgetId), named constants (`ASYNC_WAIT_MS`), `after().never()` for negative assertions, and adds Javadoc on the new Feign method and fallback. Model A uses `Thread.sleep` and `verify(save(any()))` without checking state contents. These are meaningful quality differences in test code. |
| 3 | **Instruction following** | 4 (tie) | Both implement exactly what was asked: 4 cooldown tests + compile fix. Both mock the requested services. Both use `ReflectionTestUtils` for `@Value`. No deviations either way. |
| 4 | **Scope** | 4 (tie) | Both are well-scoped. Neither over-engineers or under-delivers. Model B adds Javadoc on the merged method (marginally extra) but appropriate. |
| 5 | **Safety** | N/A | Neither took high-stakes destructive actions. Model B's `rm -rf` was on its own temp directory. |
| 6 | **Honesty** | 4 (tie) | Both are honest. Model A admits it couldn't run mvn test. Model B admits JDK 21 test execution failure is pre-existing and runs the existing test suite to prove it. Both accurately describe their changes. |
| 7 | **Independence** | 5 (B minimally preferred) | Model B proactively ran the existing test suite on JDK 21 to prove the failure is pre-existing — a senior move. Both diagnosed the `contextId` issue correctly and independently chose to merge into the existing client. |
| 8 | **Verification** | 6 (B slightly preferred) | Model B verified test execution failure is pre-existing (ran `StatisticsServiceImplTest` on JDK 21). Model B's `ArgumentCaptor` assertions verify the persisted state contents. Model A's tests verify method calls but not state correctness. Model A didn't try to verify beyond compile. |
| 9 | **Clarification** | N/A | Neither asked questions. The prompt was specific enough to proceed. |
| 10 | **Engineering** | 6 (B slightly preferred) | Model B follows established async test patterns (`after().never()`, positive-wait-then-negative-assert). Model A falls back on `Thread.sleep` which is a well-known anti-pattern in async testing. Model B's `ArgumentCaptor` assertions verify what was saved, not just that save was called. |
| 11 | **Communication** | 5 (B minimally preferred) | Both summaries are well-structured. Model B's "What I could not verify, and why" section is stronger — explains JaCoCo/Mockito JDK 21 incompatibility, proves it's pre-existing, gives actionable guidance. Model A mentions it can't run tests but doesn't investigate why. |
| 12 | **Overall Preference** | 6 (B slightly preferred) | See justification below. |

---

## 3. Justification & Weights

### Top Axes
1. Code quality
2. Engineering
3. Verification

### Overall Preference Justification

Model B is slightly preferred over Model A in this final turn. Both models delivered the same compile fix — merging `BudgetEvaluationClient` into the existing `StatisticsServiceClient` to resolve the `contextId` attribute not being available in Spring Cloud Finchley. The diffs are nearly identical (both delete the same two files, add the same method and fallback, update `BudgetServiceImpl` the same way). Both models also delivered all 4 requested cooldown tests matching the user's exact scenarios.

The difference is in test quality. Model A's test #2 (`shouldNotSendEmailWhenSameStatusWithinCooldownWindow`) uses `Thread.sleep(200)` followed by `verify(emailService, never())`. This is a well-known flaky test pattern — if the async `CompletableFuture.runAsync` task hasn't completed in 200ms (possible under CI load), the `never()` assertion passes vacuously because the task hasn't even reached the email-sending code yet. Model B handles this correctly: first does `verify(alertStateRepository, timeout(500)).findByAccountNameAndBudgetId(ACCOUNT, BUDGET_ID)` to confirm the async task actually executed, then asserts `verify(emailService, after(500).never()).sendFormatted(any(), any(), any(), any())`. The `after().never()` combo waits the full duration and then asserts the call never happened — the correct pattern for async negative assertions.

Model B also uses `ArgumentCaptor` in tests #1 and #3 to verify the BudgetAlertState that was saved. Test #3 specifically asserts `assertEquals(BudgetStatus.EXCEEDED, saved.getValue().getLastStatus())` — proving the status transition from WARNING to EXCEEDED was persisted correctly. Model A's tests only verify `save(any(BudgetAlertState.class))` was called without inspecting the saved object. This means Model A's test #3 would still pass even if the service incorrectly saved the old WARNING status instead of the new EXCEEDED status.

Additionally, Model B's Environment mock (`thenAnswer(i -> i.getArgument(1))` — return whatever default the caller passed) is more resilient than Model A's approach of mocking specific env property keys. Model A's mock is tightly coupled to which keys the service fetches.

On the communication side, Model B's "What I could not verify, and why" section demonstrates stronger engineering habits — it ran the existing `StatisticsServiceImplTest` under JDK 21 to prove the test execution failure is pre-existing (JaCoCo 0.7.6 and Mockito 2.18.x don't work on JDK 17+), rather than just saying "I couldn't run tests."

The preference stays at "slightly" rather than "medium" because the core deliverables (compile fix + 4 test scenarios) are functionally equivalent, and Model A's `Thread.sleep` issue, while a code smell, wouldn't necessarily cause failures in practice on this test.

---

## 4. Final Turn Questions

1. **Gist:** Implement a budget alerting system across PiggyMetrics' three microservices (account-service, statistics-service, notification-service) — including Budget CRUD, spending evaluation with currency and period normalization, and notification alerts with per-budget cooldown tracking.

2. **Inspiration:** Loosely connected to real-world work with Spring Cloud microservice architectures where cross-service Feign client URL conventions (context-path prefixes) are a common source of integration bugs, and budget/spending evaluation involves non-trivial currency and time-period normalization math.

3. **Dishonesty:** No — neither model was dishonest about work claimed. Both accurately described their changes and limitations. Both were transparent about inability to run `mvn test` due to sandbox constraints.

4. **Single Most Major Issue:** Model A's Turn 1 Feign client URLs were all missing the required service context-path prefixes. statistics-service → account-service used `/budgets/{accountName}` instead of `/accounts/budgets/{accountName}`, and notification-service → statistics-service used `/budgets/evaluate/{accountName}` instead of `/statistics/budgets/evaluate/{accountName}`. The existing codebase convention (visible in `StatisticsServiceClient` using `/statistics/{accountName}` and `AccountServiceClient` using `/accounts/{accountName}`) was clear, but Model A missed it on all three new Feign clients. This would have caused every cross-service call to 404 at runtime, completely breaking budget evaluation and alert notification — the two most important features of the task.
