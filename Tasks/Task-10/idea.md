#### Contributor

Summary
Adds a race comparison feature that allows users to compare two F1 races side-by-side, supporting both same-circuit and different-circuit comparisons.

Features Added

- Race comparison mode with three view modes:
  - Split view: Two tracks side-by-side with independent rendering
  - Overlay view: Single track with ghost cars from second race
  - Difference view: Color-coded position deltas between races

- Dual-track support: Each race renders on its correct circuit layout (e.g., compare Monaco vs Singapore)

- Multiple synchronization modes:
  - Lap-based: Compare lap 1 vs lap 1
  - Time-based: Align by race time (00:00 vs 00:00)
  - Distance-based: Match by track position percentage
  -

- GUI integration: New "⚡ Compare Races" button in race selection window with dual-race picker dialog

- Live leaderboards: Shows top 10 positions for both races in split view

- Playback controls: Pause, seek, speed adjustment (0.25x to 4x), view/sync mode toggling

Files Added
src/race_comparison.py - Core comparison logic and synchronization algorithms
src/interfaces/comparison_viewer.py - Arcade-based visualization with responsive rendering

Files Modified
main.py - Added comparison mode entry point and CLI arguments (--compare, --year-b, --round-b)
src/gui/race_selection.py - Added comparison dialog and GUI integration

---

Usage
CLI

```bash
python main.py --compare --year 2024 --round 5 --year-b 2023 --round-b 5
```

GUI

Click "⚡ Compare Races" button → Select two races → Click "Compare Races"

Controls

- V: Toggle view mode (Split/Overlay/Difference)
- S: Toggle sync mode (Lap/Time/Distance)
- SPACE: Pause/Resume
- ←/→: Seek backward/forward
- ↑/↓: Adjust playback speed
- R: Restart
- L: Toggle driver labels
- ESC: Close viewer

Technical Details

- Auto-adjusts to window size with responsive viewports
- Maintains track aspect ratios to prevent distortion
- Handles races with different lap counts
- Works with both Sprint and standard races

---

#### Reviewer

Just loaded it up! This looks fantastic!! Would we be able to adjust the UI styling so that it matches the existing style for the viewer?

Driver labels have also been added recently so these could be used from the existing ui :)

---

### Copilot Analysis

#### Scope Validation: ✅ GOOD

Well-scoped single feature (race comparison mode) with natural sub-components. Aligned with project roadmap. Substantial enough for 3 turns. Reviewer feedback provides organic follow-up material for UI styling and driver label reuse.

#### Draft Initial Prompt (Turn 1):

```
I want to add a race comparison feature that lets users compare two F1 races side by side. It should support comparing same-circuit races across different years (like Monza 2023 vs 2024) but also different circuits (Monaco vs Singapore). I need three view modes: a split view showing both tracks side-by-side with their own track layout and leaderboards, an overlay view with ghost cars from the second race on a single track, and a difference view that color-codes position deltas between the two races. Also need multiple sync modes - lap-based (lap 1 vs lap 1), time-based (align by race time), and distance-based (match by track position percentage). For entry points, add CLI arguments (--compare --year --round --year-b --round-b) and a "Compare Races" button in the GUI race selection window with a dual-race picker dialog. Playback controls should have pause, seek, speed adjustment (0.25x to 4x), view/sync mode toggling via keyboard. The core comparison logic should go in a new src/race_comparison.py and the arcade viewer in src/interfaces/comparison_viewer.py.
```

#### Opinions & Strategy:

- **Turn 1 focus:** Get the core feature working — comparison logic (RaceData, RaceComparison with sync algorithms), the arcade-based comparison viewer, CLI integration, and GUI dialog. This is the bulk of the work.
- **Turn 2 hooks (from reviewer):** The reviewer feedback is perfect for turn 2 — ask the model to adjust the UI styling of the comparison viewer to match the existing race replay viewer's style (use ui_components.py patterns like `LeaderboardComponent`, `SessionInfoComponent`, etc.), and to leverage the existing driver label rendering from the existing codebase instead of rolling its own.
- **Turn 3 hooks:** Cleanup pass — unused imports, hardcoded year values (current_year = 2025 should be dynamic), bare except clauses with no logging, test considerations (at minimum the pure sync/comparison logic is unit-testable), any file hygiene issues.
- **Key things to watch for in model responses:** Will they correctly understand the frame data structure from `get_race_telemetry()`? Will they handle different lap counts between races? Will they use the existing `FetchScheduleWorker` pattern for the comparison dialog? Will they follow the 2-space indent in main.py vs 4-space in other files?
