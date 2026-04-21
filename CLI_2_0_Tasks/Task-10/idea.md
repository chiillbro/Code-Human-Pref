# Task-3: Interval-Based Resource Scheduler & Conflict Resolver

**Task ID:** Task-03  
**Type:** Greenfield (Brand New Feature)  
**Language/Stack:** Python 3.11+ (CLI application, no external dependencies)

---

## Core Request (Turn 1)

### Summary

Build a command-line tool called `schedctl` that manages shared resource schedules with timezone-aware bookings, recurring availability windows, conflict detection with multiple resolution strategies, priority-based displacement, waitlist management with automatic promotion, and utilization reporting. Think of it as a self-contained scheduling engine for managing meeting rooms, shared equipment, on-call rotations, or any resource with time-based access — backed by a local JSON database with no external infrastructure dependencies.

### Detailed Requirements

**Resources:**

Each resource has:
- A unique name (alphanumeric, hyphens, underscores only).
- An optional description.
- A configured timezone using IANA timezone names (e.g., `America/New_York`, `Europe/London`, `Asia/Tokyo`).
- One or more availability windows defining when the resource can be booked.

Resources are persisted in a local JSON database file.

**Availability Windows & Recurrence:**

Each availability window defines a time range during which the resource is bookable:
- Start time and end time (within a single day or spanning midnight).
- An optional recurrence rule: `none` (one-time window), `daily`, `weekly`, `monthly`, or `yearly`.
- Recurrence configuration:
  - `interval`: every N periods (e.g., every 2 weeks). Default 1.
  - `until`: recurrence end date (inclusive). If omitted, recurs indefinitely.
  - `except`: a list of specific dates excluded from the recurrence (holidays, maintenance windows, etc.).
  - `weekdays`: for weekly recurrence, which days of the week the window applies (e.g., `mon,tue,wed,thu,fri` for business days). Not applicable to other recurrence types.
- Monthly recurrence on day 29, 30, or 31: if the target month has fewer days, the window falls on the last day of that month (e.g., a window recurring on the 31st of each month lands on Feb 28/29, Apr 30, etc.).
- All times in availability windows are interpreted in the resource's configured timezone. A resource available "09:00–17:00 daily" in `America/New_York` must remain 09:00–17:00 Eastern Time regardless of DST transitions — the UTC equivalent shifts, but the local time stays fixed.

**Bookings:**

A booking reserves a resource for a specific time slot:
- Auto-generated unique ID.
- Resource name.
- Title describing the booking.
- Start and end datetime (timezone-aware — see Datetime format below).
- Priority: integer 1–10, where 1 is the highest priority and 10 is the lowest.
- Booker name.

Booking validation:
- The booking's time slot must fall entirely within the resource's expanded availability windows for those dates. If it doesn't, the booking must be rejected with a message explaining when the resource IS available (the nearest available slot before and after the requested time).
- The booking must not overlap with any existing bookings for the same resource (see Conflict Detection).
- Zero-duration bookings (start = end) must be rejected.
- Bookings in the past must be rejected.

**Conflict Detection & Resolution:**

When a new booking overlaps with one or more existing bookings for the same resource:
- **Default behavior:** Reject the booking with details about each conflicting booking (ID, title, time range, booker, priority).
- **`--force --strategy=priority`:** If the new booking has strictly higher priority (lower number) than ALL conflicting bookings, displace each conflicting booking to the waitlist and accept the new booking. If even one conflicting booking has equal or higher priority, reject the new booking.
- **`--force --strategy=split`:** If the new booking partially overlaps with existing bookings, split the new booking around the existing ones. This creates one or two new bookings covering only the non-overlapping portions of the originally requested time. If the split would produce any segment shorter than 15 minutes, that segment is discarded. If no usable segments remain, reject.
- **Multi-booking conflicts:** A single new booking request might overlap with multiple existing bookings. All conflicts must be identified and resolved (or rejected) as a batch.

Two bookings with touching boundaries (one ends at exactly the time another starts) are NOT in conflict.

**Waitlist:**

- Displaced bookings (from priority strategy) and explicitly waitlisted requests go onto a per-resource waitlist.
- Waitlist entries are ordered by: priority (ascending — lower number first), then creation timestamp (earlier first) as tiebreaker.
- **Automatic promotion:** When a booking is cancelled, the scheduler must check the waitlist for entries that fit into the freed time slot. The highest-priority fitting entry is promoted to an active booking.
  - "Fits" means the waitlisted booking's time range falls entirely within the freed slot AND within the resource's availability.
  - If a waitlisted entry is larger than the freed slot, it is NOT promoted (no partial fulfillment).
  - **Cascading promotion:** Promoting a waitlist entry may free additional slots elsewhere (if the promoted entry was smaller than the cancelled booking). The scheduler must re-check the waitlist after each promotion until no more promotions are possible.

**Datetime Input Format:**

- All datetime inputs accept ISO 8601 format: `2025-03-15T09:00:00-05:00`, `2025-07-01T14:30:00+00:00`, `2025-12-25T00:00:00Z`.
- If no timezone offset is provided (e.g., `2025-03-15T09:00:00`), the resource's configured timezone is used.
- All internal storage is in UTC. All user-facing display converts UTC back to the resource's configured timezone.

**CLI Interface:**

- `schedctl init [--db=<path>]` — Initialize a new JSON database file (default: `schedctl.json`).
- `schedctl resource add <name> --tz=<timezone> [--description=<desc>]` — Register a new resource.
- `schedctl resource list` — List all resources in a formatted table (name, timezone, description).
- `schedctl resource availability <name> --start=<time> --end=<time> [--recurrence=none|daily|weekly|monthly|yearly] [--interval=N] [--until=<date>] [--except=<date1,date2,...>] [--weekdays=<mon,tue,...>]` — Add an availability window to a resource.
- `schedctl book <resource> --title=<title> --start=<datetime> --end=<datetime> --booker=<name> [--priority=N] [--force --strategy=priority|split]` — Create a booking.
- `schedctl cancel <booking_id>` — Cancel a booking (triggers automatic waitlist promotion).
- `schedctl list <resource> [--from=<datetime>] [--to=<datetime>]` — List active bookings for a resource in a time range, formatted as a table.
- `schedctl waitlist <resource>` — Show the waitlist for a resource.
- `schedctl check <resource> --start=<datetime> --end=<datetime>` — Check if a time slot is available. If not, report conflicts and suggest the nearest available slots (one before and one after the requested time, within the next 7 days of availability).
- `schedctl report <resource> [--from=<datetime>] [--to=<datetime>]` — Utilization report: total available hours in range, total booked hours, utilization percentage, busiest day, number of conflicts encountered, and booking count by priority bucket (1-3 high, 4-7 medium, 8-10 low).
- `schedctl version` — Print the tool version.

**Error Handling & UX:**
- Clear, actionable messages for: booking outside availability (include next available slot), booking conflict (include conflicting booking details), invalid timezone name, invalid recurrence configuration (e.g., weekdays on monthly recurrence), booking in the past, zero-duration booking, resource not found, duplicate resource name.
- Exit codes: 0 = success, 1 = validation error, 2 = booking conflict (rejected), 3 = resource not found, 4 = database/I/O error.

**Project Structure:**
- `src/schedctl/` package layout.
- `pyproject.toml` with proper metadata and CLI entry point for `schedctl`.
- No external dependencies beyond Python 3.11+ standard library (`zoneinfo`, `datetime`, `json` — all in stdlib).

---

## Expected PR Review Feedback (Turns 2 & 3)

### Turn 2 — Anticipated Flaws and Prescriptive Corrections

**1. Recurring Availability Expansion Is Broken Across DST Transitions:**  
The model will almost certainly expand recurrence by adding fixed `timedelta` offsets (e.g., `+ timedelta(days=7)` for weekly). This breaks across Daylight Saving Time transitions. A resource available "09:00–17:00 every weekday" in `America/New_York` should stay at 09:00–17:00 Eastern Time even when clocks spring forward or fall back — the UTC offset changes but the local wall-clock time stays fixed. Adding a fixed 24-hour timedelta will shift the local time by 1 hour after a DST boundary. Demand that recurrence expansion operates on naive local datetimes in the resource's timezone — compute the next occurrence by incrementing the date component, combine with the original local time, then convert to UTC. This is the only correct approach.

**2. Monthly Recurrence on Day 31 Crashes or Silently Wraps:**  
The model will likely either raise a `ValueError` when constructing `datetime(2025, 2, 31)` or silently roll into March. Demand clamping to the last day of the month: `min(target_day, calendar.monthrange(year, month)[1])`. February 28/29, April 30, June 30, September 30, November 30 must all be handled.

**3. Overlap Detection Doesn't Check Against Expanded Recurring Availability:**  
The model will probably store availability rules as-is and check bookings against each other, but not properly expand the recurring availability windows for the specific dates in question to verify the booking falls WITHIN availability. Demand that the booking validation flow first expands all recurring availability windows for the booking's date range, then verifies the booking's time slot is fully contained within at least one expanded window, and only then checks for conflicts with other bookings.

**4. Waitlist Cascading Promotion Is Missing:**  
When a booking is cancelled and a smaller waitlist entry is promoted into the freed slot, spare time may remain. The model won't check the waitlist again after each promotion. Demand a promotion loop: cancel → find best-fitting waitlist entry → promote → check if remaining free time fits another waitlist entry → repeat until no more promotions are possible.

**5. Timezone Display Is Inconsistent:**  
The model will store times in UTC but display them in UTC rather than converting to the resource's configured timezone. Or it will use `datetime.astimezone()` with the system's local timezone instead of the resource's timezone. Demand that every user-facing time display explicitly converts from stored UTC to the resource's IANA timezone using `zoneinfo.ZoneInfo`, and that the timezone abbreviation is included in displayed times (e.g., `2025-03-15 09:00 EDT`).

**6. Priority Comparison Is Inverted:**  
Priority 1 = highest, 10 = lowest. The model will very likely compare `new.priority > existing.priority` when it should be `new.priority < existing.priority` for "higher priority." This subtle inversion will make the priority displacement strategy behave exactly backwards. Demand a named helper function (e.g., `has_higher_priority(a, b)`) with a clear docstring and unit test, used consistently everywhere priority is compared.

### Turn 3 — Tests, Linting & Polish

- Demand unit tests for recurrence expansion: daily (standard + across DST transition), weekly (specific weekdays, interval > 1), monthly (day 28 in Feb, day 31 → clamped, day 29 leap year vs. non-leap), yearly (Feb 29 leap → non-leap year clamping), exception dates, end date cutoff.
- Demand tests for overlap detection: no overlap (adjacent bookings touching exactly), partial overlap, full containment, booking outside availability (rejected with next-slot suggestion), booking spanning midnight availability window.
- Demand tests for conflict resolution: priority displacement (higher displaces lower, equal priority rejected, lower does not displace higher), split strategy (split produces valid segments, segments < 15 min discarded, no valid segments → rejected), multi-booking conflicts.
- Demand tests for waitlist: basic promotion on cancel, cascading promotion (cancel frees slot, promote small entry, remaining gap promotes another entry), no promotion when waitlisted entry is too large, priority ordering in waitlist.
- Demand tests for timezone handling: UTC storage, display conversion, booking at DST boundary, naive datetime input inherits resource timezone.
- Integration test: init database, create resource with recurring availability, create several bookings, force-displace one with priority strategy, cancel another to trigger waitlist promotion, run utilization report, verify all state.
- Edge cases: zero-duration booking (rejected), booking in the past (rejected), same-second boundary (two bookings where one ends and another starts at the same instant — not a conflict).
- Fix any unresolved Turn 2 issues.
- Ensure priority comparison helper is used consistently across all modules.

---

## Why It Fits the Constraints

**~500-600 lines of core code:** Models for Resource, Booking, AvailabilityWindow, RecurrenceRule, WaitlistEntry, Conflict (~70 lines), recurrence expansion engine with DST-correct local-time processing and monthly/yearly clamping (~100 lines), booking manager with availability checking, overlap detection, and conflict resolution strategies (priority displacement + split) (~130 lines), waitlist manager with priority ordering and cascading promotion loop (~80 lines), JSON storage layer with CRUD for resources, bookings, availability, waitlist (~60 lines), reporter with utilization calculation and nearest-slot suggestions (~60 lines), CLI wiring with datetime/timezone parsing (~80 lines). Total: ~580 lines of core code.

**Natural difficulty:** Timezone-aware recurring event scheduling is one of the most reliably bug-prone domains in software engineering — it's the reason the `tz` database exists, why Google Calendar employs dedicated timezone engineers, and why every scheduling library has a "DST Known Issues" section. Monthly recurrence on boundary dates, the interaction between recurring availability expansion and one-off booking validation, and priority-based displacement with automatic waitlist cascading all represent genuine engineering challenges. Nothing here is contrived — every requirement maps to a real scheduling system feature.

**Guaranteed major issues:** DST-incorrect recurrence expansion (adding timedelta instead of local-time-then-convert), monthly day-31 crashes, missing cascading waitlist promotion, inverted priority comparison, and incomplete availability expansion checking are all near-certain model failures. The interaction between recurrence expansion and booking validation alone is enough to guarantee at least one major issue. The DST bug in particular is a classic that even experienced engineers miss on first implementation.

---

## Potential Files Modified/Created

*(Excluding test files)*

1. `pyproject.toml` — Project metadata, `schedctl` CLI entry point.
2. `src/schedctl/__init__.py` — Package init with version.
3. `src/schedctl/cli.py` — CLI argument parsing, subcommand dispatch, datetime/timezone parsing, exit code handling.
4. `src/schedctl/models.py` — Data classes: `Resource`, `AvailabilityWindow`, `RecurrenceRule`, `Booking`, `WaitlistEntry`, `Conflict`, `UtilizationReport`.
5. `src/schedctl/recurrence.py` — Recurring availability expansion engine: DST-correct local-time expansion, monthly/yearly day clamping, exception filtering, weekday filtering.
6. `src/schedctl/scheduler.py` — Core booking logic: availability checking (expand recurrence → verify containment), overlap/conflict detection, resolution strategies (priority displacement, split), nearest-slot suggestions.
7. `src/schedctl/waitlist.py` — Waitlist management: priority-ordered insertion, automatic promotion on cancellation, cascading promotion loop, fitness checking.
8. `src/schedctl/storage.py` — JSON file-based persistence: load/save database, CRUD operations for resources, bookings, availability windows, waitlist entries.
9. `src/schedctl/reporter.py` — Utilization calculation, busiest-day detection, priority-bucket breakdown, formatted report output.

---

## Golden Reference Implementation — PR Overview

### What Was Built

Complete implementation of `schedctl` as described above. 79 tests, all passing.

### Architecture Decisions

**Models (`models.py`, ~270 lines):**
Dataclasses for `Resource`, `AvailabilityWindow`, `RecurrenceRule`, `Booking`, `WaitlistEntry`, `UtilizationReport`. Every model has symmetric `to_dict`/`from_dict` for JSON round-tripping. Exception hierarchy rooted at `SchedctlError` with `ValidationError`, `ConflictError` (carries the list of conflicting bookings so the CLI can display them), `ResourceNotFoundError`, `StorageError`. Priority comparison is centralized in a single `has_higher_priority(a, b)` helper that returns `a < b` — this is the one place where the "lower number = higher priority" convention lives, so the classic inversion bug can't spread through the codebase. `Weekday.to_int()` maps to Python's `datetime.weekday()` convention (Mon=0).

**Recurrence (`recurrence.py`, ~230 lines):**
DST-correct expansion is the whole point of this module. `iter_occurrence_dates()` operates on naive local dates only — no timezone math happens at the date level. `expand_window()` then combines each occurrence date with the window's fixed local start/end time via `datetime.combine(date, time, tzinfo=ZoneInfo(tz))` and converts to UTC with `.astimezone()`. This keeps the local wall-clock time constant across DST transitions; adding `timedelta(days=7)` to a UTC datetime would shift wall-clock time by an hour across spring-forward/fall-back, which is the single most common bug in this domain. Monthly and yearly recurrence use `calendar.monthrange()` to clamp invalid target days (Jan 31 + 1 month → Feb 28/29, Feb 29 + 1 year in a non-leap year → Feb 28). Weekly recurrence anchors to the Monday of the start date's week so "every 2 weeks" stays stable. Windows where `end_time <= start_time` are treated as spanning midnight. `expand_all()` merges overlapping windows and clips to the requested range.

**Scheduler (`scheduler.py`, ~230 lines):**
`overlaps()` uses strict `<` on both sides so touching intervals (`a.end == b.start`) do NOT conflict — this matters because a 10-11 meeting and an 11-12 meeting must coexist. `validate_booking_request()` rejects zero-duration, past, naive-datetime, out-of-range-priority, and out-of-availability requests. `create_booking()` returns `(new_bookings, displaced_bookings)`; the priority strategy requires the new booking to be strictly higher priority than ALL conflicts (not just some), and displaced bookings become waitlist entries via `WaitlistEntry.from_booking()`. The split strategy walks the requested range, subtracts conflict intervals, filters fragments shorter than `MIN_SEGMENT = 15 minutes`, and returns one booking per surviving fragment. `suggest_nearest_slots()` computes free intervals by subtracting bookings from `expand_all()` output, then picks the latest fitting slot before and the earliest fitting slot after.

**Waitlist (`waitlist.py`, ~80 lines):**
`sort_waitlist()` orders by `(priority asc, created_at asc)` — FIFO within a priority tier. `_fits()` checks both no-overlap-with-active-bookings and full-containment-in-availability. `promote_from_waitlist()` runs a cascading loop: sort, walk, promote the first entry that fits, and restart the walk. This is required because promoting one entry can free or block the window for others. The loop terminates when a full pass yields no promotion.

**Storage (`storage.py`, ~110 lines):**
`Database.save()` writes to a `.tmp` sibling file and then `Path.replace()` for atomic rename — readers never see a half-written database. `init()` refuses to overwrite an existing file. `load()` raises `StorageError` on missing files or malformed JSON (caught and exit-coded as 4 by the CLI). All domain errors route through the typed exception hierarchy.

**Reporter (`reporter.py`, ~110 lines):**
Available hours come from `expand_all()` clipped to the report range. Booked hours are intersected with availability so bookings that somehow sit outside availability (force-inserted data, for example) don't skew the total. Busiest day is computed by splitting booked intervals at local-midnight boundaries in the resource's timezone, summing seconds per local date, and taking the max. Priority buckets: 1-3 high, 4-7 medium, 8-10 low.

**CLI (`cli.py`, ~370 lines):**
argparse with `init`, `resource {add,list,availability}`, `book`, `cancel`, `list`, `waitlist`, `check`, `report`, `version`. `parse_datetime()` accepts ISO 8601 with trailing `Z` and inherits the resource's timezone when no offset is given. Exit codes: 0 OK, 1 validation, 2 conflict, 3 not-found, 4 IO. Resource names are restricted to `[A-Za-z0-9_-]+`. Cancelling a booking automatically triggers cascading waitlist promotion on that resource. When a booking fails as "outside availability", the CLI also prints the nearest-before and nearest-after slots.

### Testing Strategy (79 tests)

| Test File | Count | Scope |
|---|---|---|
| `test_recurrence.py` | 24 | Date math (day-31 clamping across months, Feb 29 across years), occurrence date iteration (none/daily/interval/weekly with weekdays/every-2-weeks/monthly clamping/yearly Feb-29 clamping/exceptions/until), DST correctness (daily recurrence across March 2025 spring-forward in America/New_York — verifies 09:00 local stays at 14:00 UTC before DST and 13:00 UTC after), availability containment (inside/outside/boundary/DST-aware), window merging |
| `test_scheduler.py` | 22 | `has_higher_priority` (3 cases), overlap (no/touching-not-conflict/partial/full containment), validation (zero-duration, past, invalid priority, outside availability), `create_booking` no-conflict and conflict-without-force, priority strategy (higher displaces, equal rejected, lower rejected, multi-conflict all-must-be-lower), split strategy (middle-cut, <15min fragments discarded, total overlap rejected), nearest-slot suggestions |
| `test_waitlist.py` | 7 | Sort by priority then created_at, basic promotion into an empty schedule, priority ordering when two entries compete for the same slot, waitlisted entry overlapping active booking not promoted, cascading promotion (two small entries fill a freed 2-hour slot while a large one remains), availability-bounds enforcement during promotion |
| `test_storage.py` | 10 | `init` creates file, `init` fails if file exists, `load` fails on missing, full round-trip (resource + booking + waitlist entry), duplicate resource rejected, `get_resource` not-found, `find_booking` missing, `bookings_for`/`waitlist_for` filter by resource, atomic save leaves no `.tmp`, corrupted JSON raises |
| `test_reporter.py` | 6 | Available hours (5 weekdays × 8h = 40h), booked-hours basic (2h of 8h = 25%), booking outside availability not counted, priority buckets across all 10 priorities, busiest-day selection, `format_report` contains key fields |
| `test_integration.py` | 10 | End-to-end CLI flows: init + list empty resources, full workflow (init → add resource → availability → book → list → conflict-rejected without force), priority displacement + waitlist population, cancel triggers cascading promotion (verified by reading JSON), report command numeric output, check command (available + not-available branches with conflict listing), version, invalid resource name rejected, unknown timezone rejected, past booking rejected |

### Key Edge Cases Covered

- DST spring-forward: recurring 09:00 local window expanded across March 8-10 2025 produces 14:00 UTC on the 8th (EST) and 13:00 UTC on the 10th (EDT) — wall-clock time stays fixed
- Jan 31 + 1 month clamps to Feb 28/29 (never raises `ValueError`)
- Feb 29 + 1 year clamps to Feb 28 in non-leap years
- Weekly recurrence with `weekdays=[mon,wed,fri]` + `interval=2` stays Monday-anchored across "every 2 weeks"
- Touching bookings (10-11 followed by 11-12) are NOT conflicts — strict `<` on both sides of overlap
- Priority strategy requires the new booking to outrank every conflict, not just one
- Split strategy discards sub-15-minute fragments silently and rejects the whole request if nothing survives
- Cascading waitlist promotion: cancelling a large booking can promote multiple smaller waitlist entries in a single operation
- Availability containment is checked against the expanded window, not the recurrence-rule metadata — midnight-spanning windows (`end_time <= start_time`) are handled
- Atomic saves via `.tmp` + `Path.replace()` — readers never observe a half-written file
- Booking times are always stored in UTC; the CLI displays them in the resource's local timezone
- `parse_datetime` inherits the resource's timezone when the input has no offset, so `2025-05-01T10:00` on an `America/New_York` resource becomes 14:00 UTC (EDT) correctly


---

## Copilot Analysis & Drafted Prompt

### My Opinions on This Task

**Scope reality check (similar to Task-3):**
- This task is in the same weight class as Task-3 (`schemav`). Both target ~580 lines across 9 modules with 10+ CLI subcommands and ~70+ tests. Task-3 ran into context-limit errors on Turn 2 because Turn 1 asked for the whole surface area at once.
- To avoid the same trap, the Turn 1 prompt below keeps the **full feature set in scope** (so follow-up turns don't count as scope creep) but intentionally **over-details the core** (models, storage, recurrence, basic booking, simpler CLI subcommands) and **under-details the advanced features** (force strategies, waitlist, check, report). This lets the model reasonably prioritize core work in Turn 1, leaving the advanced features either stubbed or skipped — which gives us natural "you missed X" footing for Turns 2 and 3.

**Where models will likely struggle (natural difficulty):**
1. **DST-broken recurrence expansion** — almost certain the model adds `timedelta(days=7)` on UTC datetimes instead of incrementing local dates. Classic bug.
2. **Monthly day-31 handling** — `datetime(2025, 2, 31)` will crash or silently wrap. Needs `calendar.monthrange()` clamping.
3. **Priority inversion** — since priority 1 = highest, models frequently flip the comparison (`>` instead of `<`) and the whole displacement logic behaves backwards.
4. **Cascading waitlist promotion** — models typically promote once on cancel and stop. The loop is easy to miss.
5. **Availability containment vs. rule metadata** — models often check against raw recurrence rules instead of expanded windows.
6. **Timezone display** — storing UTC but also *displaying* UTC (or using system local tz) instead of the resource's configured zone.

**What to watch for in evaluation:**
- Clean package layout (`src/schedctl/`, `pyproject.toml` entry point) vs. everything in one file.
- Does the model use `zoneinfo` correctly or fall back to naive UTC math?
- Atomic saves (`.tmp` + `Path.replace()`) or direct-write that can corrupt on crash?
- Touching bookings (`a.end == b.start`) — strict `<` on both sides of overlap check, or inclusive comparison that treats touching as conflict?
- Does the model stub out advanced features or ignore them entirely? (Either is fine for Turn 1 — we call them out in Turn 2.)

### Planned Turn-by-Turn Scope Split

| Area | Turn 1 | Turn 2 | Turn 3 (final) |
|---|---|---|---|
| Project scaffolding, models, JSON storage | **Full detail** | — | — |
| Recurrence expansion (daily/weekly/monthly/yearly) | **Full detail** | DST correctness fix if broken | — |
| Availability containment + overlap detection + reject-on-conflict | **Full detail** | — | — |
| CLI: `init`, `resource add/list/availability`, `book` (default), `cancel`, `list`, `version` | **Full detail** | — | — |
| `--force --strategy=priority\|split` | Mentioned | **Implement (call out as missed)** | — |
| Waitlist + `schedctl waitlist` + auto-promote on cancel | Mentioned | **Implement (call out as missed)** | Cascading loop fix |
| `schedctl check` + nearest-slot suggestions | Mentioned | **Implement (call out as missed)** | — |
| `schedctl report` (utilization, busiest day, priority buckets) | Mentioned | — | **Implement (call out as missed)** |
| Priority comparison helper (`has_higher_priority`) | — | — | Fix inversion if present |
| Full test suite | — | — | **Full detail** |

### Drafted Turn 1 Prompt

> I want to build a CLI tool called `schedctl` — a self-contained resource scheduling engine for managing things like meeting rooms, shared equipment, or on-call rotations. It uses a local JSON file as its database, no external infrastructure. Python 3.11+, no third-party dependencies (stdlib only — `zoneinfo`, `datetime`, `json` are all there).
>
> **Resources**: Each resource has a unique name (alphanumeric, hyphens, underscores only), an optional description, a configured IANA timezone (`America/New_York`, `Europe/London`, etc.), and one or more availability windows.
>
> **Availability windows**: Each window has a start time and end time (can span midnight). Optional recurrence rule: `none`, `daily`, `weekly`, `monthly`, `yearly`, with `interval` (every N periods, default 1), `until` (end date inclusive), `except` (list of excluded dates), and `weekdays` (only for weekly — which days apply). All times are in the resource's configured timezone. A window like "09:00–17:00 daily" in `America/New_York` must stay at 09:00–17:00 Eastern — the UTC offset shifts across DST but wall-clock time is fixed. Monthly recurrence on day 29/30/31 should land on the last day of the month when the target month is shorter (Feb 28/29, Apr 30, etc.).
>
> **Bookings**: A booking has an auto-generated ID, resource name, title, start/end datetime, priority (1 = highest, 10 = lowest), and booker name. Validation: booking must fit entirely inside an expanded availability window, must not overlap any existing booking for the same resource, must have non-zero duration, must not be in the past. Two bookings whose boundaries touch (one ends exactly when another starts) are **not** in conflict. Datetime inputs accept ISO 8601 (`2025-03-15T09:00:00-05:00`, trailing `Z` accepted). If the input has no timezone offset, use the resource's configured timezone. Store everything in UTC internally; display in the resource's local timezone with the timezone abbreviation (e.g., `2025-03-15 09:00 EDT`).
>
> **Conflict handling**: Default is to reject a conflicting booking with details about each conflicting booking (ID, title, time range, booker, priority). The tool should also support two override strategies via `--force --strategy=priority` (displace lower-priority conflicts to the waitlist) and `--force --strategy=split` (split the new booking around existing ones, dropping sub-15-minute fragments). Multi-booking conflicts should be handled as a batch.
>
> **Waitlist**: Per-resource waitlist ordered by priority (ascending — lower number first), then by creation timestamp as tiebreaker. When a booking is cancelled, the scheduler should check the waitlist and promote the highest-priority entry that fits into the freed slot.
>
> **CLI subcommands**:
> - `schedctl init [--db=<path>]` — initialize a JSON database (default `schedctl.json`).
> - `schedctl resource add <name> --tz=<timezone> [--description=<desc>]`
> - `schedctl resource list` — formatted table.
> - `schedctl resource availability <name> --start=<time> --end=<time> [--recurrence=...] [--interval=N] [--until=<date>] [--except=<d1,d2,...>] [--weekdays=<mon,tue,...>]`
> - `schedctl book <resource> --title=<title> --start=<datetime> --end=<datetime> --booker=<name> [--priority=N] [--force --strategy=priority|split]`
> - `schedctl cancel <booking_id>`
> - `schedctl list <resource> [--from=<datetime>] [--to=<datetime>]` — formatted table of active bookings.
> - `schedctl waitlist <resource>` — show the waitlist.
> - `schedctl check <resource> --start=<datetime> --end=<datetime>` — check availability, suggest nearest slots if not available.
> - `schedctl report <resource> [--from=<datetime>] [--to=<datetime>]` — utilization percentage, busiest day, booking count by priority bucket (1-3 high, 4-7 medium, 8-10 low).
> - `schedctl version`
>
> **Error handling**: Clear, actionable messages for the common failure cases (booking outside availability, conflict, invalid timezone, invalid recurrence config, past booking, zero-duration booking, resource not found, duplicate resource). Exit codes: 0 = success, 1 = validation error, 2 = booking conflict (rejected), 3 = resource not found, 4 = database/I/O error.
>
> **Project structure**: `src/schedctl/` package layout, `pyproject.toml` with a proper CLI entry point for `schedctl`. Stdlib only.
>
> Focus on getting the **core mechanics rock-solid first**: models, JSON persistence, the recurrence expansion engine, availability containment checking, basic overlap/conflict detection with the default reject behavior, and the CLI wiring for the commands that don't need the waitlist or force strategies (`init`, `resource add/list/availability`, `book` with default behavior, `cancel`, `list`, `version`). That's the foundation — build that end-to-end first. If you have room after the core is solid and tested by hand, sketch in the advanced pieces (force strategies, waitlist, `check`, `report`), but I'd rather see the core done well than everything done half-way.

### Why This Prompt Works
- **Full scope stated once** — every feature from the idea is mentioned, so Turn 2/3 callouts are legitimate follow-ups, not scope creep.
- **Core vs. advanced clearly separated** — the last paragraph explicitly tells the model to prioritize core work. This gives the model permission to stub/skip advanced features instead of burning context on a shallow implementation of everything.
- **No implementation hints** — no mention of `calendar.monthrange`, no "use a helper for priority comparison", no "increment local date then convert to UTC". The DST bug, monthly clamping, priority inversion, and cascading promotion are all left for the model to discover or miss.
- **No AI-steering** — no "production ready", no "clean code", no "best practices". Just what to build.
- **Concrete spec on tricky details** — the DST wording ("09:00–17:00 must stay at 09:00–17:00 Eastern"), the touching-boundaries rule, the priority 1=highest convention, and the exit codes are all stated explicitly so we can hold the model to them.
