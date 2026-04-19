 All 45 tests pass:                  
  - RecurrenceServiceTest: 15 (was 12, +3 new: nextAfterHandlesLargeHourGapEfficiently,     
  estimateNReturnsConservativeLowerBound, firstOccurrenceAtOrAfterHandlesLargeHourGap) 
  - RecurringTransactionServiceTest: 9 (was 7, +2 new:                                
  duplicateKeyExceptionSkipsDuplicateButAdvancesCursorAndSavesAccount,
  allDuplicatesStillAdvancesCursorButSkipsStatistics)                 
  - TransactionServiceImplTest: 8                    
  - TransactionControllerTest: 6 
  - AccountServiceTest: 7       

  Here's what changed:

  1. Duplicate prevention (compound unique index + graceful handling)

  - Transaction.java — added @CompoundIndex(unique=true) on (accountName, sourceItemTitle,
  scheduledDate, type). MongoDB will reject duplicate inserts.
  - RecurringTransactionService — switched from batch saveAll() to per-transaction save()
  wrapped in a try/catch(DuplicateKeyException). Duplicates are logged at debug level and
  skipped. The cursor still advances (since duplicates mean the date was already covered by
  a concurrent instance). The account is always persisted when any due dates were processed
  (even if all were duplicates) so the cursor advances, but statistics are only refreshed
  when at least one transaction was newly created.

  2. PUT for approve/skip

  - TransactionController — approve and skip now use @PutMapping instead of @PostMapping,
  matching the existing codebase convention for idempotent state transitions.
  - TransactionControllerTest — updated to use put() instead of post().

  3. O(1) starting estimate for nextAfter / firstOccurrenceAtOrAfter

  - RecurrenceService — new estimateN() method uses ChronoUnit.MONTHS/DAYS/HOURS.between()
  to compute a conservative lower-bound n, then the linear scan walks forward at most 1-2
  steps. An HOUR-period item spanning 2 years now starts at ~n=17518 instead of n=0.

  4. New tests

  - RecurringTransactionServiceTest.duplicateKeyExceptionSkipsDuplicateButAdvancesCursorAndS
  avesAccount — 3 due dates where 2 raise DuplicateKeyException: verifies only 1 is counted
  as created, cursor still advances, account still saved.
  - RecurringTransactionServiceTest.allDuplicatesStillAdvancesCursorButSkipsStatistics —
  every save throws duplicate: verifies 0 created, cursor advances, account saved,
  statistics NOT called.
  - RecurrenceServiceTest.nextAfterHandlesLargeHourGapEfficiently — HOUR anchor 2 years in
  the past, confirms correct result (would have been 17,520 iterations before the fix).
  - RecurrenceServiceTest.estimateNReturnsConservativeLowerBound — asserts the estimate
  never overshoots and is within 2 of the real value.
  - RecurrenceServiceTest.firstOccurrenceAtOrAfterHandlesLargeHourGap — same large-gap
  scenario for firstOccurrenceAtOrAfter.