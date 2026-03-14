#### Description
I've implemented a complete tyre degradation analysis system for F1 Race Replay, enabling users to visualize the impact of tyre wear throughout the race in a clear and intuitive way.

#### Changes
**Features Added**
- Tyre Degradation Analysis Window: New interactive window accessible via "F1 Insights" menu that displays tyre degradation graphs.

- Multi-Driver Visualization:

    - Default mode shows all drivers simultaneously with distinct colors
    - Dropdown filter allows individual driver analysis
    - Real-time synchronization with race simulation

- Tyre Health Calculation (%):

    - Y-axis shows tyre health in percentage (100% = new, 0% = maximum life)
    - Allows negative values when tyres are used beyond expected life
    - Degradation calculated per stint, resetting when tyres are changed

- **X-Axis in Race Laps**: Synchronized with race progress, showing cumulative degradation per lap

- **Automatic Stint Reset**: Each tyre compound change starts a new stint with recalculated health

#### Technical Details
- Data Source: Collects real-time telemetry from all drivers via data stream
- Optimized Updates: Timer every 100ms to reduce lag while maintaining responsiveness
- Expected Life per Compound:
    - SOFT: 10 laps
    - MEDIUM: 15 laps
    - HARD: 20 laps
    - INTERMEDIATE: 25 laps
    - WET: 30 laps

#### Files Modified
src/gui/tyre_degradation_window.py - New analysis window with graph logic
src/gui/insights_menu.py - F1 Insights menu integration

#### Files Deleted
test_tyre_degradation.py - Non-essential test script removed

#### Testing
✅ Tested with simulated data from multiple drivers
✅ Synchronization verified with accelerated replay
✅ Degradation reset on tyre changes validated
✅ Individual driver filter confirmed

#### Notes
Degradation is calculated linearly based on compound expected life
Negative values indicate tyre overuse beyond projected life
The window updates continuously while open, even during accelerated replay