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


