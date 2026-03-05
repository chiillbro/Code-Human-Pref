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