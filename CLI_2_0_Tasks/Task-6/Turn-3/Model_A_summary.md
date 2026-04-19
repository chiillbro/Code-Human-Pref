 72 tests, 8 pre-existing errors, 0 failures. Let me verify the breakdown.
                                                                                      
● Bash(for f in /mnt/c/temp/piggy/account-service/target/surefire-reports/*.txt; do
      b=$(basename "$f"); summary=$(head -5 "$f" | grep "Tests run:"); echo "$b:      
      $summary…)
  ⎿  com.piggymetrics.account.AccountServiceApplicationTests.txt: Tests run: 1, F
     ailures: 0, Errors: 1, Skipped: 0, Time elapsed: 13.728 s <<< FAILURE! - in
     com.piggymetrics.account.AccountServiceApplicationTests
     … +29 lines (ctrl+o to expand)                                                   
         
● All passing tests:                                   
                                                                                      
  ┌──────────────────────────────────────┬───────┬────────────────────────────┐
  │              Test class              │ Count │            Type            │
  ├──────────────────────────────────────┼───────┼────────────────────────────┤
  │ TransactionControllerTest            │ 7     │ unit                       │
  ├──────────────────────────────────────┼───────┼────────────────────────────┤
  │ AccountRepositoryTest                │ 1     │ integration (pre-existing) │
  ├──────────────────────────────────────┼───────┼────────────────────────────┤
  │ TransactionRepositoryIntegrationTest │ 4     │ integration (new)          │
  ├──────────────────────────────────────┼───────┼────────────────────────────┤
  │ AccountServiceTest                   │ 12    │ unit                       │
  ├──────────────────────────────────────┼───────┼────────────────────────────┤
  │ RecurrenceServiceTest                │ 18    │ unit                       │
  ├──────────────────────────────────────┼───────┼────────────────────────────┤
  │ RecurringTransactionIntegrationTest  │ 4     │ integration (pre-existing) │
  ├──────────────────────────────────────┼───────┼────────────────────────────┤
  │ RecurringTransactionServiceTest      │ 10    │ unit                       │
  ├──────────────────────────────────────┼───────┼────────────────────────────┤
  │ TransactionServiceImplTest           │ 8     │ unit                       │
  ├──────────────────────────────────────┼───────┼────────────────────────────┤
  │ Total passing                        │ 64    │                            │
  ├──────────────────────────────────────┼───────┼────────────────────────────┤
  │ Pre-existing errors (Java 21)        │ 8     │                            │
  └──────────────────────────────────────┴───────┴────────────────────────────┘

  Here's what changed in this round:

  1. Integration tests (TransactionRepositoryIntegrationTest)

  - uniqueIndexRejectsDuplicateTransaction — saves a Transaction, then saves a second
  with the same (accountName, sourceItemTitle, scheduledDate, type) compound key but
  different amount/currency/status, asserts DuplicateKeyException is thrown by
  embedded Mongo.
  - uniqueIndexAllowsSameDateDifferentType — saves the same compound key except with
  INCOME vs EXPENSE type, verifies both insert without error (the type field
  discriminates).
  - deleteByAccountNameAndSourceItemTitleAndStatusRemovesOnlyPending — inserts 4
  transactions (POSTED, SKIPPED, PENDING_REVIEW ×2), calls the derived delete query,
  asserts only the POSTED and SKIPPED remain.
  - processAllAccountsGeneratesTransactionsInMongo — seeds an account with a daily
  recurring expense, runs processAllAccounts(Jun 3), asserts 3 transactions in Mongo
  (2 backfilled PENDING_REVIEW + 1 POSTED), and cursor advanced to Jun 4.

  Uses @DataMongoTest + @MockBean StatisticsServiceClient to replace the Feign proxy,
  plus a @TestConfiguration that registers the real RecurrenceService(UTC) and
  RecurringTransactionService.

  2. Transaction cleanup on item deletion/disable

  - TransactionRepository — added
  deleteByAccountNameAndSourceItemTitleAndStatus(String, String, TransactionStatus)
  derived query.
  - AccountServiceImpl.saveChanges — after refreshing recurrence settings, calls
  cleanupOrphanedPendingTransactions for both incomes and expenses. The method detects
   two cases:
    - Item removed: existed with recurrence enabled but missing from the updated list
  (AND not moved to the other list — see below).
    - Recurrence disabled: still present but enabled flipped to false or recurrence
  removed.

  In both cases, only PENDING_REVIEW transactions are deleted. POSTED and SKIPPED are
  historical records and stay.
  - Cross-list move fix: the method receives an otherUpdatedItems parameter (the
  complementary list). Before flagging an item for cleanup, it checks whether the
  title appears as a still-recurring item in the other list. This prevents false
  positives when a user moves an item from expenses to incomes (or vice versa).

  3. New unit tests in AccountServiceTest

  - shouldCleanupPendingTransactionsWhenRecurringItemRemoved — existing recurring item
   removed from update → delete called.
  - shouldCleanupPendingTransactionsWhenRecurrenceDisabled — item kept but
  recurrence.enabled flipped false → delete called.
  - shouldNotCleanupWhenRecurrenceStaysEnabled — item still recurring → no delete.
  - shouldNotCleanupWhenItemWasNeverRecurring — existing item had no recurrence,
  removed from update → no delete (nothing to clean).
  - shouldNotCleanupWhenItemMovedBetweenIncomeAndExpense — item moves from expenses to
   incomes with recurrence still enabled → no delete (reclassification, not removal).