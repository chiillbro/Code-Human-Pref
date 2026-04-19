# Task-3: Multi-Format Log Aggregation & Anomaly Detection Pipeline

**Task ID:** Task-03  
**Type:** Greenfield (Brand New Feature)  
**Language/Stack:** Python 3.11+ (CLI application, no external dependencies)

---

## Core Request (Turn 1)

### Summary

Build a command-line log analysis tool called `logpipe`. It ingests log files in multiple formats from multiple sources simultaneously, normalizes every entry into a unified internal schema, applies user-defined filter and transformation rules, computes time-windowed aggregate metrics, performs statistical anomaly detection to flag unusual event rate spikes or drops, and produces analysis reports. The tool must handle large log files efficiently without loading them entirely into memory, and must support a streaming "tail" mode for monitoring actively-written log files.

### Detailed Requirements

**Log Ingestion:**

The tool must accept one or more file paths as input. Each file can be in one of three supported formats:
- **JSON Lines:** Each line is a valid JSON object containing at minimum a `timestamp` field and a `message` field. Other fields are treated as metadata.
- **Syslog (RFC 3164):** Standard syslog format: `<priority>Mon DD HH:MM:SS hostname process[pid]: message`. The tool must correctly parse the priority into facility and severity, and handle the absence of a year in syslog timestamps by inferring the year from the file's modification time or the current year.
- **Custom pattern:** Users can define a regex pattern in the configuration file, with named capture groups mapping to the unified schema fields (`timestamp`, `severity`, `source`, `message`). Users must be able to specify the timestamp format string (strptime-compatible) for their custom pattern.

The tool must auto-detect the format of each file by examining the first 10 non-empty lines, unless the user explicitly specifies the format per-file in the config or via CLI flags.

**Unified Schema:**

All ingested log entries must be normalized to:
- `timestamp`: timezone-aware datetime, normalized to UTC. If the source entry has no timezone info, the tool must use a configurable default timezone from the config file.
- `severity`: one of DEBUG, INFO, WARNING, ERROR, CRITICAL. Syslog numeric severities must be mapped to these levels. If the source format has no severity, default to INFO.
- `source`: the originating file path (or hostname from syslog if available).
- `message`: the log message body.
- `metadata`: a dictionary of any additional key-value pairs extracted from the source entry.

**Filter Rules:**

Users define filter rules in a YAML configuration file. Filters determine which entries pass through for aggregation and analysis. Supported filter types:
- `severity_min`: only entries at or above this severity level pass.
- `source_include`: list of source glob patterns; only matching entries pass.
- `source_exclude`: list of source glob patterns; matching entries are dropped. Exclude takes priority over include.
- `keyword_match`: entries must contain at least one of the specified keywords in their message.
- `keyword_exclude`: entries containing any of these keywords are dropped.
- `time_range`: only entries within a specified start/end datetime range pass.

Multiple filters are combined with AND logic — an entry must pass all active filters.

**Transform Rules:**

Users define transform rules in the same configuration file. Transforms modify entries after filtering:
- `extract_fields`: apply a regex to the `message` field to extract additional named capture groups into `metadata`.
- `rename_field`: rename a metadata key.
- `add_static_field`: add a constant key-value pair to `metadata` for all entries (useful for tagging sources).
- `drop_fields`: remove specified metadata keys.

Transforms are applied in the order they are defined in the config file.

**Time-Windowed Aggregation:**

The tool must compute aggregate metrics over configurable sliding time windows. The window size and slide interval are user-configurable (e.g., 5-minute windows sliding every 1 minute).

Metrics computed per window:
- Total event count.
- Event count by severity level.
- Top N sources by event count (N is configurable, default 10).
- Error rate: percentage of entries at ERROR or CRITICAL severity.
- Events-per-second (EPS) average within the window.

**Anomaly Detection:**

The tool must detect anomalous time windows where the event rate deviates significantly from the baseline. The approach:
- Compute a rolling baseline from the most recent K windows (K configurable, default 20).
- The baseline is the median event count of those K windows (+/- the interquartile range for bounds).
- A window is flagged as anomalous if its event count is above `baseline_median + (sensitivity * IQR)` or below `baseline_median - (sensitivity * IQR)`, where `sensitivity` is a configurable multiplier (default 2.0).
- Each anomaly must be tagged as either a "spike" (above upper bound) or "drop" (below lower bound).
- The first K windows (before a full baseline exists) must not be flagged — they're baseline-building only.

**Reporting:**

After analysis, the tool must produce:
- A JSON report containing: ingestion summary (files processed, total entries, entries per format), filter summary (entries dropped per filter type), aggregation results (per-window metrics), and anomaly list (window start/end, event count, baseline, deviation direction, magnitude).
- A human-readable summary printed to stdout in table format showing: total entries ingested, entries after filtering, number of anomalous windows, top anomalies by deviation magnitude, and a per-severity breakdown.

**Streaming "Tail" Mode:**

The `tail` subcommand must monitor one or more log files for new entries in real-time (similar to `tail -f`):
- Detect new lines appended to already-open files.
- Handle file rotation: if a monitored file is replaced (renamed and a new file created at the same path), the tool must detect this and switch to reading the new file from the beginning.
- Apply the same filter and transform rules in real-time.
- Print matching entries to stdout in a configurable format (raw, JSON, or a user-specified format string).
- Compute and display rolling aggregation metrics at a configurable refresh interval.
- Flag anomalies in real-time and print alerts to stderr.

**CLI Interface:**

- `logpipe analyze <file1> [file2 ...] [--config=<path>] [--report=<path>] [--format=<fmt>]` — Batch analysis mode. Process files, apply rules, compute aggregations, detect anomalies, write report. `--format` forces a specific format for all input files instead of auto-detection.
- `logpipe tail <file1> [file2 ...] [--config=<path>] [--refresh=<seconds>]` — Streaming mode. Monitor files, print matching entries and rolling metrics.
- `logpipe config init` — Generate a starter config YAML file with all options documented as comments.
- `logpipe version` — Print version.

**Error Handling & UX:**
- Malformed log entries must be counted and reported but not crash the pipeline. The tool must skip them with a warning to stderr indicating the file, line number, and reason.
- If a file cannot be opened, report the error and continue processing other files.
- Exit codes: 0 = success, 1 = config error, 2 = no files could be processed, 3 = anomalies detected (in analyze mode, so scripts can act on it).

**Project Structure:**
- `src/logpipe/` package layout.
- `pyproject.toml` with entry point for `logpipe` CLI.
- No external dependencies beyond the Python standard library.

---

## Expected PR Review Feedback (Turns 2 & 3)

### Turn 2 — Anticipated Flaws and Prescriptive Corrections

**1. Timestamp Parsing Across Formats is Brittle:**  
The model will likely hardcode a few `strptime` patterns and fail on: syslog timestamps without a year (year inference is tricky around December/January boundaries), timezone abbreviations vs. numeric offsets, fractional seconds in JSON timestamps, and ISO 8601 variants with 'T' separator vs. space. Demand a robust timestamp parser that handles all of: `2024-01-15T10:30:00Z`, `2024-01-15 10:30:00+05:30`, `Jan 15 10:30:00` (syslog, year inferred), and fractional seconds. The parser must be a single dedicated function, not inline parsing scattered throughout the codebase.

**2. Sliding Window Aggregation Has Off-By-One Errors:**  
Expect the window boundary logic to be wrong. Events landing exactly on a window boundary will either be counted in both adjacent windows or missed entirely. The slide interval logic will likely produce overlapping windows incorrectly or gaps. Demand explicit boundary semantics: windows are `[start, end)` — inclusive start, exclusive end. An event at exactly the window boundary belongs to the window starting at that time. Demand this is documented in a docstring and tested with boundary timestamps.

**3. Anomaly Detection Baseline Bootstrap is Wrong:**  
The model will probably start flagging anomalies from window 1 or 2 (before sufficient baseline exists), producing meaningless results. Or it will use mean + standard deviation instead of median + IQR, which is not robust to outliers. Demand the exact approach specified: median + IQR based on the rolling K-window history, with no flagging during the first K windows.

**4. File Streaming Mode Doesn't Handle Rotation:**  
The `tail` mode will probably just read to EOF and poll for new content. It won't handle log rotation (the inode changes when the file is rotated). Demand that the tool check the file's inode on each poll cycle and re-open the file from the beginning if the inode has changed. This is how real `tail -f` implementations work.

**5. Memory Blowup on Large Files:**  
The model will likely read entire files into memory or accumulate all entries in a list before processing. Demand streaming processing: read line by line, apply filters immediately, accumulate only aggregate counters and window buffers in memory. The only entries that need to be kept in memory are those in the current and recent windows for anomaly baseline computation.

**6. Auto-Detection Logic Too Fragile:**  
Expect the format auto-detection to check only the first line, or to use a try/except JSON parse on every line. Demand the specified approach: examine up to 10 non-empty lines, apply format heuristics (starts with `{` = JSON, starts with `<` followed by digits = syslog, else try custom pattern), and require a majority match (>= 7 of 10) to confirm the format. If inconclusive, error with a message asking the user to specify `--format`.

### Turn 3 — Tests, Linting & Polish

- Demand unit tests for: each parser (JSON, syslog, custom pattern) with edge cases (malformed lines, missing fields, timezone variants), timestamp normalization across formats, filter logic (each filter type individually, combined AND logic), transform pipeline ordering, sliding window boundary behavior (event on exact boundary, overlapping windows, gap-free coverage), anomaly detection (baseline building, spike detection, drop detection, edge case of all-zero baseline, single-entry windows).
- Demand an integration test that: creates temporary log files in each format, runs `logpipe analyze` with a config file, and asserts on the JSON report content.
- Demand a tail-mode test that writes to a file, verifies the tool picks up new entries (this can be a subprocess-based integration test).
- Fix any Turn 2 issues that were not fully resolved.
- Ensure the malformed-line counter in the final report accurately reflects the actual count from each file.

---

## Why It Fits the Constraints

**~500-600 lines of core code:** Three distinct parsers (JSON, syslog, custom regex), a normalizer with timezone handling, a filter engine with 6 filter types, a transform pipeline with 4 transform types, a sliding window aggregation engine, an anomaly detector with median/IQR baseline, a reporter with both JSON and table output, the streaming tail implementation with rotation detection, format auto-detection, and CLI wiring — each module requires 50-90 lines of substantive logic. Total core code easily reaches 550-650 lines.

**Natural difficulty:** Log processing is deceptively simple in concept but requires careful handling of: timestamp format diversity and timezone normalization (a notoriously error-prone domain), streaming processing for memory efficiency, sliding window math with correct boundary semantics, and statistical anomaly detection that's robust to edge cases. These are all genuine challenges that real log analysis tools must solve, not artificial traps.

**Guaranteed major issues:** Timestamp parsing across formats (especially syslog year inference), sliding window boundary correctness, and the streaming tail mode with log rotation handling are three areas where the model will almost certainly produce code that works for simple test cases but breaks in production-realistic scenarios. The memory efficiency requirement (true streaming, not load-all-then-process) is another area models typically get wrong on first pass. At least one qualifies as a major issue.

---

## Potential Files Modified/Created

*(Excluding test files)*

1. `pyproject.toml` — Project metadata, CLI entry point.
2. `src/logpipe/__init__.py` — Package init with version.
3. `src/logpipe/cli.py` — CLI argument parsing, subcommand dispatch, exit codes.
4. `src/logpipe/models.py` — Data classes for LogEntry, FilterConfig, TransformConfig, WindowMetrics, AnomalyResult, AnalysisReport.
5. `src/logpipe/parsers/json_parser.py` — JSON Lines log parser.
6. `src/logpipe/parsers/syslog_parser.py` — RFC 3164 syslog parser with priority decoding and year inference.
7. `src/logpipe/parsers/pattern_parser.py` — User-defined regex pattern parser with configurable timestamp format.
8. `src/logpipe/parsers/detector.py` — Format auto-detection logic (examine first N lines, heuristic matching).
9. `src/logpipe/normalizer.py` — Unified schema normalization, timezone handling, severity mapping.
10. `src/logpipe/rules_engine.py` — Filter evaluation and transform pipeline execution.
11. `src/logpipe/aggregator.py` — Sliding time window management, per-window metric computation.
12. `src/logpipe/anomaly_detector.py` — Rolling baseline computation (median + IQR), spike/drop detection.
13. `src/logpipe/reporter.py` — JSON report generation, human-readable table formatter.
14. `src/logpipe/tail.py` — File monitoring, rotation detection, real-time entry processing, rolling display.

---

## PR Overview (Reference Implementation)

### What was built

A complete CLI log analysis tool (`logpipe`) that ingests log files in JSON Lines, syslog (RFC 3164), and custom regex formats, normalizes entries to a unified schema, applies configurable filter and transform rules, computes sliding time-window aggregate metrics, detects anomalies via rolling median + IQR baseline, and produces JSON/human-readable reports. Includes a streaming tail mode with file rotation detection.

### Architecture

- **models.py** (~150 lines) — Core data classes: `Severity` IntEnum (DEBUG–CRITICAL), `LogEntry`, `FilterConfig` (6 filter types), `TransformRule`, `AggregationConfig`, `AnomalyConfig`, `CustomPattern`, `PipelineConfig`, `WindowMetrics`, `AnomalyResult`, `IngestionSummary`, `FilterSummary`, `AnalysisReport`. All report types have `to_dict()` for JSON serialization.
- **normalizer.py** (~75 lines) — Central `parse_timestamp()` function handling ISO 8601 (Z, numeric offsets, fractional seconds, T/space separator via `fromisoformat`), syslog timestamps (Mon DD HH:MM:SS with year inference and Dec→Jan boundary rollback), and custom strptime formats. `resolve_default_tz()` for configurable timezone fallback. `SYSLOG_SEVERITY_MAP` mapping 0–7 to Severity enum.
- **parsers/json_parser.py** (~35 lines) — JSON Lines parser extracting timestamp, message, severity/level, with remaining fields as metadata.
- **parsers/syslog_parser.py** (~55 lines) — RFC 3164 parser with regex for priority/timestamp/hostname/process[pid]:message. Decodes priority into facility + severity.
- **parsers/pattern_parser.py** (~40 lines) — User-defined regex pattern parser with named capture groups and configurable strptime format.
- **parsers/detector.py** (~65 lines) — Format auto-detection examining first 10 non-empty lines. Requires >=70% match for a format. Tries JSON (starts with `{` + valid parse), syslog (`<digits>`), then custom patterns. Raises with helpful message if inconclusive.
- **parsers/__init__.py** (~65 lines) — `parse_file()` generator that streams `(entry, error)` tuples line by line, handling format detection, reference year inference for syslog, and error resilience.
- **rules_engine.py** (~60 lines) — `apply_filter()` checks all 6 filter types with AND logic (exclude takes priority over include, case-insensitive keyword matching). `apply_transforms()` applies extract_fields, rename_field, add_static_field, drop_fields in order.
- **aggregator.py** (~75 lines) — `build_windows()` with explicit `[start, end)` boundary semantics using `bisect_left` for efficient range queries. Computes total count, counts by severity, top N sources, error rate, EPS per window. Aligned start with no-gap sliding.
- **anomaly_detector.py** (~80 lines) — `detect_anomalies()` with rolling median + IQR baseline from most recent K windows. First K windows are baseline-only (never flagged). Handles IQR=0 edge case (any deviation from uniform baseline is anomalous). Tags spikes vs drops with magnitude.
- **reporter.py** (~65 lines) — JSON report writer/loader and human-readable table formatter with ingestion stats, filter summary, severity breakdown, anomaly listing.
- **tail.py** (~120 lines) — `FileMonitor` class with inode-based rotation detection. `tail_files()` polls for new content, applies filter/transform pipeline in real-time, outputs raw or JSON format, periodic entry count display.
- **config.py** (~130 lines) — `load_config()` YAML parser building `PipelineConfig` from file. `CONFIG_TEMPLATE` for `config init` subcommand with all options documented as comments.
- **cli.py** (~140 lines) — Four subcommands: `analyze` (batch pipeline), `tail` (streaming), `config init`, `version`. Exit codes: 0=success, 1=config error, 2=no files processed, 3=anomalies detected.

### Key design decisions

1. **Streaming line-by-line processing** — `parse_file()` is a generator yielding one entry at a time. Filters are applied immediately. Only aggregate counters and window buffers are kept in memory.
2. **Unified timestamp parser** — Single `parse_timestamp()` function handles all formats: ISO 8601 via `datetime.fromisoformat()` (Python 3.11+ handles Z and offsets natively), syslog via regex + strptime with year inference, and custom via user-provided strptime format. All outputs are timezone-aware UTC.
3. **[start, end) window boundaries** — Explicit inclusive-start, exclusive-end semantics. An event at exactly the boundary belongs to the window starting at that time. Uses `bisect_left` for both boundaries to ensure correct counting with no double-counting or gaps.
4. **Median + IQR anomaly detection** — More outlier-robust than mean + stddev. First K windows are baseline-building only. Handles edge case of IQR=0 (constant baseline) by flagging any deviation.
5. **Inode-based rotation detection** — Tail mode checks file inode on each poll cycle and re-opens from beginning if inode changes, mirroring real `tail -f` behavior.

### Edge cases handled

- Syslog year inference with Dec→Jan boundary rollback (timestamps >1 day in the future trigger previous year assumption)
- ISO 8601 variants: Z suffix, numeric offsets (+05:30), fractional seconds, T or space separator
- Auto-detection requires 70% majority match across 10 lines; raises helpful error if inconclusive
- Malformed lines counted and reported but don't crash the pipeline
- Empty/blank-only files detected and reported
- Filter exclude takes priority over include (source_exclude checked before source_include)
- Transform pipeline respects ordering (add then rename works correctly)
- Anomaly IQR=0 case handled separately from IQR>0 case
- Multiple files with different formats processed in a single run

### Test coverage

117 tests across 6 test files:
- **test_normalizer.py** (20): timestamp parsing (ISO variants, syslog, custom format, timezone handling, year inference), timezone resolution, syslog severity mapping
- **test_parsers.py** (29): JSON parser (basic, severity, metadata, missing fields, invalid), syslog parser (with/without PID, severity mapping, single-digit day), pattern parser (basic, no match, missing groups, extra groups, source override), format detector (JSON, syslog, mixed below threshold, empty, custom pattern, few lines), file streaming (JSON file, malformed lines, blank lines)
- **test_rules_engine.py** (25): each filter type individually (severity_min, source_include, source_exclude, keyword_match, keyword_exclude, time_range), combined AND logic, no filters, each transform type (extract_fields, rename_field, add_static_field, drop_fields), transform ordering
- **test_aggregator.py** (11): empty input, single entry, boundary inclusive start, boundary exclusive end, sliding overlap, no-gap coverage, top N sources, error rate, EPS, severity counts, empty window metrics
- **test_anomaly.py** (16): first K not flagged, spike detection, drop detection, within-bounds not flagged, IQR=0 spike/drop/no-anomaly, exactly-at-boundary not flagged, sensitivity parameter effect, rolling baseline shift, median/IQR computation (empty, single, two values, four values, odd count, uniform)
- **test_reporter.py** (5): JSON roundtrip, JSON structure, human-readable format key info, severity breakdown, no anomalies display
- **test_integration.py** (11): end-to-end CLI via subprocess — JSON file, syslog file, config with severity filter, missing file (exit 2), bad config (exit 1), anomaly exit code (exit 3), malformed line counting, config init, version, multiple mixed-format files, custom pattern end-to-end

---

## Copilot Analysis & Drafted Turn 1 Prompt

### Scope Assessment

This task is **very comparable in scope to Task-3 (schemav)** — ~1,200+ lines across 14 source files. If we dump the full detailed requirements into Turn 1, models will absolutely hit context limits on Turn 2 just like Task-3.

**Strategy:** Keep Turn 1 focused on the core batch analysis pipeline at a medium level of detail. Deliberately keep the following areas vague or unmentioned so models make their own design decisions — which gives us natural Turn 2/3 critique material:

| What to include in Turn 1 | What to hold back for Turn 2/3 review |
|---|---|
| 3 log formats (JSON Lines, Syslog, custom regex) | Exact auto-detection algorithm (10 lines, 70% threshold) |
| Unified schema normalization to UTC | Syslog year-inference edge case (Dec→Jan boundary) |
| Filter + transform rules from YAML config | Don't enumerate all 6 filter types and 4 transform types |
| Time-windowed aggregation | Exact window boundary semantics [start, end) |
| Anomaly detection (just say "statistical") | Don't specify median + IQR — let model choose, then critique |
| Mention tail mode in passing | Don't detail rotation detection, inode checking, refresh intervals |
| `logpipe analyze` CLI command | Don't specify exact exit codes, exact CLI flags |
| JSON report + human-readable summary | Don't detail exact report structure |
| Error handling (malformed lines, missing files) | Memory efficiency requirement (streaming/generator) |
| `src/logpipe/` with pyproject.toml | — |

**Turn 2 natural critique material (NOT scope creep — these are "you got this wrong" or "you missed this important aspect"):**
- "Your anomaly detection uses mean+stddev — not robust to outliers. Use median + IQR rolling baseline, first K windows should be baseline-only"
- "Your tail mode completely ignores file rotation — when a log file is rotated (inode changes), you need to detect and re-read"
- "Auto-detection only checks first line — needs to sample 10 lines with 70%+ agreement"
- "Sliding window boundaries are undefined — clarify [start, end) semantics"
- "You're loading entire files into memory — need streaming/generator approach"

**Turn 3:** Tests + remaining fixes.

### Drafted Turn 1 Prompt

> I want to build a CLI log analysis tool called `logpipe` from scratch. The idea is it ingests log files that can be in different formats — JSON Lines (each line is a JSON object with `timestamp` and `message` fields), Syslog (RFC 3164 format), or a custom regex pattern the user defines — and then normalizes everything into a common schema before doing analysis on it.
>
> Here's what it needs to do:
>
> - Parse and normalize log entries from all three formats into a unified internal representation. Timestamps should be normalized to UTC. Severity levels should be mapped to a standard set (DEBUG, INFO, WARNING, ERROR, CRITICAL). Each entry should have: timestamp, severity, source, message, and optional metadata.
> - Auto-detect the format of each input file (don't make the user specify it unless they want to).
> - Support configurable filter and transform rules defined in a YAML config file. Filters decide which entries pass through for analysis, transforms can modify entries (like extracting additional fields from the message text).
> - Compute aggregate metrics over sliding time windows — things like event counts, counts by severity, error rates, events-per-second.
> - Do anomaly detection on the windowed data to flag unusual spikes or drops in event rates.
> - The main mode is a batch `analyze` subcommand: `logpipe analyze <file1> [file2...] --config=<path> --report=<path>`. It should output a JSON report file and print a human-readable summary to stdout.
> - There should also be a `tail` subcommand for real-time monitoring (like `tail -f` but with the same filter/analysis pipeline running), and utility commands like `config init` (generate a starter config) and `version`.
> - If a log line is malformed, skip it with a warning — don't crash the whole pipeline. If a file can't be opened, report it and move on to the next one.
> - Keep it to the Python standard library plus PyYAML. Set it up as a proper `src/logpipe/` package with a `pyproject.toml`.

### My Opinions

1. **The prompt is intentionally vague on algorithms.** We don't tell the model HOW to do anomaly detection (median+IQR vs mean+stddev), HOW auto-detection should work (sampling strategy), or the exact list of filter/transform types. This means models will make different design choices — which is exactly what we want for differentiation.

2. **Tail mode is mentioned but not detailed.** Most models will implement a basic `tail -f` equivalent but will almost certainly miss log rotation detection (inode checking). This is a guaranteed natural Turn 2 critique.

3. **Memory efficiency is NOT mentioned.** Models will likely read entire files into lists. In Turn 2 or 3 we can say "you're loading everything into memory — this needs to handle large files with streaming/generator processing."

4. **The prompt says what, not how.** It doesn't steer toward any specific module structure, doesn't specify data classes, doesn't give a cheatsheet. Models have room to approach it differently.

5. **No AI-steering language.** No "make it production-ready" or "write clean code."

6. **Expected major issue candidates:**
   - Anomaly detection algorithm being naive (mean+stddev instead of median+IQR, no baseline bootstrapping period)
   - Tail mode missing file rotation
   - Timestamp parsing being brittle (especially syslog year inference)
   - Window boundary semantics being wrong (off-by-one, events counted in both or neither adjacent window)
   - Memory blowup on large files
