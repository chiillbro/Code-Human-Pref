Add a widget tooltip system to pygame-menu. Currently, there's no way to show contextual help text when a user hovers over a widget. Implement a tooltip feature so that any widget can have an optional tooltip string. When the mouse hovers over a widget for a configurable delay (e.g., 500ms), a styled tooltip surface should appear near the cursor, displaying the text with word-wrapping for long strings. The tooltip should be styled via new theme properties (widget_tooltip_font, widget_tooltip_font_size, widget_tooltip_font_color, widget_tooltip_background_color, widget_tooltip_border_color, widget_tooltip_border_width, widget_tooltip_padding, widget_tooltip_delay_ms). The tooltip must position itself intelligently — it should not overflow the window boundaries (flip to above/left of cursor when near edges). The tooltip should hide immediately when the mouse leaves the widget. Add a tooltip parameter to widget.set_tooltip(text) and to the WidgetManager factory methods (e.g., menu.add.button('Start', tooltip='Begin the game')). The tooltip rendering should happen in the Menu's draw pass (drawn last, on top of everything). Write tests covering tooltip creation, delay timing, boundary repositioning, and theme integration.

The 70/30 Execution Strategy:

Turn 1 (70%): Create pygame_menu/widgets/core/tooltip.py with a WidgetTooltip class that handles delay tracking, surface rendering (text with background/border/padding), and smart positioning. Add \_tooltip attribute and set_tooltip() / get_tooltip() methods to the base Widget class. Add the tooltip theme properties to themes.py. Integrate tooltip draw into Menu.draw() so it renders on top. Wire mouse-hover tracking using the existing WIDGET_MOUSEOVER system.
Turn 2 (30%): Add the tooltip= parameter to all WidgetManager factory methods (button, label, selector, etc.) in \_widgetmanager.py. Add word-wrapping for long tooltip text. Handle edge cases: tooltips inside scroll areas (coordinate translation), tooltips on disabled widgets, tooltip cleanup on widget removal. Write comprehensive tests.

Why it fits the constraint:
tooltip.py will be ~120 lines (rendering, positioning, delay logic, boundary detection). Base Widget changes add ~40 lines. Theme properties add ~30 lines. Menu draw integration adds ~30 lines. WidgetManager factory wiring adds ~50 lines across manager methods. Tests add ~100+ lines. Total: ~370-400 lines across 6 files. The difficulty is appropriate because tooltip positioning must account for scroll areas and screen boundaries, the hover-delay timer must integrate correctly with pygame's event loop, and Turn 3 will naturally surface issues like tooltips persisting through menu transitions or z-ordering problems with dropdowns.

Potential Files Modified:

pygame_menu/widgets/core/tooltip.py (new file)
pygame_menu/widgets/core/widget.py
pygame_menu/themes.py
pygame_menu/menu.py
pygame_menu/\_widgetmanager.py
test/test_widget_tooltip.py (new file)

---

## Copilot Analysis

### Scope Verdict: ✅ GOOD — Proceed

### Draft Initial Prompt (Turn 1)

```
I want to add a tooltip system for widgets in pygame-menu. Right now there's no way to show contextual help when a user hovers over a widget. The idea is, any widget should be able to have an optional tooltip string, and when the mouse hovers over it for a configurable delay, a styled tooltip surface appears near the cursor. Create a new WidgetTooltip class in pygame_menu/widgets/core/tooltip.py that handles the delay tracking via a hover timer, renders the text with a background/border/padding, and does smart positioning so the tooltip doesn't overflow the window boundaries (flip to above or left of cursor when near edges). In the base Widget class, add a _tooltip attribute and set_tooltip()/get_tooltip() methods, and hook the tooltip's on_mouse_enter/on_mouse_leave into the existing mouseover/mouseleave methods. Add new tooltip theme properties to themes.py (widget_tooltip_font, widget_tooltip_font_size, widget_tooltip_font_color, widget_tooltip_background_color, widget_tooltip_border_color, widget_tooltip_border_width, widget_tooltip_padding, widget_tooltip_delay_ms, widget_tooltip_max_width) with sane defaults. Then integrate the tooltip drawing into Menu.draw() so it renders last, on top of everything, by checking WIDGET_MOUSEOVER for a hovered widget with a visible tooltip. Also export WidgetTooltip from the core __init__.py.
```

### My Opinions & Notes

1. **Turn 1 Focus (70%)**: Core tooltip class creation, base widget integration (set_tooltip/get_tooltip + mouseover/mouseleave hooks), theme properties with validation, and Menu.draw() integration. This is the heaviest lift.

2. **Turn 2 Focus (30%)**: Wire the `tooltip=` kwarg through `_widgetmanager.py` so all factory methods (`menu.add.button`, `menu.add.label`, etc.) accept it. Add word-wrapping support for long tooltip text. Handle edge cases like tooltips on disabled widgets.

3. **Turn 3 Focus (Polish)**: Write comprehensive tests (tooltip creation, delay timing, boundary repositioning, theme integration, kwargs pipeline). Clean up any residual issues — unused imports, style consistency, documentation.

4. **Key Complexity Points to Watch**:
   - The tooltip must integrate with the existing `WIDGET_MOUSEOVER` global — the model needs to understand this system correctly.
   - `set_tooltip()` needs to pull theme defaults from `self._menu.get_theme()` when the widget is already added to a menu. The solution.diff does this conditionally.
   - The `_wrap_text()` logic must account for effective width (max_width minus padding minus border).
   - Tooltip font is lazy-loaded in `Menu._draw_widget_tooltip()` — this is a nice optimization the solution uses.
   - `blockSignals()`-style concern: `on_mouse_enter()` should only start the timer if not already started (idempotent), and `on_mouse_leave()` must fully reset state.

5. **Steering Strategy**: If Turn 1 is missing the WidgetManager wiring, steer toward it in Turn 2. If tests are missing by Turn 2, demand them in Turn 3. The 3-turn target should be achievable here.
