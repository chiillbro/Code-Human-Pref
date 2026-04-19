 Everything's in. Summary of changes:                
                                                                   
  1. Config fail-fast — src/logpipe/config.py                      

  - New ConfigError exception
  - Config.load() now raises on: file not readable, invalid YAML,
  or non-mapping top level. Only returns defaults when path is None
   (i.e., user didn't pass --config at all)
  - cli.main() catches ConfigError → prints logpipe: error: ... and
   exits 2

  2. Anomaly detection → rolling median + IQR —
  src/logpipe/analysis.py

  - AnalysisConfig: anomaly_z removed; added baseline_windows
  (default 20) and anomaly_sensitivity (default 1.5)
  - _detect_anomalies(): for each window i ≥ K, compute median and
  IQR of windows [i-K, i); flag spike if count > median + sens·IQR,
   drop if count < median - sens·IQR. First K windows are never
  flagged.
  - Anomaly now carries baseline (the trailing median) instead of
  z_score
  - report.py, tail.py, examples/logpipe.yaml updated to match
  - Added test_spike_in_history_does_not_mask_next_spike to lock in
   the regression you described (a single big outlier in history no
   longer hides the next one)

  3. Test coverage — 6 → 57 tests

  file: tests/test_parsers.py
  covers: JSONL (full event, missing fields, epoch s/ms, level
    aliases, non-dict, malformed) · syslog (with/without PRI,
    with/without pid, malformed) · CLF (common, combined,
    status→level, - bytes, malformed) · regex (all groups, missing
    groups, bad time_format, no-match)
  ────────────────────────────────────────
  file: tests/test_filters.py
  covers: each filter type incl. time_range and unknown-type
    warn-skip · each transform type · combined filter chain via
    apply_pipeline
  ────────────────────────────────────────
  file: tests/test_analysis.py
  covers: window-boundary placement, gap-filling zeros · anomaly:
    insufficient history, spike, drop, within-bounds, constant
    baseline (no flag), constant-then-change (flag),
    outlier-in-history robustness
  ────────────────────────────────────────
  file: tests/test_config.py
  covers: None → defaults, missing file raises, bad YAML raises,
    non-mapping raises, valid file loads
  ────────────────────────────────────────
  file: tests/test_integration.py
  covers: writes temp jsonl+syslog+config, calls
    cli.main(["analyze", ...]), asserts on the JSON report (parsers

    chosen, malformed count, per-window rates, exactly one spike
    with baseline field); also asserts missing --config → exit 2
  ────────────────────────────────────────
  file: tests/test_smoke.py
  covers: kept, updated for new AnalysisConfig fields

  Run with PYTHONPATH=src python3 -m unittest discover -s tests -v.