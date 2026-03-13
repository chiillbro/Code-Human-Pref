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