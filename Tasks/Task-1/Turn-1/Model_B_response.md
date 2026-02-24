Here is a precise account of every decision made.

---

## What was changed and why

Only **`src/gui/race_selection.py`** was modified. No other file was touched.

---

### New state on `RaceSelectionWindow`

**`_ALL_RACES_LABEL`** (class constant)
The sentinel string `"— All Races —"` used as the first item in the filter combo. Centralising it as a constant means the three places that test for it (`_on_filter_changed`, `_clear_filter`, `_rebuild_filter_options`) all compare against the same literal and can never drift.

**`_all_events: dict[int, list]`**
A lazily-populated cache that maps each loaded year to its list of event dicts. This is the key enabler of cross-year comparison: instead of discarding a year's data when the user switches to another year, every load is kept in memory. When a filter is applied, `_apply_filter` walks every year in this dict, not just the currently displayed one.

**`_active_filter: str | None`**
Records which canonical race name is currently selected, or `None` when the filter is inactive. This is checked in `_on_schedule_fetched` so that loading a new year while a filter is active immediately merges the new year's matching events into the view, without requiring the user to reselect the filter.

---

### New UI elements in `_setup_ui`

**Controls row layout**
The original `year_layout` `QHBoxLayout` that only held the year selector was widened into a shared `controls_layout` that also holds the filter widgets. This keeps all top-level controls on one line without adding a second row.

**`filter_combo` (`QComboBox`)**
Starts disabled and empty (except for the sentinel). It is enabled and populated by `_rebuild_filter_options` the first time a year's data arrives. The minimum width of 240 px is set so the combo is wide enough to show names like "Saudi Arabian Grand Prix" without being clipped. Its `currentTextChanged` signal connects to `_on_filter_changed`.

**`filter_clear_btn` (`QPushButton("✕")`)**
Starts hidden (`setVisible(False)`). It becomes visible whenever `_active_filter` is set and hidden again when the filter is cleared. This gives a fast one-click escape back to the normal view without making the user open the combo and find the sentinel.

**Year column on `schedule_tree`**
The tree's `setHeaderLabels` call gains a leading `"Year"` entry, making it a five-column tree. The column is immediately hidden (`setColumnHidden(0, True)`) so the normal single-year view is identical to before. `_add_tree_rows` toggles this column's visibility based on the `show_year` argument, so it appears precisely when and only when the cross-year filter is active.

---

### New and changed methods

**`_on_schedule_fetched(events)`** (replaces the inline `populate_schedule` body)
Stores the freshly fetched events in `_all_events[year]`, then calls `_rebuild_filter_options` so the combo grows whenever a new year's data appears. Then it branches: if `_active_filter` is set it calls `_apply_filter` (merging the new year into the existing filtered view); otherwise it calls `_show_year_schedule`. The old `populate_schedule` name is kept as a forwarding alias so nothing external breaks.

**`_add_tree_rows(events, show_year)`**
A single shared method for populating the tree, replacing two places that previously had copy-pasted `QTreeWidgetItem` construction code. The `show_year` parameter controls whether column 0 is shown and whether its cell is filled. Both rendering paths (`_show_year_schedule` and `_apply_filter`) call this.

**`_show_year_schedule(year)`**
Clears the tree and calls `_add_tree_rows` with `show_year=False`, retrieving events purely from `_all_events[year]`. This is the normal mode: a single year, no Year column, identical to the original behaviour.

**`_apply_filter(canonical)`**
Iterates every year in `_all_events`, calls `_normalize_gp_name` on each event name (with a `pure_locations` vocabulary built from all loaded names), and collects every event whose canonical form matches `canonical`. Results are sorted newest-year first (then by round number within the same year), then passed to `_add_tree_rows` with `show_year=True`. The Year column appears so the user knows which edition each row belongs to.

**`_build_pure_locations(all_names)` (static)**
Takes the flat list of all event names and extracts the "clean" geographic location phrases in two passes:

- _Pass 1_: names of the form `"X Grand Prix"` contribute `x` as a single-word location key.
- _Pass 2_: names of the form `"X Y Grand Prix"` contribute `"x y"` only if `y` is **not** already a single-word key. This rejects pairs like `"rolex australian"` (because `"australian"` is already known) while keeping genuine two-word geographies like `"las vegas"`, `"abu dhabi"`, `"saudi arabian"`, and `"united states"`.

The vocabulary improves with each additional year loaded. Loading only 2024 (whose data has sponsor prefixes) yields the best available normalisation; adding 2023 (which has clean names) gives the algorithm the single-word anchors it needs to strip those prefixes.

**`_normalize_gp_name(name, pure_locations)` (static)**
Strips a leading `"Formula 1"` prefix, then finds everything before `"Grand Prix"`. It walks the prefix words from right to left, building progressively longer suffixes (1 word, then 2, then 3…) and returning as soon as a suffix appears in `pure_locations`. This means `"Gulf Air Bahrain Grand Prix"` tries `"bahrain"` first (matched → `"Bahrain Grand Prix"`), while `"Las Vegas Grand Prix"` tries `"vegas"` (no match), then `"las vegas"` (matched → `"Las Vegas Grand Prix"`). The fallback to the last two words handles any future name that doesn't match any known location.

**`_rebuild_filter_options()`**
Collects all event names from all loaded years in one flat list, builds the `pure_locations` vocabulary once across all of them, produces a sorted set of canonical names, and repopulates the combo. It uses `blockSignals(True/False)` around the combo manipulation to prevent `_on_filter_changed` from firing during the rebuild. The previously selected text is preserved by searching for it in the new item list and restoring the index; if it no longer exists the combo falls back to index 0 (the sentinel).

**`_on_filter_changed(text)`**
Slot for `filter_combo.currentTextChanged`. If the sentinel is chosen it sets `_active_filter = None`, hides the clear button, and calls `_show_year_schedule`. Otherwise it sets `_active_filter`, shows the clear button, and calls `_apply_filter`. Either way it hides the session panel because the currently selected row is no longer valid.

**`_clear_filter()`**
Called by the `✕` button. Resets `_active_filter` to `None`, hides the clear button, resets the combo to index 0 using `blockSignals` to avoid the redundant slot invocation, and calls `_show_year_schedule` for the current year.

**`on_race_clicked`**
One new line: `year_override = ev.get("year")`. When the filter is active, `_apply_filter` injects a `"year"` key into each event dict so the Year column has something to display. That same key is captured here and forwarded to all session buttons via the lambda closure.

**`_on_session_button_clicked(ev, session_label, year_override=None)`**
Gains the optional `year_override` parameter. When it is not `None` (i.e., the user clicked a row in filter mode), the override is used as `year` instead of the year_combo value. This ensures the correct season's data is loaded even if the year_combo still shows a different year.
