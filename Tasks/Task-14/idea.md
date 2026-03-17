Add a widget tooltip system to pygame-menu. Currently, there's no way to show contextual help text when a user hovers over a widget. Implement a tooltip feature so that any widget can have an optional tooltip string. When the mouse hovers over a widget for a configurable delay (e.g., 500ms), a styled tooltip surface should appear near the cursor, displaying the text with word-wrapping for long strings. The tooltip should be styled via new theme properties (widget_tooltip_font, widget_tooltip_font_size, widget_tooltip_font_color, widget_tooltip_background_color, widget_tooltip_border_color, widget_tooltip_border_width, widget_tooltip_padding, widget_tooltip_delay_ms). The tooltip must position itself intelligently — it should not overflow the window boundaries (flip to above/left of cursor when near edges). The tooltip should hide immediately when the mouse leaves the widget. Add a tooltip parameter to widget.set_tooltip(text) and to the WidgetManager factory methods (e.g., menu.add.button('Start', tooltip='Begin the game')). The tooltip rendering should happen in the Menu's draw pass (drawn last, on top of everything). Write tests covering tooltip creation, delay timing, boundary repositioning, and theme integration.

The 70/30 Execution Strategy:

Turn 1 (70%): Create pygame_menu/widgets/core/tooltip.py with a WidgetTooltip class that handles delay tracking, surface rendering (text with background/border/padding), and smart positioning. Add _tooltip attribute and set_tooltip() / get_tooltip() methods to the base Widget class. Add the tooltip theme properties to themes.py. Integrate tooltip draw into Menu.draw() so it renders on top. Wire mouse-hover tracking using the existing WIDGET_MOUSEOVER system.
Turn 2 (30%): Add the tooltip= parameter to all WidgetManager factory methods (button, label, selector, etc.) in _widgetmanager.py. Add word-wrapping for long tooltip text. Handle edge cases: tooltips inside scroll areas (coordinate translation), tooltips on disabled widgets, tooltip cleanup on widget removal. Write comprehensive tests.

Why it fits the constraint:
tooltip.py will be ~120 lines (rendering, positioning, delay logic, boundary detection). Base Widget changes add ~40 lines. Theme properties add ~30 lines. Menu draw integration adds ~30 lines. WidgetManager factory wiring adds ~50 lines across manager methods. Tests add ~100+ lines. Total: ~370-400 lines across 6 files. The difficulty is appropriate because tooltip positioning must account for scroll areas and screen boundaries, the hover-delay timer must integrate correctly with pygame's event loop, and Turn 3 will naturally surface issues like tooltips persisting through menu transitions or z-ordering problems with dropdowns.

Potential Files Modified:

pygame_menu/widgets/core/tooltip.py (new file)
pygame_menu/widgets/core/widget.py
pygame_menu/themes.py
pygame_menu/menu.py
pygame_menu/_widgetmanager.py
test/test_widget_tooltip.py (new file)
