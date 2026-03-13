### PR 227

Contributor:

This PR introduces an improvement in the race selection GUI by adding a race-wise filter that allows users to view the same Grand Prix across different years, enabling easier historical comparison.

Additionally, minor UI improvement are introduced for a better user experience

Changes Introduced

1. Race-Wise Filtering
   Added a race-name based filter in the Race Selection UI.
   Enables easier cross-year performance comparison and historical analysis.
   Implemented independent filtering logic so users can filter either by Year or Race Name, avoiding conflicting filters.
2. Automatic Window Minimization
   Automatically minimizing the Race Selection Window when the arcade is fully initialized.
   Preventing visual clutter and improving the overall UX flow.
3. Race Replay Paused By Default
   Letting the race replay be paused by default, giving the user the freedom to
   start the replay on their own,
   setup customizations for the display before starting
   not miss the initial lap
   Improved User Experience
4. Minor Fixes
   Cleaned up unused imports.
   Motivation
   These changes aim to improve both usability and workflow efficiency for users exploring race replays, especially for multi-year comparisons and smoother UI transitions.

P.S.
This is my first-ever open source contribution and would love some feedback.
Love Formula 1, and Love the project ❤️

---

Reviewer:

Hey! Thanks for submission!! I love the idea for the country-based filtering in the menu.

I've tried to select a race, but I get hit with this error. Are you able to check this for me? :)

---

Contributor:

Hi Tom, I’ve addressed the error that was occurring. It was likely caused by two things:

1. Selecting races where telemetry data isn’t available (for example, seasons before 2018).
2. A small logic issue when switching back and forth between the Year and Race filters.
   Both cases are now handled, and I’ve tested the overall flow to make sure the filters and session loading works smoothly. Please review. <3

---

Reviewer:

Love the hard work that's gone into this!

I've removed the minimise feature because it feels like the window should still be there after I close the replay screen (personal preference).

I've also made sure that the race autoplays when the window loads :)

---

## Scope Validation

**Verdict: ✅ GOOD — Proceed**

The core task is adding a race-name-based filter for cross-year GP comparison in the Race Selection GUI. It's well-scoped — a single cohesive feature touching two files (`src/f1_data.py` for data-layer functions and `src/gui/race_selection.py` for the UI widget + filter coordination). It's neither too broad nor too trivial: it requires new data functions, a new combo box widget, mutual-exclusion logic between two filters, and adjustments to session launch logic.

**Note:** The original PR also proposed "auto-minimize window on arcade init" and "race replay paused by default", but the reviewer explicitly removed both. The solution.diff reflects only the race-name filter + import cleanup, which is the correct final scope. The prompt should NOT include those removed features.

**Gold-standard solution overview (solution.diff):**

1. `src/f1_data.py` — Adds `get_race_weekends_by_place(place)` (fetches all instances of a GP by event name across years 2018–current) and `get_all_unique_race_names()` (returns sorted set of all unique GP names). Imports `date` from `datetime`.
2. `src/gui/race_selection.py` — Adds "Select Race" combo box alongside year combo. Implements `load_by_year()` and `load_by_place()` with `blockSignals()` for independent filter behavior. Refactors `load_schedule()` to accept either a year or pre-fetched events. Adds "All Years" option to year combo. Changes year range from 2010–2025 to 2018–2025. Fixes `_on_session_button_clicked` to pull year from `ev.get("year")` instead of year combo text. Removes unused `QInputDialog` import, comments out `QPixmap`/`QFont`.

**Weaknesses in the gold-standard that models could improve on:**

- **Naming:** Uses `place` instead of `race_name` — misleading since it filters by event name, not geography
- **Hardcoded years:** `self.current_year = 2025` and `end_year=2025` default param instead of `date.today().year`
- **PEP 8 violations:** No spaces around `=` in many assignments (e.g., `place=place.lower().strip()`)
- **Synchronous loading:** `get_all_unique_race_names()` is called directly in `_setup_ui()` — blocks the GUI on startup while hitting cache/API for 8 years of schedules
- **No docstring** on `get_all_unique_race_names()` (only a short inline comment)
- **Noise comment:** `#check` left on `FetchScheduleWorker.run()`
- **No tests:** New pure functions like `get_all_unique_race_names()` and `get_race_weekends_by_place()` are easily unit-testable
- **Missing trailing newlines** at end of both modified files
- **Exact-match only:** `get_race_weekends_by_place` uses exact case-insensitive match — no fuzzy/partial name matching for GP name variations across years

---

## Initial Prompt (Draft)

```
Hey, I want to add a race-name-based filter to the Race Selection GUI so users can view the same Grand Prix across different years for historical comparison. Right now you can only browse by year and it's hard to compare how a specific GP went in different seasons. Add a "Select Race" dropdown that shows unique GP names from 2018 onward (since telemetry data before that isn't reliable), and when a race is selected, the schedule tree should display all instances of that GP across available years. The year and race filters should be independent, selecting one should reset the other so they don't conflict. Put the data-fetching logic in src/f1_data.py and the UI changes in src/gui/race_selection.py, and clean up any unused imports you find
```

---

## My Opinions / Strategy

- **Scope focus:** Only the race-name filter feature + minor import cleanup. Do NOT include auto-minimize or paused-by-default — those were rejected by the reviewer and aren't in the gold-standard diff.
- **Turn strategy:**
  - **Turn 1** → Core feature implementation (data functions in f1_data.py, combo box + filter logic in race_selection.py)
  - **Turn 2** → Code review feedback: fix naming ("place" → "race_name"), hardcoded years → dynamic, PEP 8 style, consider async loading of race names, remove noise comments, add "All Years" to year combo if missing, error logging in background workers
  - **Turn 3** → Tests (mocked unit tests for the new pure data functions) + final PR polish
- **Performance watch:** If a model loads all race names synchronously in `_setup_ui()`, flag it in Turn 2 — the GUI will freeze during startup. The ideal approach uses a background QThread similar to the existing `FetchScheduleWorker` pattern.
- **Testing expectation:** The repo has zero existing tests, but the new data functions are pure enough to mock-test. Push for tests by Turn 2/3 at the latest.
- **Differentiation from Task-1:** This is the same PR idea. To get varied model outputs, consider slightly tweaking the prompt's emphasis (e.g., stress the UX aspect of "Year" column showing in filter mode, or mention that filters must use blockSignals to avoid signal cascading) — but keep it natural and not overly prescriptive.
