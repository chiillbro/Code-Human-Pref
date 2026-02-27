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
