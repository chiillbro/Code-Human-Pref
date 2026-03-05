Summary
Refactored input handling to use the Command pattern for both keyboard and mouse events, improving code consistency and maintainability.

Changes
New Command classes in src/inputs/:
TogglePauseCommand - Play/pause toggle
StartForwardCommand / StopForwardCommand - Hold-to-forward functionality
StartRewindCommand / StopRewindCommand - Hold-to-rewind functionality
IncreaseSpeedCommand / DecreaseSpeedCommand - Playback speed controls
etc...
Benefits
Unified behavior between mouse clicks and keyboard shortcuts
Button flash feedback now works consistently for all inputs
Easier to extend with new commands in the future
Better separation of concerns

---

## Copilot Analysis

### Scope Validation: PASS

This is a focused refactoring task (Command pattern for input handling). Well-scoped, achievable, not too broad or trivial.

### Drafted Initial Prompt (Turn 1)

```
Hey, I want to refactor the input handling in the race replay window. Right now, on_key_press and on_key_release in src/interfaces/race_replay.py have these long if-elif chains and the same actions are duplicated inline in ui_components.py's RaceControlsComponent for mouse clicks. I want you to apply the Command design pattern here - create a new src/inputs/ package with an abstract Command base class and individual command classes for each action (toggle pause, start/stop forward, start/stop rewind, speed increase/decrease, set speed, restart, toggle DRS zones, toggle driver labels, toggle help popup, toggle progress bar, toggle session info, close window). The window class should use a command registry dictionary for key bindings instead of the if-elif chain, and the mouse click handler in RaceControlsComponent should also reuse these same command objects so keyboard and mouse behavior stays unified and consistent
```

### My Opinions

- The prompt covers the full scope without overspecifying implementation details, it gives the model enough direction (Command pattern, src/inputs/ package, registry dict, unify mouse+keyboard) while leaving room for the model to make good engineering decisions
- Key things to watch for in model responses:
  1. Whether they keep the `from src.services.stream import TelemetryStreamServer` import in race_replay.py (it's still used in **init** for telemetry streaming)
  2. Whether they preserve existing `_broadcast_telemetry_state()` calls that happen on pause/speed changes — dropping these would silently regress telemetry broadcasting
  3. How they handle the PLAYBACK_SPEEDS removal — the gold standard replaces the discrete list with a step-based clamp approach (STEP=0.5, MAX=4.0), but models might keep the list-based approach which is also fine
  4. Whether they add `__init__.py` files to the new packages
  5. Whether they add unnecessary markdown/summary files
