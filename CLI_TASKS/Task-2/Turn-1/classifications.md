# Turn 1 — Classifications

## Prompt (Source of Truth)

> hey there, can you help by adding a widget tooltip system to this app, currently there is no way to show contextual help text when I hover over a widget, so I would like any widget to be able to have a optional tooltip string, so here is how I want it, when the mouse hovers over a widget for a short delay (note this should be configurable), a styled tooltip should appear near the cursor showing the text. one more thing, the tooltip rendering should happen on top of everything else in the menu draw pass

---

## 1. Rationale Support (Pros & Cons)

### Model A Pros

- Introduces a dedicated `_TooltipManager` class inside `menu.py` that cleanly encapsulates tooltip state tracking (hover timing, caching, rendering) and avoids spreading tooltip logic across unrelated classes.
- Surface caching in `_TooltipManager` (`_cached_widget` / `_cached_surface`) avoids rebuilding the tooltip texture every frame, which is good for performance.
- Theme validation in `themes.py` is reasonably thorough — it checks types, handles color formatting via `_format_color_opacity`, and validates the padding tuple with a length + element check similar to how `widget_padding` is validated.
- The `set_tooltip()` method on `Widget` is clean and minimal — it stores a plain string, keeping the Widget layer simple while the Manager handles rendering.
- Tooltip draw is correctly placed after `_decorator.draw_post(surface)` in `Menu.draw()`, ensuring it renders on top of everything in the draw pass.

### Model A Cons

- Theme properties use a non-standard naming convention (`tooltip_background_color`, `tooltip_font_color`, etc.) instead of following the codebase's established `widget_tooltip_*` prefix pattern. Every other widget-related theme property uses `widget_` as prefix.
- No word-wrapping support for long tooltip text — `_build_surface` renders the text as a single line. The prompt asks for "styled tooltip" and long text will just clip or extend off-screen.
- No `get_tooltip()` method on `Widget`. There's `set_tooltip()` but no corresponding getter, which breaks the symmetric accessor pattern used everywhere else in the Widget class (e.g., `get_title`, `set_title`).
- Missing a dedicated tooltip font theme property — `_build_surface` uses `theme.widget_font` as the tooltip font, which means the tooltip always shares the same font as the widget text. There's no way to style the tooltip font independently.
- No tooltip-related tests at all. The diff doesn't include any test file. For a feature that involves hover timing, positioning, and rendering, having zero tests is a notable omission.
- Boundary clamping only checks right and bottom edges. If the tooltip is flipped to the left (`mx - sw - 6`) and the result is negative, it can go off the left side of the screen. No `max(0, ...)` clamping is applied.

### Model B Pros

- Uses the codebase's naming convention (`widget_tooltip_*`) for all theme properties, consistent with how every other widget styling property is named in `themes.py`.
- Provides 10 tooltip theme properties including extras like `widget_tooltip_font`, `widget_tooltip_font_antialias`, and `widget_tooltip_margin` — giving full independent control over tooltip styling separate from the widget font.
- Theme validation is comprehensive: `isinstance` checks, `assert_font`, `_format_color_opacity` for all 3 color properties, `_vec_to_tuple` for margin and padding, and size assertions with descriptive error messages.
- Adds `get_mouseover_time()` as a public utility method on Widget, which is genuinely reusable beyond tooltips and hooks naturally into the existing `_check_mouseover` flow via `_mouseover_time`.
- Tests are substantial (~215 lines) and cover the widget API (`set_tooltip`/`get_tooltip`), kwargs pass-through, theme attribute defaults/validation, menu draw path with simulated hover, delay-not-elapsed case, and cross-menu tooltip filtering.

### Model B Cons

- No word-wrapping for long tooltip text. The `_draw_tooltip` method splits on `\n` for manual line breaks but doesn't wrap text that exceeds the tooltip surface width.
- `_draw_tooltip` is a method directly on `Menu` rather than being extracted into a separate class or module. It's a ~40-line render method with surface building, positioning, and border drawing all inline, which makes the already large `Menu` class even bigger.
- The `tooltip` default in `_filter_widget_attributes` is `''` (empty string) instead of `None`, so `set_tooltip` gets called for every widget even when no tooltip is configured — it unconditionally runs `set_tooltip(text='', delay_ms=...)` in `_configure_widget`.
- The boundary repositioning flips the tooltip above the cursor on bottom overflow but doesn't flip horizontally on right overflow — it just clamps to `sw - box_w`, so the tooltip can overlap the cursor on the right edge.
- `_draw_tooltip` builds a new surface every single frame when displaying. There's no caching — it calls `font.render()`, creates a new `Surface`, fills, blits, and draws the border each draw cycle.
- Exposing `tooltip_delay_ms` as a per-widget kwarg in `_filter_widget_attributes` adds a second axis of configuration that could conflict with the theme default. This is unnecessary complexity for a Turn 1 implementation.

---

## 2. Axis Ratings & Preference

| Axis                          | Rating |
|-------------------------------|--------|
| Logic and correctness         | 6 - Model B Slightly Preferred |
| Naming and clarity            | 7 - Model B Medium Preferred |
| Organization and modularity   | 4 - Model A Minimally Preferred |
| Interface design              | 6 - Model B Slightly Preferred |
| Error handling and robustness | 5 - Model B Minimally Preferred |
| Comments and documentation    | 6 - Model B Slightly Preferred |
| Review/production readiness   | 7 - Model B Medium Preferred |

**Choose the final better answer:** **6 - Model B Slightly Preferred**

---

## 3. Overall Preference Justification

Model B is the better response here, primarily because it follows the codebase's naming conventions (`widget_tooltip_*`) while Model A uses a non-standard prefix pattern (`tooltip_*`), which would immediately get flagged in a real PR review since every other widget-related theme property in this codebase uses the `widget_` prefix. Model B also provides a much richer set of theme properties — including a dedicated tooltip font, antialias toggle, and separate margin control — whereas Model A reuses `widget_font` for tooltip rendering, leaving no way to style tooltip text independently. On the testing front, Model B includes ~215 lines of tests covering the widget API, kwargs, theme validation, drawing paths, and cross-menu isolation, while Model A ships zero tests, which is a significant gap for a feature involving timing and rendering. Model A does have the structural advantage of a dedicated `_TooltipManager` class with surface caching, which is cleaner architecturally than Model B's inline `_draw_tooltip` that rebuilds the surface every frame — but this organizational plus isn't enough to overcome the naming, testing, and completeness gaps. Both models lack word-wrapping and have incomplete boundary clamping, so those are a wash. Overall, Model B delivers a more convention-aware, well-tested, and complete implementation that would require less rework to merge.

---

## 4. Next Step / Follow-Up Prompt (Turn 2)

> A few things to address from the tooltip implementation:
> 
> 1. **Surface caching**: You're rebuilding the tooltip surface every frame in `_draw_tooltip` — that's a `font.render()` call, a `Surface` create, a `fill`, multiple `blit`s, and a `draw.rect` call happening 30-60 times per second for a static text box. Cache the rendered tooltip surface on the widget or menu level and only invalidate it when the tooltip text changes.
> 
> 2. **Word-wrapping**: Long tooltip text currently renders as a single unwrapped line that can extend way past the window edge. Add word-wrapping logic that breaks text at word boundaries to fit within a `widget_tooltip_max_width` theme property (default something reasonable like 300px). The `\n` splitting you already have should still work for explicit line breaks.
> 
> 3. **Boundary positioning**: The horizontal overflow case just clamps to `sw - box_w`, which means the tooltip can sit right on top of the cursor. Flip it to the left of the cursor (like you do vertically) when it would overflow the right edge, and clamp to 0 as a fallback so it never goes off-screen on any edge.
> 
> 4. **Empty-string tooltip overhead**: Right now `_filter_widget_attributes` defaults `tooltip` to `''`, so `set_tooltip(text='', ...)` gets called for every single widget even when no tooltip is wanted. Change the default to `None` and skip the `set_tooltip` call in `_configure_widget` when it's `None` — no need to run that code path for widgets that don't use tooltips.
