Exactly the two files that were meant to change, and nothing else. Here's a precise account of every decision made.

---

## What changed and why

### `src/f1_data.py`

**`from datetime import date` added to the existing import**
`date.today().year` is now used in both new public functions' docstrings and referenced in the window. The `date` type belonged in the data module once the data module needed it as part of its own range parameter contract.

**`_RACE_NAME_SUFFIXES` module-level tuple**
The suffix list was previously a class variable on `RaceSelectionWindow`, which meant GUI code owned a data-shaping rule. Moving it to a module-level constant makes it private to `f1_data`, invisible to callers, and gives a single canonical definition for the two functions that both use it.

**`_normalize_event_name(event_name) -> str` private helper**
This is the core normalisation logic — strip "Grand Prix" or "GP" from the end of a FastF1 `EventName` to get a bare location name like "Monaco" or "British". It was previously duplicated as `_normalize_race_name` inside `RaceSelectionWindow`. Moving it to `f1_data` puts the logic next to the data it shapes, keeps it testable without starting a GUI, and means any future function in this module can reuse it without crossing a layer boundary.

**`get_unique_event_names(min_year, max_year) -> list[str]`**
This is the first function asked for explicitly. It iterates over every FastF1 schedule between the two year bounds, calls `_normalize_event_name` on each `EventName`, accumulates unique bare names into a set, and returns them sorted alphabetically. Individual years that fail (network timeout, missing data) are caught per-iteration, logged with `[get_unique_event_names] Could not load schedule for {year}: {exc}`, and skipped — the scan continues rather than aborting. The return value is a plain `list[str]` that slots directly into a `QComboBox` with no further processing in the GUI.

**`get_race_weekends_by_event_name(event_name, min_year, max_year) -> list[dict]`**
The second explicitly requested function. For each year in the range it loads the schedule, calls `_normalize_event_name` on each event, and appends a match when the result equals `event_name`. Every returned dict has the same keys as `get_race_weekends_by_year` plus a `"year"` key so the caller always knows which season a row belongs to. Results are sorted by year descending before returning so the data layer controls order and the GUI has nothing to sort. Per-year failures are caught, labelled, and printed; they don't abort the scan.

---

### `src/gui/race_selection.py`

**`QInputDialog` removed from imports**
It was imported but never used anywhere in the file. Removed cleanly from the tuple.

**`QPixmap` and `QFont` removed from imports**
Also imported but never used. Removed from the `QtGui` import line, which became empty enough to drop the whole line.

**`from datetime import date` added; `current_year = 2025` removed**
`self.current_year = date.today().year` is set once in `__init__` and used everywhere that the old hardcoded literal was used: populating the year combo, passing `max_year` to both workers, and as the fallback year in `_on_session_button_clicked`. The app now rolls over correctly on 1 January of every new season without a manual edit.

**`self.min_year` changed from 2010 to 2018**
FastF1 data before 2018 is known to be incomplete or absent, so the previous range was silently fetching years it could not serve. The combo now starts at 2018, the background scan starts at 2018, and both new `f1_data` functions receive 2018 as their lower bound.

**`FetchMultiYearScheduleWorker` replaced by `FetchEventNamesWorker` and `FetchFilteredRacesWorker`**
The old worker did two conceptually distinct jobs (enumerate unique names, retrieve matching events) and contained multi-year iteration and name-normalisation logic that belongs in the data layer. The two replacement workers are thin shells: each takes the exact parameters its corresponding `f1_data` function needs, makes exactly one call into that function, and emits the result. Per-year error handling is already inside `f1_data`; the worker's outer `except` catches only a total, unexpected failure and emits it as a signal.

**Error signals wired to named handlers instead of silent lambdas**
`FetchEventNamesWorker.error` connects to `_on_event_names_error`; `FetchFilteredRacesWorker.error` connects to `_on_filtered_races_error`. Both handlers `print(f"[RaceSelectionWindow] ...")` with enough context to identify which operation failed and what the message was. This is the minimum useful debugging output without adding a logging framework that the rest of the project doesn't use.

**`RACE_NAME_SUFFIXES`, `_normalize_race_name`, `all_events_cache`, `unique_race_names`, and `_update_race_filter_options` all removed**
Every piece of data logic that was living on the window class is now gone. The GUI holds no year-keyed event cache and does no name processing of its own.

**`ALL_YEARS = "All Years"` class-level sentinel**
"All Years" is prepended as the first item in the year combo. Using a class constant instead of a bare string means every comparison and every place the string is constructed references the same definition — no risk of a typo creating a silent mismatch.

**`_on_year_changed` rewritten**
The old version tried to reset the race filter to "All Races" using `blockSignals`, which caused the two controls to fight each other. The new version reads the race filter first: if a specific race is already selected, it calls `_show_filtered_races` with the new year scope (narrowing to one year or expanding back to all years); if no race is selected and "All Years" was chosen, it clears the tree and waits; otherwise it falls through to the normal single-year `load_schedule`. The race filter combo is never programmatically reset here — the controls stay independent.

**`_on_race_filter_changed` rewritten**
When a race is selected, it calls `_show_filtered_races`. When "All Races" is selected, it reads the year combo: if "All Years", clears and waits; if a specific year, loads that year's schedule. No `blockSignals` needed.

**`_show_filtered_races` rewritten**
The old version searched `self.all_events_cache` (data the GUI had accumulated itself). The new version determines `search_min`/`search_max` from the year combo, then starts a `FetchFilteredRacesWorker` which calls `get_race_weekends_by_event_name` in the background. Results arrive in `_on_filtered_races_loaded`, which populates the tree. The tree headers switch to "Year | Event | Country | Date" here rather than in `populate_schedule`.

**`populate_schedule` guard for `ALL_YEARS`**
`populate_schedule` reads `year_combo.currentText()` to stamp events with a year. If the combo somehow shows "All Years" when `populate_schedule` fires (which shouldn't happen in normal flow, since `load_schedule` is only called with a specific year), it falls back to `self.current_year` instead of crashing on `int("All Years")`.
