Both done — your scenario now reports ◐ partial instead of
  failed, and retry timing is captured.                 
                                                                 
  Changes

  Workflow status (engine.py:_overall_status) — derived only from
   steps that actually ran (succeeded/failed/timed_out); skipped
  and cancelled are ignored:
  - all ran-steps succeeded → succeeded
  - all ran-steps failed/timed-out → failed
  - mix → partial (new)                    
  - interrupted still wins
                          
  Exit codes unchanged: succeeded → 0, partial/failed → 2,
  interrupted → 130. Pretty-printer renders partial as ◐ in
  yellow.

  Retry timing — each StepResult now tracks retry_delay (actual
  wall-clock time spent in backoff sleeps, measured around the
  interruptible wait so a Ctrl+C mid-sleep records the partial
  wait). The report gains:
  - per-step "retry_delay"
  - "summary.total_retry_delay" (sum across all steps)
                                                      
  Pretty-printer shows a Retry delay: … line under the summary
  when non-zero.

  Verified (47/47 tests)

  - Your exact case: build fails, deploy skipped, cleanup
  succeeds → status partial (4 succeeded, 1 failed, 1 skipped).                           oc  - hello.yaml (flaky step, backoff 0.2s): total_retry_delay ≈
  0.200, attributed to flaky, others 0.0.                                                 26
  - New tests: partial on mixed outcomes, partial on the
  build-fail/cleanup-succeeds branch, still failed when every
  executed step failed, and total_retry_delay ≈ sum of
  exponential backoffs.
  - README + report schema docs updated.