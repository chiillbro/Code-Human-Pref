Done. Summary of changes:                         
                                                                 
  partial workflow status                                        

  Engine._overall_status() (bujjictl/engine.py:199) now
  classifies the run, ignoring skipped steps:

  - succeeded — no step ended failed/timed_out/cancelled
  - failed — no step ended succeeded (i.e. every non-skipped step
   went bad)
  - partial — at least one of each
  - interrupted — still wins over everything

  So your build-fails / deploy-skipped / cleanup-succeeds case
  now reports ◐ partial instead of failed. Exit code for partial
  is still 2 (something did fail), so CI/shell && chains stay
  safe; the JSON status field is what distinguishes it from a
  hard failed.

  Side effect worth knowing: examples/failure.yaml now reports
  partial too (since ok succeeds before boom fails) — which is
  more accurate than before.

  total_retry_delay

  - Each StepResult now tracks retry_delay — wall-clock seconds
  actually spent in backoff between attempts (measured around the
   Event.wait(delay), so it reflects real time, including early
  wake on interrupt).
  - summary.total_retry_delay is the sum across all steps.
  - Both appear in the JSON report; the pretty-printer shows a
  Retry delay: … line under the summary.
  - Verified on hello.yaml: flaky waits ~0.2s before its 2nd
  attempt → total_retry_delay ≈ 0.200.

  48/48 tests pass (5 new: partial/failed/partial-with-skip
  classification, retry-delay accounting, zero-retry baseline).
  README updated with the status table, exit-code mapping, and
  the new report fields.