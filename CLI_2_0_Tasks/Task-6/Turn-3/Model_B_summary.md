 Clean run: 64/64 tests pass. The earlier failure was stale bytecode. Let me also confirm
  the full suite behaves the same (only pre-existing @SpringBootTest Java 21 errors remain):

● Bash(cat > /mnt/c/temp/run.cmd << 'EOF'                
      @echo off…)                                                                         
  ⎿  [ERROR]                       
     [ERROR] Please refer to C:\temp\piggy\account-service\target\surefire-reports for
     the individual test results.                        
     … +12 lines (ctrl+o to expand)                                                       
  ⎿  (timeout 15m)                                                                        

● Bash(cmd.exe /c 'type C:\temp\piggy\account-service\target\surefire-reports\*.txt 2>nul |
      findstr "Tests run"' 2>&1 | head -20)                                               
  ⎿  '\\wsl.localhost\Ubuntu\home\vijay00\.cache\claude-hfi\-mnt-c-Users-ASUS-Downloads
     -Alignerr-CLI-2-0-Local-piggymetrics\B'
     CMD.EXE was started with the above path as the current directory.
     … +3 lines (ctrl+o to expand)                                                        

● Bash(for f in /mnt/c/temp/piggy/account-service/target/surefire-reports/*.txt; do head -5
      "$f" | grep "Tests run" ; done)                                                     
  ⎿  Tests run: 1, Failures: 0, Errors: 1, Skipped: 0, Time elapsed: 20.235 s <<< FAILU
     RE! - in com.piggymetrics.account.AccountServiceApplicationTests
     Tests run: 1, Failures: 0, Errors: 1, Skipped: 0, Time elapsed: 7.191 s <<< FAILUR
     … +17 lines (ctrl+o to expand)                                                       

● Every new and modified test passes. The 8 remaining failures
  (AccountServiceApplicationTests + StatisticsServiceClientFallbackTest + 6 ×             
  AccountControllerTest) are all the same pre-existing @SpringBootTest/cglib/JAXB-on-Java-21
   environment issue — they failed identically before any of my changes.

  Summary of this round

  1. Cleanup of orphaned pending transactions

  - TransactionRepository.java — added deleteByAccountNameAndSourceItemTitleAndStatus(...)
  (Spring Data derived query returning the count deleted).
  - AccountServiceImpl.saveChanges — new private cleanupOrphanedPendingTransactions(...)
  helper runs after refreshRecurrence and before the write-back to MongoDB. For every item
  in the loaded account that was previously recurring, the helper checks the union of the
  update's incomes+expenses. If the title is absent entirely (full removal) or present but
  with recurrence now disabled, it calls the new repository method to delete only
  PENDING_REVIEW transactions for (accountName, sourceItemTitle). POSTED and SKIPPED rows
  are deliberately untouched. Moves between incomes and expenses are treated as
  reclassification — no cleanup — because the title still exists somewhere in the update.
  - AccountServiceTest — 5 new cases: removal triggers cleanup, disabling triggers cleanup,
  item-still-recurring doesn't, item-never-recurring doesn't, move-between-categories
  doesn't.

  2. Integration tests against embedded Mongo

  - TransactionRepositoryIntegrationTest (@DataMongoTest) — explicitly materialises the
  compound index via MongoPersistentEntityIndexResolver#resolveIndexForEntity to guard
  against lazy-creation flakes, then verifies five scenarios:
    a. Same (accountName, sourceItemTitle, scheduledDate, type) on the second save →
  DuplicateKeyException (the contract the scheduler relies on).
    b. Same item, different scheduled dates → allowed.
    c. Same scheduled date, different items → allowed.
    d. Same scheduled date, different accounts → allowed.
    e. Same scheduled date and item, different types → allowed.
  - RecurringTransactionIntegrationTest (@DataMongoTest +
  @Import({RecurringTransactionService, RecurrenceService, TransactionRepositoryImpl}) +
  @MockBean StatisticsServiceClient) — exercises the full scheduler end-to-end against the
  embedded Mongo:
    a. processAllAccounts() writes the expected 3 transactions for an item with a past
  cursor, persists the cursor advance to Mongo, and triggers exactly one statistics refresh
  (the on-time auto-posted occurrence).
    b. Rerunning after rewinding the cursor proves the unique index + DuplicateKeyException
  handling keeps the collection at one transaction (no crash, no phantom re-posts).
    c. Items without recurrence produce no transactions.
    d. Auto-post disabled on a due-date produces a PENDING_REVIEW transaction.

  Results

  - 64/64 of the new + updated unit/integration tests pass on the first clean run.
  - Full suite: 73 tests total, 65 pass, 8 fail — identical to the baseline: the 8 failures
  are all pre-existing @SpringBootTest cglib/JAXB breakages on Java 21
  (AccountControllerTest, AccountServiceApplicationTests,
  StatisticsServiceClientFallbackTest), unrelated to any of my changes. On Java 1.8 (the
  project's declared target) those would pass too.