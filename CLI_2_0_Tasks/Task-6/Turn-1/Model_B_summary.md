 Clean. Here's a summary of the implementation:       
                                                                                          
  What I built

  New domain model

  - domain/transaction/Transaction.java — MongoDB document with accountName, type, amount,
  currency, sourceItemTitle, status, scheduledDate, postedDate, createdDate, backfilled flag
   (indexed on accountName).
  - domain/transaction/TransactionStatus — POSTED, SKIPPED, PENDING_REVIEW.
  - domain/transaction/TransactionType — INCOME, EXPENSE.
  - domain/RecurrenceSettings.java — per-item opt-in config: enabled, anchorDate, autoPost,
  plus internally-managed nextScheduledDate and lastGeneratedDate cursor.
  - domain/Item.java — added optional recurrence field.

  Repository

  - repository/TransactionRepository + custom TransactionRepositoryCustom/Impl using
  MongoTemplate for filterable, paginated queries.

  Services

  - service/transaction/RecurrenceService — pure schedule computation. Always derives
  occurrence n from the original anchor so anchors on the 31st saturate to shorter months
  (Feb 28) without drifting (next month returns to 31).
  - service/transaction/RecurringTransactionService — the engine. Per item it collects every
   due date from nextScheduledDate through now, caps at recurring.maxBackfill (keeping the
  most recent), marks the latest occurrence as current and the rest as backfilled
  PENDING_REVIEW regardless of autoPost, advances the cursor, and triggers
  StatisticsServiceClient.updateStatistics when any transaction was generated.
  - service/transaction/RecurringTransactionJob — thin @Scheduled(cron = "${recurring.cron:0
   0 1 * * *}") wrapper.
  - service/transaction/TransactionService(+Impl) — list with filtering + pagination, get by
   id, approve (pending→posted, triggers statistics refresh), skip (pending→skipped), delete
   (pending only). All operations scope transactions to the caller's account.

  Controller

  - controller/TransactionController — REST endpoints under /current/transactions: GET (with
   status/type/from/to/page/size params, clamped to safe bounds, sorted by scheduledDate
  DESC), GET /{id}, POST /{id}/approve, POST /{id}/skip, DELETE /{id}.

  Wiring

  - AccountApplication — added @EnableScheduling.
  - AccountServiceImpl.saveChanges — now recomputes nextScheduledDate via
  RecurrenceService.firstOccurrenceAtOrAfter whenever an item's recurrence is newly enabled
  or its anchor/period changes. Disabling clears the cursor; unrelated edits preserve it so
  the user doesn't lose their place.
  - config/src/main/resources/shared/account-service.yml — new recurring.cron (default 0 0 1
   * * *) and recurring.maxBackfill (default 30) properties for Spring Cloud Config.
  - account-service/src/test/resources/application.yml — pushes the cron to once-a-year
  during tests so the scheduler is a no-op.

  Tests

  - RecurrenceServiceTest (12 tests) — validates month-end clamping (Jan 31 → Feb 28 → Mar
  31 → Apr 30 → May 31), leap-year Feb 29, quarterly/yearly/daily/hourly, nextAfter,
  firstOccurrenceAtOrAfter semantics, rejections.
  - RecurringTransactionServiceTest (7 tests) — single on-time auto-post, pending-review
  when autoPost disabled, backfill generates all missed dates with the latest as current,
  backfill cap drops oldest, skips items without recurrence / disabled / future cursor,
  end-to-end processAllAccounts.
  - TransactionServiceImplTest (8 tests) — approve/skip/delete happy paths and invalid-state
   rejections, cross-account access rejection, missing-id rejection, statistics refresh on
  approve.
  - TransactionControllerTest (6 tests) — list/get/approve/skip/delete routing, default and
  clamped pagination.
  - AccountServiceTest — added two cases covering nextScheduledDate recomputation on enable
  and clearing on disable.

  All 33 new tests pass, plus the existing AccountServiceTest (7) and AccountRepositoryTest
  (1). The 8 remaining failures in AccountControllerTest / AccountServiceApplicationTests /
  StatisticsServiceClientFallbackTest are pre-existing @SpringBootTest context-loading
  failures caused by Java 21 + Spring 5.0 cglib incompatibility in this environment (the
  project targets Java 1.8 per pom.xml:21); they fail identically on the unmodified
  baseline.