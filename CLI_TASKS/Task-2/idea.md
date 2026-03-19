Add a widget tooltip system to pygame-menu. Currently, there's no way to show contextual help text when a user hovers over a widget. Implement a tooltip feature so that any widget can have an optional tooltip string. When the mouse hovers over a widget for a configurable delay (e.g., 500ms), a styled tooltip surface should appear near the cursor, displaying the text with word-wrapping for long strings. The tooltip should be styled via new theme properties (widget_tooltip_font, widget_tooltip_font_size, widget_tooltip_font_color, widget_tooltip_background_color, widget_tooltip_border_color, widget_tooltip_border_width, widget_tooltip_padding, widget_tooltip_delay_ms). The tooltip must position itself intelligently — it should not overflow the window boundaries (flip to above/left of cursor when near edges). The tooltip should hide immediately when the mouse leaves the widget. Add a tooltip parameter to widget.set_tooltip(text) and to the WidgetManager factory methods (e.g., menu.add.button('Start', tooltip='Begin the game')). The tooltip rendering should happen in the Menu's draw pass (drawn last, on top of everything). Write tests covering tooltip creation, delay timing, boundary repositioning, and theme integration.

The 70/30 Execution Strategy:

Turn 1 (70%): Create pygame_menu/widgets/core/tooltip.py with a WidgetTooltip class that handles delay tracking, surface rendering (text with background/border/padding), and smart positioning. Add \_tooltip attribute and set_tooltip() / get_tooltip() methods to the base Widget class. Add the tooltip theme properties to themes.py. Integrate tooltip draw into Menu.draw() so it renders on top. Wire mouse-hover tracking using the existing WIDGET_MOUSEOVER system.
Turn 2 (30%): Add the tooltip= parameter to all WidgetManager factory methods (button, label, selector, etc.) in \_widgetmanager.py. Add word-wrapping for long tooltip text. Handle edge cases: tooltips inside scroll areas (coordinate translation), tooltips on disabled widgets, tooltip cleanup on widget removal. Write comprehensive tests.

Why it fits the constraint:
tooltip.py will be ~120 lines (rendering, positioning, delay logic, boundary detection). Base Widget changes add ~40 lines. Theme properties add ~30 lines. Menu draw integration adds ~30 lines. WidgetManager factory wiring adds ~50 lines across manager methods. Tests add ~100+ lines. Total: ~370-400 lines across 6 files. The difficulty is appropriate because tooltip positioning must account for scroll areas and screen boundaries, the hover-delay timer must integrate correctly with pygame's event loop, and Turn 3 will naturally surface issues like tooltips persisting through menu transitions or z-ordering problems with dropdowns.

---

## Drafted Turn 1 Prompt

> Add a widget tooltip system to pygame-menu. Currently there's no built-in way to show contextual help text when a user hovers over a widget. I'd like any widget to be able to have an optional tooltip string — when the mouse hovers over a widget for a short configurable delay, a styled tooltip should appear near the cursor showing the text. The tooltip needs to handle long text (word-wrapping), and it should position itself so it doesn't overflow the window edges (flipping to the other side of the cursor when near boundaries). The tooltip styling — font, colors, border, padding, delay — should all be configurable through the theme system, consistent with how other widget styling properties work in this codebase. It should also be possible to pass a `tooltip` parameter when creating widgets through the manager (e.g. `menu.add.button('Start', tooltip='Begin the game')`). The tooltip rendering should happen on top of everything else in the menu draw pass. Include tests for the tooltip functionality.

---

## My Opinions & Notes

**Why this prompt works:**

- It defines the full scope of the feature (what the tooltip system should do) without spelling out how to implement it. It doesn't say "create a WidgetTooltip class" or "hook into WIDGET_MOUSEOVER" or "add a `_tooltip` attribute to Widget." The models have room to approach the architecture differently.
- It's naturally scoped to a single PR — a tooltip system touches the widget base, themes, widget manager, menu draw, and tests, which is substantial but atomic.
- It's hard enough that Turn 1 won't be perfect. The models will likely miss some of: tooltip cleanup on menu transitions, coordinate translation inside scroll areas, z-ordering against dropselects, or theme validation. These give us concrete follow-up material.
- It doesn't say "make it production ready" or anything similar.

**Things to watch for in model responses:**

1. **Theme integration completeness** — did they add ALL the theme properties (font, font_size, font_color, bg_color, border_color, border_width, padding, delay_ms, max_width) AND wire them through `Theme.validate()` including color formatting, tuple conversion, and size assertions?
2. **Widget lifecycle** — does the tooltip properly reset when mouse leaves? Does it handle the case where `set_tooltip()` is called before the widget is added to a menu (i.e., before `_menu` is set)?
3. **Draw ordering** — does the tooltip actually render last in `Menu.draw()` so it appears on top of everything, including the menubar and focus overlays?
4. **Boundary repositioning** — not just flipping, but also clamping so a tooltip near a corner doesn't go offscreen in both directions.
5. **WidgetManager wiring** — did they actually pop `tooltip` from kwargs in `_filter_widget_attributes()` and apply it in `_configure_widget()`?
6. **Test coverage** — delay timing, boundary flipping, surface caching/invalidation, theme inheritance, kwargs pass-through for multiple widget types.
