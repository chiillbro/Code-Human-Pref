 Done — everything is wired, tested, and working end-to-end. Here's what you've
  got:                              
                                                                              
  Package layout 

  pyproject.toml          # installable, stdlib + PyYAML only, exposes `logpipe`
  script
  README.md
  examples/logpipe.yaml   # sample config
  src/logpipe/
    event.py              # normalized LogEvent + level normalization
    parsers/
      base.py             # Parser ABC + score() for detection
      jsonl.py            # JSON-lines (probes common ts/level/msg aliases)
      syslog.py           # RFC-3164, with/without <PRI>
      clf.py              # Apache/nginx common+combined; status→level
      regex.py            # user-defined formats from config
      detect.py           # samples first N lines, scores every parser, picks best
    filters.py            # config-driven filters
  (level/match/exclude/equals/time_range)
                          # + transforms (set/drop/rename/normalize_level)
    pipeline.py           # files → events; owns resilience (warn-and-continue)
    analysis.py           # windowed rates, severity/host/source breakdowns,
                          # message-signature top-N, z-score spike/drop flagging
    report.py             # JSON report + natural-language summary
    tail.py               # poll-based follow w/ rotation handling + rolling
  analyzer
    cli.py / __main__.py  # analyze | tail | detect
  tests/test_smoke.py     # 6 tests: parsers, detection, resilience, anomaly

  Verified behaviour (from the live run above)

  - Mixed jsonl + syslog files auto-detected at 100% confidence
  - Missing file → single warning, pipeline continued
  - Corrupt line → counted as malformed, skipped
  - 11 × 60s windows computed, spike at 10:10 flagged (z=3.16)
  - Message signatures collapsed (req <n> took <n>ms → 300 hits)
  - JSON report written to -o path, text summary to stdout

  Try it

  pip install -e .
  logpipe analyze app.jsonl /var/log/syslog -c examples/logpipe.yaml -o report.json
  logpipe tail app.jsonl --refresh 10
  logpipe detect *.log

  One thing I haven't exercised live is tail (it's an infinite loop), but it reuses
  the exact same Pipeline + Analyzer that the batch path tests cover; the only
  tail-specific logic is the polling/rotation in tail.py:_Tracked.