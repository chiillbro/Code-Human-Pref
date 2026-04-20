Here's what changed:                                          
                                                        
  ---                                  
  1. Syslog year inference — src/logpipe/parsers/syslog.py
                                                          
  The parser now accepts a reference_date (defaults to
  date.today()). After parsing the month from the timestamp, if
  parsed_month > reference_month, the year rolls back by one.
  December entries analyzed in April get 2025 instead of 2026.

  The old default_year= API still works unchanged — it sets
  ref_month=12 so rollback never fires, keeping backward
  compatibility for callers that pin an explicit year.

  2. logpipe config init — src/logpipe/cli.py

  New subcommand: logpipe config init > logpipe.yaml. Prints a
  fully-commented starter config covering every filter type (level,
   match, exclude, equals, time_range), every transform type (set,
  drop, rename, normalize_level), custom parser definition, and all
   analysis knobs. All lines are comments so the output is valid
  YAML out of the box — users uncomment what they need.

  3. Anomaly bounds in output — src/logpipe/analysis.py, report.py,
   tail.py

  Anomaly now carries lower_bound and upper_bound (the actual
  median ± sensitivity × IQR thresholds). These appear in:
  - JSON report: "lower_bound": 10.0, "upper_bound": 10.0 alongside
   baseline                                                        
  - Text summary: 80 events (baseline 10.0, bounds [10.0, 10.0])
  - Tail live output: count=80 baseline=10 bounds=[10, 10]      
                                                          
  Tests: 57 → 62

  ┌─────────────────────────────────────────┬───────────────────┐
  │                new tests                │  what they cover  │
  ├─────────────────────────────────────────┼───────────────────┤
  │                                         │ Dec parsed in     │
  │ test_year_rollback_december_in_april    │ April → previous  │
  │                                         │ year              │
  ├─────────────────────────────────────────┼───────────────────┤
  │ test_no_rollback_same_month             │ same month →      │
  │                                         │ current year      │
  ├─────────────────────────────────────────┼───────────────────┤
  │ test_no_rollback_earlier_month          │ earlier month →   │
  │                                         │ current year      │
  ├─────────────────────────────────────────┼───────────────────┤
  │ test_default_year_compat_no_rollback    │ old default_year= │
  │                                         │  API unchanged    │
  ├─────────────────────────────────────────┼───────────────────┤
  │                                         │ all keywords      │
  │ test_config_init_prints_documented_yaml │ present, all      │
  │                                         │ lines are         │
  │                                         │ comments          │
  └─────────────────────────────────────────┴───────────────────┘

  Existing test_spike_flagged was expanded to
  test_spike_flagged_with_bounds — verifies upper_bound/lower_bound
   are present and count > upper_bound.
