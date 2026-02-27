Session Info Banner Feature
Description
This PR adds a new Session Info Banner component that displays comprehensive race session metadata in a prominent banner at the top-center of the screen.

Problem Solved
Currently, session information (event name, circuit, date, etc.) is only visible in the window title, which is easy to miss. This feature makes essential session metadata clearly visible during race replay without cluttering the interface.

Files Changed:

src/ui_components.py - New SessionInfoComponent class (~100 lines)
src/interfaces/race_replay.py - Component integration and rendering
src/arcade_replay.py - Pass session_info parameter
main.py - Extract session metadata from FastF1 session object
Technical Details
Data Sources:
All information is sourced from the FastF1 session.event object:

Event name, country, location (circuit)
Date (formatted as "Month DD, YYYY")
Year and round number from user input
Total laps from race telemetry
Design Decisions:

Position: Top-center to avoid conflicts with existing UI (leaderboard on right, telemetry on left)
Size: Max 900px width, 60px height - compact but readable
Colors: Dark semi-transparent background (rgba 20,20,20,220), white/gray text
Layout: Two-line display for compact presentation
User Controls
[I] key - Toggle session info banner on/off
Testing
Code compiles without errors (python -m py_compile)
Banner displays correctly with all session data
Toggle functionality works as expected
Adapts to window width (responsive design)
Does not obstruct race view or other UI elements
Gracefully handles missing data fields
Backwards Compatibility
No breaking changes
Optional parameter (session_info=None) - works without data
Existing functionality unchanged
Contribution Guidelines
Following the roadmap.md contribution guidelines:

Focused PR - only session info banner feature
Clear description with technical details
Aligns with project goal of improving user experience
Maintains clean, readable code structure
Additional Notes
This feature enhances the session information display mentioned in community discussions, providing users with always-visible context about what race they're watching. The design is intentionally minimal to maintain the clean aesthetic of the application while adding significant value.
