#### Contributor

**Description**
This PR adds a real-time incident detection system that automatically identifies key moments during F1 races—overtakes, near-misses, and pit stops. Users can now navigate through these moments instantly with keyboard shortcuts, making it easier to review critical race events without scrubbing through the entire replay.

**Key Features**:

- Automatic detection of overtakes by analyzing driver position changes
- Identifies dangerous moments when drivers are in close proximity at speed
- Detects pit stop moments via speed analysis
- Interactive on-screen panel showing all detected incidents with color-coded types
- Keyboard navigation: Press N/P to jump between incidents, C to toggle the panel, F to filter by incident type
- Incidents panel auto-scrolls to keep selected incident visible
- Proper window resize handling for the incidents panel
  How it works:
  The system analyzes telemetry data once at startup to build an incidents list. Each incident stores the frame number, lap, involved drivers, and type. When users navigate with N or P, the replay automatically jumps to that moment and pauses, allowing them to watch the action unfold.

Technical improvements:

- Fixed rank comparisons in overtake detection to properly identify position changes
- Implemented scroll offset system for large incident lists
- Added robust exception handling with fallback empty state
- All 13 unit tests passing

**NOTE:** this is a open PR and no reviewer commented yet, so while you are analyzing the solution, please try to also analyze for correctness and you may make changes such that it will be our gold standard

---

## Phase 1 Analysis

### Scope Validation: PASS

The task is well-scoped — a single, cohesive feature (incident detection + UI panel + keyboard shortcuts) spanning data analysis, UI component creation, and integration with the existing replay. Not too broad (stays within one thematic feature), not trivial (requires algorithm design, UI work, integration, and tests).

### Gold-Standard Solution Notes

**In-scope (core incident detection feature):**

- `src/incident_detection.py` — `Incident` dataclass + `IncidentDetector` class that detects overtakes (position change analysis), near-misses (proximity at speed), and pit stops (speed drops). Has dedup logic and filtering helpers.
- `src/ui_components_incidents.py` — `IncidentsPanelComponent` for the on-screen panel with incident list, color coding, scroll offset, selection highlight.
- `src/interfaces/race_replay.py` — Integration: `_initialize_incidents()`, keyboard shortcuts (N/P/C/F), panel draw call, resize handling.
- `tests/test_incidents.py` — 13 unit tests for Incident, IncidentDetector, and IncidentsPanelComponent.

**Out-of-scope things in the diff (should NOT be prompted for):**

- Telemetry streaming (`TelemetryStreamServer`, `_broadcast_telemetry_state`, `enable_telemetry` param, `close()` method) — `src/services/stream.py` doesn't even exist in the repo.
- KD-Tree (`cKDTree`) optimization for `_project_to_reference` — unrelated performance tweak.

**Potential issues in the gold standard to watch for:**

1. `IncidentsPanelComponent` doesn't extend `BaseComponent` (every other UI component does). Its `draw(viewport_width, viewport_height)` signature doesn't match the standard `draw(window)` pattern.
2. Near-miss detection is O(n²) pairwise on EVERY frame — very expensive. Overtakes sample ~100 checks, near-misses should too.
3. `on_resize()` is a no-op — could at least adjust panel position for different window sizes.
4. No `__str__` test in the incident tests, and the mock frame structure uses both `driver_positions` (tuple format) and `drivers` (dict format) which could be confusing.

### Opinions

- The initial prompt should describe the feature clearly: detect overtakes/near-misses/pit stops, build an incident list at startup, display in a panel, keyboard navigation (N/P/C/F), tests.
- Keep it conversational and specific without over-prescribing implementation details. The model should figure out the separation of concerns on its own.
- Turn 2 will likely address: code style consistency (BaseComponent), edge cases, performance, any missing tests.
- Turn 3 can clean up remaining issues and push toward PR-readiness.

### Initial Prompt Draft

```
I want to add a race incident detection system to the replay viewer. It should analyze all the telemetry frame data once at startup and build a list of incidents, each incident storing the frame number, lap, involved drivers and the type of incident. For detection, I need it to identify three types: overtakes (by analyzing position changes between frames), near-misses (drivers in close proximity while both are at speed), and pit stops (via significant speed drops). There should be an on-screen incidents panel that shows all detected incidents with color-coded types, and I need keyboard navigation where N/P jumps to the next/previous incident and pauses the replay, C toggles the panel visibility, and F cycles through incident type filters. The panel should handle scrolling when the list is long and keep the selected incident visible. Put the detection logic in its own module separate from the gui and make the panel its own UI component. Write unit tests for both the detection logic and the panel component.
```
