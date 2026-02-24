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

The task is well-scoped: it adds a single cohesive feature (race-name-based filtering for cross-year comparison) to an existing GUI module, touching two files (`f1_data.py` and `gui/race_selection.py`). It's not too broad (it's not "redesign the GUI") and not too trivial (it requires new data functions, a new UI widget, and coordination logic between two filter controls). It aligns with the project's goals — the `Local_Setup_Guide.md` lists GUI improvements as desired feature ideas.

**Key things the gold-standard solution does:**

1. Adds `get_race_weekends_by_place(place)` and `get_all_unique_race_names()` to `f1_data.py`
2. Adds a "Select Race" combo box to the GUI alongside the year combo
3. Implements mutual-exclusion logic: selecting a race resets year to "All Years" and vice versa, using `blockSignals()` to prevent cascading events
4. Adjusts `_on_session_button_clicked` to pull `year` from event data when filtering by race name
5. Removes unused `QInputDialog` import, comments out unused `QPixmap, QFont`

**Weaknesses in the gold-standard to watch for models improving upon:**

- Naming: `place` is misleading — it filters by _race event name_, not geographic place
- Hardcoded `end_year=2025` and `current_year=2025` instead of using `date.today().year`
- Inconsistent code style: no spaces around `=` in assignments (e.g., `place=place.lower()`)
- No docstring on `get_all_unique_race_names()`
- Noise comment `#check` on `FetchScheduleWorker.run()`
- No tests whatsoever (repo has no test suite, but new pure functions are easily testable)
- `get_race_weekends_by_place` matches only exact event names (case-insensitive), no fuzzy/partial matching
- `get_all_unique_race_names()` is called at import/init time (each startup hits the cache for all years 2018–2025)
- Both modified files lose trailing newline

**What I'd want an ideal model response to do:**

1. Use `race_name` instead of `place` throughout
2. Use `date.today().year` dynamically instead of hardcoded year constants
3. Follow existing code style (4-space indent, spaces around `=`)
4. Add proper docstrings to new functions
5. Consider a background thread for `get_all_unique_race_names()` since it could be slow
6. Include a "Year" column in the schedule tree when filtering by race name (so users see which year each result is from)
7. Write unit tests for the new data functions

---

## Initial Prompt (Draft)

```
Add a race-name-based filter to the Race Selection GUI that lets users view the same
Grand Prix across different years for historical comparison.

Specifically:
- Add a new "Select Race" dropdown in `src/gui/race_selection.py` that lists all unique
  race event names from 2018 to present.
- When a user selects a race name, the schedule tree should display all instances of that
  race across available years, replacing the current year-filtered view.
- The year and race-name filters should be independent — selecting one should reset the
  other to an "All" default, so the filters don't conflict.
- Add the necessary data-fetching functions in `src/f1_data.py` to support retrieving
  race weekends by event name and listing all unique race names.
- Ensure the session launch logic correctly determines the year for sessions loaded via
  the race-name filter (since entries span multiple years).
- Clean up any unused imports you find in the files you touch.
- Make sure any year references are dynamic (use the current year) rather than hardcoded
  constants that need manual updates.
```

---

## My Opinions / Notes

- **Scope trimming from the PR:** The original PR also included "auto-minimize window" and "paused-by-default" changes. The reviewer removed both. I've scoped the prompt to only the race-name filter feature + import cleanup, which is the final accepted scope. This keeps the task focused and avoids models introducing features that would be reverted.
- **Testing expectation:** The repo has zero tests. Since the new `get_all_unique_race_names()` and `get_race_weekends_by_place()` are pure-ish functions (they call FastF1 but return simple dicts/lists), asking for tests in Turn 2 or 3 is reasonable. We should push for at least mocked unit tests.
- **Turn strategy:** Turn 1 = implement the feature. Turn 2 = code review feedback (style, naming, edge cases, robustness). Turn 3 = tests + final polish for PR readiness.
- **Performance concern:** Loading all unique race names from 2018–present on startup could be slow. A good model might suggest lazy-loading or background threading. Worth noting in review but not critical to block on.
