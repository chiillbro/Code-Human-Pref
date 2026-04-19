All three changes are done. Here's the summary:
                                                                   
  1. Config.load() now fails hard on bad config                    

  Config.load() raises ConfigError (caught by the CLI as exit code
  1) when a user-specified --config path is missing or has invalid
  YAML. No more silent fallback to defaults.

  $ logpipe analyze app.jsonl -c typo.yaml
  logpipe: error: cannot open config file 'typo.yaml': ...

  2. Anomaly detection: median + IQR rolling baseline

  Replaced the mean+stdev approach with:
  - For each window at position i, the preceding K windows form the
   baseline (configurable via anomaly_baseline, default 20)        
  - Computes median and IQR (Q3-Q1) of that baseline       
  - Flags spike if count > median + sensitivity x IQR, drop if
  count < median - sensitivity x IQR                          
  - First K windows are never flagged (not enough history)
  - When IQR=0 (flat baseline), any deviation is flagged  
  - Config keys: anomaly_sensitivity (default 1.5),     
  anomaly_baseline (default 20)                    
                               
  A single big spike no longer inflates the baseline for
  surrounding windows.

  3. Test coverage: 6 -> 94 tests

  File: test_parsers.py    
  Tests: 30                
  Covers: JSONL edge cases (aliases, epochs, missing fields, level
    aliases, non-dict JSON, score), syslog +/- PRI + all
  severities,
     CLF common/combined/dash-bytes, regex with/without groups
  ────────────────────────────────────────
  File: test_filters.py
  Tests: 22
  Covers: Every filter type
  (level/match/exclude/equals/time_range),
    transforms (set/drop/rename), combined pipeline, unknown types,

    bad regex
  ────────────────────────────────────────
  File: test_analysis.py
  Tests: 21
  Covers: _floor boundary precision, signature collapse, windowed
    bucketing, gap filling, boundary events, level/host/message
    aggregation, no-timestamp events, anomaly
    spike/drop/within-bounds/constant-baseline/first-K-skipped
  ────────────────────────────────────────
  File: test_integration.py
  Tests: 9
  Covers: Full CLI invocation with temp files, JSON report
    validation, missing-file resilience, config filter application,

    window override, missing/invalid config errors, detect
    subcommand
  ────────────────────────────────────────
  File: test_smoke.py
  Tests: 6
  Covers: Original smoke tests (updated for new API)