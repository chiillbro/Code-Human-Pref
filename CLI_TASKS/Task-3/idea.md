# Task-1: Widget Animation & Transition System

## Task ID
Task-01

## Type
Substantial New Feature

## Core Request (Turn 1)

### Summary
Implement a comprehensive **animation and transition system** for pygame-menu widgets. Today, all widget state changes (show/hide, enable/disable, focus/blur, value changes) are instantaneous — there are no fade-ins, slide-ins, scale transitions, or easing curves. This task requires building a reusable animation engine that any widget can hook into, allowing smooth visual transitions between states.

### Detailed Requirements

1. **Animation Engine (`pygame_menu/animation.py` — new file):**
   - Create an `Animation` class that represents a single tween/transition. It must track: start value, end value, duration (ms), elapsed time, easing function, completion callback, and loop/bounce settings.
   - Create an `AnimationManager` class (singleton-per-menu) that maintains a list of active `Animation` instances, ticks them forward each frame via a single `update(dt)` call, removes completed animations, and fires their callbacks.
   - Implement at least 6 easing functions: `linear`, `ease_in_quad`, `ease_out_quad`, `ease_in_out_quad`, `ease_in_cubic`, `ease_out_back`. These must be pure stateless functions that accept `t` (0.0–1.0) and return the eased value.

2. **Widget Integration (`pygame_menu/widgets/core/widget.py`):**
   - Add methods to the base `Widget` class: `animate_property(property_name, target_value, duration_ms, easing, on_complete)`, `cancel_animations()`, `has_active_animation()`.
   - Animatable properties must include at minimum: `_font_color` (color transitions), `_padding` (padding transitions), `_translate_virtual` (position/slide transitions), and `_opacity` (a new alpha channel property for fade effects).
   - The widget's `_draw()` and `_render()` pipeline must respect the current `_opacity` value to support fade-in/fade-out (use `Surface.set_alpha()`).

3. **Opacity Support (`pygame_menu/widgets/core/widget.py`):**
   - Add a new `_opacity` attribute (int, 0–255, default 255) to the Widget base class.
   - Implement `set_opacity(opacity)` and `get_opacity()` public methods.
   - During rendering, apply the opacity value to the widget's surface via `set_alpha()` before blitting.

4. **Pre-built Transition Helpers (`pygame_menu/transitions.py` — new file):**
   - `fade_in(widget, duration_ms=300, easing='ease_out_quad')` — animates opacity from 0 to 255.
   - `fade_out(widget, duration_ms=300, easing='ease_out_quad')` — animates opacity from 255 to 0.
   - `slide_in(widget, direction='left', distance=50, duration_ms=400, easing='ease_out_cubic')` — translates the widget from an offset to its natural position.
   - `slide_out(widget, direction='right', distance=50, duration_ms=400, easing='ease_out_cubic')` — translates the widget away from its natural position.
   - `color_transition(widget, from_color, to_color, duration_ms=500, easing='linear')` — smoothly transitions font color.
   - `pulse(widget, scale=1.1, duration_ms=600, easing='ease_in_out_quad', loop=True)` — grows/shrinks widget padding to create a pulsing effect.

5. **Menu-level Hook (`pygame_menu/menu.py`):**
   - The `Menu` must instantiate its own `AnimationManager` and call `animation_manager.update(dt)` during every frame of `mainloop()` and `update()`.
   - When widgets are added to a menu, they must be registered with the menu's `AnimationManager`.
   - Add a `Menu.set_widget_entrance_animation(animation_fn)` method that, if set, automatically applies the given animation function to every widget when the menu is first displayed.

6. **Theme Integration (`pygame_menu/themes.py`):**
   - Add new theme attributes: `widget_entrance_animation`, `widget_entrance_animation_duration`, and `widget_entrance_animation_easing` that allow themes to declare a default entrance animation (e.g., `fade_in`) applied to all widgets when the menu opens.

---

## Expected PR Review Feedback (Turns 2 & 3)

### Turn 2 — Anticipated Flaws to Critique

1. **Missing delta-time calculation:** The model will likely pass a hardcoded `dt` or compute it incorrectly inside `mainloop()`. The animation manager's `update(dt)` must use the real elapsed time from `pygame.time.Clock` (which `mainloop()` already uses internally). Critique the delta-time source.
2. **Opacity applied too late or too early:** The model will likely set alpha on the final blitted surface but forget about the widget's shadow, border, or selection effect surfaces — causing visual artifacts where the border is fully opaque while the widget body fades. Demand consistency across all rendered layers.
3. **No animation cancellation on widget removal:** If a widget is removed from the menu while an animation is active, the `AnimationManager` will still hold a reference and try to update a dead widget. Demand cleanup logic in `Menu.remove_widget()`.
4. **Easing functions not clamped:** The `ease_out_back` easing intentionally overshoots (goes above 1.0). The model will probably not clamp the resulting interpolated values for properties like opacity (which must stay in 0–255) or color channels (0–255). Demand per-property clamping.
5. **Color interpolation done naively:** The model will interpolate RGB channels linearly as integers, which can produce visual banding. Ensure interpolation handles the tuple/list properly and rounds correctly for each channel, including the alpha channel if present.
6. **`pulse()` implementation brittle:** The model will likely modify padding directly, which will conflict with the theme's padding. It should use `_translate_virtual` or a dedicated scale transform rather than mutating padding.
7. **No guard against animating non-existent properties:** `animate_property('nonexistent', ...)` should raise a clear `AttributeError`, not silently fail.

### Turn 3 — Tests, Linting & Polish

1. **Unit tests for all 6 easing functions:** Verify boundary values (`t=0.0` → 0.0, `t=1.0` → 1.0), midpoint behavior, and that `ease_out_back` overshoots as expected.
2. **Unit tests for `AnimationManager`:** Test `update()` with controlled `dt`, verify completion callbacks fire, test removal of completed animations, test cancellation.
3. **Integration tests for fade_in/fade_out:** Create a widget, apply `fade_in`, step the animation manager forward, assert opacity changes at intermediate and final steps.
4. **Integration test for `slide_in`:** Verify `_translate_virtual` moves from offset to (0, 0).
5. **Test widget removal during animation:** Ensure no exceptions and the animation is cleaned up.
6. **Test theme entrance animation:** Create a menu with `widget_entrance_animation` set in the theme, verify it is applied when `mainloop` is first called.
7. **Linting:** Ensure all new code passes `ruff check .` and `ruff format .` with zero issues.
8. **Docstrings and type hints:** All public methods must have complete docstrings and type annotations consistent with the project's existing style.

---

## Why It Fits the Constraint

- **~550–650 lines of new core code:** `animation.py` (~200 lines: Animation class, AnimationManager, 6 easing functions), `transitions.py` (~150 lines: 6 transition helpers with parameter validation), `widget.py` modifications (~100 lines: opacity property, animate_property, cancel_animations, render pipeline changes), `menu.py` modifications (~60 lines: AnimationManager hookup, entrance animation, widget registration/cleanup), `themes.py` modifications (~40 lines: new theme attributes + validation).
- **High difficulty, single-turn imperfect:** The task requires threading animation state through the existing render pipeline (widget → surface → shadow → border → selection effect → scroll area), which is deeply layered. The model must understand `_draw()`, `_render()`, `_draw_shadow()`, `_draw_border()`, and `_draw_selection_effect()` — and correctly apply opacity at the right stage. It also must integrate with the existing `mainloop()` timing without breaking the event loop. This complexity ensures a naturally flawed first attempt.
- **No scope creep possible in Turn 2/3:** All 6 transition types and the full animation engine are defined in Turn 1. Turn 2 only fixes flawed implementations of those same features. Turn 3 adds tests and polish.

---

## Potential Files Modified

| # | File Path | Change Type |
|---|---|---|
| 1 | `pygame_menu/animation.py` | **New file** — Animation, AnimationManager, easing functions |
| 2 | `pygame_menu/transitions.py` | **New file** — Pre-built transition helpers |
| 3 | `pygame_menu/widgets/core/widget.py` | Modify — add opacity, animate_property, render pipeline |
| 4 | `pygame_menu/menu.py` | Modify — AnimationManager integration, entrance hooks |
| 5 | `pygame_menu/themes.py` | Modify — new entrance animation theme attributes |
| 6 | `pygame_menu/__init__.py` | Modify — export new modules |

---

## PR Overview — Implementation Summary

### What Was Built

A comprehensive **Widget Animation & Transition System** for pygame-menu, enabling smooth visual transitions (fade, slide, color, pulse) on any widget via a frame-driven animation engine with configurable easing functions.

### Files Changed

| File | Status | Lines | Description |
|------|--------|-------|-------------|
| `pygame_menu/animation.py` | **New** | ~350 | Core animation engine: 7 easing functions (`linear`, `ease_in_quad`, `ease_out_quad`, `ease_in_out_quad`, `ease_in_cubic`, `ease_out_cubic`, `ease_out_back`), `Animation` class (per-property tweening with bounce/loop/callbacks/clamping), `AnimationManager` (lifecycle management, per-widget tracking, cancellation) |
| `pygame_menu/transitions.py` | **New** | ~310 | Pre-built helpers: `fade_in()`, `fade_out()`, `slide_in()`, `slide_out()`, `color_transition()`, `pulse()` — all retrieve the `AnimationManager` from the widget's menu automatically |
| `pygame_menu/widgets/core/widget.py` | **Modified** | +150 | Added `_opacity` attribute (0–255), rewrote `draw()` for opacity compositing via `_OffsetSurface` proxy (ensures shadow/border/decorator layers all share uniform opacity), added public API: `set_opacity()`, `get_opacity()`, `animate_property()`, `cancel_animations()`, `has_active_animation()` |
| `pygame_menu/menu.py` | **Modified** | +20 | Instantiated `AnimationManager` in `__init__()`, added `get_animation_manager()` method, wired `animation_manager.update(dt)` into `mainloop()` using real delta-time from `Clock.get_time()`, added animation cleanup in `remove_widget()` |
| `pygame_menu/themes.py` | **Modified** | +20 | Added `widget_entrance_animation` (str \| None), `widget_entrance_animation_duration` (int, default 300), `widget_entrance_animation_easing` (str, default `'ease_out_quad'`) type annotations, `__init__` kwargs, and `validate()` assertions |
| `pygame_menu/__init__.py` | **Modified** | +4 | Exported `animation` and `transitions` modules; hardened metadata parsing against `IndexError`/`AttributeError` |
| `test/test_animation.py` | **New** | ~230 | 36 tests: easing boundary/monotonicity checks, `_interpolate` scalar/color/tuple, `_clamp_opacity`, `Animation` lifecycle/callback/bounce/clamping, `AnimationManager` create/cancel/clear |
| `test/test_transitions.py` | **New** | ~155 | 25 tests: fade in/out opacity, slide in/out 4 directions + invalid, color transition completion, pulse registration, Widget opacity API, `animate_property`/`cancel_animations`, Menu integration + widget removal cleanup |

### Key Design Decisions

1. **`_OffsetSurface` proxy pattern** — Rather than modifying every sub-draw method (`_draw_shadow`, `_draw_background_color`, `_draw_border`, decorators), the `draw()` method redirects all rendering to a temporary surface via a coordinate-translating proxy. The composite is then blitted with `set_alpha()`, ensuring all layers share the same opacity.

2. **Per-property clampers** — The `_PROPERTY_CLAMPERS` dict maps property names (e.g. `_opacity`) to bounding functions, safely handling easing overshoot (e.g. `ease_out_back` exceeding 1.0).

3. **Color interpolation** — `_interpolate()` detects 3/4-element color tuples and applies per-channel clamping (0–255) with proper rounding, preventing visual artifacts from naive linear interpolation.

4. **Real delta-time** — `mainloop()` uses `Clock.get_time()` after `Clock.tick()` to feed the true elapsed milliseconds to the animation manager, not a hardcoded value.

5. **Automatic cleanup on widget removal** — `Menu.remove_widget()` calls `animation_manager.cancel_for_widget(widget)` before detaching, preventing dangling references.

### Test Results

```
61 passed in 1.21s (animation + transitions)
714 passed, 1 skipped (full regression including test_menu, test_themes, test_widgets)
Ruff lint: All checks passed!
```
